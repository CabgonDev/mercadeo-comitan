package com.cabgon.mercadeocomitan

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.auth.FirebaseAuth

class PantallaInicio : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val navRunnable = Runnable {
        if (isFinishing || isDestroyed) return@Runnable
        val destino = if (FirebaseAuth.getInstance().currentUser != null) {
            MainActivity::class.java
        } else {
            OpcionesLogin::class.java
        }
        startActivity(Intent(this, destino))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pantalla_inicio)

        // Nunca obligamos a un usuario autenticado a atravesar el login otra vez.
        handler.postDelayed(navRunnable, 650)
    }

    override fun onDestroy() {
        handler.removeCallbacks(navRunnable)
        super.onDestroy()
    }
}
