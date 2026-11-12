@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Notificaciones

import android.util.Log
import com.cabgon.mercadeocomitan.utils.NotificationHelper
import com.cabgon.mercadeocomitan.utils.CrashlyticsHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFcmService : FirebaseMessagingService() {

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        Log.i("FCM", "Token renovado para el servicio legado")
        CrashlyticsHelper.logEvent("fcm_token_renewed")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        try {
            val data = remoteMessage.data
            val notificationType = data["type"] ?: "chat_message"

            Log.d("FCM", "Notification received: type=$notificationType, data=$data")

            when (notificationType) {
                "chat_message", NotificationHelper.NotificationType.CHAT_MESSAGE -> {
                    handleChatNotification(remoteMessage)
                }
                "new_anuncio_category", NotificationHelper.NotificationType.NEW_ANUNCIO_CATEGORY -> {
                    handleNewAnuncioNotification(data)
                }
                "anuncio_no_response", NotificationHelper.NotificationType.ANUNCIO_NO_RESPONSE -> {
                    handleReminderNotification(data)
                }
                "anuncio_favorite_update", NotificationHelper.NotificationType.ANUNCIO_FAVORITE -> {
                    handleFavoriteNotification(data)
                }
                "system_update", NotificationHelper.NotificationType.SYSTEM_UPDATE -> {
                    handleSystemNotification(data)
                }
                else -> {
                    // Fallback a notificación básica
                    Log.w("FCM", "Unknown notification type: $notificationType")
                    handleChatNotification(remoteMessage)
                }
            }
        } catch (e: Exception) {
            CrashlyticsHelper.logError(e, "MyFcmService.onMessageReceived()")
            Log.e("FCM", "Error procesando notificación: ${e.message}")
        }
    }

    private fun handleChatNotification(remoteMessage: RemoteMessage) {
        if (remoteMessage.notification != null) {
            val senderUid = remoteMessage.data["senderUid"] ?: "Desconocido"
            val title = remoteMessage.notification?.title ?: "Nuevo mensaje"
            val body = remoteMessage.notification?.body ?: ""

            val notificationId = NotificationHelper.generarNotificationId(
                NotificationHelper.NotificationType.CHAT_MESSAGE,
                senderUid
            )

            NotificationHelper.mostrarNotificacionChat(
                context = this,
                senderUid = senderUid,
                senderName = title,
                message = body,
                notificationId = notificationId
            )
        } else {
            Log.d("FCM", "Mensaje sin notificación")
        }
    }

    private fun handleNewAnuncioNotification(data: Map<String, String>) {
        val anuncioId = data["anuncioId"] ?: return
        val titulo = data["titulo"] ?: "Nuevo anuncio"
        val categoria = data["categoria"] ?: "General"
        val precio = data["precio"] ?: ""

        val notificationId = NotificationHelper.generarNotificationId(
            NotificationHelper.NotificationType.NEW_ANUNCIO_CATEGORY,
            anuncioId
        )

        NotificationHelper.mostrarNotificacionNuevoAnuncio(
            context = this,
            anuncioId = anuncioId,
            titulo = titulo,
            categoria = categoria,
            precio = precio,
            notificationId = notificationId
        )
    }

    private fun handleReminderNotification(data: Map<String, String>) {
        val anuncioId = data["anuncioId"] ?: return
        val titulo = data["titulo"] ?: "Tu anuncio"
        val dias = data["dias"]?.toIntOrNull() ?: 3

        val notificationId = NotificationHelper.generarNotificationId(
            NotificationHelper.NotificationType.ANUNCIO_NO_RESPONSE,
            anuncioId
        )

        NotificationHelper.mostrarNotificacionRecordatorio(
            context = this,
            anuncioId = anuncioId,
            tituloAnuncio = titulo,
            diasSinRespuesta = dias,
            notificationId = notificationId
        )
    }

    private fun handleFavoriteNotification(data: Map<String, String>) {
        val anuncioId = data["anuncioId"] ?: return
        val titulo = data["titulo"] ?: "Favorito"
        val cambio = data["cambio"] ?: "actualizado"

        val notificationId = NotificationHelper.generarNotificationId(
            NotificationHelper.NotificationType.ANUNCIO_FAVORITE,
            anuncioId
        )

        NotificationHelper.mostrarNotificacionFavorito(
            context = this,
            anuncioId = anuncioId,
            titulo = titulo,
            cambio = cambio,
            notificationId = notificationId
        )
    }

    private fun handleSystemNotification(data: Map<String, String>) {
        val titulo = data["titulo"] ?: "Actualización del sistema"
        val mensaje = data["mensaje"] ?: ""

        val notificationId = NotificationHelper.generarNotificationId(
            NotificationHelper.NotificationType.SYSTEM_UPDATE,
            System.currentTimeMillis().toString()
        )

        NotificationHelper.mostrarNotificacionSistema(
            context = this,
            titulo = titulo,
            mensaje = mensaje,
            notificationId = notificationId
        )
    }
}
