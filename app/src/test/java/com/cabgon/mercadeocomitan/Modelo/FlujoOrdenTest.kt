package com.cabgon.mercadeocomitan.Modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FlujoOrdenTest {

    @Test
    fun contraEntregaCompletaSoloConConfirmacionDeAmbasPartes() {
        val base = orden(MetodoPago.CONTRA_ENTREGA)
        val reservada = base.transicionarA(EstadosOrden.RESERVADA, RolOrden.VENDEDOR, 10L)
        val programada = reservada.transicionarA(EstadosOrden.ENTREGA_PROGRAMADA, RolOrden.COMPRADOR, 20L)
        val entregada = programada.transicionarA(EstadosOrden.ENTREGADA_POR_VENDEDOR, RolOrden.VENDEDOR, 30L)
        val recibida = entregada.transicionarA(EstadosOrden.RECIBIDA_POR_COMPRADOR, RolOrden.COMPRADOR, 40L)
        val completada = recibida.transicionarA(EstadosOrden.COMPLETADA, RolOrden.SISTEMA, 50L)

        assertEquals(EstadosOrden.COMPLETADA, completada.estado)
        assertEquals(30L, completada.confirmadaPorVendedorEn)
        assertEquals(40L, completada.confirmadaPorCompradorEn)
        assertFalse(completada.puedeTransicionarA(EstadosOrden.CANCELADA, RolOrden.VENDEDOR))
    }

    @Test
    fun transferenciaExigeEstadoRecibidoAntesDeMarcarPago() {
        val base = orden(MetodoPago.CONTRA_ENTREGA).seleccionarMetodoPago(MetodoPago.TRANSFERENCIA, 1L)
        val reservada = base.transicionarA(EstadosOrden.RESERVADA, RolOrden.VENDEDOR, 10L)
        val esperandoPago = reservada.transicionarA(EstadosOrden.ESPERANDO_PAGO, RolOrden.VENDEDOR, 20L)

        assertFalse(esperandoPago.puedeTransicionarA(EstadosOrden.PAGO_RECIBIDO, RolOrden.VENDEDOR))
        assertFalse(esperandoPago.puedeTransicionarA(EstadosOrden.CANCELADA, RolOrden.COMPRADOR))
        assertTrue(esperandoPago.puedeTransicionarA(EstadosOrden.CANCELADA, RolOrden.VENDEDOR))
        val recibida = esperandoPago.copy(estadoTransferencia = EstadoTransferencia.TRANSFERENCIA_RECIBIDA)
        val pagada = recibida.transicionarA(EstadosOrden.PAGO_RECIBIDO, RolOrden.VENDEDOR, 30L)
        assertEquals(EstadosOrden.PAGO_RECIBIDO, pagada.estado)
    }

    @Test
    fun transferenciaNoPuedeAgendarEntregaAntesDeQueElVendedorConfirmeElDeposito() {
        val reservada = orden(MetodoPago.TRANSFERENCIA)
        assertFalse(reservada.puedeTransicionarA(EstadosOrden.ENTREGA_PROGRAMADA, RolOrden.COMPRADOR))
        assertFails<TransicionOrdenInvalida> {
            reservada.transicionarA(EstadosOrden.ENTREGA_PROGRAMADA, RolOrden.COMPRADOR, 10L)
        }

        val esperando = reservada.copy(
            estado = EstadosOrden.ESPERANDO_PAGO,
            estadoTransferencia = EstadoTransferencia.ESPERANDO_TRANSFERENCIA,
        )
        assertFalse(esperando.puedeTransicionarA(EstadosOrden.ENTREGA_PROGRAMADA, RolOrden.VENDEDOR))

        val pagada = esperando.copy(
            estado = EstadosOrden.PAGO_RECIBIDO,
            estadoTransferencia = EstadoTransferencia.TRANSFERENCIA_RECIBIDA,
        )
        assertTrue(pagada.puedeTransicionarA(EstadosOrden.ENTREGA_PROGRAMADA, RolOrden.VENDEDOR))
    }

    @Test
    fun vendedorNoPuedeCompletarLaRecepcionDelComprador() {
        val entregada = orden(MetodoPago.CONTRA_ENTREGA)
            .transicionarA(EstadosOrden.RESERVADA, RolOrden.VENDEDOR, 10L)
            .transicionarA(EstadosOrden.ENTREGA_PROGRAMADA, RolOrden.VENDEDOR, 20L)
            .transicionarA(EstadosOrden.ENTREGADA_POR_VENDEDOR, RolOrden.VENDEDOR, 30L)

        assertFalse(entregada.puedeTransicionarA(EstadosOrden.RECIBIDA_POR_COMPRADOR, RolOrden.VENDEDOR))
        assertFails<TransicionOrdenInvalida> {
            entregada.transicionarA(EstadosOrden.RECIBIDA_POR_COMPRADOR, RolOrden.VENDEDOR, 40L)
        }
    }

    @Test
    fun compradorPuedeConfirmarYCompletarEnUnaSolaAccion() {
        val entregada = orden(MetodoPago.CONTRA_ENTREGA)
            .transicionarA(EstadosOrden.RESERVADA, RolOrden.VENDEDOR, 10L)
            .transicionarA(EstadosOrden.ENTREGA_PROGRAMADA, RolOrden.VENDEDOR, 20L, 100L)
            .transicionarA(EstadosOrden.ENTREGADA_POR_VENDEDOR, RolOrden.VENDEDOR, 30L)

        val completada = entregada.transicionarA(EstadosOrden.COMPLETADA, RolOrden.COMPRADOR, 40L)

        assertEquals(EstadosOrden.COMPLETADA, completada.estado)
        assertEquals(40L, completada.confirmadaPorCompradorEn)
        assertEquals(100L, completada.entregaProgramadaEn)
    }

    @Test
    fun transferenciaManualSigueElFlujoYPermiteReintentoTrasRechazo() {
        val inicial = transferencia().copy(estado = EstadoTransferencia.NO_APLICA)
        val solicitud = inicial.transicionarA(EstadoTransferencia.SOLICITUD_PAGO_ENVIADA, RolOrden.COMPRADOR, 10L)
        val esperando = solicitud.transicionarA(EstadoTransferencia.ESPERANDO_TRANSFERENCIA, RolOrden.VENDEDOR, 20L)
        val rechazada = esperando.transicionarA(EstadoTransferencia.RECHAZADA, RolOrden.VENDEDOR, 30L)
        val reintentada = rechazada.transicionarA(EstadoTransferencia.SOLICITUD_PAGO_ENVIADA, RolOrden.COMPRADOR, 40L)

        assertEquals(EstadoTransferencia.SOLICITUD_PAGO_ENVIADA, reintentada.estado)
        assertEquals(40L, reintentada.solicitudEnviadaEn)
    }

    @Test
    fun clabeSeValidaYSeEnmascaraSinExponerlaCompleta() {
        val clabe = "646180157012345676"
        assertTrue(esClabeValida(clabe))
        assertFalse(esClabeValida("64618015701234567"))
        assertEquals("646***********5676", enmascararClabe(clabe))
    }

    @Test
    fun soloVendedorPuedeRegistrarCuentaDestino() {
        val transferencia = transferencia()
        val actualizada = transferencia.registrarCuentaVendedor(
            clabe = "646180157012345676",
            banco = "Banco local",
            titular = "Vendedor Comitán",
            actor = RolOrden.VENDEDOR,
            ahora = 50L,
        )

        assertEquals("Banco local", actualizada.banco)
        assertEquals(50L, actualizada.actualizadoEn)
        assertFails<TransicionTransferenciaInvalida> {
            transferencia.registrarCuentaVendedor(
                clabe = "646180157012345676",
                banco = "Banco local",
                titular = "Vendedor Comitán",
                actor = RolOrden.COMPRADOR,
                ahora = 60L,
            )
        }
    }

    @Test
    fun solicitudInicialPuedeEsperarLaCuentaDelVendedor() {
        val solicitud = ModeloTransferencia(
            id = "transferencia-2",
            ordenId = "orden-1",
            compradorId = "comprador-1",
            vendedorId = "vendedor-1",
            importeCentavos = 10000L,
        )

        assertTrue(solicitud.validar().isEmpty())
    }

    private fun orden(metodoPago: MetodoPago) = ModeloOrden(
        id = "orden-1",
        anuncioId = "anuncio-1",
        compradorId = "comprador-1",
        vendedorId = "vendedor-1",
        importeCentavos = 10000L,
        metodoPago = metodoPago,
    )

    private fun transferencia() = ModeloTransferencia(
        id = "transferencia-1",
        ordenId = "orden-1",
        compradorId = "comprador-1",
        vendedorId = "vendedor-1",
        clabeInterbancaria = "646180157012345678",
        importeCentavos = 10000L,
    )

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        try {
            block()
            fail("Se esperaba ${T::class.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }
}
