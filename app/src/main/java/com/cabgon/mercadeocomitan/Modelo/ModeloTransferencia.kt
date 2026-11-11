package com.cabgon.mercadeocomitan.Modelo

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

/**
 * Información de una transferencia bancaria manual.
 *
 * El vendedor controla el estado porque la aplicación no consulta el banco.
 * El comprobante es evidencia auxiliar: nunca sustituye la verificación del
 * vendedor en su cuenta bancaria.
 */
@Keep
@IgnoreExtraProperties
data class ModeloTransferencia(
    var id: String = "",
    var ordenId: String = "",
    var compradorId: String = "",
    var vendedorId: String = "",
    var clabeInterbancaria: String = "",
    var banco: String = "",
    var titular: String = "",
    var importeCentavos: Long = 0L,
    var moneda: String = "MXN",
    var estado: EstadoTransferencia = EstadoTransferencia.SOLICITUD_PAGO_ENVIADA,
    var solicitudEnviadaEn: Long = 0L,
    var esperandoDesde: Long = 0L,
    var recibidaEn: Long = 0L,
    var rechazadaEn: Long = 0L,
    var actualizadoEn: Long = 0L,
    var folioConfirmacion: String = "",
    var motivoRechazo: String = "",
    var comprobanteUri: String = "",
)

class TransicionTransferenciaInvalida(message: String) : IllegalStateException(message)

/** Permite que únicamente el vendedor registre o reemplace la cuenta destino. */
fun ModeloTransferencia.registrarCuentaVendedor(
    clabe: String,
    banco: String,
    titular: String,
    actor: RolOrden,
    ahora: Long,
): ModeloTransferencia {
    if (actor != RolOrden.VENDEDOR && actor != RolOrden.ADMIN) {
        throw TransicionTransferenciaInvalida("Solo el vendedor puede registrar la cuenta destino")
    }
    val clabeLimpia = clabe.trim()
    if (!esClabeValida(clabeLimpia)) {
        throw TransicionTransferenciaInvalida("La CLABE debe tener 18 dígitos")
    }
    return copy(
        clabeInterbancaria = clabeLimpia,
        banco = banco.trim(),
        titular = titular.trim(),
        actualizadoEn = ahora,
    )
}

object FlujoTransferencia {

    fun puedeTransicionar(
        transferencia: ModeloTransferencia,
        nuevoEstado: EstadoTransferencia,
        actor: RolOrden,
    ): Boolean {
        if (transferencia.estado == nuevoEstado) return false
        return when (transferencia.estado) {
            EstadoTransferencia.NO_APLICA ->
                nuevoEstado == EstadoTransferencia.SOLICITUD_PAGO_ENVIADA &&
                    (actor == RolOrden.COMPRADOR || actor == RolOrden.SISTEMA)

            EstadoTransferencia.SOLICITUD_PAGO_ENVIADA ->
                nuevoEstado == EstadoTransferencia.ESPERANDO_TRANSFERENCIA &&
                    (actor == RolOrden.VENDEDOR || actor == RolOrden.SISTEMA)

            EstadoTransferencia.ESPERANDO_TRANSFERENCIA -> when (nuevoEstado) {
                EstadoTransferencia.TRANSFERENCIA_RECIBIDA,
                EstadoTransferencia.RECHAZADA,
                -> actor == RolOrden.VENDEDOR || actor == RolOrden.ADMIN
                else -> false
            }

            EstadoTransferencia.RECHAZADA ->
                nuevoEstado == EstadoTransferencia.SOLICITUD_PAGO_ENVIADA &&
                    (actor == RolOrden.COMPRADOR || actor == RolOrden.SISTEMA)

            EstadoTransferencia.TRANSFERENCIA_RECIBIDA -> false
        }
    }

    fun transicionar(
        transferencia: ModeloTransferencia,
        nuevoEstado: EstadoTransferencia,
        actor: RolOrden,
        ahora: Long,
    ): ModeloTransferencia {
        if (!puedeTransicionar(transferencia, nuevoEstado, actor)) {
            throw TransicionTransferenciaInvalida(
                "No se puede pasar ${transferencia.estado} a $nuevoEstado con actor $actor"
            )
        }
        return transferencia.copy(
            estado = nuevoEstado,
            actualizadoEn = ahora,
            solicitudEnviadaEn = if (nuevoEstado == EstadoTransferencia.SOLICITUD_PAGO_ENVIADA) {
                ahora
            } else {
                transferencia.solicitudEnviadaEn
            },
            esperandoDesde = if (nuevoEstado == EstadoTransferencia.ESPERANDO_TRANSFERENCIA) {
                ahora
            } else {
                transferencia.esperandoDesde
            },
            recibidaEn = if (nuevoEstado == EstadoTransferencia.TRANSFERENCIA_RECIBIDA) ahora else transferencia.recibidaEn,
            rechazadaEn = if (nuevoEstado == EstadoTransferencia.RECHAZADA) ahora else transferencia.rechazadaEn,
        )
    }
}

fun ModeloTransferencia.puedeTransicionarA(
    nuevoEstado: EstadoTransferencia,
    actor: RolOrden,
): Boolean = FlujoTransferencia.puedeTransicionar(this, nuevoEstado, actor)

fun ModeloTransferencia.transicionarA(
    nuevoEstado: EstadoTransferencia,
    actor: RolOrden,
    ahora: Long,
): ModeloTransferencia = FlujoTransferencia.transicionar(this, nuevoEstado, actor, ahora)

fun ModeloTransferencia.validar(): List<String> = buildList {
    if (id.isBlank()) add("id es obligatorio")
    if (ordenId.isBlank()) add("ordenId es obligatorio")
    if (compradorId.isBlank()) add("compradorId es obligatorio")
    if (vendedorId.isBlank()) add("vendedorId es obligatorio")
    if (importeCentavos <= 0L) add("importeCentavos debe ser mayor que cero")
    if (moneda.length != 3) add("moneda debe ser un código de tres letras")
    if (estado != EstadoTransferencia.SOLICITUD_PAGO_ENVIADA && !esClabeValida(clabeInterbancaria)) {
        add("clabeInterbancaria debe tener 18 dígitos")
    }
    if (estado == EstadoTransferencia.RECHAZADA && motivoRechazo.isBlank()) {
        add("motivoRechazo es obligatorio cuando la transferencia es rechazada")
    }
}

fun esClabeValida(clabe: String): Boolean {
    val limpia = clabe.trim()
    if (!limpia.matches(Regex("\\d{18}"))) return false
    val pesos = intArrayOf(3, 7, 1)
    val suma = limpia.take(17).mapIndexed { index, digito ->
        (digito.digitToInt() * pesos[index % pesos.size]) % 10
    }.sum()
    return limpia.last().digitToInt() == (10 - (suma % 10)) % 10
}

fun enmascararClabe(clabe: String): String {
    val limpia = clabe.trim()
    if (!esClabeValida(limpia)) return ""
    return "${limpia.take(3)}***********${limpia.takeLast(4)}"
}
