package com.cabgon.mercadeocomitan

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cabgon.mercadeocomitan.Opciones_login.Login_email
import com.cabgon.mercadeocomitan.databinding.ActivityCambiarPaswordBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class CambiarPasword : AppCompatActivity() {

    private lateinit var binding: ActivityCambiarPaswordBinding
    private lateinit var progressDialog: ProgressDialogCompat
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseUser: FirebaseUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCambiarPaswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.ToolbarRL
        val baseHeight = toolbar.layoutParams.height
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = toolbar.layoutParams
            params.height = baseHeight + bars.top
            toolbar.layoutParams = params
            toolbar.setPadding(toolbar.paddingLeft, bars.top, toolbar.paddingRight, toolbar.paddingBottom)
            insets
        }

        progressDialog = ProgressDialogCompat(this)
        progressDialog.setTitle(getString(R.string.progress_title))
        progressDialog.setCanceledOnTouchOutside(false)

        firebaseAuth = FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null || currentUser.email.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Sesión no válida. Inicia sesión nuevamente",
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(this, Login_email::class.java))
            finishAffinity()
            return
        }
        firebaseUser = currentUser

        binding.BtnActualizarPass.setOnClickListener {
            validarInformacion()
        }
    }

    private var passwordActual = ""
    private var passwordNueva = ""
    private var passwordNuevaR = ""
    private fun validarInformacion() {
        passwordActual = binding.EtPasswordActual.text.toString().trim()
        passwordNueva = binding.EtPasswordNueva.text.toString().trim()
        passwordNuevaR = binding.EtPasswordNuevaR.text.toString().trim()

        if (passwordActual.isEmpty()) {
            binding.EtPasswordActual.error = "Ingrese su contraseña actual"
            binding.EtPasswordActual.requestFocus()
        } else if (passwordNueva.isEmpty()) {
            binding.EtPasswordNueva.error = "Ingrese su nueva contraseña"
            binding.EtPasswordNueva.requestFocus()
        } else if (passwordNueva.length < 6) {
            binding.EtPasswordNueva.error = "La nueva contraseña debe tener al menos 6 caracteres"
            binding.EtPasswordNueva.requestFocus()
        } else if (passwordNueva == passwordActual) {
            binding.EtPasswordNueva.error = "La nueva contraseña debe ser diferente a la actual"
            binding.EtPasswordNueva.requestFocus()
        } else if (passwordNuevaR.isEmpty()) {
            binding.EtPasswordNuevaR.error = "Confirme su nueva contraseña"
            binding.EtPasswordNuevaR.requestFocus()
        } else if (passwordNueva != passwordNuevaR) {
            binding.EtPasswordNuevaR.error = "Las contraseñas no coinciden"
            binding.EtPasswordNuevaR.requestFocus()
        } else {
            autenticarUsuarioCamPass()
        }
    }

    private fun autenticarUsuarioCamPass() {
        progressDialog.setMessage("Autenticando Usuario")
        progressDialog.show()

        val email = firebaseUser.email.orEmpty()
        val autoCredencial = EmailAuthProvider.getCredential(email, passwordActual)
        firebaseUser.reauthenticate(autoCredencial)
            .addOnSuccessListener {
                progressDialog.dismiss()
                actualizarPassword()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(
                    this,
                    "${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }

    }

    private fun actualizarPassword() {
        progressDialog.setMessage("Actualizando Contraseña")
        progressDialog.show()

        firebaseUser.updatePassword(passwordNueva)
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(
                    this,
                    "Contraseña actualizada",
                    Toast.LENGTH_SHORT
                ).show()

                firebaseAuth.signOut()
                startActivity(Intent(this, Login_email::class.java))
                finishAffinity()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(
                    this,
                    "${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun onDestroy() {
        progressDialog.dismiss()
        super.onDestroy()
    }

}