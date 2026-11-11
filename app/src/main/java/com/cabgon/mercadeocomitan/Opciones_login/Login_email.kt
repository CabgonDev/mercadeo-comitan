package com.cabgon.mercadeocomitan.Opciones_login

import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cabgon.mercadeocomitan.MainActivity
import com.cabgon.mercadeocomitan.RecuperarPassword
import com.cabgon.mercadeocomitan.Registro_email
import com.cabgon.mercadeocomitan.databinding.ActivityLoginEmailBinding
import com.google.firebase.auth.FirebaseAuth

class Login_email : AppCompatActivity() {

    private lateinit var binding: ActivityLoginEmailBinding
    private lateinit var firebaseAuth: FirebaseAuth

    private val handler = Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val basePaddingLeft = binding.backgroundLayout.paddingLeft
        val basePaddingTop = binding.backgroundLayout.paddingTop
        val basePaddingRight = binding.backgroundLayout.paddingRight
        val basePaddingBottom = binding.backgroundLayout.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.backgroundLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                basePaddingLeft + bars.left,
                basePaddingTop + bars.top,
                basePaddingRight + bars.right,
                basePaddingBottom + bars.bottom
            )
            insets
        }

        firebaseAuth = FirebaseAuth.getInstance()

        binding.BtnLogin.setOnClickListener { validarInfo() }
        binding.TxtRegistrarme.setOnClickListener {
            startActivity(Intent(this@Login_email, Registro_email::class.java))
        }
        binding.TvRecuperar.setOnClickListener {
            startActivity(Intent(this@Login_email, RecuperarPassword::class.java))
        }
    }

    private var email = ""
    private var password = ""

    private fun validarInfo() {
        email = binding.EtEmail.text?.toString()?.trim().orEmpty()
        password = binding.EtPassword.text?.toString()?.trim().orEmpty()

        when {
            email.isEmpty() -> {
                binding.EtEmail.error = "Ingrese email"
                binding.EtEmail.requestFocus()
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.EtEmail.error = "Formato de email incorrecto"
                binding.EtEmail.requestFocus()
            }
            password.isEmpty() -> {
                binding.EtPassword.error = "Ingrese contraseña"
                binding.EtPassword.requestFocus()
            }
            else -> {
                mostrarCargando()
                loginUsuario()
            }
        }
    }

    private fun loginUsuario() {
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                mostrarMensajeFinal()
            }
            .addOnFailureListener { e ->
                ocultarCargando()
                Toast.makeText(
                    this,
                    "No se pudo iniciar sesión: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // ==========================
    // Overlay + Animaciones
    // ==========================
    private fun mostrarCargando() {
        aplicarEfectoBlur(true)
        binding.loadingOverlay.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(250).start()
        }
        iniciarAnimacionMensajes()
    }

    private fun iniciarAnimacionMensajes() {
        binding.loadingText.text = "Validando tus credenciales..."
    }

    private fun mostrarMensajeFinal() {
        binding.loadingText.text = "¡Bienvenido!"
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                ocultarCargando()
                startActivity(Intent(this, MainActivity::class.java))
                finishAffinity()
            }
        }, 500)
    }

    private fun ocultarCargando() {
        handler.removeCallbacksAndMessages(null)
        aplicarEfectoBlur(false)
        binding.loadingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { binding.loadingOverlay.visibility = View.GONE }
            .start()
    }

    private fun aplicarEfectoBlur(activar: Boolean) {
        val fondo = binding.backgroundLayout // Solo el contenido, no el overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            fondo.setRenderEffect(
                if (activar) RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP)
                else null
            )
        } else {
            // Fallback para versiones anteriores: leve atenuación
            fondo.alpha = if (activar) 0.6f else 1f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
