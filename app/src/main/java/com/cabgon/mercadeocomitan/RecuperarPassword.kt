package com.cabgon.mercadeocomitan

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cabgon.mercadeocomitan.databinding.ActivityRecuperarPasswordBinding
import com.google.firebase.auth.FirebaseAuth

class RecuperarPassword : AppCompatActivity() {

    private lateinit var binding: ActivityRecuperarPasswordBinding
    private lateinit var progressDialog: ProgressDialogCompat
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRecuperarPasswordBinding.inflate(layoutInflater)
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

        binding.BtnEnviarInstrucciones.setOnClickListener {
            validarEmail()
        }
    }

    private var email = ""
    private fun validarEmail() {
        email = binding.EtEmail.text.toString().trim()
        if (email.isEmpty()) {
            binding.EtEmail.error = "Ingrese su email"
            binding.EtEmail.requestFocus()
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.EtEmail.error = "Ingrese un email válido"
            binding.EtEmail.requestFocus()
        } else {
            enviarInstrucciones()
        }
    }
    private fun enviarInstrucciones() {
        progressDialog.setMessage("Enviando instrucciones a $email")
        progressDialog.show()

        firebaseAuth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(
                    this,
                    "Instrucciones enviadas a $email",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(
                    this,
                    "No se pudo enviar instrucciones a $email debido a ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun onDestroy() {
        progressDialog.dismiss()
        super.onDestroy()
    }
}
