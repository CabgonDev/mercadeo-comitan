@file:Suppress("PackageName", "PackageDirectoryMismatch")

package com.cabgon.mercadeocomitan.Chat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.MainActivity
import com.cabgon.mercadeocomitan.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().uid ?: return
        val tokenId = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        FirebaseDatabase.getInstance().getReference("Usuarios").child(uid).updateChildren(
            mapOf(
                "fcmToken" to token,
                "fcmTokens/$tokenId/token" to token,
                "fcmTokens/$tokenId/updatedAt" to ServerValue.TIMESTAMP,
            )
        )
    }

    companion object {
        /**
         * Uid del usuario con el que el usuario está chateando en este momento.
         * Si coincide con el senderUid entrante, NO mostramos notificación.
         */
        @Volatile
        var currentChatUid: String? = null

        private const val CHANNEL_ID = "chat_messages"
        private const val CHANNEL_NAME = "Mensajes"
        private const val CHANNEL_DESC = "Notificaciones de mensajes"
        private const val MAX_HISTORIAL_POR_CHAT = 5

        private data class MensajeNotif(val texto: String, val timestamp: Long)

        private val historialMensajes = ConcurrentHashMap<String, MutableList<MensajeNotif>>()
        private val mensajesNotificados = ConcurrentHashMap.newKeySet<String>()

        fun limpiarNotificacionChat(context: Context, senderUid: String) {
            if (senderUid.isBlank()) return
            historialMensajes.remove(senderUid)
            NotificationManagerCompat.from(context).cancel(senderUid.hashCode())
        }

        fun mostrarNotificacionEstiloMensajeria(
            context: Context,
            senderName: String,
            body: String,
            uidVendedor: String?,
            senderAvatar: String? = null,
            messageId: String? = null
        ) {
            if ((!uidVendedor.isNullOrBlank()) && (currentChatUid == uidVendedor)) return
            if (!messageId.isNullOrBlank() && !mensajesNotificados.add(messageId)) return

            ensureChannel(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val appContext = context.applicationContext
            val avatarBitmap: Bitmap? = if (!senderAvatar.isNullOrBlank()) {
                runCatching {
                    Glide.with(appContext)
                        .asBitmap()
                        .load(senderAvatar)
                        .circleCrop()
                        .submit(192, 192)
                        .get()
                }.getOrNull()
            } else {
                null
            }

            val personBuilder = Person.Builder()
                .setName(senderName.ifBlank { "Usuario" })
                .setKey(uidVendedor ?: senderName)
            avatarBitmap?.let { personBuilder.setIcon(IconCompat.createWithBitmap(it)) }
            val remitente = personBuilder.build()
            val yo = Person.Builder().setName("Tú").build()

            val messagingStyle = NotificationCompat.MessagingStyle(yo)
                .setConversationTitle(senderName.ifBlank { "Nuevo mensaje" })

            val ahora = System.currentTimeMillis()
            if (!uidVendedor.isNullOrBlank()) {
                val lista = historialMensajes.getOrPut(uidVendedor) { mutableListOf() }
                synchronized(lista) {
                    lista.add(MensajeNotif(body, ahora))
                    while (lista.size > MAX_HISTORIAL_POR_CHAT) {
                        lista.removeAt(0)
                    }
                    for ((texto, timestamp) in lista) {
                        messagingStyle.addMessage(texto, timestamp, remitente)
                    }
                }
            } else {
                messagingStyle.addMessage(body, ahora, remitente)
            }

            val intent = if (uidVendedor.isNullOrBlank()) {
                Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                Intent(appContext, ChatActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("uidVendedor", uidVendedor)
                }
            }

            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val notifId = uidVendedor?.hashCode() ?: ahora.toInt()
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                notifId,
                intent,
                pendingFlags
            )

            val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_chats)
                .setContentTitle(senderName)
                .setContentText(body)
                .setStyle(messagingStyle)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(pendingIntent)

            if (avatarBitmap != null) {
                builder.setLargeIcon(avatarBitmap)
            }

            try {
                NotificationManagerCompat.from(appContext).notify(notifId, builder.build())
            } catch (se: SecurityException) {
                Log.e("FCM", "Sin permiso para notificaciones: ${se.message}")
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val ch = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = CHANNEL_DESC }
                    nm.createNotificationChannel(ch)
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val type = data["notificationType"] ?: return
            if (type != "nuevo_mensaje") return

            val senderUid = data["senderUid"] ?: return
            val receiverUid = data["receiverUid"] ?: return
            if (!Constantes.esClaveFirebaseValida(senderUid)) return
            if (!Constantes.esClaveFirebaseValida(receiverUid)) return
            if (FirebaseAuth.getInstance().currentUser?.uid != receiverUid) return
            val preview = data["preview"] ?: "Tienes un nuevo mensaje"
            val messageId = data["messageId"]

            // Si el chat de ese usuario está abierto, no notificar
            if (currentChatUid == senderUid) return

            // El callback del dispositivo confirma la entrega; el servidor solo
            // inicia el envío y no adelanta el recibo.
            marcarComoEntregado(data)

            val senderName = data["senderName"]
            val senderAvatar = data["senderAvatar"]
            if (!senderName.isNullOrBlank()) {
                mostrarNotificacionEstiloMensajeria(
                    context = this,
                    senderName = senderName,
                    body = preview,
                    uidVendedor = senderUid,
                    senderAvatar = senderAvatar,
                    messageId = messageId
                )
            } else {
                // Fallback: buscar nombre y foto en la BD si no llegaron en el payload
                FirebaseDatabase.getInstance().getReference("Usuarios")
                    .child(senderUid)
                    .get()
                    .addOnSuccessListener { snap ->
                        val resolvedName = snap.child("nombres").getValue(String::class.java) ?: "Nuevo mensaje"
                        val resolvedAvatar = snap.child("urlImagenPerfil").getValue(String::class.java)
                        Thread {
                            mostrarNotificacionEstiloMensajeria(
                                context = applicationContext,
                                senderName = resolvedName,
                                body = preview,
                                uidVendedor = senderUid,
                                senderAvatar = resolvedAvatar,
                                messageId = messageId
                            )
                        }.start()
                    }
            }
            return
        }

        // Fallback muy básico si viniera NOTIFICATION (no data)
        remoteMessage.notification?.let { notif ->
            val title = notif.title ?: "Nuevo mensaje"
            val body = notif.body ?: "Tienes un nuevo mensaje"
            mostrarNotificacionEstiloMensajeria(
                context = this,
                senderName = title,
                body = body,
                uidVendedor = null
            )
        }
    }

    private fun marcarComoEntregado(data: Map<String, String>) {
        val chatId = data["chatId"]?.trim().orEmpty()
        val messageId = data["messageId"]?.trim().orEmpty()
        val receiverUid = data["receiverUid"]?.trim().orEmpty()
        if (chatId.isBlank() || messageId.isBlank()) return
        if (FirebaseAuth.getInstance().currentUser?.uid != receiverUid) return
        if (!Constantes.esClaveFirebaseValida(chatId) ||
            !Constantes.esClaveFirebaseValida(messageId)
        ) return

        FirebaseDatabase.getInstance().reference
            .child("Chats").child(chatId).child("mensajes").child(messageId)
            .let { EstadoReciboMensaje.marcar(it, receiverUid, EstadoReciboMensaje.ENTREGADO) }
    }
}
