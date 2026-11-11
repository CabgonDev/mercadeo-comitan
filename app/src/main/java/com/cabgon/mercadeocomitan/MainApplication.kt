package com.cabgon.mercadeocomitan

import android.app.Application
import com.cabgon.mercadeocomitan.Chat.ChatNotificationWatcher
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

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

        // La presencia debe vivir a nivel de proceso para que cambiar entre
        // MainActivity y ChatActivity no marque al usuario como desconectado.
        PresenceManager.install(this)
        ChatNotificationWatcher.install(this)
    }
}
