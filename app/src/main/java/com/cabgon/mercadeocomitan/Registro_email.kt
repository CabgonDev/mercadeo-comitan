package com.cabgon.mercadeocomitan

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cabgon.mercadeocomitan.databinding.ActivityRegistroEmailBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class Registro_email : AppCompatActivity() {

    private lateinit var binding : ActivityRegistroEmailBinding

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialogCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegistroEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.ToolbarRl
        val baseHeight = toolbar.layoutParams.height
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = toolbar.layoutParams
            params.height = baseHeight + bars.top
            toolbar.layoutParams = params
            toolbar.setPadding(toolbar.paddingLeft, bars.top, toolbar.paddingRight, toolbar.paddingBottom)
            insets
        }

        firebaseAuth = FirebaseAuth.getInstance()

        progressDialog = ProgressDialogCompat(this)
        progressDialog.setTitle(getString(R.string.progress_title))
        progressDialog.setCanceledOnTouchOutside(false)

        binding.BtnRegistrar.setOnClickListener {
            validarInfo()
        }
    }

    private var email = ""
    private var password = ""
    private var r_password = ""
    private fun validarInfo() {
        email = binding.EtEmail.text.toString().trim()
        password = binding.EtPassword.text.toString().trim()
        r_password = binding.EtRPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.EtEmail.error = "Ingrese su email"
            binding.EtEmail.requestFocus()
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.EtEmail.error = "Formato de email incorrecto"
            binding.EtEmail.requestFocus()
        } else if (password.isEmpty()) {
            binding.EtPassword.error = "Ingrese su contraseña"
            binding.EtPassword.requestFocus()
        } else if (password.length < 6) {
            binding.EtPassword.error = "La contraseña debe tener al menos 6 caracteres"
            binding.EtPassword.requestFocus()
        } else if (r_password.isEmpty()) {
            binding.EtRPassword.error = "Repita su contraseña"
            binding.EtRPassword.requestFocus()
        } else if (password != r_password) {
            binding.EtRPassword.error = "Las contraseñas no coinciden"
            binding.EtRPassword.requestFocus()
        } else {
            registrarUsuario()
        }
    }

    private fun registrarUsuario() {
        progressDialog.setMessage("Creando cuenta")
        progressDialog.show()
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                // Enviar email de verificación ANTES de crear perfil
                authResult.user?.sendEmailVerification()
                    ?.addOnSuccessListener {
                        llenarInfoBD()
                    }
                    ?.addOnFailureListener { e ->
                        progressDialog.dismiss()
                        Toast.makeText(
                            this,
                            "Cuenta creada pero no se pudo enviar email de verificación: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        // Aún así crear el perfil
                        llenarInfoBD()
                    }
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

    private fun llenarInfoBD() {
        progressDialog.setMessage("Guardando información")
        val tiempo = Constantes.obtenerTiempoDis()
        val currentUser = firebaseAuth.currentUser
        val uidUsuario = currentUser?.uid
        if (currentUser == null || uidUsuario.isNullOrBlank()) {
            progressDialog.dismiss()
            Toast.makeText(this, "No se pudo obtener la sesión del usuario", Toast.LENGTH_SHORT).show()
            return
        }
        val emailUsuario = currentUser.email.orEmpty().ifBlank { email }

        val hashMap = HashMap<String, Any>()
        hashMap["nombres"] = ""
        hashMap["codigoTelefono"] = ""
        hashMap["telefono"] = ""
        hashMap["urlImagenPerfil"] = ""
        hashMap["provedor"] = "Email"
        hashMap["escribiendo"] = ""
        hashMap["tiempo"] = tiempo
        hashMap["estado"] = "Online"
        hashMap["email"] = emailUsuario
        hashMap["uid"] = uidUsuario
        hashMap["fecha_nac"] = ""
        // Mismo perfil base que el registro con Google (no concede privilegios).
        hashMap["rol"] = Constantes.ROL_USUARIO
        hashMap["bloqueado"] = false
        hashMap["canPost"] = false

        val ref = FirebaseDatabase.getInstance().getReference("Usuarios")
        ref.child(uidUsuario)
            .setValue(hashMap)
            .addOnSuccessListener {
                progressDialog.dismiss()
                startActivity(Intent(this, MainActivity::class.java))
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
