package com.cabgon.mercadeocomitan

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorComentario
import com.cabgon.mercadeocomitan.Modelo.ModeloComentario
import com.cabgon.mercadeocomitan.databinding.ActivityComentariosBinding
import com.cabgon.mercadeocomitan.databinding.CuadroDAgregarComentarioBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Comentarios : AppCompatActivity() {

    private lateinit var binding: ActivityComentariosBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var comentarioArrayList: ArrayList<ModeloComentario>
    private lateinit var adaptadorComentario: AdaptadorComentario
    private var uidVendedor = ""
    private var comentariosRef: DatabaseReference? = null
    private var comentariosListener: ValueEventListener? = null
    private var dialogProgreso: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityComentariosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        uidVendedor = intent.getStringExtra("uidVendedor")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: run {
                Toast.makeText(this, R.string.comentarios_uid_invalido, Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        configurarInsets()
        configurarLista()

        binding.IbAgregarComentario.setOnClickListener { dialogComentar() }
        binding.btnReintentarComentarios.setOnClickListener { listarComentarios() }

        listarComentarios()
    }

    private fun configurarLista() {
        comentarioArrayList = ArrayList()
        adaptadorComentario = AdaptadorComentario(this, comentarioArrayList)
        binding.RvComentarios.setHasFixedSize(true)
        binding.RvComentarios.adapter = adaptadorComentario
    }

    private fun listarComentarios() {
        mostrarCarga()
        comentariosListener?.let { listener -> comentariosRef?.removeEventListener(listener) }

        val ref = FirebaseDatabase.getInstance()
            .getReference("ComentariosVendedores")
            .child(uidVendedor)
            .child("Comentarios")
        comentariosRef = ref
        comentariosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                comentarioArrayList.clear()
                for (ds in snapshot.children) {
                    val modelo = runCatching {
                        ds.getValue(ModeloComentario::class.java)
                    }.getOrNull()
                    if (modelo != null && modelo.comentario.isNotBlank()) {
                        comentarioArrayList.add(modelo)
                    }
                }
                comentarioArrayList.sortByDescending { it.tiempo.toLongOrNull() ?: 0L }
                adaptadorComentario.notifyDataSetChanged()
                actualizarContador(comentarioArrayList.size)
                mostrarContenido(comentarioArrayList.isEmpty())
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("Comentarios", "No se pudieron cargar los comentarios", error.toException())
                mostrarError()
            }
        }
        ref.addValueEventListener(comentariosListener as ValueEventListener)
    }

    private fun actualizarContador(total: Int) {
        binding.tvConteoComentarios.text = when (total) {
            0 -> getString(R.string.comentarios_contador_vacio)
            1 -> getString(R.string.comentarios_contador_uno, total)
            else -> getString(R.string.comentarios_contador_varios, total)
        }
    }

    private fun mostrarCarga() {
        binding.progressComentarios.visibility = View.VISIBLE
        binding.RvComentarios.visibility = View.GONE
        binding.estadoVacioComentarios.visibility = View.GONE
        binding.estadoErrorComentarios.visibility = View.GONE
    }

    private fun mostrarContenido(vacio: Boolean) {
        binding.progressComentarios.visibility = View.GONE
        binding.RvComentarios.visibility = if (vacio) View.GONE else View.VISIBLE
        binding.estadoVacioComentarios.visibility = if (vacio) View.VISIBLE else View.GONE
        binding.estadoErrorComentarios.visibility = View.GONE
    }

    private fun mostrarError() {
        binding.progressComentarios.visibility = View.GONE
        binding.RvComentarios.visibility = View.GONE
        binding.estadoVacioComentarios.visibility = View.GONE
        binding.estadoErrorComentarios.visibility = View.VISIBLE
    }

    private fun dialogComentar() {
        val agregarBinding = CuadroDAgregarComentarioBinding.inflate(LayoutInflater.from(this))
        val alertDialog = MaterialAlertDialogBuilder(this)
            .setView(agregarBinding.root)
            .create()

        alertDialog.setCanceledOnTouchOutside(true)
        alertDialog.setOnShowListener {
            alertDialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
        agregarBinding.IbCerrar.setOnClickListener { alertDialog.dismiss() }
        agregarBinding.BtnComentar.setOnClickListener {
            val texto = agregarBinding.EtAgregarComentario.text?.toString()?.trim().orEmpty()
            if (texto.isBlank()) {
                agregarBinding.TILComentario.error = getString(R.string.comentarios_texto_vacio)
                return@setOnClickListener
            }
            agregarBinding.TILComentario.error = null
            alertDialog.dismiss()
            agregarComentario(texto)
        }
        alertDialog.show()
    }

    private fun agregarComentario(texto: String) {
        val uidActual = firebaseAuth.currentUser?.uid
        if (uidActual.isNullOrBlank()) {
            Toast.makeText(this, R.string.comentarios_sesion_requerida, Toast.LENGTH_SHORT).show()
            return
        }

        dialogProgreso = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.comentarios_publicando)
            .setMessage(R.string.comentarios_espera)
            .setView(ProgressBar(this).apply { isIndeterminate = true })
            .setCancelable(false)
            .create()
        dialogProgreso?.show()

        val tiempo = Constantes.obtenerTiempoDis().toString()
        val valores = hashMapOf<String, Any>(
            "id" to tiempo,
            "tiempo" to tiempo,
            "uid" to uidActual,
            "uid_vendedor" to uidVendedor,
            "comentario" to texto.take(500),
        )

        FirebaseDatabase.getInstance()
            .getReference("ComentariosVendedores")
            .child(uidVendedor)
            .child("Comentarios")
            .child(tiempo)
            .setValue(valores)
            .addOnSuccessListener {
                cerrarDialogoProgreso()
                Toast.makeText(this, R.string.comentarios_publicado, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                cerrarDialogoProgreso()
                Toast.makeText(this, error.message ?: getString(R.string.comentarios_error_titulo), Toast.LENGTH_SHORT).show()
            }
    }

    private fun cerrarDialogoProgreso() {
        dialogProgreso?.dismiss()
        dialogProgreso = null
    }

    @Suppress("DEPRECATION")
    private fun configurarInsets() {
        val toolbarBaseHeight = binding.ToolbarRl.layoutParams.height
        val toolbarBasePaddingTop = binding.ToolbarRl.paddingTop
        val rvBasePaddingBottom = binding.RvComentarios.paddingBottom
        val emptyBasePaddingBottom = binding.estadoVacioComentarios.paddingBottom
        val errorBasePaddingBottom = binding.estadoErrorComentarios.paddingBottom

        window.statusBarColor = ContextCompat.getColor(this, R.color.mi_color)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.md_theme_background)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarParams = binding.ToolbarRl.layoutParams
            toolbarParams.height = toolbarBaseHeight + bars.top
            binding.ToolbarRl.layoutParams = toolbarParams
            binding.ToolbarRl.setPadding(
                binding.ToolbarRl.paddingLeft,
                toolbarBasePaddingTop + bars.top,
                binding.ToolbarRl.paddingRight,
                binding.ToolbarRl.paddingBottom,
            )
            binding.RvComentarios.setPadding(
                binding.RvComentarios.paddingLeft,
                binding.RvComentarios.paddingTop,
                binding.RvComentarios.paddingRight,
                rvBasePaddingBottom + bars.bottom,
            )
            binding.estadoVacioComentarios.setPadding(
                binding.estadoVacioComentarios.paddingLeft,
                binding.estadoVacioComentarios.paddingTop,
                binding.estadoVacioComentarios.paddingRight,
                emptyBasePaddingBottom + bars.bottom,
            )
            binding.estadoErrorComentarios.setPadding(
                binding.estadoErrorComentarios.paddingLeft,
                binding.estadoErrorComentarios.paddingTop,
                binding.estadoErrorComentarios.paddingRight,
                errorBasePaddingBottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onDestroy() {
        comentariosListener?.let { listener -> comentariosRef?.removeEventListener(listener) }
        comentariosListener = null
        comentariosRef = null
        cerrarDialogoProgreso()
        super.onDestroy()
    }
}
