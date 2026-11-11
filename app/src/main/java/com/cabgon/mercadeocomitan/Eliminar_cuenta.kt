package com.cabgon.mercadeocomitan

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cabgon.mercadeocomitan.databinding.ActivityEliminarCuentaBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Eliminar_cuenta : AppCompatActivity() {

    private lateinit var binding : ActivityEliminarCuentaBinding

    private lateinit var progressDialog: ProgressDialogCompat
    private lateinit var firebaseAuth: FirebaseAuth
    private var firebaseUser : FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEliminarCuentaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.root.findViewById<android.view.View>(R.id.ToolbarRl)
        val baseHeight = toolbar.layoutParams.height
        val button = binding.BtnEliminarMiCuenta
        val baseBottomMargin = (button.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarParams = toolbar.layoutParams
            toolbarParams.height = baseHeight + bars.top
            toolbar.layoutParams = toolbarParams
            toolbar.setPadding(toolbar.paddingLeft, bars.top, toolbar.paddingRight, toolbar.paddingBottom)
            val buttonParams = button.layoutParams as android.view.ViewGroup.MarginLayoutParams
            buttonParams.bottomMargin = baseBottomMargin + bars.bottom
            button.layoutParams = buttonParams
            insets
        }

        progressDialog = ProgressDialogCompat(this)
        progressDialog.setTitle(getString(R.string.progress_title))
        progressDialog.setCanceledOnTouchOutside(false)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseUser = firebaseAuth.currentUser

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTaskRoot) {
                    irMainActivity()
                } else {
                    finish()
                }
            }
        })

        binding.BtnEliminarMiCuenta.setOnClickListener {
            eliminarCuenta()
        }
    }

    private fun eliminarCuenta() {
        val user = firebaseUser ?: firebaseAuth.currentUser
        val miUid = user?.uid?.takeIf { it.isNotBlank() && Constantes.esClaveFirebaseValida(it) }
        if (user == null || miUid == null) {
            Toast.makeText(this, "No se encontró una sesión activa", Toast.LENGTH_SHORT).show()
            irMainActivity()
            return
        }

        progressDialog.setMessage("Eliminando cuenta permanentemente...")
        progressDialog.show()

        user.delete()
            .addOnSuccessListener {
                val anuncios = FirebaseDatabase.getInstance().getReference("Anuncios")
                anuncios.orderByChild("uid").equalTo(miUid)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            for (ds in snapshot.children) {
                                ds.ref.removeValue()
                            }

                            val usuarioBD = FirebaseDatabase.getInstance().getReference("Usuarios")
                            usuarioBD.child(miUid).removeValue()
                                .addOnSuccessListener {
                                    progressDialog.dismiss()
                                    irMainActivity()
                                }
                                .addOnFailureListener { e ->
                                    progressDialog.dismiss()
                                    Toast.makeText(this@Eliminar_cuenta, "${e.message}", Toast.LENGTH_SHORT).show()
                                    irMainActivity()
                                }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            progressDialog.dismiss()
                            Toast.makeText(this@Eliminar_cuenta, "No se pudo eliminar la cuenta", Toast.LENGTH_SHORT).show()
                            irMainActivity()
                        }
                    })
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun irMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }

    override fun onDestroy() {
        progressDialog.dismiss()
        super.onDestroy()
    }
}
