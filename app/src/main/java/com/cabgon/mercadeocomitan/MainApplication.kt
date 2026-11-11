package com.cabgon.mercadeocomitan

import android.app.Application
import com.cabgon.mercadeocomitan.Chat.ChatNotificationWatcher
import com.cabgon.mercadeocomitan.utils.NotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inicializa Firebase
        FirebaseApp.initializeApp(this)

        // Configura App Check con Play Integrity
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // Inicializa Firebase Crashlytics
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(true)
            // Logs personalizados para identificar usuario (opcional)
            setUserId("user_${System.currentTimeMillis()}")
        }

        // Crear canales de notificación
        NotificationHelper.crearCanalesNotificacion(this)

        // La presencia debe vivir a nivel de proceso para que cambiar entre
        // MainActivity y ChatActivity no marque al usuario como desconectado.
        PresenceManager.install(this)
        ChatNotificationWatcher.install(this)
    }
}
