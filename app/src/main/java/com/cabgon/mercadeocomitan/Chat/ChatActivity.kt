@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Chat

import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorChat
import com.cabgon.mercadeocomitan.Modelo.ModeloChat
import com.cabgon.mercadeocomitan.databinding.ActivityChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class ChatActivity : AppCompatActivity() {

    private companion object {
        const val MAX_MENSAJE_CARACTERES = 4_000
        const val MAX_IMAGEN_BYTES = 10L * 1024L * 1024L
        const val ESTADO_ENVIADO = EstadoReciboMensaje.ENVIADO
        const val ESTADO_ENTREGADO = EstadoReciboMensaje.ENTREGADO
        const val ESTADO_LEIDO = EstadoReciboMensaje.LEIDO
    }

    private lateinit var binding: ActivityChatBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val storage by lazy { FirebaseStorage.getInstance().reference }

    private lateinit var chatId: String
    private lateinit var receptorUid: String

    // RV + adapter + listener
    private lateinit var adaptador: AdaptadorChat
    private val mensajes = ArrayList<ModeloChat>()
    private var mensajesListener: ChildEventListener? = null
    private var mensajesQuery: Query? = null
    private var chatConfigurado = false

    // Typing & presence
    private val estadoFlow = MutableStateFlow(false) // OTRO está escribiendo
    private var typingJob: Job? = null
    private var dotsJob: Job? = null
    private var typingListener: ValueEventListener? = null
    private var presenciaListener: ValueEventListener? = null
    private var uploadingImage = false
    private var ultimoEstadoPresencia = "desconectado"

    // The system picker grants a temporary read URI, so no storage permission is required.
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) uploadImageAndSend(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Args
        val myUid = auth.uid ?: run { finish(); return }
        receptorUid = intent.getStringExtra("uidVendedor")?.trim().orEmpty()
        if (!Constantes.esClaveFirebaseValida(receptorUid) || receptorUid == myUid) {
            finish()
            return
        }
        val expectedChatId = Constantes.rutaChat(receptorUid, myUid)
        val requestedChatId = intent.getStringExtra("keyChat")?.trim().orEmpty()
        // Deep links must not let callers select a chat outside the two participants.
        chatId = requestedChatId.takeIf { it == expectedChatId } ?: expectedChatId
        chatConfigurado = true

        // Perfil vendedor
        cargarInfoVendedor(receptorUid)

        // Presence
        escucharPresencia(receptorUid)

        // Typing
        configurarTyping(myUid)

        // RecyclerView
        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.chatsRv.layoutManager = lm
        adaptador = AdaptadorChat(this, mensajes, chatId)
        binding.chatsRv.adapter = adaptador
        adaptador.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.chatsRv.scrollToPosition(adaptador.itemCount - 1)
            }
        })

        // Escuchar mensajes
        escucharMensajes()

        // Botones
        binding.enviarFAB.setOnClickListener { onEnviarClick(myUid) }
        binding.adjuntarFAB.setOnClickListener { pickImage.launch("image/*") }
    }

    private fun marcarMensajesLeidos() {
        val myUid = auth.uid ?: return
        val ref = db.child("Chats").child(chatId).child("mensajes")

        // Marca en memoria + DB
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        ref.orderByChild("receptorUid").equalTo(myUid)
            .get().addOnSuccessListener { snap ->
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || isFinishing || isDestroyed) {
                    return@addOnSuccessListener
                }
                for (ds in snap.children) {
                    if (ds.child("receptorUid").getValue(String::class.java) == myUid)
                        EstadoReciboMensaje.marcar(ds.ref, myUid, ESTADO_LEIDO)
                }
            }
    }

    private fun onEnviarClick(myUid: String) {
        val texto = binding.EtMensajeChat.text?.toString()?.trim().orEmpty()
        if (texto.isEmpty()) {
            Toast.makeText(this, "Escribe un mensaje", Toast.LENGTH_SHORT).show()
            return
        }
        if (texto.length > MAX_MENSAJE_CARACTERES) {
            Toast.makeText(this, "El mensaje no puede superar 4000 caracteres", Toast.LENGTH_SHORT).show()
            return
        }
        enviarMensajeTexto(myUid, receptorUid, texto)
    }

    private fun enviarMensajeTexto(emisorUid: String, receptorUid: String, mensaje: String) {
        enviarMensaje(
            emisorUid = emisorUid,
            receptorUid = receptorUid,
            mensaje = mensaje,
            tipoMensaje = Constantes.MENSAJE_TIPO_TEXTO,
            onSuccess = { binding.EtMensajeChat.setText("") }
        )
    }

    private fun uploadImageAndSend(uri: Uri) {
        if (uploadingImage) return
        val myUid = auth.uid ?: return
        if (!esImagenUri(uri)) {
            Toast.makeText(this, "Selecciona una imagen válida", Toast.LENGTH_SHORT).show()
            return
        }
        val size = obtenerTamanoUri(uri)
        if (size != null && size > MAX_IMAGEN_BYTES) {
            Toast.makeText(this, "La imagen no puede superar 10 MB", Toast.LENGTH_SHORT).show()
            return
        }

        val messageRef = db.child("Chats").child(chatId).child("mensajes").push()
        val messageId = messageRef.key ?: return
        val imageRef = storage.child("Chats").child(chatId).child("imagenes").child(messageId)
        val contentType = contentResolver.getType(uri)
            ?.lowercase()
            ?.takeIf { it.startsWith("image/") }
            ?: "image/jpeg"
        val metadata = StorageMetadata.Builder().setContentType(contentType).build()

        uploadingImage = true
        binding.adjuntarFAB.isEnabled = false
        Toast.makeText(this, "Subiendo imagen…", Toast.LENGTH_SHORT).show()
        imageRef.putFile(uri, metadata)
            .addOnSuccessListener {
                imageRef.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        enviarMensaje(
                            ref = messageRef,
                            emisorUid = myUid,
                            receptorUid = receptorUid,
                            mensaje = downloadUri.toString(),
                            tipoMensaje = Constantes.MENSAJE_TIPO_IMAGEN,
                            onSuccess = { finalizarCargaImagen("Imagen enviada") },
                            onFailure = { error ->
                                imageRef.delete()
                                finalizarCargaImagen(error.message ?: "No se pudo enviar la imagen")
                            }
                        )
                    }
                    .addOnFailureListener { error ->
                        imageRef.delete()
                        finalizarCargaImagen(error.message ?: "No se pudo obtener la imagen")
                    }
            }
            .addOnFailureListener { error ->
                finalizarCargaImagen(error.message ?: "No se pudo subir la imagen")
            }
    }

    private fun enviarMensaje(
        emisorUid: String,
        receptorUid: String,
        mensaje: String,
        tipoMensaje: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit = { error ->
            Toast.makeText(this, error.message ?: "No se pudo enviar", Toast.LENGTH_SHORT).show()
        }
    ) {
        val ref = db.child("Chats").child(chatId).child("mensajes").push()
        enviarMensaje(ref, emisorUid, receptorUid, mensaje, tipoMensaje, onSuccess, onFailure)
    }

    private fun enviarMensaje(
        ref: DatabaseReference,
        emisorUid: String,
        receptorUid: String,
        mensaje: String,
        tipoMensaje: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val msgId = ref.key ?: System.currentTimeMillis().toString()
        val payload = mapOf(
            "idMensaje" to msgId,
            "mensaje" to mensaje,
            "emisorUid" to emisorUid,
            "receptorUid" to receptorUid,
            "tipoMensaje" to tipoMensaje,
            "tiempo" to ServerValue.TIMESTAMP,
            "estado" to "enviado"
        )
        ref.setValue(payload)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    private fun finalizarCargaImagen(mensaje: String) {
        uploadingImage = false
        if (!isFinishing && !isDestroyed) {
            binding.adjuntarFAB.isEnabled = true
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }
    }

    private fun obtenerTamanoUri(uri: Uri): Long? {
        val desdeCursor = runCatching {
            contentResolver.query(uri, arrayOf("_size"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()?.takeIf { it >= 0L }
        if (desdeCursor != null) return desdeCursor

        return runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0L }
            }
        }.getOrNull()
    }

    private fun esImagenUri(uri: Uri): Boolean {
        val mime = runCatching { contentResolver.getType(uri) }
            .getOrNull()
            ?.lowercase()
        if (!mime.isNullOrBlank()) return mime.startsWith("image/")

        // Algunos proveedores no informan MIME. Confirma que el contenido sea
        // una imagen sin decodificar el bitmap completo en memoria.
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                options.outWidth > 0 && options.outHeight > 0
            } == true
        }.getOrDefault(false)
    }

    // ---- Perfil vendedor ----
    private fun cargarInfoVendedor(uidVendedor: String) {
        binding.TxtNombreVendedorChat.setText(R.string.chat_cargando)
        binding.TxtEstadoChat.text = ""
        db.child("Usuarios").child(uidVendedor)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val nombres = s.child("nombres").getValue(String::class.java)
                        ?: getString(R.string.chat_usuario)
                    val urlImg = s.child("urlImagenPerfil").getValue(String::class.java) ?: ""
                    binding.TxtNombreVendedorChat.text = nombres
                    Glide.with(this@ChatActivity)
                        .load(urlImg)
                        .placeholder(R.drawable.img_perfil)
                        .error(R.drawable.img_perfil)
                        .into(binding.toolbarIv)
                }
                override fun onCancelled(error: DatabaseError) {
                    binding.TxtNombreVendedorChat.setText(R.string.chat_usuario)
                }
            })
    }

    // ---- Presence ----
    private fun escucharPresencia(uidVendedor: String) {
        val ref = db.child("Usuarios").child(uidVendedor)
        presenciaListener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val onlineAny = s.child("online").value
                ultimoEstadoPresencia = when (onlineAny) {
                    is Boolean -> if (onlineAny) "en línea" else "desconectado"
                    is Number -> {
                        val ts = onlineAny.toLong()
                        if (ts > 0) "últ. vez: ${Constantes.obtenerFechaHora(ts)}" else "desconectado"
                    }
                    else -> {
                        val last = s.child("lastActive").getValue(Long::class.java) ?: 0L
                        if (last > 0) "últ. vez: ${Constantes.obtenerFechaHora(last)}" else "desconectado"
                    }
                }
                if (!estadoFlow.value) binding.TxtEstadoChat.text = ultimoEstadoPresencia
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // ---- Typing ----
    private fun configurarTyping(myUid: String) {
        val typingState = MutableStateFlow("")
        binding.EtMensajeChat.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { typingState.value = s?.toString().orEmpty() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        lifecycleScope.launch {
            typingState
                .debounce(150.milliseconds)
                .collectLatest { texto ->
                    if (texto.isBlank()) {
                        typingJob?.cancel()
                        setTyping(myUid, false)
                        return@collectLatest
                    }
                    setTyping(myUid, true)
                    typingJob?.cancel()
                    typingJob = lifecycleScope.launch {
                        delay(1500.milliseconds)
                        setTyping(myUid, false)
                    }
                }
        }
        val typingPath = db.child("Chats").child(chatId).child("typing").child(receptorUid)
        typingListener = typingPath.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val escribiendo = snapshot.getValue(Boolean::class.java) == true
                estadoFlow.value = escribiendo
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                estadoFlow.collectLatest { escribiendo ->
                    if (escribiendo) {
                        startTypingDots()
                    } else {
                        stopTypingDots()
                        binding.TxtEstadoChat.text = ultimoEstadoPresencia
                    }
                }
            }
        }
    }

    private fun setTyping(myUid: String, isTyping: Boolean) {
        db.child("Chats").child(chatId).child("typing").child(myUid).setValue(isTyping)
    }

    private fun startTypingDots() {
        if (dotsJob?.isActive == true) return
        dotsJob = lifecycleScope.launch {
            val base = getString(R.string.chat_escribiendo)
            var i = 0
            while (isActive) {
                binding.TxtEstadoChat.text = getString(
                    R.string.chat_escribiendo_puntos,
                    base,
                    ".".repeat((i % 3) + 1)
                )
                i++
                delay(350.milliseconds)
            }
        }
    }
    private fun stopTypingDots() { dotsJob?.cancel(); dotsJob = null }

    // ---- Mensajes en tiempo real ----
    private fun escucharMensajes() {
        val ref = db.child("Chats").child(chatId).child("mensajes")
        val query = ref.orderByChild("tiempo")
        mensajesQuery = query
        mensajesListener = object : ChildEventListener {
            override fun onChildAdded(ds: DataSnapshot, prevKey: String?) {
                val m = ModeloChat().apply {
                    idMensaje   = ds.child("idMensaje").getValue(String::class.java) ?: ds.key.orEmpty()
                    mensaje     = ds.child("mensaje").getValue(String::class.java) ?: ""
                    emisorUid   = ds.child("emisorUid").getValue(String::class.java) ?: ""
                    receptorUid = ds.child("receptorUid").getValue(String::class.java) ?: ""
                    tipoMensaje = ds.child("tipoMensaje").getValue(String::class.java)
                        ?: Constantes.MENSAJE_TIPO_TEXTO
                    tiempo      = ds.child("tiempo").getValue(Long::class.java) ?: 0L
                    estado      = ds.child("estado").getValue(String::class.java) ?: ESTADO_ENVIADO
                }

                if (!esMensajeDeEsteChat(m)) return

                // La entrega y la lectura son estados distintos. Si la pantalla
                // está visible, el mensaje se lee; si llega en segundo plano,
                // queda entregado hasta que el usuario abra este chat.
                marcarMensajeEntrante(m)

                mensajes.add(m)
                adaptador.notifyItemInserted(mensajes.size - 1)
            }

            override fun onChildChanged(ds: DataSnapshot, prevKey: String?) {
                val id = ds.child("idMensaje").getValue(String::class.java) ?: ds.key.orEmpty()
                val idx = mensajes.indexOfFirst { it.idMensaje == id }
                if (idx >= 0) {
                    val emisor = ds.child("emisorUid").getValue(String::class.java).orEmpty()
                    val receptor = ds.child("receptorUid").getValue(String::class.java).orEmpty()
                    if (!esParticipante(emisor, receptor)) return
                    mensajes[idx].apply {
                        mensaje     = ds.child("mensaje").getValue(String::class.java) ?: mensaje
                        tipoMensaje = ds.child("tipoMensaje").getValue(String::class.java) ?: tipoMensaje
                        tiempo      = ds.child("tiempo").getValue(Long::class.java) ?: tiempo
                        estado      = ds.child("estado").getValue(String::class.java) ?: estado
                    }
                    adaptador.notifyItemChanged(idx)
                }
            }

            override fun onChildRemoved(ds: DataSnapshot) {
                val id = ds.child("idMensaje").getValue(String::class.java) ?: ds.key.orEmpty()
                val idx = mensajes.indexOfFirst { it.idMensaje == id }
                if (idx >= 0) {
                    mensajes.removeAt(idx)
                    adaptador.notifyItemRemoved(idx)
                }
            }
            override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        query.addChildEventListener(mensajesListener as ChildEventListener)
    }

    private fun esParticipante(emisorUid: String, receptorUid: String): Boolean {
        val myUid = auth.uid ?: return false
        return (emisorUid == myUid && receptorUid == this.receptorUid) ||
            (emisorUid == this.receptorUid && receptorUid == myUid)
    }

    private fun esMensajeDeEsteChat(mensaje: ModeloChat): Boolean {
        if (!Constantes.esClaveFirebaseValida(mensaje.idMensaje)) return false
        if (!esParticipante(mensaje.emisorUid, mensaje.receptorUid)) return false
        if (mensaje.mensaje.isBlank()) return false
        if (mensaje.tipoMensaje != Constantes.MENSAJE_TIPO_TEXTO &&
            mensaje.tipoMensaje != Constantes.MENSAJE_TIPO_IMAGEN
        ) return false
        if (mensaje.tipoMensaje == Constantes.MENSAJE_TIPO_IMAGEN) {
            val scheme = runCatching { mensaje.mensaje.toUri().scheme }.getOrNull()
            if (scheme != "https") return false
        }
        return true
    }

    private fun marcarMensajeEntrante(mensaje: ModeloChat) {
        val myUid = auth.uid ?: return
        if (mensaje.receptorUid != myUid || mensaje.estado == ESTADO_LEIDO) return

        val estadoNuevo = if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            ESTADO_LEIDO
        } else {
            ESTADO_ENTREGADO
        }

        if (mensaje.estado == estadoNuevo) return

        db.child("Chats").child(chatId).child("mensajes")
            .child(mensaje.idMensaje)
            .let { EstadoReciboMensaje.marcar(it, myUid, estadoNuevo) }
    }

    override fun onResume() {
        super.onResume()
        if (!chatConfigurado) return
        MyFirebaseMessagingService.currentChatUid = receptorUid
        MyFirebaseMessagingService.limpiarNotificacionChat(this, receptorUid)
        marcarMensajesLeidos()
    }

    override fun onPause() {
        if (chatConfigurado) {
            if (MyFirebaseMessagingService.currentChatUid == receptorUid) {
                MyFirebaseMessagingService.currentChatUid = null
            }
            auth.uid?.let { setTyping(it, false) }
            stopTypingDots()
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (chatConfigurado) {
            auth.uid?.let { setTyping(it, false) }
        }
        stopTypingDots()
        typingJob?.cancel()
        mensajesListener?.let { mensajesQuery?.removeEventListener(it) }
        mensajesListener = null
        mensajesQuery = null
        if (chatConfigurado) {
            typingListener?.let {
                db.child("Chats").child(chatId).child("typing").child(receptorUid).removeEventListener(it)
            }
            presenciaListener?.let {
                db.child("Usuarios").child(receptorUid).removeEventListener(it)
            }
            if (MyFirebaseMessagingService.currentChatUid == receptorUid) {
                MyFirebaseMessagingService.currentChatUid = null
            }
        }
        super.onDestroy()
    }
}
