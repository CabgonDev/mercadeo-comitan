package com.cabgon.mercadeocomitan

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorCategoriaFavorita
import com.cabgon.mercadeocomitan.Modelo.ModeloCategoria
import com.cabgon.mercadeocomitan.databinding.ActivityCategoriasFavoritasBinding
import com.cabgon.mercadeocomitan.utils.MessageUtils
import com.cabgon.mercadeocomitan.utils.CrashlyticsHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Activity para gestionar categorías favoritas del usuario
 * Las notificaciones de nuevos anuncios se enviarán solo para estas categorías
 */
class CategoriasFavoritasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoriasFavoritasBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var adaptador: AdaptadorCategoriaFavorita

    private val categoriasList = ArrayList<ModeloCategoria>()
    private val categoriasFavoritasActuales = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriasFavoritasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        configurarToolbar()
        cargarCategorias()
        configurarListeners()
    }

    private fun configurarToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Categorías Favoritas"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun configurarListeners() {
        binding.btnGuardar.setOnClickListener {
            guardarCategoriasFavoritas()
        }
    }

    private fun cargarCategorias() {
        binding.progressBar.visibility = android.view.View.VISIBLE

        val uid = firebaseAuth.uid
        if (uid == null) {
            MessageUtils.showError(binding.root, "Usuario no autenticado")
            finish()
            return
        }

        // Primero cargar las categorías favoritas actuales
        FirebaseDatabase.getInstance().getReference("Usuarios")
            .child(uid)
            .child("categoriasFavoritas")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    categoriasFavoritasActuales.clear()
                    for (child in snapshot.children) {
                        if (child.getValue(Boolean::class.java) == true) {
                            categoriasFavoritasActuales.add(child.key ?: "")
                        }
                    }

                    // Luego cargar todas las categorías disponibles
                    cargarTodasLasCategorias()
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.progressBar.visibility = android.view.View.GONE
                    MessageUtils.showError(binding.root, "Error al cargar favoritos: ${error.message}")
                    CrashlyticsHelper.logError(
                        Exception(error.message),
                        "CategoriasFavoritasActivity.cargarCategoriasFavoritas()"
                    )
                }
            })
    }

    private fun cargarTodasLasCategorias() {
        FirebaseDatabase.getInstance().getReference("Categorias")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    categoriasList.clear()

                    for (child in snapshot.children) {
                        val categoria = child.getValue(ModeloCategoria::class.java)
                        if (categoria != null) {
                            categoria.categoria = child.key ?: ""
                            categoria.esFavorita = categoriasFavoritasActuales.contains(categoria.categoria)
                            categoriasList.add(categoria)
                        }
                    }

                    // Ordenar alfabéticamente
                    categoriasList.sortBy { it.categoria }

                    configurarRecyclerView()
                    binding.progressBar.visibility = android.view.View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.progressBar.visibility = android.view.View.GONE
                    MessageUtils.showError(binding.root, "Error al cargar categorías: ${error.message}")
                    CrashlyticsHelper.logError(
                        Exception(error.message),
                        "CategoriasFavoritasActivity.cargarTodasLasCategorias()"
                    )
                }
            })
    }

    private fun configurarRecyclerView() {
        adaptador = AdaptadorCategoriaFavorita(this, categoriasList)
        binding.rvCategorias.apply {
            layoutManager = LinearLayoutManager(this@CategoriasFavoritasActivity)
            adapter = adaptador
            setHasFixedSize(true)
        }

        actualizarContador()
    }

    private fun actualizarContador() {
        val seleccionadas = categoriasList.count { it.esFavorita }
        binding.tvContador.text = "$seleccionadas categorías seleccionadas"
    }

    fun onCategoriaToggled() {
        actualizarContador()
    }

    private fun guardarCategoriasFavoritas() {
        val uid = firebaseAuth.uid
        if (uid == null) {
            MessageUtils.showError(binding.root, "Usuario no autenticado")
            return
        }

        binding.btnGuardar.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        val updates = HashMap<String, Any?>()

        // Limpiar todas las categorías favoritas anteriores
        for (categoria in categoriasList) {
            if (categoria.esFavorita) {
                updates["categoriasFavoritas/${categoria.categoria}"] = true
            } else {
                // Eliminar si existía antes
                updates["categoriasFavoritas/${categoria.categoria}"] = null
            }
        }

        FirebaseDatabase.getInstance().getReference("Usuarios")
            .child(uid)
            .updateChildren(updates)
            .addOnSuccessListener {
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnGuardar.isEnabled = true

                MessageUtils.showSuccess(
                    binding.root,
                    "Preferencias guardadas. Recibirás notificaciones de estas categorías."
                )

                CrashlyticsHelper.logUserAction("categorias_favoritas_guardadas")

                // Volver atrás después de 1 segundo
                binding.root.postDelayed({
                    finish()
                }, 1000)
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnGuardar.isEnabled = true

                MessageUtils.showError(binding.root, "Error al guardar: ${e.message}")
                CrashlyticsHelper.logError(e, "CategoriasFavoritasActivity.guardarCategoriasFavoritas()")
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
