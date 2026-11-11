package com.cabgon.mercadeocomitan.Pedidos

import com.cabgon.mercadeocomitan.Modelo.EstadoTransferencia
import com.cabgon.mercadeocomitan.Modelo.EstadosOrden
import com.cabgon.mercadeocomitan.Modelo.MetodoPago
import com.cabgon.mercadeocomitan.Modelo.ModeloOrden
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentacionOrdenTest {

    @Test
    fun filtrarOrdenesSeparaComprasVentasYEnCurso() {
        val compraActiva = ModeloOrden(
            id = "1",
            compradorId = "uid-yo",
            vendedorId = "uid-otro",
            estado = EstadosOrden.RESERVADA,
        )
        val ventaCompletada = ModeloOrden(
            id = "2",
            compradorId = "uid-comprador",
            vendedorId = "uid-yo",
            estado = EstadosOrden.COMPLETADA,
        )
        val ventaCancelada = ModeloOrden(
            id = "3",
            compradorId = "uid-comprador",
            vendedorId = "uid-yo",
            estado = EstadosOrden.CANCELADA,
        )
        val lista = listOf(compraActiva, ventaCompletada, ventaCancelada)

        assertEquals(3, PresentacionOrden.filtrarOrdenes(lista, "uid-yo", FiltroPedidos.TODOS).size)
        assertEquals(listOf(compraActiva), PresentacionOrden.filtrarOrdenes(lista, "uid-yo", FiltroPedidos.COMPRAS))
        assertEquals(
            listOf(ventaCompletada, ventaCancelada),
            PresentacionOrden.filtrarOrdenes(lista, "uid-yo", FiltroPedidos.VENTAS),
        )
        assertEquals(listOf(compraActiva), PresentacionOrden.filtrarOrdenes(lista, "uid-yo", FiltroPedidos.EN_CURSO))
    }

    @Test
    fun esOrdenActivaDistingueEstadosEnCursoDeFinalizados() {
        assertTrue(PresentacionOrden.esOrdenActiva(EstadosOrden.SOLICITUD_ENVIADA))
        assertTrue(PresentacionOrden.esOrdenActiva(EstadosOrden.ENTREGA_PROGRAMADA))
        assertTrue(PresentacionOrden.esOrdenActiva(EstadosOrden.DISPUTA_ABIERTA))
        assertFalse(PresentacionOrden.esOrdenActiva(EstadosOrden.COMPLETADA))
        assertFalse(PresentacionOrden.esOrdenActiva(EstadosOrden.CANCELADA))
        assertFalse(PresentacionOrden.esOrdenActiva(EstadosOrden.RECHAZADA))
        assertFalse(PresentacionOrden.esOrdenActiva(EstadosOrden.EXPIRADA))
    }

    @Test
    fun tonoEstadoYDescripcionesReflejanElFlujoDeLaOrden() {
        val ordenTransferencia = ModeloOrden(
            id = "ord-1",
            compradorId = "comprador-1",
            vendedorId = "vendedor-1",
            metodoPago = MetodoPago.TRANSFERENCIA,
            estado = EstadosOrden.ESPERANDO_PAGO,
            estadoTransferencia = EstadoTransferencia.ESPERANDO_TRANSFERENCIA,
        )

        assertEquals(TonoEstadoOrden.PENDIENTE, PresentacionOrden.tonoEstado(ordenTransferencia.estado))
        assertTrue(
            PresentacionOrden.textoSugerenciaAccion(ordenTransferencia, "vendedor-1")
                .contains("banco", ignoreCase = true)
        )
        assertTrue(
            PresentacionOrden.textoSugerenciaAccion(ordenTransferencia, "comprador-1")
                .contains("transferencia", ignoreCase = true)
        )
    }

    @Test
    fun convertirPrecioACentavosSoportaSeparadoresDeMilesYDecimales() {
        assertEquals(150_000L, PresentacionOrden.convertirPrecioACentavos("1,500"))
        assertEquals(150_050L, PresentacionOrden.convertirPrecioACentavos("$1,500.50"))
        assertEquals(150_050L, PresentacionOrden.convertirPrecioACentavos("1.500,50"))
        assertEquals(25_000L, PresentacionOrden.convertirPrecioACentavos("250"))
        assertEquals(9_950L, PresentacionOrden.convertirPrecioACentavos("99,50"))
        assertEquals(0L, PresentacionOrden.convertirPrecioACentavos(""))
        assertEquals(0L, PresentacionOrden.convertirPrecioACentavos("gratis"))
    }
}

