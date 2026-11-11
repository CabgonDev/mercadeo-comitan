@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.DetalleAnuncio

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorImgSlider
import com.cabgon.mercadeocomitan.Anuncios.CrearAnuncio
import com.cabgon.mercadeocomitan.Chat.ChatActivity
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.DetalleVendedor.DetalleVendedor
import com.cabgon.mercadeocomitan.MainActivity
import com.cabgon.mercadeocomitan.Modelo.ModeloimgSlider
import com.cabgon.mercadeocomitan.Pedidos.CalificacionRepository
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.databinding.ActivityDetalleAnuncioBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.Locale

class DetalleAnuncio : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleAnuncioBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val calificacionRepository = CalificacionRepository()
    private var idAnuncio: String = ""
    private var bannerAdView: AdView? = null

    // Flag lógico para bloquear acción (sin deshabilitar botones)
    private var contactoHabilitado: Boolean = true

    // Colores originales para restaurar
    private var btnLlamarTextColorOriginal: Int = Color.WHITE
    private var btnSmsTextColorOriginal: Int = Color.WHITE
    private var btnLlamarIconTintOriginal: ColorStateList? = null
    private var btnSmsIconTintOriginal: ColorStateList? = null

    private var anuncioLatitud = 0.0
    private var anuncioLongitud = 0.0

    private var uidVendedor = ""
    private var telVendedor = ""
    private var favorito = false

    private lateinit var imagenSliderArrayList: ArrayList<ModeloimgSlider>

    // Refs y listeners para limpiar en onDestroy
    private var anuncioRef: DatabaseReference? = null
    private var anuncioListener: ValueEventListener? = null

    private var vendedorRef: DatabaseReference? = null
    private var vendedorListener: ValueEventListener? = null

    private var imagenesRef: DatabaseReference? = null
    private var imagenesListener: ValueEventListener? = null

    private var favoritosRef: DatabaseReference? = null
    private var favoritosListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleAnuncioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarInsets()

        // Ads
        MobileAds.initialize(this@DetalleAnuncio) {}
        val adView = AdView(this)
        adView.adUnitId = "ca-app-pub-7205378719919520/4714839549"
        adView.setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(this, 360))
        binding.adViewContainer.removeAllViews()
        binding.adViewContainer.addView(adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        bannerAdView = adView
        // Ads END

        // Oculta acciones por default hasta cargar
        binding.IbEditar.visibility = View.GONE
        binding.IbEliminar.visibility = View.GONE
        binding.BtnMapa.visibility = View.GONE
        binding.BtnLlamar.visibility = View.GONE
        binding.BtnSms.visibility = View.GONE
        binding.BtnChat.visibility = View.GONE
        binding.BtnComprar.visibility = View.GONE

        // Guarda colores/tintes originales
        btnLlamarTextColorOriginal = binding.BtnLlamar.currentTextColor
        btnSmsTextColorOriginal = binding.BtnSms.currentTextColor
        btnLlamarIconTintOriginal = binding.BtnLlamar.iconTint
        btnSmsIconTintOriginal = binding.BtnSms.iconTint

        firebaseAuth = FirebaseAuth.getInstance()

        // Valida idAnuncio recibido
        idAnuncio = intent.getStringExtra("idAnuncio")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" && Constantes.esClaveFirebaseValida(it) } ?: run {
            Toast.makeText(this, "No se recibió un anuncio válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Incrementa vistas
        Constantes.incrementarVistas(idAnuncio)

        comprobarAnuncioFav()
        cargarInfoAnuncio()
        cargarImgAnuncio()

        binding.IbEditar.setOnClickListener { opcionesDialog() }

        binding.IbFav.setOnClickListener {
            if (favorito) {
                Constantes.eliminarAnuncioFav(this, idAnuncio)
            } else {
                Constantes.agregarAnuncioFav(this, idAnuncio)
            }
        }

        binding.IbEliminar.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Eliminar anuncio")
                .setMessage("¿Estás seguro que deseas eliminar este anuncio?")
                .setPositiveButton("Eliminar") { dialog, _ ->
                    eliminarAnuncio()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        // WhatsApp (bloqueo con flag, sin deshabilitar el botón)
        binding.BtnSms.setOnClickListener {
            if (!contactoHabilitado) {
                Snackbar.make(binding.root, "El vendedor deshabilitó esta opción de contacto", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val numeroTelefono = telVendedor
            if (numeroTelefono.isBlank()) {
                Snackbar.make(binding.root, "El vendedor no tiene número telefónico", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val mensaje = Uri.encode("¡¡¡Hola! Me comunico por uno de tus anuncios en Mercadeo Comitán!!!")
            val uri = ("https://wa.me/$numeroTelefono/?text=$mensaje").toUri()
            val whats = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") }
            try {
                startActivity(whats)
            } catch (_: ActivityNotFoundException) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: ActivityNotFoundException) {
                    Snackbar.make(binding.root, "WhatsApp no está instalado", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        // Mapa
        binding.BtnMapa.setOnClickListener {
            Constantes.mapaIntent(this, anuncioLatitud, anuncioLongitud)
        }

        // Llamar (bloqueo con flag, sin deshabilitar el botón)
        binding.BtnLlamar.setOnClickListener {
            if (!contactoHabilitado) {
                Snackbar.make(binding.root, "El vendedor deshabilitó esta opción de contacto", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val numTel = telVendedor
            if (numTel.isBlank()) {
                Snackbar.make(binding.root, "El vendedor no tiene número telefónico", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Constantes.llamarIntent(this, numTel)
        }

        binding.BtnChat.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("uidVendedor", uidVendedor)
            startActivity(intent)
        }

        binding.BtnComprar.setOnClickListener {
            if (uidVendedor.isBlank()) {
                Toast.makeText(this, "El vendedor aún no está disponible", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, com.cabgon.mercadeocomitan.PedidoActivity::class.java).apply {
                putExtra("idAnuncio", idAnuncio)
                putExtra("uidVendedor", uidVendedor)
                putExtra("titulo", binding.TvTitulo.text?.toString().orEmpty())
                putExtra("precio", binding.TvPrecio.text?.toString().orEmpty())
            })
        }

        binding.IvInfoVendedor.setOnClickListener {
            val intent = Intent(this, DetalleVendedor::class.java)
            intent.putExtra("uidVendedor", uidVendedor)
            startActivity(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun configurarInsets() {
        val toolbarBaseHeight = binding.toolbarRl.layoutParams.height
        val toolbarBasePaddingTop = binding.toolbarRl.paddingTop
        val ctaBaseMarginBottom =
            (binding.BtnComprar.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin
        val scrollBasePaddingBottom = binding.detalleScroll.paddingBottom

        binding.root.setBackgroundColor(ContextCompat.getColor(this, R.color.fondo2))
        window.statusBarColor = ContextCompat.getColor(this, R.color.mi_color)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.md_theme_surface)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val toolbarParams = binding.toolbarRl.layoutParams
            toolbarParams.height = toolbarBaseHeight + bars.top
            binding.toolbarRl.layoutParams = toolbarParams
            binding.toolbarRl.setPadding(
                binding.toolbarRl.paddingLeft,
                toolbarBasePaddingTop + bars.top,
                binding.toolbarRl.paddingRight,
                binding.toolbarRl.paddingBottom
            )

            val ctaParams = binding.BtnComprar.layoutParams as android.view.ViewGroup.MarginLayoutParams
            ctaParams.bottomMargin = ctaBaseMarginBottom + bars.bottom
            binding.BtnComprar.layoutParams = ctaParams

            binding.detalleScroll.setPadding(
                binding.detalleScroll.paddingLeft,
                binding.detalleScroll.paddingTop,
                binding.detalleScroll.paddingRight,
                scrollBasePaddingBottom + bars.bottom
            )

            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    /** Aplica tinte gris y alpha cuando contacto está deshabilitado; restaura cuando está habilitado */
    private fun aplicarEstadoContactoUI(enabled: Boolean, tieneTelefono: Boolean? = null) {
        // Si explícitamente no hay teléfono, también atenuamos (aunque el switch esté on)
        val efectivamenteHabilitado = enabled && (tieneTelefono ?: true)

        val alpha = if (efectivamenteHabilitado) 1f else 0.5f
        binding.BtnLlamar.alpha = alpha
        binding.BtnSms.alpha = alpha

        // Colores
        val gris = "#FFFFFFFF".toColorInt() // Material Grey 500
        val grisList = ColorStateList.valueOf(gris)

        if (efectivamenteHabilitado) {
            // Restaurar originales
            binding.BtnLlamar.setTextColor(btnLlamarTextColorOriginal)
            binding.BtnSms.setTextColor(btnSmsTextColorOriginal)
            binding.BtnLlamar.iconTint = btnLlamarIconTintOriginal
            binding.BtnSms.iconTint = btnSmsIconTintOriginal
        } else {
            // Grisear
            binding.BtnLlamar.setTextColor(gris)
            binding.BtnSms.setTextColor(gris)
            binding.BtnLlamar.iconTint = grisList
            binding.BtnSms.iconTint = grisList
        }

        // Nunca los deshabilites para que el click llegue y podamos mostrar Snackbar
        binding.BtnLlamar.isEnabled = true
        binding.BtnLlamar.isClickable = true
        binding.BtnSms.isEnabled = true
        binding.BtnSms.isClickable = true
    }

    private fun opcionesDialog() {
        val popupMenu = PopupMenu(this, binding.IbEditar)
        popupMenu.menu.add(Menu.NONE, 0, 0, "Editar")
        popupMenu.menu.add(Menu.NONE, 1, 1, "Marcar como VENDIDO")
        popupMenu.show()

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> {
                    val intent = Intent(this, CrearAnuncio::class.java)
                    intent.putExtra("Edicion", true)
                    intent.putExtra("idAnuncio", idAnuncio)
                    startActivity(intent)
                }
                1 -> dialogMarcarVendido()
            }
            true
        }
    }

    private fun cargarInfoAnuncio() {
        anuncioRef = FirebaseDatabase.getInstance().getReference("Anuncios").child(idAnuncio)
        anuncioListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(this@DetalleAnuncio, "Anuncio no disponible", Toast.LENGTH_SHORT).show()
                    return
                }

                // Lectura directa (sin data class)
                uidVendedor = snapshot.child("uid").getValue(String::class.java).orEmpty()
                val titulo = snapshot.child("titulo").getValue(String::class.java).orEmpty()
                val descripcion = snapshot.child("descripcion").getValue(String::class.java).orEmpty()
                val direccion = snapshot.child("direccion").getValue(String::class.java).orEmpty()
                val condicion = snapshot.child("condicion").getValue(String::class.java).orEmpty() // p.ej. "Nuevo/Usado"
                val categoria = snapshot.child("categoria").getValue(String::class.java).orEmpty()
                val precio = snapshot.child("precio").getValue(String::class.java).orEmpty()
                val estado = snapshot.child("estado").getValue(String::class.java).orEmpty()       // p.ej. "Disponible/Vendido"
                val vistas = snapshot.child("contadorVistas").getValue(Long::class.java) ?: 0L
                anuncioLatitud = snapshot.child("latitud").getValue(Double::class.java) ?: 0.0
                anuncioLongitud = snapshot.child("longitud").getValue(Double::class.java) ?: 0.0

                val esDueno = firebaseAuth.currentUser?.uid != null && uidVendedor == firebaseAuth.uid
                if (esDueno) {
                    binding.IbEditar.visibility = View.VISIBLE
                    binding.IbEliminar.visibility = View.VISIBLE
                    binding.BtnMapa.visibility = View.GONE
                    binding.BtnLlamar.visibility = View.GONE
                    binding.BtnSms.visibility = View.GONE
                    binding.BtnChat.visibility = View.GONE
                    binding.BtnComprar.visibility = View.GONE
                    binding.TxtDescrVendedor.visibility = View.GONE
                    binding.perfilVendedor.visibility = View.GONE
                } else {
                    binding.IbEditar.visibility = View.GONE
                    binding.IbEliminar.visibility = View.GONE
                    binding.BtnMapa.visibility = View.VISIBLE
                    binding.BtnLlamar.visibility = View.VISIBLE
                    binding.BtnSms.visibility = View.VISIBLE
                    binding.BtnChat.visibility = View.VISIBLE
                    binding.BtnComprar.visibility = if (estado.equals(Constantes.anuncio_disponible, true)) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    binding.TxtDescrVendedor.visibility = View.VISIBLE
                    binding.perfilVendedor.visibility = View.VISIBLE
                }

                binding.TvTitulo.text = titulo
                binding.TvDescr.text = descripcion
                binding.TvDireccion.text = direccion
                binding.TvCondicion.text = estado     // ✔ condición (Nuevo/Usado)
                binding.TvCat.text = categoria
                binding.TvPrecio.text = precio
                binding.TvEstado.text = condicion           // ✔ estado (Disponible/Vendido)
                binding.TvVistas.text = vistas.toString()

                if (estado.equals("Disponible", true)) {
                    binding.TvSimboloPrecio.setTextColor("#48C9B0".toColorInt())
                    binding.TvPrecio.setTextColor("#48C9B0".toColorInt())
                    binding.TvCondicion.setTextColor("#48C9B0".toColorInt())
                } else {
                    binding.TvSimboloPrecio.setTextColor(Color.RED)
                    binding.TvPrecio.setTextColor(Color.RED)
                    binding.TvCondicion.setTextColor(Color.RED)
                }

                cargarInfoVendedor()
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("DetalleAnuncio", "DB cancel anuncio", error.toException())
            }
        }
        anuncioRef!!.addValueEventListener(anuncioListener as ValueEventListener)
    }

    private fun marcarAnuncioVendido() {
        val hasMap = HashMap<String, Any>()
        hasMap["estado"] = Constantes.anuncio_vendido
        val ref = FirebaseDatabase.getInstance().getReference("Anuncios")
        ref.child(idAnuncio)
            .updateChildren(hasMap)
            .addOnSuccessListener {
                Toast.makeText(this, "El anuncio ha sido marcado como vendido", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "No se marcó como vendido debido a ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun dialogMarcarVendido() {
        if (isFinishing || isDestroyed) return
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.cuadro_d_marcar_vendido)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val btnSi: MaterialButton = dialog.findViewById(R.id.Btn_siM)
        val btnNo: MaterialButton = dialog.findViewById(R.id.Btn_noM)

        btnSi.setOnClickListener {
            marcarAnuncioVendido()
            dialog.dismiss()
        }
        btnNo.setOnClickListener { dialog.dismiss() }

        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun cargarInfoVendedor() {
        val uid = uidVendedor
        if (uid.isBlank() || !Constantes.esClaveFirebaseValida(uid)) {
            android.util.Log.w("DetalleAnuncio", "uidVendedor inválido; no se cargará vendedor")
            return
        }

        vendedorListener?.let { vendedorRef?.removeEventListener(it) }
        vendedorRef = FirebaseDatabase.getInstance().getReference("Usuarios").child(uid)
        vendedorListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    android.util.Log.w("DetalleAnuncio", "Usuario no existe: $uid")
                    binding.TxtDescrVendedor.visibility = View.GONE
                    binding.perfilVendedor.visibility = View.GONE
                    return
                }

                // Switch de contacto del vendedor (flag + feedback visual)
                val habilitarContacto = snapshot.child("habilitarContacto").getValue(Boolean::class.java) ?: true
                val esDueno = firebaseAuth.currentUser?.uid != null && uidVendedor == firebaseAuth.uid
                if (!esDueno) {
                    contactoHabilitado = habilitarContacto
                }

                // Datos del vendedor
                val telefono = snapshot.child("telefono").getValue(String::class.java).orEmpty()
                val codTel = snapshot.child("codigoTelefono").getValue(String::class.java).orEmpty()
                val nombres = snapshot.child("nombres").getValue(String::class.java).orEmpty()
                val imgPerf = snapshot.child("urlImagenPerfil").getValue(String::class.java).orEmpty()

                val tiempoNode = snapshot.child("tiempo")
                val tiempoReg: Long = when (val v = tiempoNode.value) {
                    is Long -> v
                    is Int -> v.toLong()
                    is Double -> v.toLong()
                    is String -> v.toLongOrNull() ?: 0L
                    else -> 0L
                }

                val fechaFmt = Constantes.obtenerFecha(tiempoReg)

                telVendedor = buildString {
                    if (codTel.isNotBlank()) append(codTel)
                    if (telefono.isNotBlank()) append(telefono)
                }

                binding.TvNombres.text = nombres
                binding.TvMiembro.text = fechaFmt
                calificacionRepository.cargarResumen(uid) { resultado ->
                    if (!isFinishing && !isDestroyed) {
                        val resumen = resultado.getOrNull()
                        binding.TvCalificacionVendedor.text = when {
                            resumen == null -> "Calificación no disponible"
                            resumen.total == 0 -> "Sin calificaciones todavía"
                            else -> "★ ${String.format(Locale.US, "%.1f", resumen.promedio)} · ${resumen.total} opiniones"
                        }
                    }
                }

                // Aplica UI según switch y disponibilidad de teléfono
                val tieneTel = telVendedor.isNotBlank()
                aplicarEstadoContactoUI(contactoHabilitado, tieneTelefono = tieneTel)

                // Glide seguro
                if (!isFinishing && !isDestroyed && binding.Imgperfil.isAttachedToWindow) {
                    if (imgPerf.isNotBlank()) {
                        Glide.with(binding.Imgperfil)
                            .load(imgPerf)
                            .placeholder(R.drawable.img_perfil)
                            .error(R.drawable.img_perfil)
                            .into(binding.Imgperfil)
                    } else {
                        binding.Imgperfil.setImageResource(R.drawable.img_perfil)
                    }
                } else {
                    binding.Imgperfil.setImageResource(R.drawable.img_perfil)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("DetalleAnuncio", "DB cancel vendedor", error.toException())
            }
        }
        vendedorRef!!.addValueEventListener(vendedorListener as ValueEventListener)
    }

    private fun cargarImgAnuncio() {
        imagenSliderArrayList = ArrayList()
        imagenesRef = FirebaseDatabase.getInstance()
            .getReference("Anuncios").child(idAnuncio).child("Imagenes")

        imagenesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                imagenSliderArrayList.clear()
                for (ds in snapshot.children) {
                    val idImg = ds.key ?: ""
                    val url = ds.child("imagenUrl").getValue(String::class.java)
                        ?: ds.child("urlImagen").getValue(String::class.java)
                    if (!url.isNullOrBlank()) {
                        imagenSliderArrayList.add(ModeloimgSlider(idImg, url))
                    }
                }
                if (!isFinishing && !isDestroyed && binding.imagenSliderVp.isAttachedToWindow) {
                    binding.imagenSliderVp.adapter =
                        AdaptadorImgSlider(this@DetalleAnuncio, imagenSliderArrayList)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("DetalleAnuncio", "DB cancel imagenes", error.toException())
            }
        }
        imagenesRef!!.addValueEventListener(imagenesListener as ValueEventListener)
    }

    private fun comprobarAnuncioFav() {
        val uid = firebaseAuth.uid ?: return
        favoritosRef = FirebaseDatabase.getInstance().getReference("Usuarios")
            .child(uid)
            .child("Favoritos")
            .child(idAnuncio)

        favoritosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                favorito = snapshot.exists()
                binding.IbFav.setImageResource(
                    if (favorito) R.drawable.ic_anuncio_es_favorito else R.drawable.ic_no_favorito
                )
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("DetalleAnuncio", "DB cancel favoritos", error.toException())
            }
        }
        favoritosRef!!.addValueEventListener(favoritosListener as ValueEventListener)
    }

    private fun eliminarAnuncio() {
        val ref = FirebaseDatabase.getInstance().getReference("Anuncios")
        ref.child(idAnuncio)
            .removeValue()
            .addOnSuccessListener {
                startActivity(Intent(this@DetalleAnuncio, MainActivity::class.java))
                finishAffinity()
                Toast.makeText(this, "Se eliminó el anuncio con éxito", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        bannerAdView?.resume()
    }

    override fun onPause() {
        bannerAdView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        // Limpia listeners para evitar callbacks en Activity destruida
        anuncioListener?.let { anuncioRef?.removeEventListener(it) }
        anuncioListener = null
        anuncioRef = null

        vendedorListener?.let { vendedorRef?.removeEventListener(it) }
        vendedorListener = null
        vendedorRef = null

        imagenesListener?.let { imagenesRef?.removeEventListener(it) }
        imagenesListener = null
        imagenesRef = null

        favoritosListener?.let { favoritosRef?.removeEventListener(it) }
        favoritosListener = null
        favoritosRef = null

        bannerAdView?.destroy()
        bannerAdView = null

        super.onDestroy()
    }
}
