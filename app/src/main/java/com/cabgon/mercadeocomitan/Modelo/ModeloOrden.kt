package com.cabgon.mercadeocomitan.Modelo

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

/**
 * Orden entre un comprador y un vendedor.
 *
 * El importe se guarda en centavos para evitar errores de redondeo. La clase
 * no procesa pagos: solamente representa el acuerdo, su entrega y el estado
 * informado por las personas involucradas.
 */
@Keep
@IgnoreExtraProperties
data class ModeloOrden(
    var id: String = "",
    var anuncioId: String = "",
    var compradorId: String = "",
    var vendedorId: String = "",
    var tituloAnuncio: String = "",
    var importeCentavos: Long = 0L,
    var moneda: String = "MXN",
    var metodoPago: MetodoPago = MetodoPago.CONTRA_ENTREGA,
    var estado: EstadosOrden = EstadosOrden.SOLICITUD_ENVIADA,
    var estadoTransferencia: EstadoTransferencia = EstadoTransferencia.NO_APLICA,
    var clabeDestino: String = "",
    var bancoDestino: String = "",
    var titularDestino: String = "",
    var creadaEn: Long = 0L,
    var actualizadaEn: Long = 0L,
    var entregaProgramadaEn: Long = 0L,
    var puntoEncuentro: String = "",
    var notas: String = "",
    var confirmadaPorVendedorEn: Long = 0L,
    var confirmadaPorCompradorEn: Long = 0L,
    var motivoCancelacion: String = "",
)

/**
 * El comprador elige el método antes de reservar. En transferencia la
 * solicitud queda visible de inmediato para que el vendedor pueda registrar
 * su CLABE y continuar con la verificación manual.
 */
fun ModeloOrden.seleccionarMetodoPago(metodo: MetodoPago, ahora: Long): ModeloOrden {
    if (estado != EstadosOrden.SOLICITUD_ENVIADA) {
        throw TransicionOrdenInvalida("El método de pago solo puede elegirse al enviar la solicitud")
    }
    return copy(
        metodoPago = metodo,
        estadoTransferencia = if (metodo == MetodoPago.TRANSFERENCIA) {
            EstadoTransferencia.SOLICITUD_PAGO_ENVIADA
        } else {
            EstadoTransferencia.NO_APLICA
        },
        actualizadaEn = ahora,
    )
}

/** Errores de dominio sin dependencia de Android o Firebase. */
class TransicionOrdenInvalida(message: String) : IllegalStateException(message)

/** Reglas de transición de una orden. Todas las mutaciones pasan por aquí. */
object FlujoOrden {

    fun puedeTransicionar(
        orden: ModeloOrden,
        nuevoEstado: EstadosOrden,
        actor: RolOrden,
    ): Boolean {
        if (orden.estado == nuevoEstado) return false
        if (orden.estado == EstadosOrden.COMPLETADA || orden.estado == EstadosOrden.EXPIRADA) {
            return false
        }

        return when (orden.estado) {
            EstadosOrden.SOLICITUD_ENVIADA -> when (nuevoEstado) {
                EstadosOrden.RESERVADA -> actor == RolOrden.VENDEDOR || actor == RolOrden.SISTEMA
                EstadosOrden.RECHAZADA, EstadosOrden.CANCELADA ->
                    actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR || actor == RolOrden.ADMIN
                EstadosOrden.DISPUTA_ABIERTA -> actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR
                else -> false
            }

            EstadosOrden.RESERVADA -> when (nuevoEstado) {
                EstadosOrden.ESPERANDO_PAGO ->
                    orden.metodoPago == MetodoPago.TRANSFERENCIA &&
                        orden.estadoTransferencia != EstadoTransferencia.NO_APLICA &&
                        (actor == RolOrden.VENDEDOR || actor == RolOrden.SISTEMA)
                EstadosOrden.ENTREGA_PROGRAMADA ->
                    orden.metodoPago == MetodoPago.CONTRA_ENTREGA &&
                        (actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR || actor == RolOrden.SISTEMA)
                EstadosOrden.CANCELADA ->
                    actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR || actor == RolOrden.ADMIN
                EstadosOrden.DISPUTA_ABIERTA -> actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR
                else -> false
            }

            EstadosOrden.ESPERANDO_PAGO -> when (nuevoEstado) {
                EstadosOrden.PAGO_RECIBIDO ->
                    orden.metodoPago == MetodoPago.TRANSFERENCIA &&
                        orden.estadoTransferencia == EstadoTransferencia.TRANSFERENCIA_RECIBIDA &&
                        (actor == RolOrden.VENDEDOR || actor == RolOrden.SISTEMA)
                EstadosOrden.CANCELADA ->
                    actor == RolOrden.VENDEDOR || actor == RolOrden.ADMIN
                EstadosOrden.DISPUTA_ABIERTA -> actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR
                else -> false
            }

            EstadosOrden.PAGO_RECIBIDO -> when (nuevoEstado) {
                EstadosOrden.ENTREGA_PROGRAMADA ->
                    actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR || actor == RolOrden.SISTEMA
                EstadosOrden.CANCELADA -> actor == RolOrden.VENDEDOR || actor == RolOrden.ADMIN
                EstadosOrden.DISPUTA_ABIERTA -> actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR
                else -> false
            }

            EstadosOrden.ENTREGA_PROGRAMADA -> when (nuevoEstado) {
                EstadosOrden.ENTREGADA_POR_VENDEDOR -> actor == RolOrden.VENDEDOR
                EstadosOrden.CANCELADA ->
                    actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR || actor == RolOrden.ADMIN
                EstadosOrden.DISPUTA_ABIERTA -> actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR
                else -> false
            }

            EstadosOrden.ENTREGADA_POR_VENDEDOR -> when (nuevoEstado) {
                EstadosOrden.RECIBIDA_POR_COMPRADOR -> actor == RolOrden.COMPRADOR
                EstadosOrden.COMPLETADA -> actor == RolOrden.COMPRADOR
                EstadosOrden.DISPUTA_ABIERTA -> actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR
                else -> false
            }

            EstadosOrden.RECIBIDA_POR_COMPRADOR ->
                nuevoEstado == EstadosOrden.COMPLETADA &&
                    (actor == RolOrden.SISTEMA || actor == RolOrden.COMPRADOR)

            EstadosOrden.RECHAZADA -> when (nuevoEstado) {
                EstadosOrden.RESERVADA -> actor == RolOrden.VENDEDOR || actor == RolOrden.SISTEMA
                EstadosOrden.CANCELADA -> actor == RolOrden.COMPRADOR || actor == RolOrden.VENDEDOR
                else -> false
            }

            EstadosOrden.CANCELADA, EstadosOrden.DISPUTA_ABIERTA -> false
            EstadosOrden.COMPLETADA, EstadosOrden.EXPIRADA -> false
        }
    }

