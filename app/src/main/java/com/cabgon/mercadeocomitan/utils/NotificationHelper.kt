package com.cabgon.mercadeocomitan.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cabgon.mercadeocomitan.Chat.ChatActivity
import com.cabgon.mercadeocomitan.DetalleAnuncio.DetalleAnuncio
import com.cabgon.mercadeocomitan.MainActivity
import com.cabgon.mercadeocomitan.R

/**
 * Helper centralizado para gestionar notificaciones push
 * Soporta múltiples tipos de notificaciones con deep links
 */
object NotificationHelper {

    // IDs de canales
    const val CHANNEL_CHAT = "chat_channel"
    const val CHANNEL_ANUNCIOS = "anuncios_channel"
    const val CHANNEL_SISTEMA = "sistema_channel"
    const val CHANNEL_MARKETING = "marketing_channel"

    // Tipos de notificación
    object NotificationType {
        const val CHAT_MESSAGE = "chat_message"
        const val NEW_ANUNCIO_CATEGORY = "new_anuncio_category"
        const val ANUNCIO_NO_RESPONSE = "anuncio_no_response"
        const val ANUNCIO_FAVORITE = "anuncio_favorite"
        const val SYSTEM_UPDATE = "system_update"
        const val PROMOTIONAL = "promotional"
    }

    /**
     * Crea todos los canales de notificación necesarios
     * Debe llamarse al inicio de la app (MainApplication.onCreate)
     */
    fun crearCanalesNotificacion(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal de Chat (Prioridad Alta)
            val chatChannel = NotificationChannel(
                CHANNEL_CHAT,
                "Mensajes de Chat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de mensajes nuevos"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            // Canal de Anuncios (Prioridad Media)
            val anunciosChannel = NotificationChannel(
                CHANNEL_ANUNCIOS,
                "Nuevos Anuncios",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Nuevos anuncios en tus categorías favoritas"
                enableVibration(true)
                setShowBadge(true)
            }

            // Canal de Sistema (Prioridad Baja)
            val sistemaChannel = NotificationChannel(
                CHANNEL_SISTEMA,
                "Sistema",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Actualizaciones y mantenimiento"
                setShowBadge(false)
            }

            // Canal de Marketing (Prioridad Baja)
            val marketingChannel = NotificationChannel(
                CHANNEL_MARKETING,
                "Promociones",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ofertas y promociones especiales"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(
                listOf(chatChannel, anunciosChannel, sistemaChannel, marketingChannel)
            )
        }
    }

    /**
     * Muestra notificación de mensaje de chat
     */
    fun mostrarNotificacionChat(
        context: Context,
        senderUid: String,
        senderName: String,
        message: String,
        notificationId: Int
    ) {
        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra("uidVendedor", senderUid)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(R.drawable.icono_notificacion)
            .setContentTitle(senderName)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)

        CrashlyticsHelper.logEvent("notification_shown", "chat_message")
    }

    /**
     * Muestra notificación de nuevo anuncio en categoría favorita
     */
    fun mostrarNotificacionNuevoAnuncio(
        context: Context,
        anuncioId: String,
        titulo: String,
        categoria: String,
        precio: String,
        notificationId: Int
    ) {
        val intent = Intent(context, DetalleAnuncio::class.java).apply {
            putExtra("idAnuncio", anuncioId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ANUNCIOS)
            .setSmallIcon(R.drawable.icono_notificacion)
            .setContentTitle("Nuevo en $categoria")
            .setContentText("$titulo - $precio")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$titulo\n$precio\n\nNuevo anuncio disponible en tu categoría favorita: $categoria"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)

        CrashlyticsHelper.logEvent("notification_shown", "new_anuncio_$categoria")
    }

    /**
     * Muestra notificación de recordatorio para anuncio sin respuesta
     */
    fun mostrarNotificacionRecordatorio(
        context: Context,
        anuncioId: String,
        tituloAnuncio: String,
        diasSinRespuesta: Int,
        notificationId: Int
    ) {
        val intent = Intent(context, DetalleAnuncio::class.java).apply {
            putExtra("idAnuncio", anuncioId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ANUNCIOS)
            .setSmallIcon(R.drawable.icono_notificacion)
            .setContentTitle("¿Aún está disponible?")
            .setContentText("Tu anuncio \"$tituloAnuncio\" lleva $diasSinRespuesta días sin mensajes")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Tu anuncio \"$tituloAnuncio\" lleva $diasSinRespuesta días sin recibir mensajes.\n\n¿Deseas actualizarlo o revisar el precio?"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)

        CrashlyticsHelper.logEvent("notification_shown", "reminder_no_response")
    }

    /**
     * Muestra notificación cuando un anuncio favorito tiene cambios
     */
    fun mostrarNotificacionFavorito(
        context: Context,
        anuncioId: String,
        titulo: String,
        cambio: String,
        notificationId: Int
    ) {
        val intent = Intent(context, DetalleAnuncio::class.java).apply {
            putExtra("idAnuncio", anuncioId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ANUNCIOS)
            .setSmallIcon(R.drawable.icono_notificacion)
            .setContentTitle("Favorito actualizado")
            .setContentText("$titulo - $cambio")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)

        CrashlyticsHelper.logEvent("notification_shown", "favorite_updated")
    }

    /**
     * Muestra notificación del sistema (actualizaciones, mantenimiento)
     */
    fun mostrarNotificacionSistema(
        context: Context,
        titulo: String,
        mensaje: String,
        notificationId: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SISTEMA)
            .setSmallIcon(R.drawable.icono_notificacion)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)

        CrashlyticsHelper.logEvent("notification_shown", "system")
    }

    /**
     * Cancela una notificación específica
     */
    fun cancelarNotificacion(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * Cancela todas las notificaciones
     */
    fun cancelarTodasNotificaciones(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    /**
     * Genera un ID único para notificaciones basado en el tipo y referencia
     */
    fun generarNotificationId(tipo: String, referencia: String): Int {
        return "$tipo:$referencia".hashCode()
    }
}
