@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Pedidos

import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.EstadoTransferencia
import com.cabgon.mercadeocomitan.Modelo.EstadosOrden
import com.cabgon.mercadeocomitan.Modelo.FlujoOrden
import com.cabgon.mercadeocomitan.Modelo.FlujoTransferencia
import com.cabgon.mercadeocomitan.Modelo.MetodoPago
import com.cabgon.mercadeocomitan.Modelo.ModeloOrden
import com.cabgon.mercadeocomitan.Modelo.ModeloTransferencia
import com.cabgon.mercadeocomitan.Modelo.RolOrden
import com.cabgon.mercadeocomitan.Modelo.esClabeValida
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.auth.FirebaseAuth

/**
 * Acceso único a las órdenes locales. La transferencia es manual: Firebase
 * solo guarda la solicitud y el estado que el vendedor confirma en su banco.
 * Las reglas de Firebase deben impedir que un cliente altere órdenes ajenas.
 */
class OrdenRepository(
    private val ordenesRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("Ordenes"),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    fun crearOrden(
        anuncioId: String,
        compradorId: String,
        vendedorId: String,
        titulo: String,
        importeCentavos: Long,
        metodoPago: MetodoPago,
        puntoEncuentro: String = "",
        notas: String = "",
        onComplete: (Result<String>) -> Unit,
    ) {
        if (auth.currentUser?.uid != compradorId) {
            onComplete(Result.failure(SecurityException("La solicitud debe enviarse desde la cuenta compradora")))
            return
        }
        if (compradorId.isBlank() || vendedorId.isBlank() || compradorId == vendedorId ||
            !Constantes.esClaveFirebaseValida(anuncioId) ||
            !Constantes.esClaveFirebaseValida(compradorId) ||
            !Constantes.esClaveFirebaseValida(vendedorId)
        ) {
            onComplete(Result.failure(IllegalArgumentException("Comprador y vendedor no son válidos")))
            return
        }
        if (importeCentavos <= 0L) {
            onComplete(Result.failure(IllegalArgumentException("El importe debe ser mayor que cero")))
            return
        }

        val id = ordenesRef.push().key
        if (id.isNullOrBlank()) {
            onComplete(Result.failure(IllegalStateException("No se pudo generar la orden")))
            return
        }

        val ahora = System.currentTimeMillis()
        val transferencia = if (metodoPago == MetodoPago.TRANSFERENCIA) {
            mapOf(
                "estado" to EstadoTransferencia.SOLICITUD_PAGO_ENVIADA.name,
                "solicitudEnviadaEn" to ahora,
            )
        } else {
            mapOf("estado" to EstadoTransferencia.NO_APLICA.name)
        }
        val datos = hashMapOf<String, Any>(
            "id" to id,
            "anuncioId" to anuncioId,
            "compradorId" to compradorId,
            "vendedorId" to vendedorId,
            "tituloAnuncio" to titulo,
            "importeCentavos" to importeCentavos,
            "moneda" to "MXN",
            "metodoPago" to metodoPago.name,
            "estado" to EstadosOrden.SOLICITUD_ENVIADA.name,
            "estadoTransferencia" to if (metodoPago == MetodoPago.TRANSFERENCIA) {
                EstadoTransferencia.SOLICITUD_PAGO_ENVIADA.name
            } else {
                EstadoTransferencia.NO_APLICA.name
            },
            "creadaEn" to ahora,
            "actualizadaEn" to ahora,
            "puntoEncuentro" to puntoEncuentro.trim(),
            "notas" to notas.trim(),
            "transferencia" to transferencia,
            "historial" to mapOf(
                ahora.toString() to mapOf(
                    "estado" to EstadosOrden.SOLICITUD_ENVIADA.name,
                    "actorId" to compradorId,
                    "creadoEn" to ahora,
                    "nota" to "Solicitud creada por el comprador",
                ),
            ),
        )

        ordenesRef.child(id).setValue(datos)
            .addOnSuccessListener { onComplete(Result.success(id)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun observarOrden(id: String, onChange: (ModeloOrden?) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onChange(snapshot.takeIf { it.exists() }?.let(::mapOrden))
            }

            override fun onCancelled(error: DatabaseError) {
                onChange(null)
            }
        }
        if (Constantes.esClaveFirebaseValida(id)) {
            ordenesRef.child(id).addValueEventListener(listener)
        } else {
            onChange(null)
        }
        return listener
    }

    fun quitarObservador(id: String, listener: ValueEventListener) {
        if (!Constantes.esClaveFirebaseValida(id)) return
        ordenesRef.child(id).removeEventListener(listener)
    }

    fun listarOrdenes(uid: String, onComplete: (Result<List<ModeloOrden>>) -> Unit) {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            onComplete(Result.failure(SecurityException("Inicia sesión para consultar tus pedidos")))
            return
        }

        val lock = Any()
        val resultado = linkedMapOf<String, ModeloOrden>()
        var errorConsulta: DatabaseError? = null
        var consultasPendientes = 2
        fun terminar(snapshot: DataSnapshot?, error: DatabaseError? = null) {
            var resultadoFinal: Result<List<ModeloOrden>>? = null
            synchronized(lock) {
                if (errorConsulta == null && error != null) errorConsulta = error
                snapshot?.children?.forEach { child ->
                    val orden = mapOrden(child)
                    resultado[orden.id] = orden
                }
                consultasPendientes -= 1
                if (consultasPendientes == 0) {
                    resultadoFinal = errorConsulta?.let { Result.failure(it.toException()) }
                        ?: Result.success(resultado.values.sortedByDescending { it.actualizadaEn })
                }
            }
            resultadoFinal?.let(onComplete)
        }
        fun consultar(query: Query) {
            query.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) = terminar(snapshot)
                override fun onCancelled(error: DatabaseError) = terminar(null, error)
            })
        }

        consultar(ordenesRef.orderByChild("compradorId").equalTo(uid))
        consultar(ordenesRef.orderByChild("vendedorId").equalTo(uid))
    }

    fun observarOrdenesUsuario(
        uid: String,
        onUpdate: (Result<List<ModeloOrden>>) -> Unit,
    ): () -> Unit {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            onUpdate(Result.failure(SecurityException("Inicia sesión para consultar tus pedidos")))
            return {}
        }

        val lock = Any()
        var compras = emptyMap<String, ModeloOrden>()
        var ventas = emptyMap<String, ModeloOrden>()
        var comprasCargadas = false
        var ventasCargadas = false

        fun emitir() {
            val listaCombinada: List<ModeloOrden>? = synchronized(lock) {
                if (!comprasCargadas || !ventasCargadas) {
                    null
                } else {
                    val combinadas = LinkedHashMap<String, ModeloOrden>(compras.size + ventas.size)
                    combinadas.putAll(compras)
                    combinadas.putAll(ventas)
                    combinadas.values.sortedByDescending {
                        it.actualizadaEn.takeIf { ts -> ts > 0L } ?: it.creadaEn
                    }
                }
            }
            listaCombinada?.let { onUpdate(Result.success(it)) }
        }

        val queryCompras = ordenesRef.orderByChild("compradorId").equalTo(uid)
        val queryVentas = ordenesRef.orderByChild("vendedorId").equalTo(uid)

        val listenerCompras = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nuevas = LinkedHashMap<String, ModeloOrden>()
                snapshot.children.forEach { child ->
                    val orden = mapOrden(child)
                    if (orden.id.isNotBlank()) nuevas[orden.id] = orden
                }
                synchronized(lock) {
                    compras = nuevas
                    comprasCargadas = true
                }
                emitir()
            }

            override fun onCancelled(error: DatabaseError) {
                onUpdate(Result.failure(error.toException()))
            }
        }

        val listenerVentas = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nuevas = LinkedHashMap<String, ModeloOrden>()
                snapshot.children.forEach { child ->
                    val orden = mapOrden(child)
                    if (orden.id.isNotBlank()) nuevas[orden.id] = orden
                }
                synchronized(lock) {
                    ventas = nuevas
                    ventasCargadas = true
                }
                emitir()
            }

            override fun onCancelled(error: DatabaseError) {
                onUpdate(Result.failure(error.toException()))
            }
        }

        queryCompras.addValueEventListener(listenerCompras)
        queryVentas.addValueEventListener(listenerVentas)

        return {
            queryCompras.removeEventListener(listenerCompras)
            queryVentas.removeEventListener(listenerVentas)
        }
    }

    fun transicionar(
        id: String,
        actorId: String,
        nuevoEstado: EstadosOrden,
        nota: String = "",
        fechaEntrega: Long? = null,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        if (!Constantes.esClaveFirebaseValida(id) || !Constantes.esClaveFirebaseValida(actorId)) {
            onComplete(Result.failure(IllegalArgumentException("Identificador de orden inválido")))
            return
        }
        if (auth.currentUser?.uid != actorId) {
            onComplete(Result.failure(SecurityException("La sesión no corresponde al participante")))
            return
        }
        if (nuevoEstado == EstadosOrden.ENTREGA_PROGRAMADA &&
            (fechaEntrega == null || fechaEntrega <= System.currentTimeMillis())
        ) {
            onComplete(Result.failure(IllegalArgumentException("Elige una fecha y hora futura para la entrega")))
            return
        }

        val ejecutar: (DataSnapshot?) -> Unit = { cuenta ->
            var motivoInvalido: String? = null
            val ahora = System.currentTimeMillis()
            ordenesRef.child(id).runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    motivoInvalido = null
                    if (currentData.value == null) {
                        motivoInvalido = "La orden ya no existe"
                        return Transaction.abort()
                    }
                    val orden = mapOrden(currentData)
                    val actor = rolDe(actorId, orden)
                    if (actor == null) {
                        motivoInvalido = "El usuario no pertenece a esta orden"
                        return Transaction.abort()
                    }
                    if (!FlujoOrden.puedeTransicionar(orden, nuevoEstado, actor)) {
                        motivoInvalido = "La transición no está permitida"
                        return Transaction.abort()
                    }

                    val clabe = cuenta?.child("clabeInterbancaria")?.getValue(String::class.java).orEmpty()
                    val banco = cuenta?.child("banco")?.getValue(String::class.java).orEmpty()
                    val titular = cuenta?.child("titular")?.getValue(String::class.java).orEmpty()
                    val habilitada = cuenta?.child("transferenciaHabilitada")?.getValue(Boolean::class.java) == true
                    if (nuevoEstado == EstadosOrden.ESPERANDO_PAGO &&
                        (actor != RolOrden.VENDEDOR || orden.metodoPago != MetodoPago.TRANSFERENCIA ||
                            !habilitada || !esClabeValida(clabe) || banco.isBlank())
                    ) {
                        motivoInvalido = "Configura una CLABE válida antes de enviar instrucciones"
                        return Transaction.abort()
                    }

                    currentData.child("estado").value = nuevoEstado.name
                    currentData.child("actualizadaEn").value = ahora
                    currentData.child("historial").child(ahora.toString()).value = mapOf(
                        "estado" to nuevoEstado.name,
                        "actorId" to actorId,
                        "creadoEn" to ahora,
                        "nota" to nota,
                    )
                    when (nuevoEstado) {
                        EstadosOrden.ESPERANDO_PAGO -> {
                            currentData.child("estadoTransferencia").value = EstadoTransferencia.ESPERANDO_TRANSFERENCIA.name
                            currentData.child("transferencia").child("estado").value = EstadoTransferencia.ESPERANDO_TRANSFERENCIA.name
                            currentData.child("transferencia").child("esperandoDesde").value = ahora
                            currentData.child("transferencia").child("clabeInterbancaria").value = clabe
                            currentData.child("transferencia").child("banco").value = banco
                            currentData.child("transferencia").child("titular").value = titular
                        }
                        EstadosOrden.ENTREGA_PROGRAMADA -> currentData.child("entregaProgramadaEn").value = fechaEntrega
                        EstadosOrden.ENTREGADA_POR_VENDEDOR -> currentData.child("confirmadaPorVendedorEn").value = ahora
                        EstadosOrden.RECIBIDA_POR_COMPRADOR -> currentData.child("confirmadaPorCompradorEn").value = ahora
                        EstadosOrden.COMPLETADA -> {
                            if (currentData.child("confirmadaPorCompradorEn").asLong() == 0L) {
                                currentData.child("confirmadaPorCompradorEn").value = ahora
                            }
                        }
                        EstadosOrden.CANCELADA -> currentData.child("motivoCancelacion").value = nota
                        else -> Unit
                    }
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    when {
                        error != null -> onComplete(Result.failure(error.toException()))
                        committed -> onComplete(Result.success(Unit))
                        else -> onComplete(Result.failure(IllegalStateException(motivoInvalido ?: "La transición no se guardó")))
                    }
                }
            })
        }

        if (nuevoEstado == EstadosOrden.ESPERANDO_PAGO) {
            FirebaseDatabase.getInstance().getReference("CuentasTransferencia")
                .child(actorId).get()
                .addOnSuccessListener(ejecutar)
                .addOnFailureListener { onComplete(Result.failure(it)) }
        } else {
            ejecutar(null)
        }
    }

    fun confirmarTransferencia(
        id: String,
        vendedorId: String,
        nota: String = "",
        onComplete: (Result<Unit>) -> Unit,
    ) {
        if (!Constantes.esClaveFirebaseValida(id) || !Constantes.esClaveFirebaseValida(vendedorId)) {
            onComplete(Result.failure(IllegalArgumentException("Identificador de orden inválido")))
            return
        }
        if (auth.currentUser?.uid != vendedorId) {
            onComplete(Result.failure(SecurityException("La sesión no corresponde al vendedor")))
            return
        }
        var motivoInvalido: String? = null
        val ahora = System.currentTimeMillis()
        ordenesRef.child(id).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                motivoInvalido = null
                if (currentData.value == null) {
                    motivoInvalido = "La orden ya no existe"
                    return Transaction.abort()
                }
                val orden = mapOrden(currentData)
                if (orden.vendedorId != vendedorId || orden.metodoPago != MetodoPago.TRANSFERENCIA) {
                    motivoInvalido = "Solo el vendedor puede confirmar esta transferencia"
                    return Transaction.abort()
                }
                val actual = ModeloTransferencia(
                    ordenId = orden.id,
                    compradorId = orden.compradorId,
                    vendedorId = orden.vendedorId,
                    estado = enumOrDefault(
                        currentData.child("transferencia").child("estado").getValue(String::class.java),
                        orden.estadoTransferencia,
                    ),
                )
                val ordenConTransferenciaRecibida = orden.copy(
                    estadoTransferencia = EstadoTransferencia.TRANSFERENCIA_RECIBIDA,
                )
                if (!FlujoTransferencia.puedeTransicionar(actual, EstadoTransferencia.TRANSFERENCIA_RECIBIDA, RolOrden.VENDEDOR) ||
                    !FlujoOrden.puedeTransicionar(
                        ordenConTransferenciaRecibida,
                        EstadosOrden.PAGO_RECIBIDO,
                        RolOrden.VENDEDOR,
                    )
                ) {
                    motivoInvalido = "La transferencia no está esperando confirmación"
                    return Transaction.abort()
                }
                currentData.child("estado").value = EstadosOrden.PAGO_RECIBIDO.name
                currentData.child("estadoTransferencia").value = EstadoTransferencia.TRANSFERENCIA_RECIBIDA.name
                currentData.child("actualizadaEn").value = ahora
                currentData.child("transferencia").child("estado").value = EstadoTransferencia.TRANSFERENCIA_RECIBIDA.name
                currentData.child("transferencia").child("recibidaEn").value = ahora
                currentData.child("transferencia").child("actualizadoEn").value = ahora
                currentData.child("historial").child(ahora.toString()).value = mapOf(
                    "estado" to EstadosOrden.PAGO_RECIBIDO.name,
                    "actorId" to vendedorId,
                    "creadoEn" to ahora,
                    "nota" to nota.ifBlank { "Transferencia confirmada por el vendedor" },
                )
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                when {
                    error != null -> onComplete(Result.failure(error.toException()))
                    committed -> onComplete(Result.success(Unit))
                    else -> onComplete(Result.failure(IllegalStateException(motivoInvalido ?: "La transferencia no se confirmó")))
                }
            }
        })
    }

    private fun rolDe(uid: String, orden: ModeloOrden): RolOrden? = when (uid) {
        orden.compradorId -> RolOrden.COMPRADOR
        orden.vendedorId -> RolOrden.VENDEDOR
        else -> null
    }

    private fun mapOrden(snapshot: DataSnapshot): ModeloOrden = ModeloOrden(
        id = snapshot.child("id").getValue(String::class.java) ?: snapshot.key.orEmpty(),
        anuncioId = snapshot.child("anuncioId").getValue(String::class.java).orEmpty(),
        compradorId = snapshot.child("compradorId").getValue(String::class.java).orEmpty(),
        vendedorId = snapshot.child("vendedorId").getValue(String::class.java).orEmpty(),
        tituloAnuncio = snapshot.child("tituloAnuncio").getValue(String::class.java).orEmpty(),
        importeCentavos = snapshot.child("importeCentavos").asLong(),
        moneda = snapshot.child("moneda").getValue(String::class.java) ?: "MXN",
        metodoPago = enumOrDefault(snapshot.child("metodoPago").getValue(String::class.java), MetodoPago.CONTRA_ENTREGA),
        estado = enumOrDefault(snapshot.child("estado").getValue(String::class.java), EstadosOrden.SOLICITUD_ENVIADA),
        estadoTransferencia = enumOrDefault(snapshot.child("estadoTransferencia").getValue(String::class.java), EstadoTransferencia.NO_APLICA),
        clabeDestino = snapshot.child("transferencia").child("clabeInterbancaria").getValue(String::class.java).orEmpty(),
        bancoDestino = snapshot.child("transferencia").child("banco").getValue(String::class.java).orEmpty(),
        titularDestino = snapshot.child("transferencia").child("titular").getValue(String::class.java).orEmpty(),
        creadaEn = snapshot.child("creadaEn").asLong(),
        actualizadaEn = snapshot.child("actualizadaEn").asLong(),
        entregaProgramadaEn = snapshot.child("entregaProgramadaEn").asLong(),
        puntoEncuentro = snapshot.child("puntoEncuentro").getValue(String::class.java).orEmpty(),
        notas = snapshot.child("notas").getValue(String::class.java).orEmpty(),
        confirmadaPorVendedorEn = snapshot.child("confirmadaPorVendedorEn").asLong(),
        confirmadaPorCompradorEn = snapshot.child("confirmadaPorCompradorEn").asLong(),
        motivoCancelacion = snapshot.child("motivoCancelacion").getValue(String::class.java).orEmpty(),
    )

    private fun mapOrden(data: MutableData): ModeloOrden = ModeloOrden(
        id = data.child("id").getValue(String::class.java) ?: data.key.orEmpty(),
        anuncioId = data.child("anuncioId").getValue(String::class.java).orEmpty(),
        compradorId = data.child("compradorId").getValue(String::class.java).orEmpty(),
        vendedorId = data.child("vendedorId").getValue(String::class.java).orEmpty(),
        tituloAnuncio = data.child("tituloAnuncio").getValue(String::class.java).orEmpty(),
        importeCentavos = data.child("importeCentavos").asLong(),
        moneda = data.child("moneda").getValue(String::class.java) ?: "MXN",
        metodoPago = enumOrDefault(data.child("metodoPago").getValue(String::class.java), MetodoPago.CONTRA_ENTREGA),
        estado = enumOrDefault(data.child("estado").getValue(String::class.java), EstadosOrden.SOLICITUD_ENVIADA),
        estadoTransferencia = enumOrDefault(
            data.child("estadoTransferencia").getValue(String::class.java),
            EstadoTransferencia.NO_APLICA,
        ),
        clabeDestino = data.child("transferencia").child("clabeInterbancaria").getValue(String::class.java).orEmpty(),
        bancoDestino = data.child("transferencia").child("banco").getValue(String::class.java).orEmpty(),
        titularDestino = data.child("transferencia").child("titular").getValue(String::class.java).orEmpty(),
        creadaEn = data.child("creadaEn").asLong(),
        actualizadaEn = data.child("actualizadaEn").asLong(),
        entregaProgramadaEn = data.child("entregaProgramadaEn").asLong(),
        puntoEncuentro = data.child("puntoEncuentro").getValue(String::class.java).orEmpty(),
        notas = data.child("notas").getValue(String::class.java).orEmpty(),
        confirmadaPorVendedorEn = data.child("confirmadaPorVendedorEn").asLong(),
        confirmadaPorCompradorEn = data.child("confirmadaPorCompradorEn").asLong(),
        motivoCancelacion = data.child("motivoCancelacion").getValue(String::class.java).orEmpty(),
    )

    private fun DataSnapshot.asLong(): Long = when (val value = value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    private fun MutableData.asLong(): Long = when (val value = value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
