package com.cabgon.mercadeocomitan.Modelo

/** Estados que puede recorrer una orden dentro del marketplace local. */
enum class EstadosOrden {
    SOLICITUD_ENVIADA,
    RESERVADA,
    ESPERANDO_PAGO,
    PAGO_RECIBIDO,
    ENTREGA_PROGRAMADA,
    ENTREGADA_POR_VENDEDOR,
    RECIBIDA_POR_COMPRADOR,
    COMPLETADA,
    RECHAZADA,
    CANCELADA,
    DISPUTA_ABIERTA,
    EXPIRADA,
}

/** Métodos de pago disponibles sin procesar dinero dentro de la aplicación. */
enum class MetodoPago {
    CONTRA_ENTREGA,
    TRANSFERENCIA,
}

/** Identidad que intenta ejecutar una transición de la orden. */
enum class RolOrden {
    COMPRADOR,
    VENDEDOR,
    ADMIN,
    SISTEMA,
}

/** Estado de una transferencia manual informada por el vendedor. */
enum class EstadoTransferencia {
    NO_APLICA,
    SOLICITUD_PAGO_ENVIADA,
    ESPERANDO_TRANSFERENCIA,
    TRANSFERENCIA_RECIBIDA,
    RECHAZADA,
}
