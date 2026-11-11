package com.cabgon.mercadeocomitan.Modelo

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class ModeloUsuario(
    var uid: String? = null,
    var nombres: String? = null,
    var email: String? = null,
    var urlImagenPerfil: String? = null,
    var estado: String? = null,
    var fcmToken: String? = null,
    var rol: String? = "usuario",
    var puedePublicarGratis: Boolean = false,
    var bloqueado: Boolean = false,
    var transferenciaHabilitada: Boolean = false,
    var calificacionPromedio: Double = 0.0,
    var totalCalificaciones: Int = 0
)
