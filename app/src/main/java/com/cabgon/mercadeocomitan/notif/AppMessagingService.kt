package com.cabgon.mercadeocomitan.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cabgon.mercadeocomitan.MainActivity
import com.cabgon.mercadeocomitan.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppMessagingService : FirebaseMessagingService() {

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        // Este servicio legado no se registra en AndroidManifest.
        // No imprimir tokens FCM en Logcat: identifican instalaciones concretas.
        // El servicio activo guarda los tokens en Firebase en ruta por dispositivo.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Si viene "notification" lo puedes dejar al sistema, pero por consistencia siempre muestro yo.
        val title = message.notification?.title ?: message.data["title"] ?: "Mercadeo Comitán"
        val body  = message.notification?.body  ?: message.data["body"]  ?: "Nuevo mensaje"

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "mensajes_mc"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Mensajes", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val pi = PendingIntent.getActivity(this, 0, intent, flags)
        val largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_round)

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_notif) // usa un ícono válido, blanco puro
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
