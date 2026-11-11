package com.cabgon.mercadeocomitan.DetalleVendedor

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorAnuncio
import com.cabgon.mercadeocomitan.Comentarios
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.ModeloAnuncio
import com.cabgon.mercadeocomitan.Pedidos.CalificacionRepository
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.databinding.ActivityDetalleVendedorBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class DetalleVendedor : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleVendedorBinding
    private val calificacionRepository = CalificacionRepository()
    private var uidVendedor = ""
    private var anunciosRef: com.google.firebase.database.DatabaseReference? = null
    private var anunciosQuery: com.google.firebase.database.Query? = null
    private var anunciosListener: ValueEventListener? = null
    private var vendedorRef: com.google.firebase.database.DatabaseReference? = null
    private var vendedorListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleVendedorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarInsets()

        uidVendedor = intent.getStringExtra("uidVendedor")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" && Constantes.esClaveFirebaseValida(it) }
            ?: run {
                finish()
                return
            }

        binding.anunciosRv.setHasFixedSize(true)

        cargarInfoVendedor()
        cargarCalificacionVendedor()
        cargarAnunciosVendedor()

        binding.IvComentarios.setOnClickListener {
            val intent = Intent(this, Comentarios::class.java)
            intent.putExtra("uidVendedor", uidVendedor)
            startActivity(intent)
        }
    }

    private fun cargarCalificacionVendedor() {
        binding.TvCalificacionVendedor.setText(R.string.calificacion_vendedor_cargando)
        calificacionRepository.cargarResumen(uidVendedor) { resultado ->
            if (isFinishing || isDestroyed) return@cargarResumen

            val resumen = resultado.getOrNull()
            binding.TvCalificacionVendedor.text = when {
                resultado.isFailure || resumen == null ->
                    getString(R.string.calificacion_vendedor_no_disponible)
                resumen.total == 0 ->
                    getString(R.string.calificacion_vendedor_sin_datos)
                else -> getString(
                    R.string.calificacion_vendedor_resumen,
                    String.format(Locale.US, "%.1f", resumen.promedio),
                    resumen.total,
                )
            }
        }
    }

    private fun cargarAnunciosVendedor() {
        val anuncioArrayList : ArrayList<ModeloAnuncio> = ArrayList()

        anunciosRef = FirebaseDatabase.getInstance().getReference("Anuncios")
        anunciosListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    anuncioArrayList.clear()
                    for (ds in snapshot.children){
                        try {
                            val modeloAnuncio = ds.getValue(ModeloAnuncio::class.java)
                            if (modeloAnuncio != null) anuncioArrayList.add(modeloAnuncio)
                        }catch (e: Exception){
                            android.util.Log.w("DetalleVendedor", "Anuncio omitido", e)
                        }
                    }

                    val adaptador = AdaptadorAnuncio(this@DetalleVendedor, anuncioArrayList)
                    binding.anunciosRv.adapter = adaptador

                    val contadorAnuncios = "${anuncioArrayList.size}"
                    binding.TvNumAnuncios.text = contadorAnuncios
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("DetalleVendedor", "No se pudieron cargar los anuncios", error.toException())
                }
            }
        anunciosQuery = anunciosRef!!.orderByChild("uid").equalTo(uidVendedor).also {
            it.addValueEventListener(anunciosListener as ValueEventListener)
        }

    }

    private fun cargarInfoVendedor() {
        vendedorRef = FirebaseDatabase.getInstance().getReference("Usuarios").child(uidVendedor)
        vendedorListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val nombres = snapshot.child("nombres").getValue(String::class.java).orEmpty()
                    val imagen = snapshot.child("urlImagenPerfil").getValue(String::class.java).orEmpty()
                    val tiempo_r = when (val value = snapshot.child("tiempo").value) {
                        is Long -> value
                        is Int -> value.toLong()
                        is Double -> value.toLong()
                        is String -> value.toLongOrNull() ?: 0L
                        else -> 0L
                    }

                    val f_fecha = Constantes.obtenerFecha(tiempo_r)

                    binding.TvNombres.text = nombres.ifBlank { "Vendedor local" }
                    binding.TvMiembro.text = f_fecha

                    if (!isFinishing && !isDestroyed && imagen.isNotBlank()) {
                        Glide.with(applicationContext)
                            .load(imagen)
                            .placeholder(R.drawable.img_perfil)
                            .error(R.drawable.img_perfil)
                            .into(binding.IvVendedor)
                    } else if (imagen.isBlank()) {
                        binding.IvVendedor.setImageResource(R.drawable.img_perfil)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("DetalleVendedor", "No se pudo cargar el vendedor", error.toException())
                }
            }
        vendedorRef!!.addValueEventListener(vendedorListener as ValueEventListener)
    }

    @Suppress("DEPRECATION")
    private fun configurarInsets() {
        val toolbarBaseHeight = binding.ToolbarRl.layoutParams.height
        val toolbarBaseTop = binding.ToolbarRl.paddingTop
        val rvBasePaddingBottom = binding.anunciosRv.paddingBottom

        window.statusBarColor = ContextCompat.getColor(this, R.color.mi_color)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.md_theme_surface)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarParams = binding.ToolbarRl.layoutParams
            toolbarParams.height = toolbarBaseHeight + bars.top
            binding.ToolbarRl.layoutParams = toolbarParams
            binding.ToolbarRl.setPadding(
                binding.ToolbarRl.paddingLeft,
                toolbarBaseTop + bars.top,
                binding.ToolbarRl.paddingRight,
                binding.ToolbarRl.paddingBottom
            )
            binding.anunciosRv.setPadding(
                binding.anunciosRv.paddingLeft,
                binding.anunciosRv.paddingTop,
                binding.anunciosRv.paddingRight,
                rvBasePaddingBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onDestroy() {
        anunciosListener?.let { listener ->
            anunciosQuery?.removeEventListener(listener) ?: anunciosRef?.removeEventListener(listener)
        }
        vendedorListener?.let { vendedorRef?.removeEventListener(it) }
        anunciosListener = null
        vendedorListener = null
        anunciosQuery = null
        anunciosRef = null
        vendedorRef = null
        super.onDestroy()
    }
}
