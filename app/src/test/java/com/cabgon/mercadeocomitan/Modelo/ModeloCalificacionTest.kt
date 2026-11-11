package com.cabgon.mercadeocomitan.Modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeloCalificacionTest {

    @Test
    fun calificacionValidaTieneEntreUnaYCincoEstrellas() {
        val calificacion = ModeloCalificacion(
            id = "cal-1",
            ordenId = "orden-1",
            autorId = "comprador-1",
            usuarioCalificadoId = "vendedor-1",
            estrellas = 5,
            comentario = "Entrega puntual",
        )

        assertTrue(calificacion.validar().isEmpty())
    }

    @Test
    fun calificacionInvalidaExplicaElRangoDeEstrellas() {
        val errores = ModeloCalificacion(
            id = "cal-1",
            ordenId = "orden-1",
            autorId = "comprador-1",
            usuarioCalificadoId = "vendedor-1",
            estrellas = 6,
        ).validar()

        assertEquals(1, errores.size)
        assertTrue(errores.single().contains("estrellas"))
    }
}
