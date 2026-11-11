@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Notificaciones

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cabgon.mercadeocomitan.Chat.ChatActivity
import com.cabgon.mercadeocomitan.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.Random

class MyFcmService : FirebaseMessagingService() {

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        Log.i("FCM", "Token renovado para el servicio legado")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        if(remoteMessage.notification != null){

            val senderUid = remoteMessage.data["senderUid"]

            showNotification(
                remoteMessage.notification?.title,
                remoteMessage.notification?.body,
                senderUid ?: "Desconocido",
            )

        }else{
            Log.d("FCM", "Mensaje sin notificación")

        }

    }

    private fun showNotification(title: String?, body: String?, senderUid: String) {
        val notificationId = Random().nextInt(3000)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        configurarCanalNotification(notificationManager)
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("uidVendedor", senderUid)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, "default_channel")
            .setSmallIcon(R.drawable.icono_notificacion)
            .setContentTitle(title ?: "Sin titulo")
            .setContentText(body ?: "Sin contenido")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun configurarCanalNotification(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val notificationChannel = NotificationChannel(
                "default_channel",
                "Chat_Channel",
                NotificationManager.IMPORTANCE_HIGH,
            )
            notificationChannel.description = "Show Chat Notifications"
            notificationChannel.enableVibration(true)
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
}
