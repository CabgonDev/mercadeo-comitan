@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Anuncios

import com.cabgon.mercadeocomitan.ProgressDialogCompat
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.View
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorImagenSeleccionada
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.ModeloImagenSeleccionada
import com.cabgon.mercadeocomitan.Modelo.ModeloUsuario
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.SeleccionarUbicacion
import com.cabgon.mercadeocomitan.billing.BillingHelper
import com.cabgon.mercadeocomitan.databinding.ActivityCrearAnuncioBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.cabgon.mercadeocomitan.utils.ImageUtils
import com.cabgon.mercadeocomitan.utils.ValidationUtils
import com.cabgon.mercadeocomitan.utils.MessageUtils

class CrearAnuncio : AppCompatActivity() {

    // =============================
    // USUARIO ACTUAL
    // =============================
    private var miUsuario: ModeloUsuario? = null

    private lateinit var binding: ActivityCrearAnuncioBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialogCompat
    private lateinit var billing: BillingHelper

    private var imagenUri: Uri? = null
    private val imagenSelecArrayList = ArrayList<ModeloImagenSeleccionada>()
    private lateinit var adaptadorImagenSeleccionada: AdaptadorImagenSeleccionada

    private var edicion = false
    private var idAnuncioEditar = ""

    private var marca = ""
    private var categoria = ""
    private var condicion = ""
    private var direccion = ""
    private var precio = ""
    private var titulo = ""
    private var descripcion = ""
    private var latitud = 0.0
    private var longitud = 0.0

    private companion object {
        const val MAX_IMAGENES = 8
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCrearAnuncioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val ctaBaseMarginBottom = (binding.btnCrearAnuncio.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin
        val paywallBasePaddingBottom = binding.panelPaywall.paddingBottom
        val scrollBasePaddingTop = binding.scrollCrear.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootCrearAnuncio) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(bars.bottom, ime.bottom)

            binding.scrollCrear.setPadding(
                binding.scrollCrear.paddingLeft,
                scrollBasePaddingTop + bars.top,
                binding.scrollCrear.paddingRight,
                binding.scrollCrear.paddingBottom
            )

            val ctaParams = binding.btnCrearAnuncio.layoutParams as android.view.ViewGroup.MarginLayoutParams
            ctaParams.bottomMargin = ctaBaseMarginBottom + bottomInset
            binding.btnCrearAnuncio.layoutParams = ctaParams

            binding.panelPaywall.setPadding(
                binding.panelPaywall.paddingLeft,
                bars.top,
                binding.panelPaywall.paddingRight,
                paywallBasePaddingBottom + bottomInset
            )
            insets
        }

        firebaseAuth = FirebaseAuth.getInstance()
        // ⚠️ si en Play tu producto se llama diferente, cámbialo AQUÍ
        billing = BillingHelper(this, BillingHelper.PRODUCT_ID)

        progressDialog = ProgressDialogCompat(this)
        progressDialog.setTitle(getString(R.string.progress_title))
        progressDialog.setCanceledOnTouchOutside(false)

        // 🔹 PRE-conectar y PRE-cargar producto de pago único
        billing.startConnection { ok ->
            if (ok) {
                billing.loadProduct { loaded ->
                    if (!loaded) {
                        toast("Producto no encontrado en Play")
                    }
                }
            } else {
                // opcional mostrar algo
                // toast("Billing no disponible")
            }
        }

        // fondo animado
        val animBg = binding.rootCrearAnuncio.background
        if (animBg is android.graphics.drawable.AnimationDrawable) {
            animBg.setEnterFadeDuration(800)
            animBg.setExitFadeDuration(2500)
            animBg.start()
        }

        // ¿viene en modo edición?
        edicion = intent.getBooleanExtra("Edicion", false)
        if (edicion) {
            idAnuncioEditar = intent.getStringExtra("idAnuncio")?.trim().orEmpty()
            if (!Constantes.esClaveFirebaseValida(idAnuncioEditar)) {
                toast("No se pudo identificar el anuncio a editar")
                finish()
                return
            }
        }
        // 🔹 primero cargamos al usuario y decidimos si formulario o paywall
        cargarUsuarioActual()

        // listeners de formulario
        binding.btnAgregarFotos.setOnClickListener { mostrarOpciones() }

