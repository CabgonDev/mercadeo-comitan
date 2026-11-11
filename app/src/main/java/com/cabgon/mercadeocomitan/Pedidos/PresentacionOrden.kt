@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Pedidos

import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.EstadoTransferencia
import com.cabgon.mercadeocomitan.Modelo.EstadosOrden
import com.cabgon.mercadeocomitan.Modelo.MetodoPago
import com.cabgon.mercadeocomitan.Modelo.ModeloOrden
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

enum class FiltroPedidos {
    TODOS,
    COMPRAS,
    VENTAS,
    EN_CURSO,
}

enum class TonoEstadoOrden {
    PENDIENTE,
    EN_PROGRESO,
    COMPLETADO,
    ALERTA,
}

object PresentacionOrden {

    private val estadosFinalizados = setOf(
        EstadosOrden.COMPLETADA,
        EstadosOrden.RECHAZADA,
        EstadosOrden.CANCELADA,
        EstadosOrden.EXPIRADA,
    )

    fun esOrdenActiva(estado: EstadosOrden): Boolean = estado !in estadosFinalizados

    fun filtrarOrdenes(
        ordenes: List<ModeloOrden>,
        uid: String,
        filtro: FiltroPedidos,
    ): List<ModeloOrden> = when (filtro) {
        FiltroPedidos.TODOS -> ordenes
        FiltroPedidos.COMPRAS -> ordenes.filter { it.compradorId == uid }
        FiltroPedidos.VENTAS -> ordenes.filter { it.vendedorId == uid }
        FiltroPedidos.EN_CURSO -> ordenes.filter { esOrdenActiva(it.estado) }
    }

    fun tonoEstado(estado: EstadosOrden): TonoEstadoOrden = when (estado) {
        EstadosOrden.SOLICITUD_ENVIADA,
        EstadosOrden.ESPERANDO_PAGO,
        -> TonoEstadoOrden.PENDIENTE

        EstadosOrden.RESERVADA,
        EstadosOrden.PAGO_RECIBIDO,
        EstadosOrden.ENTREGA_PROGRAMADA,
        EstadosOrden.ENTREGADA_POR_VENDEDOR,
        EstadosOrden.RECIBIDA_POR_COMPRADOR,
        -> TonoEstadoOrden.EN_PROGRESO

        EstadosOrden.COMPLETADA -> TonoEstadoOrden.COMPLETADO

        EstadosOrden.RECHAZADA,
        EstadosOrden.CANCELADA,
        EstadosOrden.DISPUTA_ABIERTA,
        EstadosOrden.EXPIRADA,
        -> TonoEstadoOrden.ALERTA
    }

    fun textoEstadoCorto(estado: EstadosOrden): String = when (estado) {
        EstadosOrden.SOLICITUD_ENVIADA -> "Solicitud enviada"
        EstadosOrden.RESERVADA -> "Reservado"
        EstadosOrden.ESPERANDO_PAGO -> "Esperando transferencia"
        EstadosOrden.PAGO_RECIBIDO -> "Transferencia recibida"
        EstadosOrden.ENTREGA_PROGRAMADA -> "Entrega programada"
        EstadosOrden.ENTREGADA_POR_VENDEDOR -> "Entregado por vendedor"
        EstadosOrden.RECIBIDA_POR_COMPRADOR -> "Recibido por comprador"
        EstadosOrden.COMPLETADA -> "Completada"
        EstadosOrden.RECHAZADA -> "Rechazada"
        EstadosOrden.CANCELADA -> "Cancelada"
        EstadosOrden.DISPUTA_ABIERTA -> "Disputa abierta"
        EstadosOrden.EXPIRADA -> "Expirada"
    }

    fun textoEstadoDetallado(orden: ModeloOrden): String {
        val base = when (orden.estado) {
            EstadosOrden.SOLICITUD_ENVIADA -> "Solicitud enviada"
            EstadosOrden.RESERVADA -> "Reservado"
            EstadosOrden.ESPERANDO_PAGO -> "Esperando transferencia"
            EstadosOrden.PAGO_RECIBIDO -> "Transferencia recibida"
            EstadosOrden.ENTREGA_PROGRAMADA -> "Entrega programada"
            EstadosOrden.ENTREGADA_POR_VENDEDOR -> "Vendedor marcó entregado"
            EstadosOrden.RECIBIDA_POR_COMPRADOR -> "Comprador confirmó recepción"
            EstadosOrden.COMPLETADA -> "Operación completada"
            EstadosOrden.RECHAZADA -> "Solicitud rechazada"
            EstadosOrden.CANCELADA -> "Pedido cancelado"
            EstadosOrden.DISPUTA_ABIERTA -> "Disputa abierta"
            EstadosOrden.EXPIRADA -> "Pedido expirado"
        }
        return if (orden.estado !in setOf(EstadosOrden.CANCELADA, EstadosOrden.RECHAZADA, EstadosOrden.EXPIRADA) &&
            orden.metodoPago == MetodoPago.TRANSFERENCIA &&
            orden.estadoTransferencia != EstadoTransferencia.NO_APLICA
        ) {
            val sub = textoTransferencia(orden.estadoTransferencia)
            if (sub.isNotBlank() && !base.contains(sub, ignoreCase = true)) "$base · $sub" else base
        } else {
            base
        }
    }

