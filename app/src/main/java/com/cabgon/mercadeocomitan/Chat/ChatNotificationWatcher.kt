@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Chat

import android.app.Application
import com.cabgon.mercadeocomitan.Constantes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * Escucha globalmente en `/Chats` los mensajes entrantes con estado `enviado` para el usuario actual.
 * Garantiza que mientras la app esté viva en cualquier pantalla o en segundo plano:
 * 1. Se marque automáticamente el recibo como `entregado` (doble palomita).
 * 2. Se muestre la notificación estilo WhatsApp/Messenger si no está abierto ese chat,
 *    sin duplicar con FCM gracias al control de `messageId` en [MyFirebaseMessagingService].
 */
object ChatNotificationWatcher {
    private var installed = false
    private var appContext: Application? = null
    private var currentUid: String? = null
    private var chatsRef: DatabaseReference? = null
    private var chatsListener: ChildEventListener? = null
    private val inicioSesionMs = System.currentTimeMillis()

    fun install(application: Application) {
        if (installed) return
        installed = true
        appContext = application

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid == currentUid) return@addAuthStateListener
            detenerEscucha()
            currentUid = uid
            if (!uid.isNullOrBlank()) {
                iniciarEscucha(uid)
            }
        }
    }

    private fun iniciarEscucha(uid: String) {
        val ref = FirebaseDatabase.getInstance().getReference("Chats")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                procesarChat(snapshot, uid, esCambioEnVivo = false)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                procesarChat(snapshot, uid, esCambioEnVivo = true)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) = Unit
        }
        chatsRef = ref
        chatsListener = listener
        ref.addChildEventListener(listener)
    }

    private fun detenerEscucha() {
        val ref = chatsRef
        val listener = chatsListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }
        chatsRef = null
        chatsListener = null
    }

    private fun procesarChat(chatSnap: DataSnapshot, miUid: String, esCambioEnVivo: Boolean) {
        val chatId = chatSnap.key ?: return
        if (!Constantes.esClaveFirebaseValida(chatId)) return

        val mensajesSnap = chatSnap.child("mensajes")
        if (!mensajesSnap.exists()) return

        var ultimoNoEntregado: DataSnapshot? = null
        var ultimoTiempo = 0L

        for (msg in mensajesSnap.children) {
            val receptor = msg.child("receptorUid").getValue(String::class.java).orEmpty()
            if (receptor != miUid) continue

            val emisor = msg.child("emisorUid").getValue(String::class.java).orEmpty()
            if (!Constantes.esClaveFirebaseValida(emisor) || emisor == miUid) continue

            val estado = msg.child("estado").getValue(String::class.java) ?: EstadoReciboMensaje.ENVIADO
            if (estado == EstadoReciboMensaje.ENVIADO) {
                EstadoReciboMensaje.marcar(msg.ref, miUid, EstadoReciboMensaje.ENTREGADO)

                val tiempo = msg.child("tiempo").getValue(Long::class.java) ?: 0L
                if (esCambioEnVivo || tiempo >= inicioSesionMs - 10_000L) {
                    if (tiempo >= ultimoTiempo) {
                        ultimoTiempo = tiempo
                        ultimoNoEntregado = msg
                    }
                }
            }
        }

        val msgNotificar = ultimoNoEntregado ?: return
        val senderUid = msgNotificar.child("emisorUid").getValue(String::class.java).orEmpty()
        if (MyFirebaseMessagingService.currentChatUid == senderUid) return

        val messageId = msgNotificar.child("idMensaje").getValue(String::class.java)
            ?: msgNotificar.key.orEmpty()
        val tipo = msgNotificar.child("tipoMensaje").getValue(String::class.java).orEmpty()
        val rawText = msgNotificar.child("mensaje").getValue(String::class.java).orEmpty()
        val preview = if (tipo == Constantes.MENSAJE_TIPO_IMAGEN) {
            "📷 Imagen"
        } else {
            rawText.ifBlank { "Tienes un nuevo mensaje" }
        }

        val ctx = appContext ?: return
        FirebaseDatabase.getInstance().getReference("Usuarios").child(senderUid)
            .get()
            .addOnSuccessListener { userSnap ->
                val senderName = userSnap.child("nombres").getValue(String::class.java)
                    ?.takeIf { it.isNotBlank() } ?: "Nuevo mensaje"
                val senderAvatar = userSnap.child("urlImagenPerfil").getValue(String::class.java)
                Thread {
                    MyFirebaseMessagingService.mostrarNotificacionEstiloMensajeria(
                        context = ctx,
                        senderName = senderName,
                        body = preview,
                        uidVendedor = senderUid,
                        senderAvatar = senderAvatar,
                        messageId = messageId
                    )
                }.start()
            }
    }
}