    fun transicionar(
        orden: ModeloOrden,
        nuevoEstado: EstadosOrden,
        actor: RolOrden,
        ahora: Long,
        fechaEntrega: Long? = null,
    ): ModeloOrden {
        if (!puedeTransicionar(orden, nuevoEstado, actor)) {
            throw TransicionOrdenInvalida(
                "No se puede pasar ${orden.estado} a $nuevoEstado con actor $actor"
            )
        }

        return orden.copy(
            estado = nuevoEstado,
            actualizadaEn = ahora,
            entregaProgramadaEn = if (nuevoEstado == EstadosOrden.ENTREGA_PROGRAMADA) {
                fechaEntrega ?: if (orden.entregaProgramadaEn > 0L) orden.entregaProgramadaEn else ahora
            } else {
                orden.entregaProgramadaEn
            },
            confirmadaPorVendedorEn = if (nuevoEstado == EstadosOrden.ENTREGADA_POR_VENDEDOR) {
                ahora
            } else {
                orden.confirmadaPorVendedorEn
            },
            confirmadaPorCompradorEn = if (
                nuevoEstado == EstadosOrden.RECIBIDA_POR_COMPRADOR ||
                (nuevoEstado == EstadosOrden.COMPLETADA && orden.estado == EstadosOrden.ENTREGADA_POR_VENDEDOR)
            ) {
                ahora
            } else {
                orden.confirmadaPorCompradorEn
            },
        )
    }
}

fun ModeloOrden.puedeTransicionarA(nuevoEstado: EstadosOrden, actor: RolOrden): Boolean =
    FlujoOrden.puedeTransicionar(this, nuevoEstado, actor)

fun ModeloOrden.transicionarA(
    nuevoEstado: EstadosOrden,
    actor: RolOrden,
    ahora: Long,
    fechaEntrega: Long? = null,
): ModeloOrden = FlujoOrden.transicionar(this, nuevoEstado, actor, ahora, fechaEntrega)

fun ModeloOrden.validar(): List<String> = buildList {
    if (id.isBlank()) add("id es obligatorio")
    if (anuncioId.isBlank()) add("anuncioId es obligatorio")
    if (compradorId.isBlank()) add("compradorId es obligatorio")
    if (vendedorId.isBlank()) add("vendedorId es obligatorio")
    if (compradorId == vendedorId && compradorId.isNotBlank()) add("comprador y vendedor deben ser distintos")
    if (importeCentavos < 0L) add("importeCentavos no puede ser negativo")
    if (moneda.length != 3) add("moneda debe ser un código de tres letras")
    if (metodoPago == MetodoPago.CONTRA_ENTREGA && estadoTransferencia != EstadoTransferencia.NO_APLICA) {
        add("contra entrega no puede tener estado de transferencia")
    }
    if (metodoPago == MetodoPago.TRANSFERENCIA && estadoTransferencia == EstadoTransferencia.NO_APLICA) {
        add("transferencia requiere un estado de transferencia")
    }
}
