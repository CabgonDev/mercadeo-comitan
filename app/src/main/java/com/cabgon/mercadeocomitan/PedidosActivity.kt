package com.cabgon.mercadeocomitan

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorPedido
import com.cabgon.mercadeocomitan.Modelo.ModeloOrden
import com.cabgon.mercadeocomitan.Pedidos.FiltroPedidos
import com.cabgon.mercadeocomitan.Pedidos.OrdenRepository
import com.cabgon.mercadeocomitan.Pedidos.PresentacionOrden
import com.cabgon.mercadeocomitan.databinding.ActivityPedidosBinding
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth

class PedidosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPedidosBinding
    private val repository = OrdenRepository()
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var adaptadorPedido: AdaptadorPedido

    private var todasLasOrdenes: List<ModeloOrden> = emptyList()
    private var filtroActual: FiltroPedidos = FiltroPedidos.TODOS
    private var haCargadoAlMenosUnaVez = false
    private var consultaToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityPedidosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Mis pedidos y ventas"

        configurarInsets()
        configurarFiltros()
        configurarLista()

        binding.IbActualizarPedidos.setOnClickListener {
            cargarPedidos(mostrarIndicadorSiVacio = true)
        }
        binding.btnReintentarPedidos.setOnClickListener {
            cargarPedidos(mostrarIndicadorSiVacio = true)
        }
    }

    override fun onResume() {
        super.onResume()
        cargarPedidos(mostrarIndicadorSiVacio = !haCargadoAlMenosUnaVez)
    }

    private fun configurarLista() {
        adaptadorPedido = AdaptadorPedido(
            uidActualProvider = { auth.uid.orEmpty() },
            onPedidoClick = { orden ->
                if (orden.id.isNotBlank()) {
                    startActivity(
                        Intent(this, PedidoActivity::class.java).apply {
                            putExtra("ordenId", orden.id)
                        }
                    )
                }
            },
        )
        binding.RvPedidos.adapter = adaptadorPedido
    }

    private fun configurarFiltros() {
        val tabs = binding.tabFiltroPedidos
        tabs.addTab(tabs.newTab().setText("Todos").setTag(FiltroPedidos.TODOS))
        tabs.addTab(tabs.newTab().setText("Compras").setTag(FiltroPedidos.COMPRAS))
        tabs.addTab(tabs.newTab().setText("Ventas").setTag(FiltroPedidos.VENTAS))
        tabs.addTab(tabs.newTab().setText("En curso").setTag(FiltroPedidos.EN_CURSO))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val nuevoFiltro = tab?.tag as? FiltroPedidos ?: FiltroPedidos.TODOS
                if (filtroActual != nuevoFiltro) {
                    filtroActual = nuevoFiltro
                    aplicarFiltroYRenderizar()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun cargarPedidos(mostrarIndicadorSiVacio: Boolean) {
        val uid = auth.uid
        if (uid.isNullOrBlank()) {
            todasLasOrdenes = emptyList()
            adaptadorPedido.submitList(emptyList())
            actualizarResumen(emptyList(), "")
            mostrarVacio(
                titulo = "Inicia sesión para ver tus pedidos",
                descripcion = "Accede a tu cuenta para consultar el historial de tus compras y ventas.",
            )
            return
        }

        if (mostrarIndicadorSiVacio && todasLasOrdenes.isEmpty()) {
            mostrarCarga()
        }
        binding.IbActualizarPedidos.isEnabled = false
        binding.IbActualizarPedidos.alpha = 0.6f

        val tokenActual = ++consultaToken
        repository.listarOrdenes(uid) { resultado ->
            runOnUiThread {
                if (isFinishing || isDestroyed || tokenActual != consultaToken) return@runOnUiThread
                binding.IbActualizarPedidos.isEnabled = true
                binding.IbActualizarPedidos.alpha = 1f
                haCargadoAlMenosUnaVez = true

                resultado.fold(
                    onSuccess = { pedidos ->
                        todasLasOrdenes = pedidos
                        actualizarResumen(pedidos, uid)
                        aplicarFiltroYRenderizar()
                    },
                    onFailure = { error ->
                        if (todasLasOrdenes.isEmpty()) {
                            mostrarError(
                                "No se pudieron cargar tus pedidos: ${error.message ?: "error de conexión"}"
                            )
                        }
                    },
                )
            }
        }
    }

    private fun actualizarResumen(pedidos: List<ModeloOrden>, uid: String) {
        val total = pedidos.size
        binding.tvConteoPedidos.text = when (total) {
            0 -> "Sin pedidos"
            1 -> "1 operación"
            else -> "$total operaciones"
        }

        if (pedidos.isEmpty() || uid.isBlank()) {
            binding.tvSubtituloResumenPedidos.text = "Gestiona tus solicitudes, entregas y transferencias"
            return
        }

        val compras = pedidos.count { it.compradorId == uid }
        val ventas = pedidos.count { it.vendedorId == uid }
        val enCurso = pedidos.count { PresentacionOrden.esOrdenActiva(it.estado) }
        binding.tvSubtituloResumenPedidos.text =
            "$compras compras · $ventas ventas · $enCurso en curso"
    }

    private fun aplicarFiltroYRenderizar() {
        val uid = auth.uid.orEmpty()
        val filtradas = PresentacionOrden.filtrarOrdenes(todasLasOrdenes, uid, filtroActual)
        adaptadorPedido.submitList(filtradas)

        if (filtradas.isEmpty()) {
            val (titulo, descripcion) = when {
                todasLasOrdenes.isEmpty() ->
                    "Aún no tienes pedidos ni ventas" to
                        "Cuando envíes una solicitud de compra o recibas un pedido en tus anuncios, aparecerá aquí."
                filtroActual == FiltroPedidos.COMPRAS ->
                    "No tienes compras registradas" to
                        "Explora los anuncios en Comitán y envía una solicitud cuando te interese un producto."
                filtroActual == FiltroPedidos.VENTAS ->
                    "No tienes ventas registradas" to
                        "Aquí verás las solicitudes de compra que otros usuarios envíen a tus anuncios."
                filtroActual == FiltroPedidos.EN_CURSO ->
                    "No tienes pedidos en curso" to
                        "Todas tus operaciones están completadas o finalizadas."
                else ->
                    "Aún no tienes pedidos ni ventas" to
                        "Tus operaciones aparecerán en esta sección."
            }
            mostrarVacio(titulo, descripcion)
        } else {
            mostrarLista()
        }
    }

    private fun mostrarCarga() {
        binding.progressPedidos.visibility = View.VISIBLE
        binding.RvPedidos.visibility = View.GONE
        binding.estadoVacioPedidos.visibility = View.GONE
        binding.estadoErrorPedidos.visibility = View.GONE
    }

    private fun mostrarLista() {
        binding.progressPedidos.visibility = View.GONE
        binding.RvPedidos.visibility = View.VISIBLE
        binding.estadoVacioPedidos.visibility = View.GONE
        binding.estadoErrorPedidos.visibility = View.GONE
    }

    private fun mostrarVacio(titulo: String, descripcion: String) {
        binding.progressPedidos.visibility = View.GONE
        binding.RvPedidos.visibility = View.GONE
        binding.estadoErrorPedidos.visibility = View.GONE
        binding.tvVacioPedidosTitulo.text = titulo
        binding.tvVacioPedidosDescripcion.text = descripcion
        binding.estadoVacioPedidos.visibility = View.VISIBLE
    }

    private fun mostrarError(mensaje: String) {
        binding.progressPedidos.visibility = View.GONE
        binding.RvPedidos.visibility = View.GONE
        binding.estadoVacioPedidos.visibility = View.GONE
        binding.tvErrorPedidosDescripcion.text = mensaje
        binding.estadoErrorPedidos.visibility = View.VISIBLE
    }

    @Suppress("DEPRECATION")
    private fun configurarInsets() {
        val toolbarBaseHeight = binding.ToolbarRl.layoutParams.height
        val toolbarBasePaddingTop = binding.ToolbarRl.paddingTop
        val rvBasePaddingBottom = binding.RvPedidos.paddingBottom
        val emptyBasePaddingBottom = binding.estadoVacioPedidos.paddingBottom
        val errorBasePaddingBottom = binding.estadoErrorPedidos.paddingBottom

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
            binding.RvPedidos.setPadding(
                binding.RvPedidos.paddingLeft,
                binding.RvPedidos.paddingTop,
                binding.RvPedidos.paddingRight,
                rvBasePaddingBottom + bars.bottom,
            )
            binding.estadoVacioPedidos.setPadding(
                binding.estadoVacioPedidos.paddingLeft,
                binding.estadoVacioPedidos.paddingTop,
                binding.estadoVacioPedidos.paddingRight,
                emptyBasePaddingBottom + bars.bottom,
            )
            binding.estadoErrorPedidos.setPadding(
                binding.estadoErrorPedidos.paddingLeft,
                binding.estadoErrorPedidos.paddingTop,
                binding.estadoErrorPedidos.paddingRight,
                errorBasePaddingBottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }
}