        // autocompletes
        val adaptadorCat = ArrayAdapter(this, R.layout.item_categoria, Constantes.categorias)
        binding.etCategoria.setAdapter(adaptadorCat)

        val adaptadorCon = ArrayAdapter(this, R.layout.item_condicion, Constantes.condiciones)
        binding.etCondicion.setAdapter(adaptadorCon)

        binding.etLocacion.setOnClickListener {
            val intent = Intent(this, SeleccionarUbicacion::class.java)
            seleccionarUbicacionArl.launch(intent)
        }

        binding.tilLocacion.setEndIconOnClickListener {
            binding.etLocacion.performClick()
        }

        binding.btnCrearAnuncio.setOnClickListener {
            validarDatos()
        }
    }

    // =========================================================
    // 1) Cargar usuario y decidir si vemos formulario o paywall
    // =========================================================
    private fun cargarUsuarioActual() {
        val uid = firebaseAuth.uid
        if (uid == null) {
            mostrarPaywall()
            return
        }

        val ref = FirebaseDatabase.getInstance().getReference("Usuarios").child(uid)
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                miUsuario = snapshot.getValue(ModeloUsuario::class.java)

                // 🔸 Si es admin / superadmin / tiene flag -> pasa directo
                if (puedePublicarSinPagar()) {
                    FirebaseDatabase.getInstance().reference
                        .child("Usuarios").child(uid)
                        .child("canPost").setValue(true)

                    mostrarFormulario()
                    if (edicion) {
                        cargarDetalles()
                        binding.btnCrearAnuncio.setText(R.string.btn_guardar_cambios)
                    } else {
                        binding.btnCrearAnuncio.setText(R.string.btn_publicar_anuncio)
                    }
                    return
                }

                // 🔸 Si NO es admin -> usar canPost
                FirebaseDatabase.getInstance().reference
                    .child("Usuarios").child(uid)
                    .child("canPost")
                    .get()
                    .addOnSuccessListener { snap ->
                        val canPost = snap.getValue(Boolean::class.java) == true
                        if (canPost) {
                            mostrarFormulario()
                            if (edicion) {
                                cargarDetalles()
                                binding.btnCrearAnuncio.setText(R.string.btn_guardar_cambios)
                            } else {
                                binding.btnCrearAnuncio.setText(R.string.btn_publicar_anuncio)
                            }
                        } else {
                            mostrarPaywall()
                        }
                    }
                    .addOnFailureListener {
                        mostrarPaywall()
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                mostrarPaywall()
            }
        })
    }

    // =========================================================
    // ¿PUEDE PUBLICAR SIN PAGAR?
    // =========================================================
    private fun puedePublicarSinPagar(): Boolean {
        val rol = miUsuario?.rol
        val gratis = miUsuario?.puedePublicarGratis == true
        return rol == Constantes.ROL_SUPERADMIN ||
                rol == Constantes.ROL_ADMIN ||
                gratis
    }

    // =========================================================
    // PAYWALL
    // =========================================================
    private fun mostrarPaywall() {
        binding.panelPaywall.visibility = View.VISIBLE
        binding.formPublicar.visibility = View.GONE

        binding.btnComprar.setOnClickListener {
            // 👉 si por alguna razón el admin cae aquí, lo dejamos pasar
            if (puedePublicarSinPagar()) {
                val uid = firebaseAuth.uid
                if (uid != null) {
                    FirebaseDatabase.getInstance().reference
                        .child("Usuarios").child(uid)
                        .child("canPost").setValue(true)
                }
                toast("Tienes permisos para publicar sin pagar")
                mostrarFormulario()
                if (edicion) {
                    cargarDetalles()
                    binding.btnCrearAnuncio.setText(R.string.btn_guardar_cambios)
                } else {
                    binding.btnCrearAnuncio.setText(R.string.btn_publicar_anuncio)
                }
                return@setOnClickListener
            }

            // 👉 aquí ya SUPONEMOS que Billing está conectado y el producto cargado
            billing.launchPurchase(this@CrearAnuncio) { success, error ->
                if (success) {
                    // animación
                    binding.lottieUnlock.visibility = View.VISIBLE
                    binding.lottieUnlock.playAnimation()
                    binding.lottieUnlock.addAnimatorListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            binding.lottieUnlock.visibility = View.GONE
                        }
                    })

                    // guardar en Firebase
                    val uid2 = firebaseAuth.uid
                    if (uid2 != null) {
                        FirebaseDatabase.getInstance().reference
                            .child("Usuarios").child(uid2)
                            .child("canPost").setValue(true)
                    }

                    toast("¡Listo! Ya puedes publicar")
                    mostrarFormulario()
                    if (edicion) {
                        cargarDetalles()
                        binding.btnCrearAnuncio.setText(R.string.btn_guardar_cambios)
                    } else {
                        binding.btnCrearAnuncio.setText(R.string.btn_publicar_anuncio)
                    }
                } else {
                    toast(error ?: "No se pudo completar")
                }
            }
        }
    }

    private fun mostrarFormulario() {
        binding.panelPaywall.visibility = View.GONE
        binding.formPublicar.visibility = View.VISIBLE
        cargarImagenes()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // =========================================================
    // EDICIÓN
    // =========================================================
    private fun cargarDetalles() {
        val ref = FirebaseDatabase.getInstance().getReference("Anuncios")
        ref.child(idAnuncioEditar)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                val uidActual = firebaseAuth.uid
                val propietario = snapshot.child("uid").getValue(String::class.java)
                if (!snapshot.exists() || uidActual.isNullOrBlank() || propietario != uidActual) {
                    toast("No tienes acceso a este anuncio")
                    finish()
                    return
                }

                    val tituloDb = snapshot.child("titulo").getValue(String::class.java).orEmpty()
                    val categoriaDb = snapshot.child("categoria").getValue(String::class.java).orEmpty()
                    val condicionDb = snapshot.child("condicion").getValue(String::class.java).orEmpty()
                    val locacionDb = snapshot.child("direccion").getValue(String::class.java).orEmpty()
                    val precioDb = snapshot.child("precio").getValue(String::class.java).orEmpty()
                    val marcaDb = snapshot.child("marca").getValue(String::class.java).orEmpty()
                    val descripcionDb = snapshot.child("descripcion").getValue(String::class.java).orEmpty()
                    latitud = (snapshot.child("latitud").value as? Number)?.toDouble() ?: 0.0
                    longitud = (snapshot.child("longitud").value as? Number)?.toDouble() ?: 0.0

                    binding.etTitulo.setText(tituloDb)
                    binding.etCategoria.setText(categoriaDb, false)
                    binding.etCondicion.setText(condicionDb, false)
                    binding.etLocacion.setText(locacionDb)
                    binding.etPrecio.setText(precioDb)
                    binding.etMarca.setText(marcaDb)
                    binding.etDescripcion.setText(descripcionDb)

                    val refimagenes = snapshot.child("Imagenes").ref
                    refimagenes.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(s2: DataSnapshot) {
                            imagenSelecArrayList.clear()
                            for (ds in s2.children) {
                                val id = ds.child("id").getValue(String::class.java)
                                    .orEmpty()
                                    .ifBlank { ds.key.orEmpty() }
                                val imagenUrl = ds.child("imagenUrl").getValue(String::class.java)
                                    ?: ds.child("urlImagen").getValue(String::class.java)
                                if (id.isNotBlank() && !imagenUrl.isNullOrBlank()) {
                                    val modelo = ModeloImagenSeleccionada(id, null, imagenUrl, true)
                                    imagenSelecArrayList.add(modelo)
                                }
                            }
                            cargarImagenes()
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // =========================================================
    // VALIDAR
    // =========================================================
    private fun validarDatos() {
        listOf(
            binding.tilMarca,
            binding.tilCategoria,
            binding.tilCondicion,
            binding.tilLocacion,
            binding.tilPrecio,
            binding.tilTitulo,
            binding.tilDescripcion
        ).forEach { it.error = null }

        marca = binding.etMarca.text.toString().trim()
        categoria = binding.etCategoria.text.toString().trim()
        condicion = binding.etCondicion.text.toString().trim()
        direccion = binding.etLocacion.text.toString().trim()
        precio = binding.etPrecio.text.toString().trim()
        titulo = binding.etTitulo.text.toString().trim()
        descripcion = binding.etDescripcion.text.toString().trim()

        when {
            !ValidationUtils.isValidLength(marca, 1, 100) -> {
                binding.tilMarca.error = "Agrega la marca (1-100 caracteres)"
                binding.etMarca.requestFocus()
            }
            categoria.isEmpty() -> {
                binding.tilCategoria.error = "Elige una categoría"
                binding.etCategoria.requestFocus()
            }
            condicion.isEmpty() -> {
                binding.tilCondicion.error = "Elige la condición"
                binding.etCondicion.requestFocus()
            }
            direccion.isEmpty() -> {
                binding.tilLocacion.error = "Elige una ubicación"
                binding.etLocacion.requestFocus()
            }
            !ValidationUtils.isValidPrice(precio) -> {
                binding.tilPrecio.error = "Ingresa un precio válido mayor que cero"
                binding.etPrecio.requestFocus()
            }
            !ValidationUtils.isValidLength(titulo, 3, 120) -> {
                binding.tilTitulo.error = "El título debe tener entre 3 y 120 caracteres"
                binding.etTitulo.requestFocus()
            }
            !ValidationUtils.isValidLength(descripcion, 10, 1000) -> {
                binding.tilDescripcion.error = "La descripción debe tener entre 10 y 1000 caracteres"
                binding.etDescripcion.requestFocus()
            }
            !ValidationUtils.isSafeText(titulo) || !ValidationUtils.isSafeText(descripcion) -> {
                MessageUtils.showError(binding.root, "El texto contiene caracteres no permitidos")
            }
            else -> {
                if (edicion) actualizarAnuncio() else {
                    if (imagenSelecArrayList.isEmpty()) {
                        MessageUtils.showWarning(binding.root, "Agrega al menos UNA imagen")
                    } else {
                        agregarAnuncio()
                    }
                }
            }
        }
    }

    private fun normalizarTextoPrecio(raw: String): String {
        val limpio = raw.trim().replace("$", "").replace(" ", "")
        if (limpio.isEmpty()) return ""
        return when {
            limpio.contains(',') && limpio.contains('.') -> {
                if (limpio.lastIndexOf('.') > limpio.lastIndexOf(',')) {
                    limpio.replace(",", "")
                } else {
                    limpio.replace(".", "").replace(",", ".")
                }
            }
            limpio.contains(',') -> {
                val partes = limpio.split(',')
                if (partes.size == 2 && partes[1].length <= 2) {
                    limpio.replace(",", ".")
                } else {
                    limpio.replace(",", "")
                }
            }
            else -> limpio
        }
    }

    // =========================================================
    // ACTUALIZAR
    // =========================================================
    private fun actualizarAnuncio() {
        progressDialog.setMessage("Actualizando anuncio...")
        progressDialog.show()

        val hashMap = hashMapOf<String, Any>(
            "marca" to marca,
            "categoria" to categoria,
            "condicion" to condicion,
            "direccion" to direccion,
            "precio" to precio,
            "titulo" to titulo,
            "descripcion" to descripcion,
            "latitud" to latitud,
            "longitud" to longitud
        )

        FirebaseDatabase.getInstance().getReference("Anuncios")
            .child(idAnuncioEditar)
            .updateChildren(hashMap)
            .addOnSuccessListener {
                cargarImagenesStorage(idAnuncioEditar)
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                toast("Falló la actualización")
            }
    }

    // =========================================================
    // NUEVO
    // =========================================================
    private fun agregarAnuncio() {
        progressDialog.setMessage("Publicando Anuncio...")
        progressDialog.show()

        val tiempo = Constantes.obtenerTiempoDis()
        val ref = FirebaseDatabase.getInstance().getReference("Anuncios")
        val uid = firebaseAuth.uid
        if (uid.isNullOrBlank()) {
            progressDialog.dismiss()
            toast("Tu sesión expiró. Inicia sesión nuevamente")
            finish()
            return
        }
        val keyId = ref.push().key
        if (keyId.isNullOrBlank()) {
            progressDialog.dismiss()
            toast("No se pudo preparar el anuncio")
            return
        }

        val hashMap = HashMap<String, Any>()
        hashMap["id"] = keyId
        hashMap["uid"] = uid
        hashMap["marca"] = marca
        hashMap["categoria"] = categoria
        hashMap["condicion"] = condicion
        hashMap["direccion"] = direccion
        hashMap["precio"] = precio
        hashMap["titulo"] = titulo
        hashMap["descripcion"] = descripcion
        hashMap["estado"] = Constantes.anuncio_disponible
        hashMap["tiempo"] = tiempo
        hashMap["latitud"] = latitud
        hashMap["longitud"] = longitud
        hashMap["contadorVistas"] = 0

        ref.child(keyId)
            .setValue(hashMap)
            .addOnSuccessListener {
                cargarImagenesStorage(keyId)
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                toast("No se pudo publicar")
            }
    }

    // =========================================================
    // SUBIR IMÁGENES
    // =========================================================
    private fun cargarImagenesStorage(keyId: String) {
        if (imagenSelecArrayList.isEmpty()) {
            progressDialog.dismiss()
            onBackPressedDispatcher.onBackPressed()
            return
        }

        val imagenesPendientes = imagenSelecArrayList.filterNot { it.deInternet }
        if (imagenesPendientes.isEmpty()) {
            terminarSubida()
            return
        }

        val pendientes = AtomicInteger(imagenesPendientes.size)
        var huboError = false

        fun finalizarImagen(error: String? = null) {
            if (error != null) {
                huboError = true
                if (!isFinishing && !isDestroyed) toast(error)
            }
            if (pendientes.decrementAndGet() == 0) {
                if (huboError) {
                    progressDialog.dismiss()
                    if (!isFinishing && !isDestroyed) toast("No se pudieron subir todas las imágenes")
                } else {
                    terminarSubida()
                }
            }
        }

        lifecycleScope.launch {
            for (modelo in imagenesPendientes) {
                val uri = modelo.imagenUri
                if (uri == null) {
                    finalizarImagen("Se encontró una imagen inválida")
                    continue
                }

                // Comprimir la imagen antes de subir
                val compressed = ImageUtils.compressImage(this@CrearAnuncio, uri)
                if (compressed == null) {
                    finalizarImagen("No se pudo comprimir la imagen")
                    continue
                }

                val nombreImagen = modelo.id
                val storageRef = FirebaseStorage.getInstance().getReference("Anuncios/$nombreImagen")

                // Subir bytes comprimidos en lugar del archivo original
                storageRef.putBytes(compressed)
                    .addOnSuccessListener {
                        storageRef.downloadUrl
                            .addOnSuccessListener { url ->
                                val data = hashMapOf<String, Any>(
                                    "id" to nombreImagen,
                                    "imagenUrl" to url.toString()
                                )
                                FirebaseDatabase.getInstance().getReference("Anuncios")
                                    .child(keyId).child("Imagenes").child(nombreImagen)
                                    .updateChildren(data)
                                    .addOnSuccessListener { finalizarImagen() }
                                    .addOnFailureListener { error ->
                                        storageRef.delete()
                                        finalizarImagen(error.message ?: "No se pudo guardar la imagen")
                                    }
                            }
                            .addOnFailureListener { error ->
                                storageRef.delete()
                                finalizarImagen(error.message ?: "No se pudo obtener la imagen")
                            }
                    }
                    .addOnFailureListener { error ->
                        finalizarImagen(error.message ?: "No se pudo subir la imagen")
                    }
            }
        }
    }

    private fun terminarSubida() {
        progressDialog.dismiss()
        if (edicion) {
            toast("Su anuncio ha sido actualizado exitosamente")
            finish()
        } else {
            toast("Su anuncio ha sido publicado exitosamente")
            onBackPressedDispatcher.onBackPressed()
        }
    }

    // =========================================================
    // IMÁGENES
    // =========================================================
    private fun mostrarOpciones() {
        val popupMenu = PopupMenu(this, binding.btnAgregarFotos)
        popupMenu.menu.add(Menu.NONE, 1, 1, "Camara")
        popupMenu.menu.add(Menu.NONE, 2, 2, "Galeria")
        popupMenu.show()

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    solicitarPermisoCamara.launch(arrayOf(android.Manifest.permission.CAMERA))
                }
                2 -> {
                    imagenGaleria()
                }
            }
            true
        }
    }

    private fun imagenGaleria() {
        resultadoGaleria.launch("image/*")
    }

    private val resultadoGaleria =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) agregarImagenLocal(uri) else toast("Cancelado")
        }

    private val solicitarPermisoCamara =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            val allOk = res.values.all { it }
            if (allOk) imageCamara() else toast("No se dieron los permisos")
        }

    private fun imageCamara() {
        val contentValues = ContentValues()
        contentValues.put(MediaStore.Images.Media.TITLE, "Titulo_imagen")
        contentValues.put(MediaStore.Images.Media.DESCRIPTION, "Descripcion_imagen")
        imagenUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imagenUri == null) {
            toast("No se pudo preparar la cámara")
            return
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, imagenUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        resultadoCamaraArl.launch(intent)
    }

    private val resultadoCamaraArl =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK) {
                imagenUri?.let { uri ->
                    agregarImagenLocal(uri)
                } ?: toast("La cámara no devolvió una imagen")
            } else {
                imagenUri?.let { contentResolver.delete(it, null, null) }
                imagenUri = null
                toast("Cancelado")
            }
        }

    private fun agregarImagenLocal(uri: Uri) {
        if (imagenSelecArrayList.size >= MAX_IMAGENES) {
            toast("Puedes agregar hasta $MAX_IMAGENES fotos")
            return
        }

        // Validar dimensiones de la imagen
        if (!ImageUtils.isValidImageSize(this, uri)) {
            MessageUtils.showError(
                binding.root,
                "La imagen debe tener entre 200x200 y 8192x8192 píxeles"
            )
            return
        }

        // Obtener tamaño original
        val originalSize = ImageUtils.getImageSize(this, uri)
        val originalSizeMB = originalSize / (1024f * 1024f)

        // Mostrar indicador de compresión
        progressDialog.setMessage("Optimizando imagen...")
        progressDialog.show()

        lifecycleScope.launch {
            try {
                // Comprimir imagen en background
                val compressed = ImageUtils.compressImage(this@CrearAnuncio, uri)

                progressDialog.dismiss()

                if (compressed != null) {
                    val compressedSizeMB = compressed.size / (1024f * 1024f)
                    val reduction = ((1 - compressedSizeMB / originalSizeMB) * 100).toInt()

                    // Agregar a la lista
                    imagenUri = uri
                    imagenSelecArrayList.add(
                        ModeloImagenSeleccionada(UUID.randomUUID().toString(), uri, null, false)
                    )
                    cargarImagenes()

                    // Mostrar feedback de compresión
                    if (reduction > 10) {
                        MessageUtils.showSuccess(
                            binding.root,
                            "Imagen optimizada: ${originalSizeMB.format(1)}MB → ${compressedSizeMB.format(1)}MB (-$reduction%)"
                        )
                    } else {
                        MessageUtils.showSuccess(binding.root, "Imagen agregada")
                    }
                } else {
                    MessageUtils.showError(
                        binding.root,
                        "No se pudo procesar la imagen. Intenta con otra."
                    )
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                MessageUtils.showError(
                    binding.root,
                    "Error al procesar la imagen: ${e.message}"
                )
            }
        }
    }

    private fun Float.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }

    private fun cargarImagenes() {
        adaptadorImagenSeleccionada =
            AdaptadorImagenSeleccionada(this, imagenSelecArrayList, idAnuncioEditar) {
                actualizarEstadoFotos()
            }
        binding.rvImagenes.adapter = adaptadorImagenSeleccionada
        actualizarEstadoFotos()
    }

    private fun actualizarEstadoFotos() {
        binding.tvFotosContador.text =
            getString(R.string.fotos_contador, imagenSelecArrayList.size, MAX_IMAGENES)
        binding.btnAgregarFotos.isEnabled = imagenSelecArrayList.size < MAX_IMAGENES

        val filas = maxOf(1, (imagenSelecArrayList.size + 3) / 4)
        val altoFila = (96 * resources.displayMetrics.density).toInt()
        val altoDeseado = if (imagenSelecArrayList.isEmpty()) {
            (104 * resources.displayMetrics.density).toInt()
        } else {
            filas * altoFila
        }
        val params = binding.rvImagenes.layoutParams
        if (params.height != altoDeseado) {
            params.height = altoDeseado
            binding.rvImagenes.layoutParams = params
        }
    }

    // seleccionar ubicación
    private val seleccionarUbicacionArl =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK) {
                val data = r.data
                if (data != null) {
                    latitud = data.getDoubleExtra("latitud", 0.0)
                    longitud = data.getDoubleExtra("longitud", 0.0)
                    direccion = data.getStringExtra("direccion") ?: ""
                    binding.etLocacion.setText(direccion)
                }
            } else {
                toast("Cancelado")
            }
        }

    override fun onDestroy() {
        progressDialog.dismiss()
        billing.endConnection()
        super.onDestroy()
    }
}
