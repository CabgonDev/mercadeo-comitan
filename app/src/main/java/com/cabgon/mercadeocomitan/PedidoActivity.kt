package com.cabgon.mercadeocomitan

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.cabgon.mercadeocomitan.Chat.ChatActivity
import com.cabgon.mercadeocomitan.DetalleAnuncio.DetalleAnuncio
import com.cabgon.mercadeocomitan.Modelo.EstadoTransferencia
import com.cabgon.mercadeocomitan.Modelo.EstadosOrden
import com.cabgon.mercadeocomitan.Modelo.MetodoPago
import com.cabgon.mercadeocomitan.Modelo.ModeloOrden
import com.cabgon.mercadeocomitan.Pedidos.CalificacionRepository
import com.cabgon.mercadeocomitan.Pedidos.OrdenRepository
import com.cabgon.mercadeocomitan.Pedidos.PresentacionOrden
import com.cabgon.mercadeocomitan.Pedidos.TonoEstadoOrden
import com.cabgon.mercadeocomitan.databinding.ActivityPedidoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class PedidoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPedidoBinding
    private lateinit var auth: FirebaseAuth
    private val ordenRepository = OrdenRepository()
    private val calificacionRepository = CalificacionRepository()
    private var ordenListener: ValueEventListener? = null
    private var ordenObservadaId = ""
    private var ordenActual: ModeloOrden? = null
    private var ordenId = ""
    private var idAnuncio = ""
    private var uidVendedor = ""
    private var titulo = ""
    private var precio = ""
    private var clabeVendedor = ""
    private var bancoVendedor = ""
    private var titularVendedor = ""
    private var transferenciaHabilitada = false
    private var verificandoCalificacion = false
    private var creandoOrden = false
    private var procesandoAccion = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityPedidoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarInsets()

        auth = FirebaseAuth.getInstance()
        idAnuncio = intent.getStringExtra("idAnuncio").orEmpty()
        uidVendedor = intent.getStringExtra("uidVendedor").orEmpty()
        titulo = intent.getStringExtra("titulo").orEmpty()
        precio = intent.getStringExtra("precio").orEmpty()
        ordenId = intent.getStringExtra("ordenId").orEmpty()

        binding.tvPedidoTitulo.text = titulo.ifBlank { "Pedido" }
        binding.tvPedidoPrecio.text = precio.ifBlank { "$0 MXN" }
        binding.rbContraEntrega.isChecked = true
        binding.rgMetodoPago.setOnCheckedChangeListener { _, checkedId ->
            binding.cardDatosTransferencia.visibility =
                if (checkedId == binding.rbTransferencia.id) View.VISIBLE else View.GONE
        }
        binding.btnCopiarClabe.setOnClickListener { copiarClabe() }
        binding.btnEnviarSolicitud.setOnClickListener { enviarSolicitud() }

        if (ordenId.isBlank()) {
            if (auth.uid.isNullOrBlank() ||
                !Constantes.esClaveFirebaseValida(idAnuncio) ||
                !Constantes.esClaveFirebaseValida(uidVendedor)
            ) {
                Toast.makeText(this, "No se puede iniciar el pedido", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            prepararNuevaOrden()
        } else {
            if (!Constantes.esClaveFirebaseValida(ordenId)) {
                Toast.makeText(this, "No se pudo identificar el pedido", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            prepararOrdenExistente()
        }
    }

    private fun prepararNuevaOrden() {
        binding.tvPedidoEncabezado.text = "Solicitar pedido"
        binding.tvPedidoRolChip.text = "Nueva solicitud"
        binding.tvPedidoEstadoChip.text = "Por confirmar"
        binding.tvPedidoEstado.text = "Revisa el método de pago y envía la solicitud al vendedor."
        binding.llDetallesExtraOrden.visibility = View.GONE
        binding.llAtajosOrden.visibility = View.GONE
        cargarDatosVendedor(uidVendedor)
    }

    private fun prepararOrdenExistente() {
        binding.tvPedidoEncabezado.text = "Detalle del pedido"
        binding.btnEnviarSolicitud.visibility = View.GONE
        detenerObservadorActual()
        ordenObservadaId = ordenId
        ordenListener = ordenRepository.observarOrden(ordenId) { orden ->
            if (orden == null) {
                if (isFinishing || isDestroyed) return@observarOrden
                Toast.makeText(this, "No se pudo cargar el pedido", Toast.LENGTH_LONG).show()
                return@observarOrden
            }
            val uid = auth.uid
            if (uid.isNullOrBlank() || (uid != orden.compradorId && uid != orden.vendedorId)) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "No tienes acceso a este pedido", Toast.LENGTH_LONG).show()
                    finish()
                }
                return@observarOrden
            }
            if (isFinishing || isDestroyed) return@observarOrden
            procesandoAccion = false
            ordenActual = orden
            idAnuncio = orden.anuncioId
            uidVendedor = orden.vendedorId
            titulo = orden.tituloAnuncio
            clabeVendedor = orden.clabeDestino
            bancoVendedor = orden.bancoDestino
            titularVendedor = orden.titularDestino
            binding.tvPedidoTitulo.text = titulo.ifBlank { "Pedido" }
            binding.tvPedidoPrecio.text = formatearCentavos(orden.importeCentavos)
            renderOrden(orden)
            if (orden.metodoPago == MetodoPago.TRANSFERENCIA) actualizarDatosTransferenciaUI()
        }
    }

    private fun cargarDatosVendedor(uid: String) {
        FirebaseDatabase.getInstance().getReference("Usuarios").child(uid).get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                transferenciaHabilitada = snapshot.child("transferenciaHabilitada").getValue(Boolean::class.java) == true
                val disponible = transferenciaHabilitada
                binding.rbTransferencia.isEnabled = disponible
                binding.rbTransferencia.text = if (disponible) {
                    "Transferencia bancaria al vendedor"
                } else {
                    "Transferencia no disponible (el vendedor no la configuró)"
                }
                actualizarDatosTransferenciaUI()
            }
            .addOnFailureListener {
                if (isFinishing || isDestroyed) return@addOnFailureListener
                transferenciaHabilitada = false
                binding.rbTransferencia.isEnabled = false
                binding.rbTransferencia.text = "Transferencia no disponible"
            }
    }

    private fun actualizarDatosTransferenciaUI() {
        val datosDisponibles = clabeVendedor.isNotBlank() && bancoVendedor.isNotBlank()
        if (datosDisponibles) {
            binding.tvBancoTransferencia.text = "Banco: $bancoVendedor"
            if (titularVendedor.isNotBlank()) {
                binding.tvTitularTransferencia.visibility = View.VISIBLE
                binding.tvTitularTransferencia.text = "Titular: $titularVendedor"
            } else {
                binding.tvTitularTransferencia.visibility = View.GONE
            }
            binding.tvClabeTransferencia.text = "CLABE: $clabeVendedor"
            binding.btnCopiarClabe.visibility = View.VISIBLE
        } else {
            binding.tvBancoTransferencia.text = "El vendedor compartirá sus datos al aceptar la solicitud."
            binding.tvTitularTransferencia.visibility = View.GONE
            binding.tvClabeTransferencia.text = ""
            binding.btnCopiarClabe.visibility = View.GONE
        }
        if (ordenActual?.metodoPago == MetodoPago.TRANSFERENCIA || binding.rbTransferencia.isChecked) {
            binding.cardDatosTransferencia.visibility = View.VISIBLE
        }
    }

    private fun enviarSolicitud() {
        if (creandoOrden) return
        val compradorId = auth.uid ?: return
        val metodo = if (binding.rbTransferencia.isChecked) MetodoPago.TRANSFERENCIA else MetodoPago.CONTRA_ENTREGA
        if (metodo == MetodoPago.TRANSFERENCIA && !transferenciaHabilitada) {
            Toast.makeText(this, "El vendedor aún no tiene transferencia habilitada", Toast.LENGTH_LONG).show()
            return
        }
        val importeCentavos = convertirPrecioACentavos(precio)
        if (importeCentavos <= 0L) {
            Toast.makeText(this, "El precio del anuncio no es válido", Toast.LENGTH_LONG).show()
            return
        }
        creandoOrden = true
        binding.btnEnviarSolicitud.isEnabled = false
        binding.btnEnviarSolicitud.text = "Enviando solicitud…"
        ordenRepository.crearOrden(
            anuncioId = idAnuncio,
            compradorId = compradorId,
            vendedorId = uidVendedor,
            titulo = titulo,
            importeCentavos = importeCentavos,
            metodoPago = metodo,
            puntoEncuentro = binding.etPuntoEncuentro.text?.toString().orEmpty(),
        ) { resultado ->
            if (isFinishing || isDestroyed) return@crearOrden
            resultado.onSuccess { id ->
                ordenId = id
                Toast.makeText(this, "Solicitud enviada al vendedor", Toast.LENGTH_LONG).show()
                prepararOrdenExistente()
            }.onFailure { error ->
                creandoOrden = false
                binding.btnEnviarSolicitud.isEnabled = true
                binding.btnEnviarSolicitud.text = "Enviar solicitud"
                Toast.makeText(this, "No se creó el pedido: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderOrden(orden: ModeloOrden) {
        val usuario = auth.uid.orEmpty()
        val esVendedor = usuario == orden.vendedorId

        // Chips de rol y estado
        if (esVendedor) {
            binding.tvPedidoRolChip.text = "Tu venta"
            binding.tvPedidoRolChip.setBackgroundResource(R.drawable.bg_chip_teal)
            binding.tvPedidoRolChip.setTextColor(
                ContextCompat.getColor(this, R.color.md_theme_onPrimaryContainer)
            )
        } else {
            binding.tvPedidoRolChip.text = "Tu compra"
            binding.tvPedidoRolChip.setBackgroundResource(R.drawable.bg_chip_blue)
            binding.tvPedidoRolChip.setTextColor(
                ContextCompat.getColor(this, R.color.md_theme_onSecondaryContainer)
            )
        }

        binding.tvPedidoEstadoChip.text = PresentacionOrden.textoEstadoCorto(orden.estado)
        when (PresentacionOrden.tonoEstado(orden.estado)) {
            TonoEstadoOrden.PENDIENTE -> {
                binding.tvPedidoEstadoChip.setBackgroundResource(R.drawable.bg_chip_amber)
                binding.tvPedidoEstadoChip.setTextColor(
                    ContextCompat.getColor(this, R.color.md_theme_onTertiaryContainer)
                )
            }
            TonoEstadoOrden.EN_PROGRESO -> {
                binding.tvPedidoEstadoChip.setBackgroundResource(R.drawable.bg_chip_teal)
                binding.tvPedidoEstadoChip.setTextColor(
                    ContextCompat.getColor(this, R.color.md_theme_onPrimaryContainer)
                )
            }
            TonoEstadoOrden.COMPLETADO -> {
                binding.tvPedidoEstadoChip.setBackgroundResource(R.drawable.bg_chip_success)
                binding.tvPedidoEstadoChip.setTextColor(
                    ContextCompat.getColor(this, R.color.success_color)
                )
            }
            TonoEstadoOrden.ALERTA -> {
                binding.tvPedidoEstadoChip.setBackgroundResource(R.drawable.bg_chip_error)
                binding.tvPedidoEstadoChip.setTextColor(
                    ContextCompat.getColor(this, R.color.md_theme_onErrorContainer)
                )
            }
        }

        binding.tvPedidoEstado.text = "Estado: ${textoEstado(orden)}"

        // Resumen de método de pago y fecha programada
        binding.llDetallesExtraOrden.visibility = View.VISIBLE
        binding.tvPedidoMetodoResumen.text =
            "Método de pago: ${PresentacionOrden.textoMetodoPagoResumen(orden)}"
        if (orden.entregaProgramadaEn > 0L) {
            binding.tvPedidoFechaEntrega.visibility = View.VISIBLE
            binding.tvPedidoFechaEntrega.text =
                "Entrega programada: ${Constantes.obtenerFechaHora(orden.entregaProgramadaEn)}"
        } else {
            binding.tvPedidoFechaEntrega.visibility = View.GONE
        }

        // Atajos hacia el anuncio y el chat con la contraparte
        val contraparteUid = if (esVendedor) orden.compradorId else orden.vendedorId
        val puedeAbrirAnuncio = Constantes.esClaveFirebaseValida(orden.anuncioId)
        val puedeAbrirChat = Constantes.esClaveFirebaseValida(contraparteUid) && contraparteUid != usuario
        binding.llAtajosOrden.visibility = if (puedeAbrirAnuncio || puedeAbrirChat) View.VISIBLE else View.GONE
        binding.btnVerAnuncioPedido.visibility = if (puedeAbrirAnuncio) View.VISIBLE else View.GONE
        binding.btnVerAnuncioPedido.setOnClickListener {
            startActivity(
                Intent(this, DetalleAnuncio::class.java).apply {
                    putExtra("idAnuncio", orden.anuncioId)
                }
            )
        }
        binding.btnChatContraparte.visibility = if (puedeAbrirChat) View.VISIBLE else View.GONE
        binding.btnChatContraparte.text = if (esVendedor) "Chat con comprador" else "Chat con vendedor"
        binding.btnChatContraparte.setOnClickListener {
            startActivity(
                Intent(this, ChatActivity::class.java).apply {
                    putExtra("uidVendedor", contraparteUid)
                }
            )
        }

        // Punto de encuentro
        binding.tvAyudaPuntoEncuentro.text = if (orden.puntoEncuentro.isNotBlank()) {
            "Lugar acordado en la solicitud."
        } else {
            "No se especificó un punto de encuentro al crear la solicitud."
        }
        binding.etPuntoEncuentro.setText(orden.puntoEncuentro.ifBlank { "Por acordar en el chat" })
        binding.etPuntoEncuentro.isEnabled = false

        // Datos de transferencia (solo para órdenes por transferencia)
        binding.tvTituloMetodoPago.text = "Datos para transferencia"
        binding.cardMetodoPago.visibility = if (orden.metodoPago == MetodoPago.TRANSFERENCIA) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (orden.metodoPago == MetodoPago.TRANSFERENCIA) {
            binding.rbContraEntrega.visibility = View.GONE
            binding.rbTransferencia.isChecked = true
            binding.rbTransferencia.isEnabled = false
        }

        // Guía y botones de acción
        binding.cardAccionesOrden.visibility = View.VISIBLE
        binding.tvGuiaAccionOrden.text = PresentacionOrden.textoSugerenciaAccion(orden, usuario)
        ocultarAcciones()

        when (orden.estado) {
            EstadosOrden.SOLICITUD_ENVIADA -> if (esVendedor) {
                mostrarAccion1("Aceptar solicitud") { transicionar(EstadosOrden.RESERVADA) }
                mostrarAccion2("Rechazar solicitud") { transicionar(EstadosOrden.RECHAZADA) }
            } else {
                mostrarAccion1("Cancelar solicitud") { transicionar(EstadosOrden.CANCELADA) }
            }
            EstadosOrden.RESERVADA -> if (orden.metodoPago == MetodoPago.TRANSFERENCIA) {
                if (esVendedor) {
                    mostrarAccion1("Enviar instrucciones de transferencia") { transicionar(EstadosOrden.ESPERANDO_PAGO) }
                    mostrarAccion2("Cancelar pedido") { transicionar(EstadosOrden.CANCELADA) }
                } else {
                    mostrarAccion1("Cancelar pedido") { transicionar(EstadosOrden.CANCELADA) }
                }
            } else {
                mostrarAccion1("Agendar entrega") { seleccionarFechaEntrega() }
                mostrarAccion2("Cancelar pedido") { transicionar(EstadosOrden.CANCELADA) }
            }
            EstadosOrden.ESPERANDO_PAGO -> if (esVendedor) {
                mostrarAccion1("Marcar transferencia recibida") { confirmarTransferencia() }
                mostrarAccion2("Cancelar pedido") { transicionar(EstadosOrden.CANCELADA) }
            }
            EstadosOrden.PAGO_RECIBIDO -> {
                mostrarAccion1("Agendar entrega") { seleccionarFechaEntrega() }
            }
            EstadosOrden.ENTREGA_PROGRAMADA -> if (esVendedor) {
                mostrarAccion1("Marcar entregado") { transicionar(EstadosOrden.ENTREGADA_POR_VENDEDOR) }
            }
            EstadosOrden.ENTREGADA_POR_VENDEDOR -> if (!esVendedor) {
                mostrarAccion1("Confirmar recepción y finalizar") { transicionar(EstadosOrden.COMPLETADA) }
                mostrarAccion2("Abrir disputa") { transicionar(EstadosOrden.DISPUTA_ABIERTA) }
            }
            EstadosOrden.RECIBIDA_POR_COMPRADOR -> if (!esVendedor) {
                mostrarAccion1("Finalizar operación") { transicionar(EstadosOrden.COMPLETADA) }
            }
            EstadosOrden.COMPLETADA -> {
                val contraparte = if (usuario == orden.compradorId) orden.vendedorId else orden.compradorId
                if ((usuario == orden.compradorId || usuario == orden.vendedorId) && !verificandoCalificacion) {
                    comprobarCalificacion(orden, usuario, contraparte)
                }
            }
            else -> Unit
        }
    }

    private fun transicionar(nuevoEstado: EstadosOrden) {
        if (procesandoAccion) return
        establecerEstadoBotonesAccion(habilitados = false)
        ordenRepository.transicionar(ordenId, auth.uid.orEmpty(), nuevoEstado) { resultado ->
            if (isFinishing || isDestroyed) return@transicionar
            establecerEstadoBotonesAccion(habilitados = true)
            resultado.onFailure {
                Toast.makeText(this, it.message ?: "Transición no permitida", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun seleccionarFechaEntrega() {
        if (procesandoAccion) return
        val inicial = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                if (isFinishing || isDestroyed) return@DatePickerDialog
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        if (isFinishing || isDestroyed) return@TimePickerDialog
                        val fecha = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        if (fecha <= System.currentTimeMillis()) {
                            Toast.makeText(this, "Elige una fecha y hora futura", Toast.LENGTH_LONG).show()
                            return@TimePickerDialog
                        }
                        establecerEstadoBotonesAccion(habilitados = false)
                        ordenRepository.transicionar(
                            ordenId,
                            auth.uid.orEmpty(),
                            EstadosOrden.ENTREGA_PROGRAMADA,
                            fechaEntrega = fecha,
                        ) { resultado ->
                            if (isFinishing || isDestroyed) return@transicionar
                            establecerEstadoBotonesAccion(habilitados = true)
                            resultado.onFailure {
                                Toast.makeText(this, it.message ?: "No se pudo agendar la entrega", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    inicial.get(Calendar.HOUR_OF_DAY),
                    inicial.get(Calendar.MINUTE),
                    false,
                ).show()
            },
            inicial.get(Calendar.YEAR),
            inicial.get(Calendar.MONTH),
            inicial.get(Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    private fun comprobarCalificacion(orden: ModeloOrden, usuario: String, contraparte: String) {
        if (verificandoCalificacion) return
        verificandoCalificacion = true
        binding.btnCalificar.visibility = View.VISIBLE
        binding.btnCalificar.isEnabled = false
        binding.btnCalificar.text = "Consultando…"
        calificacionRepository.yaCalifico(orden.id, usuario, contraparte) { resultado ->
            if (ordenActual?.id != orden.id || isFinishing || isDestroyed) return@yaCalifico
            verificandoCalificacion = false
            resultado.fold(
                onSuccess = { yaCalifico ->
                    if (yaCalifico) {
                        binding.btnCalificar.visibility = View.GONE
                    } else {
                        binding.btnCalificar.visibility = View.VISIBLE
                        binding.btnCalificar.isEnabled = true
                        binding.btnCalificar.text = "Calificar la operación"
                        binding.btnCalificar.setOnClickListener { mostrarDialogoCalificacion(orden) }
                    }
                },
                onFailure = { error ->
                    binding.btnCalificar.visibility = View.VISIBLE
                    binding.btnCalificar.isEnabled = true
                    binding.btnCalificar.text = "Reintentar consulta"
                    binding.btnCalificar.setOnClickListener { comprobarCalificacion(orden, usuario, contraparte) }
                    Toast.makeText(this, "No se pudo consultar tu calificación: ${error.message}", Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    private fun confirmarTransferencia() {
        if (procesandoAccion) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar transferencia recibida")
            .setMessage("Revisa tu banca y confirma solo cuando veas el depósito acreditado.")
            .setPositiveButton("Confirmar") { _, _ ->
                establecerEstadoBotonesAccion(habilitados = false)
                ordenRepository.confirmarTransferencia(ordenId, auth.uid.orEmpty()) { resultado ->
                    if (isFinishing || isDestroyed) return@confirmarTransferencia
                    establecerEstadoBotonesAccion(habilitados = true)
                    resultado.onFailure {
                        Toast.makeText(this, it.message ?: "No se actualizó la transferencia", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Volver", null)
            .show()
    }

    private fun establecerEstadoBotonesAccion(habilitados: Boolean) {
        procesandoAccion = !habilitados
        binding.btnAccionOrden1.isEnabled = habilitados
        binding.btnAccionOrden2.isEnabled = habilitados
    }

    private fun mostrarAccion1(texto: String, accion: () -> Unit) {
        binding.btnAccionOrden1.visibility = View.VISIBLE
        binding.btnAccionOrden1.isEnabled = !procesandoAccion
        binding.btnAccionOrden1.text = texto
        binding.btnAccionOrden1.setOnClickListener { accion() }
    }

    private fun mostrarAccion2(texto: String, accion: () -> Unit) {
        binding.btnAccionOrden2.visibility = View.VISIBLE
        binding.btnAccionOrden2.isEnabled = !procesandoAccion
        binding.btnAccionOrden2.text = texto
        binding.btnAccionOrden2.setOnClickListener { accion() }
    }

    private fun ocultarAcciones() {
        binding.btnAccionOrden1.visibility = View.GONE
        binding.btnAccionOrden2.visibility = View.GONE
        binding.btnCalificar.visibility = View.GONE
    }

    private fun mostrarDialogoCalificacion(orden: ModeloOrden) {
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }
        val ratingBar = RatingBar(this).apply {
            numStars = 5
            stepSize = 1f
            rating = 5f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        val comentario = EditText(this).apply {
            hint = "Comentario opcional"
            maxLines = 4
        }
        contenedor.addView(ratingBar)
        contenedor.addView(comentario)
        MaterialAlertDialogBuilder(this)
            .setTitle("Califica la operación")
            .setView(contenedor)
            .setPositiveButton("Publicar") { _, _ ->
                val autor = auth.uid.orEmpty()
                val destino = if (autor == orden.compradorId) orden.vendedorId else orden.compradorId
                binding.btnCalificar.isEnabled = false
                calificacionRepository.guardar(
                    orden.id,
                    orden.anuncioId,
                    autor,
                    destino,
                    ratingBar.rating.toInt(),
                    comentario.text.toString(),
                ) { resultado ->
                    if (isFinishing || isDestroyed) return@guardar
                    resultado.onSuccess {
                        binding.btnCalificar.visibility = View.GONE
                        Toast.makeText(this, "Calificación publicada", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        binding.btnCalificar.isEnabled = true
                        Toast.makeText(this, it.message ?: "No se publicó la calificación", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun copiarClabe() {
        if (clabeVendedor.isBlank()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CLABE", clabeVendedor))
        Toast.makeText(this, "CLABE copiada", Toast.LENGTH_SHORT).show()
    }

    private fun textoEstado(orden: ModeloOrden): String =
        PresentacionOrden.textoEstadoDetallado(orden)

    private fun convertirPrecioACentavos(valor: String): Long =
        PresentacionOrden.convertirPrecioACentavos(valor)

    private fun formatearCentavos(centavos: Long): String =
        NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("MX").build()).format(centavos / 100.0)

    private fun detenerObservadorActual() {
        if (ordenObservadaId.isNotBlank()) {
            ordenListener?.let { ordenRepository.quitarObservador(ordenObservadaId, it) }
        }
        ordenListener = null
        ordenObservadaId = ""
    }

    @Suppress("DEPRECATION")
    private fun configurarInsets() {
        val toolbarBaseHeight = binding.ToolbarPedidoRl.layoutParams.height
        val toolbarBasePaddingTop = binding.ToolbarPedidoRl.paddingTop
        val scrollBasePaddingBottom = binding.scrollPedido.paddingBottom

        window.statusBarColor = ContextCompat.getColor(this, R.color.mi_color)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.md_theme_background)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarParams = binding.ToolbarPedidoRl.layoutParams
            toolbarParams.height = toolbarBaseHeight + bars.top
            binding.ToolbarPedidoRl.layoutParams = toolbarParams
            binding.ToolbarPedidoRl.setPadding(
                binding.ToolbarPedidoRl.paddingLeft,
                toolbarBasePaddingTop + bars.top,
                binding.ToolbarPedidoRl.paddingRight,
                binding.ToolbarPedidoRl.paddingBottom,
            )
            binding.scrollPedido.setPadding(
                binding.scrollPedido.paddingLeft,
                binding.scrollPedido.paddingTop,
                binding.scrollPedido.paddingRight,
                scrollBasePaddingBottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onDestroy() {
        detenerObservadorActual()
        super.onDestroy()
    }
}
