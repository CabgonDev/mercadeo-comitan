package com.cabgon.mercadeocomitan.Chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EstadoReciboMensajeTest {

    @Test
    fun envioPuedeAvanzarAEntregadoOLeido() {
        assertTrue(EstadoReciboMensaje.puedeAvanzar("enviado", "entregado"))
        assertTrue(EstadoReciboMensaje.puedeAvanzar("enviado", "leido"))
    }

    @Test
    fun entregadoSoloPuedeAvanzarALeido() {
        assertTrue(EstadoReciboMensaje.puedeAvanzar("entregado", "leido"))
        assertFalse(EstadoReciboMensaje.puedeAvanzar("entregado", "enviado"))
    }

    @Test
    fun leidoEsEstadoFinalYLosEstadosDesconocidosNoAvanzan() {
        assertFalse(EstadoReciboMensaje.puedeAvanzar("leido", "entregado"))
        assertFalse(EstadoReciboMensaje.puedeAvanzar("leido", "enviado"))
        assertFalse(EstadoReciboMensaje.puedeAvanzar("desconocido", "leido"))
    }
}
