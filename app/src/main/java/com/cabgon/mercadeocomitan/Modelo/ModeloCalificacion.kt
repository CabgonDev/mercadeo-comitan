package com.cabgon.mercadeocomitan.Modelo

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

/** Calificación posterior a una orden completada. */
@Keep
@IgnoreExtraProperties
data class ModeloCalificacion(
    var id: String = "",
    var ordenId: String = "",
    var anuncioId: String = "",
    var autorId: String = "",
    var usuarioCalificadoId: String = "",
    var estrellas: Int = 0,
    var comentario: String = "",
    var creadaEn: Long = 0L,
    var reportada: Boolean = false,
)

class CalificacionInvalida(message: String) : IllegalArgumentException(message)

fun ModeloCalificacion.validar(): List<String> = buildList {
    if (id.isBlank()) add("id es obligatorio")
    if (ordenId.isBlank()) add("ordenId es obligatorio")
    if (autorId.isBlank()) add("autorId es obligatorio")
    if (usuarioCalificadoId.isBlank()) add("usuarioCalificadoId es obligatorio")
    if (autorId == usuarioCalificadoId && autorId.isNotBlank()) add("no puedes calificarte a ti mismo")
    if (estrellas !in 1..5) add("estrellas debe estar entre 1 y 5")
    if (comentario.length > 1000) add("comentario no puede superar 1000 caracteres")
}

fun ModeloCalificacion.validarOExcepcion(): ModeloCalificacion {
    val errores = validar()
    if (errores.isNotEmpty()) throw CalificacionInvalida(errores.joinToString("; "))
    return this
}
