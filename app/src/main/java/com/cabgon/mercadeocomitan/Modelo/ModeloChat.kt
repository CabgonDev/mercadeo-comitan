package com.cabgon.mercadeocomitan.Modelo

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
class ModeloChat {
    var idMensaje: String = ""
    var mensaje: String = ""
    var emisorUid: String = ""
    var receptorUid: String = ""
    var tipoMensaje: String = ""
    var tiempo: Long = 0L
    var estado: String = "enviado"

    constructor()
    constructor(
        idMensaje: String,
        mensaje: String,
        emisorUid: String,
        receptorUid: String,
        tipoMensaje: String,
        tiempo: Long,
        estado: String = "enviado"
    ) {
        this.idMensaje = idMensaje
        this.mensaje = mensaje
        this.emisorUid = emisorUid
        this.receptorUid = receptorUid
        this.tipoMensaje = tipoMensaje
        this.tiempo = tiempo
        this.estado = estado
    }
}
