package com.cabgon.mercadeocomitan

import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.cabgon.mercadeocomitan.Opciones_login.Login_email
import com.cabgon.mercadeocomitan.databinding.ActivityOpcionesLoginBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch

class OpcionesLogin : AppCompatActivity() {

    private lateinit var binding: ActivityOpcionesLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth

    private val handler = Handler(Looper.getMainLooper())

    private var loginComplete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) primero auth
        firebaseAuth = FirebaseAuth.getInstance()

        // 2) si YA hay sesión, no pintes el login → vete al main de una vez
        val user = firebaseAuth.currentUser
        if (user != null) {
            // nos vamos YA
            irAMain()

            // y ACTUALIZAMOS en segundo plano sin bloquear la navegación
            actualizarUsuarioSilencioso(user)
            return
        }

        // 3) si NO hay sesión, ahora sí pintamos la pantalla
        enableEdgeToEdge()
        binding = ActivityOpcionesLoginBinding.inflate(layoutInflater)
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

        binding.IngresarEmail.setOnClickListener {
            startActivity(Intent(this@OpcionesLogin, Login_email::class.java))
        }

        binding.IngresarGoogle.setOnClickListener {
            mostrarCargando()
            googleLogin()
        }
    }

    // ==========================
    // caso: ya había sesión (fire-and-forget)
    // ==========================
    private fun actualizarUsuarioSilencioso(user: com.google.firebase.auth.FirebaseUser) {
        val uidUsuario = user.uid
        val ref = FirebaseDatabase.getInstance().getReference("Usuarios").child(uidUsuario)
        ref.get().addOnSuccessListener { snapshot ->
            ref.updateChildren(construirActualizacionPerfil(snapshot, user, "Google"))
        }
    }

    private fun irAMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }

    // ==========================
    // 🔐 LOGIN CON GOOGLE
    // ==========================
    private fun googleLogin() {
        binding.loadingText.text = "Esperando confirmación de Google..."
        val credentialManager = CredentialManager.create(this)

        lifecycleScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {}

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            try {
                val result = credentialManager.getCredential(request = request, context = this@OpcionesLogin)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    autenticacionGoogle(googleIdTokenCredential.idToken)
                } else {
                    ocultarCargando()
                    Toast.makeText(this@OpcionesLogin, "No se obtuvo una credencial válida.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: GetCredentialException) {
                ocultarCargando()
                Toast.makeText(this@OpcionesLogin, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                ocultarCargando()
                Toast.makeText(this@OpcionesLogin, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun autenticacionGoogle(idToken: String?) {
        if (idToken.isNullOrBlank()) {
            ocultarCargando()
            Toast.makeText(this, "No se pudo obtener el token de Google", Toast.LENGTH_SHORT).show()
            return
        }
        binding.loadingText.text = "Verificando tu cuenta..."
        firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnSuccessListener { authResult ->
                val user = authResult.user

                // 🚨 SEGURIDAD: Verificar que el email de Google esté verificado
                if (user == null || user.email.isNullOrBlank()) {
                    firebaseAuth.signOut()
                    ocultarCargando()
                    Toast.makeText(this, "No se pudo obtener información de la cuenta de Google", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Google ya verifica los emails, pero agregar capa extra de seguridad
                if (!user.isEmailVerified) {
                    firebaseAuth.signOut()
                    ocultarCargando()
                    Toast.makeText(this, "Tu cuenta de Google debe tener el email verificado", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // 🚨 SEGURIDAD: Detectar patrones sospechosos de email
                val email = user.email!!
                if (esEmailSospechoso(email)) {
                    // Registrar intento sospechoso
                    firebaseAuth.signOut()
                    ocultarCargando()
                    Toast.makeText(this, "No se pudo completar el registro. Contacta a soporte.", Toast.LENGTH_SHORT).show()
                    android.util.Log.w("OpcionesLogin", "Registro bloqueado - email sospechoso: $email")
                    return@addOnSuccessListener
                }

                // ✅ TODO OK - pasamos a crear/actualizar usuario
                llenarInfoBD()
            }
            .addOnFailureListener { e ->
                ocultarCargando()
                Toast.makeText(this, "Error de autenticación: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * 🚨 SEGURIDAD: Detecta patrones de emails sospechosos usados por bots
     */
    private fun esEmailSospechoso(email: String): Boolean {
        val emailLower = email.lowercase()

        // Patrón 1: nombre.numero@gmail.com (ej: murieltodd.68760@gmail.com)
        val patron1 = Regex("^[a-z]+\\.[0-9]{4,6}@gmail\\.com$")
        if (patron1.matches(emailLower)) {
            return true
        }

        // Patrón 2: nombreapellido.numerolargo@gmail.com
        val patron2 = Regex("^[a-z]+[a-z]+\\.[0-9]{5,}@gmail\\.com$")
        if (patron2.matches(emailLower)) {
            return true
        }

        // Patrón 3: Más de 4 números consecutivos en el email
        if (Regex("[0-9]{5,}").containsMatchIn(emailLower)) {
            return true
        }

        // Patrón 4: Nombres comunes de bots
        val nombresSospechosos = listOf(
            "test", "demo", "sample", "fake", "bot", "spam",
            "admin", "root", "support", "info", "noreply"
        )
        if (nombresSospechosos.any { emailLower.startsWith(it) }) {
            return true
        }

        return false
    }

    // ==========================
    // 🗂️ CREAR / ACTUALIZAR USUARIO EN BD
    // ==========================
    private fun llenarInfoBD() {
        binding.loadingText.text = "Preparando tu perfil..."
        val user = firebaseAuth.currentUser ?: return
        val uidUsuario = user.uid
        val ref = FirebaseDatabase.getInstance().getReference("Usuarios").child(uidUsuario)

        ref.get().addOnSuccessListener { snapshot ->
            ref.updateChildren(construirActualizacionPerfil(snapshot, user, "Google"))
                .addOnSuccessListener {
                    loginComplete = true
                    mostrarBienvenida()
                }
                .addOnFailureListener { e ->
                    ocultarCargando()
                    Toast.makeText(this, "Error al actualizar usuario: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener { e ->
            ocultarCargando()
            Toast.makeText(this, "Error al verificar usuario: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Completa sólo campos ausentes y presencia. Roles, bloqueo, favoritos, teléfono,
     * fecha de nacimiento y permisos de publicación pertenecen al perfil existente.
     */
    private fun construirActualizacionPerfil(
        snapshot: com.google.firebase.database.DataSnapshot,
        user: com.google.firebase.auth.FirebaseUser,
        proveedor: String
    ): HashMap<String, Any> {
        val updates = HashMap<String, Any>()
        val uid = user.uid
        val email = user.email.orEmpty()
        val nombre = user.displayName.orEmpty()
        val foto = user.photoUrl?.toString().orEmpty()

        updates["uid"] = uid
        updates["estado"] = "Online"
        updates["lastActive"] = com.google.firebase.database.ServerValue.TIMESTAMP
        if (!snapshot.child("tiempo").exists()) updates["tiempo"] = Constantes.obtenerTiempoDis()
        if (!snapshot.child("email").exists() && email.isNotBlank()) updates["email"] = email
        if (!snapshot.child("nombres").exists() && nombre.isNotBlank()) updates["nombres"] = nombre
        if (!snapshot.child("urlImagenPerfil").exists() && foto.isNotBlank()) updates["urlImagenPerfil"] = foto
        if (!snapshot.child("provedor").exists()) updates["provedor"] = proveedor
        if (!snapshot.child("escribiendo").exists()) updates["escribiendo"] = ""
        if (!snapshot.exists()) {
            updates["rol"] = Constantes.ROL_USUARIO
            updates["bloqueado"] = false
            updates["canPost"] = false
        }
        return updates
    }

    // ==========================
    // 💫 OVERLAY + MENSAJES
    // ==========================
    private fun mostrarCargando() {
        loginComplete = false

        aplicarEfectoBlur(true)

        binding.loadingOverlay.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(300).start()
        }

        binding.loaderContainer.scaleX = 0.8f
        binding.loaderContainer.scaleY = 0.8f
        binding.loaderContainer.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator())
            .start()

        binding.loadingText.text = "Conectando con Google..."
    }

    private fun mostrarBienvenida() {
        if (!loginComplete) return
        binding.loadingText.text = "¡Bienvenido!"
        handler.postDelayed({ mostrarLogoBienvenida() }, 500)
    }

    private fun mostrarLogoBienvenida() {
        val logo = binding.logoBienvenida
        logo.visibility = View.VISIBLE
        logo.scaleX = 0.7f
        logo.scaleY = 0.7f
        logo.alpha = 0f

        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                handler.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        ocultarCargando()
                        irAMain()
                    }
                }, 900)
            }
            .start()
    }

    private fun ocultarCargando() {
        handler.removeCallbacksAndMessages(null)
        aplicarEfectoBlur(false)
        binding.loadingOverlay.animate()
            .alpha(0f)
            .setDuration(350)
            .withEndAction {
                binding.loadingOverlay.visibility = View.GONE
                binding.logoBienvenida.visibility = View.GONE
            }
            .start()
    }

    private fun aplicarEfectoBlur(activar: Boolean) {
        val fondo = binding.backgroundLayout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            fondo.setRenderEffect(
                if (activar) RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP)
                else null
            )
        } else {
            fondo.alpha = if (activar) 0.4f else 1f
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
