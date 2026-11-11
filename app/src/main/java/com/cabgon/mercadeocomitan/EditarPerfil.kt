package com.cabgon.mercadeocomitan

import android.app.DatePickerDialog
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.widget.PopupMenu
import android.widget.Toast
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide
import com.cabgon.mercadeocomitan.databinding.ActivityEditarPerfilBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditarPerfil : AppCompatActivity() {

    private companion object {
        const val DATE_FORMAT = "dd/MM/yyyy"
        const val MAX_IMAGE_BYTES = 5L * 1024L * 1024L
        const val STATE_IMAGE_URI = "editar_perfil_image_uri"
        const val STATE_CAMERA_URI = "editar_perfil_camera_uri"
        const val MENU_CAMERA = 1
        const val MENU_GALLERY = 2
    }

    private lateinit var binding: ActivityEditarPerfilBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialogCompat

    private var imageUri: Uri? = null
    private var cameraUri: Uri? = null
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditarPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarInsets()

        imageUri = savedInstanceState?.getString(STATE_IMAGE_URI)?.let(Uri::parse)
        cameraUri = savedInstanceState?.getString(STATE_CAMERA_URI)?.let(Uri::parse)
        firebaseAuth = FirebaseAuth.getInstance()
        binding.EtFNac.showSoftInputOnFocus = false
        progressDialog = ProgressDialogCompat(this).apply {
            setTitle(getString(R.string.progress_title))
            setCanceledOnTouchOutside(cancel = false)
        }

        binding.BtnActualizar.setOnClickListener { validarInfo() }
        binding.EtFNac.setOnClickListener { establecerFecha() }
        binding.IvAbrirCalendario.setOnClickListener { establecerFecha() }
        binding.FABCambiarImg.setOnClickListener { selecImagenDe() }

        setFormEnabled(false)
        cargarInfo()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_IMAGE_URI, imageUri?.toString())
        outState.putString(STATE_CAMERA_URI, cameraUri?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::progressDialog.isInitialized) {
            progressDialog.dismiss()
        }
        super.onDestroy()
    }

    private fun configurarInsets() {
        val root = binding.rootEditarPerfil
        val basePaddingTop = root.paddingTop
        val basePaddingBottom = root.paddingBottom

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                top = basePaddingTop + bars.top,
                bottom = basePaddingBottom + maxOf(bars.bottom, ime.bottom),
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun establecerFecha() {
        val calendario = Calendar.getInstance()
        parseFecha(binding.EtFNac.text?.toString().orEmpty())?.let { calendario.time = it }
        val datePicker = DatePickerDialog.OnDateSetListener { _, anio, mes, dia ->
            calendario.set(Calendar.YEAR, anio)
            calendario.set(Calendar.MONTH, mes)
            calendario.set(Calendar.DAY_OF_MONTH, dia)

            val formato = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            binding.EtFNac.setText(formato.format(calendario.time))
            binding.TILNac.error = null
        }

        DatePickerDialog(
            this,
            datePicker,
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH),
        ).apply {
            this.datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun validarInfo() {
        if (isSaving) return
        clearFieldErrors()

        val nombres = binding.EtNombres.text?.toString()
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()
        val fechaNacimiento = binding.EtFNac.text?.toString()?.trim().orEmpty()
        val codigo = binding.selectorCod.selectedCountryCodeWithPlus.toString().trim()
        val telefono = binding.EtTelefono.text?.toString().orEmpty()
            .filter(Char::isDigit)
        val fecha = parseFecha(fechaNacimiento)

        when {
            nombres.length !in 2..60 ||
                !nombres.any(Char::isLetter) ||
                !nombres.all { (it.isLetter() || it.isWhitespace()) || it in ".-'" } -> {
                mostrarError(binding.TILNombres, binding.EtNombres, R.string.editar_perfil_error_nombre)
            }

            fecha == null || fecha.after(Calendar.getInstance().time) -> {
                mostrarError(binding.TILNac, binding.EtFNac, R.string.editar_perfil_error_fecha)
            }

            codigo.none(Char::isDigit) -> {
                mostrarError(binding.TILTelefono, binding.EtTelefono, R.string.editar_perfil_error_codigo)
            }

            telefono.length !in 7..15 -> {
                mostrarError(binding.TILTelefono, binding.EtTelefono, R.string.editar_perfil_error_telefono)
            }

            else -> actualizarInfo(nombres, fechaNacimiento, codigo, telefono)
        }
    }

    private fun clearFieldErrors() {
        binding.TILNombres.error = null
        binding.TILNac.error = null
        binding.TILTelefono.error = null
    }

    private fun mostrarError(
        layout: com.google.android.material.textfield.TextInputLayout,
        field: android.widget.EditText,
        messageRes: Int,
    ) {
        layout.error = getString(messageRes)
        field.requestFocus()
    }

    private fun actualizarInfo(
        nombres: String,
        fechaNacimiento: String,
        codigo: String,
        telefono: String,
    ) {
        val uid = firebaseAuth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            mostrarSesionExpirada()
            return
        }

        comenzarOperacion(R.string.editar_perfil_actualizando)

        val datos = hashMapOf<String, Any>(
            "nombres" to nombres,
            "fecha_nac" to fechaNacimiento,
            "codigoTelefono" to codigo,
            "telefono" to telefono,
        )

        FirebaseDatabase.getInstance()
            .getReference("Usuarios")
            .child(uid)
            .updateChildren(datos)
            .addOnSuccessListener {
                finalizarGuardado()
                mostrarMensaje(R.string.editar_perfil_actualizado)
            }
            .addOnFailureListener {
                finalizarGuardado()
                mostrarMensaje(R.string.editar_perfil_error_actualizar)
            }
    }

    private fun finalizarGuardado() {
        finalizarOperacion()
    }

    private fun comenzarOperacion(messageRes: Int) {
        isSaving = true
        setFormEnabled(false)
        progressDialog.setMessage(getString(messageRes))
        if (!isFinishing && !isDestroyed) {
            progressDialog.show()
        }
    }

    private fun finalizarOperacion() {
        isSaving = false
        if (::progressDialog.isInitialized) {
            progressDialog.dismiss()
        }
        if (!isFinishing && !isDestroyed) {
            setFormEnabled(true)
        }
    }

    private fun setFormEnabled(enabled: Boolean) {
        val controlsEnabled = enabled && !isSaving
        binding.EtNombres.isEnabled = controlsEnabled
        binding.EtFNac.isEnabled = controlsEnabled
        binding.EtTelefono.isEnabled = controlsEnabled
        binding.selectorCod.isEnabled = controlsEnabled
        binding.IvAbrirCalendario.isEnabled = controlsEnabled
        binding.BtnActualizar.isEnabled = controlsEnabled
        binding.FABCambiarImg.isEnabled = controlsEnabled
    }

    private fun cargarInfo() {
        val uid = firebaseAuth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            mostrarSesionExpirada()
            return
        }

        if (!isFinishing && !isDestroyed) {
            progressDialog.setMessage(getString(R.string.Cargando))
            progressDialog.show()
        }

        FirebaseDatabase.getInstance()
            .getReference("Usuarios")
            .child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return

                    val nombres = snapshot.child("nombres")
                        .getValue(String::class.java)
                        .orEmpty()
                    val imagen = snapshot.child("urlImagenPerfil")
                        .getValue(String::class.java)
                        .orEmpty()
                    val fechaNacimiento = snapshot.child("fecha_nac")
                        .getValue(String::class.java)
                        .orEmpty()
                    val telefono = snapshot.child("telefono")
                        .getValue(String::class.java)
                        .orEmpty()
                    val codigoTelefono = snapshot.child("codigoTelefono")
                        .getValue(String::class.java)
                        .orEmpty()

                    binding.EtNombres.setText(nombres)
                    binding.EtFNac.setText(fechaNacimiento)
                    binding.EtTelefono.setText(telefono)

                    Glide.with(this@EditarPerfil)
                        .load(imagen.takeUnless(String::isBlank))
                        .placeholder(R.drawable.img_perfil)
                        .error(R.drawable.img_perfil)
                        .into(binding.imgPerfil)

                    codigoTelefono
                        .filter(Char::isDigit)
                        .toIntOrNull()
                        ?.let { binding.selectorCod.setCountryForPhoneCode(it) }
                    progressDialog.dismiss()
                    setFormEnabled(true)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!isFinishing && !isDestroyed) {
                        progressDialog.dismiss()
                        setFormEnabled(true)
                        mostrarMensaje(R.string.editar_perfil_error_cargar)
                    }
                }
            })
    }

    private fun selecImagenDe() {
        val popupMenu = PopupMenu(this, binding.FABCambiarImg)
        popupMenu.menu.add(Menu.NONE, MENU_CAMERA, Menu.NONE, getString(R.string.editar_perfil_camara))
        popupMenu.menu.add(Menu.NONE, MENU_GALLERY, Menu.NONE, getString(R.string.editar_perfil_galeria))

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CAMERA -> solicitarPermisosCamara()
                MENU_GALLERY -> resultadoGaleria.launch("image/*")
            }
            true
        }
        popupMenu.show()
    }

    private fun solicitarPermisosCamara() {
        val permisos = buildList {
            add(android.Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        solicitarPermisoCamara.launch(permisos.toTypedArray())
    }

    private val solicitarPermisoCamara =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
            if (resultado.values.isNotEmpty() && resultado.values.all { it }) {
                imageCamara()
            } else {
                mostrarMensaje(R.string.editar_perfil_permiso_camara)
            }
        }

    private fun imageCamara() {
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "perfil_${System.currentTimeMillis()}.jpg",
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/MercadeoComitan",
                )
            }
        }

        imageUri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        )
        cameraUri = imageUri

        val uri = imageUri
        if (uri == null) {
            mostrarMensaje(R.string.editar_perfil_camara_error)
            return
        }

        resultadoCamaraArl.launch(uri)
    }

    private val resultadoCamaraArl =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { guardada ->
            val uri = imageUri
            if (guardada && uri != null) {
                procesarImagen(uri)
            } else {
                uri?.let { contentResolver.delete(it, null, null) }
                imageUri = null
                cameraUri = null
                mostrarMensaje(R.string.editar_perfil_imagen_cancelada)
            }
        }

    private val resultadoGaleria =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            cameraUri = null
            if (uri == null) {
                mostrarMensaje(R.string.editar_perfil_imagen_cancelada)
            } else {
                procesarImagen(uri)
            }
        }

    private fun procesarImagen(uri: Uri) {
        val mimeType = resolverImagenMimeType(uri)
        if (mimeType == null) {
            eliminarCapturaTemporal(uri)
            mostrarMensaje(R.string.editar_perfil_imagen_invalida)
            return
        }

        val imageSize = obtenerTamanoImagen(uri)

        if (imageSize > MAX_IMAGE_BYTES) {
            eliminarCapturaTemporal(uri)
            mostrarMensaje(R.string.editar_perfil_imagen_grande)
            return
        }

        imageUri = uri
        cameraUri = null
        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.img_perfil)
            .error(R.drawable.img_perfil)
            .into(binding.imgPerfil)
        subirImagenStorage(uri, mimeType)
    }

    private fun obtenerTamanoImagen(uri: Uri): Long = runCatching {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it >= 0L }
        } ?: contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.statSize.takeIf { it >= 0L }
        } ?: -1L
    }.getOrDefault(-1L)

    private fun resolverImagenMimeType(uri: Uri): String? {
        val providerType = contentResolver.getType(uri)?.lowercase(Locale.ROOT)
        if (providerType?.startsWith("image/") == true) {
            return providerType
        }

        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            .lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)
            ?.takeIf { it.startsWith("image/") }
            ?: if (providerType == null && uri.scheme == "content") "image/*" else null
    }

    private fun subirImagenStorage(uri: Uri, mimeType: String) {
        val uid = firebaseAuth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            mostrarSesionExpirada()
            return
        }

        comenzarOperacion(R.string.editar_perfil_subiendo_imagen)

        val metadata = StorageMetadata.Builder()
            .setContentType(mimeType)
            .build()

        FirebaseStorage.getInstance()
            .getReference("imagenesPerfil")
            .child(uid)
            .putFile(uri, metadata)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        actualizarImagenBD(downloadUri.toString())
                    }
                    .addOnFailureListener {
                        finalizarImagen(exito = false)
                    }
            }
            .addOnFailureListener {
                finalizarImagen(exito = false)
            }
    }

    private fun actualizarImagenBD(urlImagen: String) {
        val uid = firebaseAuth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            finalizarImagen(exito = false)
            mostrarSesionExpirada()
            return
        }

        progressDialog.setMessage(getString(R.string.editar_perfil_actualizando_imagen))
        FirebaseDatabase.getInstance()
            .getReference("Usuarios")
            .child(uid)
            .updateChildren(mapOf("urlImagenPerfil" to urlImagen))
            .addOnSuccessListener { finalizarImagen(exito = true) }
            .addOnFailureListener { finalizarImagen(exito = false) }
    }

    private fun finalizarImagen(exito: Boolean) {
        finalizarOperacion()
        if (!isFinishing && !isDestroyed) {
            mostrarMensaje(
                if (exito) {
                    R.string.editar_perfil_imagen_actualizada
                } else {
                    R.string.editar_perfil_foto_error
                },
            )
        }
    }

    private fun eliminarCapturaTemporal(uri: Uri) {
        if (cameraUri == uri) {
            contentResolver.delete(uri, null, null)
            cameraUri = null
            imageUri = null
        }
    }

    private fun parseFecha(value: String) =
        runCatching {
            val formatter = SimpleDateFormat(DATE_FORMAT, Locale.ROOT).apply {
                isLenient = false
            }
            val position = ParsePosition(0)
            formatter.parse(value, position)?.takeIf {
                position.errorIndex < 0 && position.index == value.length
            }
        }.getOrNull()

    private fun mostrarSesionExpirada() {
        if (!isFinishing && !isDestroyed) {
            mostrarMensaje(R.string.editar_perfil_error_sesion)
            finish()
        }
    }

    private fun mostrarMensaje(messageRes: Int) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        }
    }
}