    fun textoTransferencia(estado: EstadoTransferencia): String = when (estado) {
        EstadoTransferencia.SOLICITUD_PAGO_ENVIADA -> "solicitud de pago enviada"
        EstadoTransferencia.ESPERANDO_TRANSFERENCIA -> "esperando transferencia"
        EstadoTransferencia.TRANSFERENCIA_RECIBIDA -> "transferencia recibida"
        EstadoTransferencia.RECHAZADA -> "transferencia rechazada"
        EstadoTransferencia.NO_APLICA -> ""
    }

    fun textoMetodoPagoResumen(orden: ModeloOrden): String {
        val base = if (orden.metodoPago == MetodoPago.TRANSFERENCIA) {
            "Transferencia bancaria"
        } else {
            "Contra entrega en Comitán"
        }
        return if (orden.metodoPago == MetodoPago.TRANSFERENCIA &&
            orden.estadoTransferencia != EstadoTransferencia.NO_APLICA &&
            orden.estado !in setOf(EstadosOrden.CANCELADA, EstadosOrden.RECHAZADA, EstadosOrden.EXPIRADA)
        ) {
            val detalle = textoTransferencia(orden.estadoTransferencia)
            if (detalle.isNotBlank()) "$base · $detalle" else base
        } else {
            base
        }
    }

    fun textoDetalleEntregaOPunto(orden: ModeloOrden): String {
        val partes = mutableListOf<String>()
        if (orden.entregaProgramadaEn > 0L) {
            partes.add("Entrega: ${Constantes.obtenerFechaHora(orden.entregaProgramadaEn)}")
        }
        if (orden.puntoEncuentro.isNotBlank()) {
            partes.add("Punto: ${orden.puntoEncuentro.trim()}")
        }
        return partes.joinToString(" · ")
    }

    fun textoSugerenciaAccion(orden: ModeloOrden, uidActual: String): String {
        val esVendedor = uidActual.isNotBlank() && uidActual == orden.vendedorId
        return when (orden.estado) {
            EstadosOrden.SOLICITUD_ENVIADA ->
                if (esVendedor) "Nueva solicitud · acepta o rechaza el pedido"
                else "Esperando confirmación del vendedor"

            EstadosOrden.RESERVADA ->
                if (orden.metodoPago == MetodoPago.TRANSFERENCIA) {
                    if (esVendedor) "Envía las instrucciones de transferencia al comprador"
                    else "Esperando instrucciones de transferencia del vendedor"
                } else {
                    "Pedido reservado · acuerda fecha y hora de entrega"
                }

            EstadosOrden.ESPERANDO_PAGO ->
                if (esVendedor) "Verifica el depósito en tu banco antes de confirmar"
                else "Realiza la transferencia a la cuenta del vendedor"

            EstadosOrden.PAGO_RECIBIDO ->
                "Pago confirmado · agenda la fecha y hora de entrega"

            EstadosOrden.ENTREGA_PROGRAMADA ->
                if (esVendedor) "Acude a la entrega y marca cuando entregues el producto"
                else "Entrega agendada · acude al punto acordado"

            EstadosOrden.ENTREGADA_POR_VENDEDOR ->
                if (esVendedor) "Esperando que el comprador confirme la recepción"
                else "Confirma la recepción para finalizar el pedido"

            EstadosOrden.RECIBIDA_POR_COMPRADOR ->
                if (esVendedor) "El comprador confirmó la recepción"
                else "Finaliza la operación para calificar"

            EstadosOrden.COMPLETADA -> "Operación finalizada · toca para ver detalles o calificar"
            EstadosOrden.RECHAZADA -> "Solicitud rechazada"
            EstadosOrden.CANCELADA ->
                if (orden.motivoCancelacion.isNotBlank()) "Cancelado: ${orden.motivoCancelacion}"
                else "Pedido cancelado"
            EstadosOrden.DISPUTA_ABIERTA -> "Disputa abierta en revisión"
            EstadosOrden.EXPIRADA -> "Solicitud expirada"
        }
    }

    fun formatearCentavos(centavos: Long): String {
        val formato = NumberFormat.getCurrencyInstance(
            Locale.Builder().setLanguage("es").setRegion("MX").build()
        )
        return formato.format(centavos / 100.0)
    }

    fun convertirPrecioACentavos(valor: String): Long = runCatching {
        val soloNumeros = valor.replace(Regex("[^0-9,.]"), "")
        if (soloNumeros.isBlank()) return 0L

        val normalizado = when {
            soloNumeros.contains(',') && soloNumeros.contains('.') -> {
                if (soloNumeros.lastIndexOf('.') > soloNumeros.lastIndexOf(',')) {
                    soloNumeros.replace(",", "")
                } else {
                    soloNumeros.replace(".", "").replace(",", ".")
                }
            }
            soloNumeros.contains(',') -> {
                val partes = soloNumeros.split(',')
                if (partes.size == 2 && partes[1].length in 1..2) {
                    soloNumeros.replace(",", ".")
                } else {
                    soloNumeros.replace(",", "")
                }
            }
            soloNumeros.count { it == '.' } > 1 -> {
                soloNumeros.replace(".", "")
            }
            else -> soloNumeros
        }

        val centavos = BigDecimal(normalizado)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        centavos.coerceAtLeast(0L)
    }.getOrDefault(0L)
}

