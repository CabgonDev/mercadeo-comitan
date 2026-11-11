package com.cabgon.mercadeocomitan.Modelo

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
class ModeloChats {

    var keyChat: String = ""          // ID o ruta del chat (uid1_uid2)
    var uidRecibimos: String = ""     // UID del otro participante
    var urlImagenPerfil: String = ""  // Imagen del otro usuario
    var nombres: String = ""          // Nombre del otro usuario
    var ultimoMensaje: String = ""    // Último mensaje mostrado en lista
    var tipoMensaje: String = ""      // Tipo (texto / imagen)
    var emisorUid: String = ""        // UID emisor del último mensaje
    var receptorUid: String = ""      // UID receptor
    var idMensaje: String = ""        // ID del último mensaje
    var mensaje: String = ""          // Contenido del último mensaje
    var tiempo: Long = 0              // Timestamp del último mensaje

    constructor()

    constructor(
        keyChat: String,
        uidRecibimos: String,
        urlImagenPerfil: String,
        nombres: String,
        ultimoMensaje: String,
        tipoMensaje: String,
        emisorUid: String,
        receptorUid: String,
        idMensaje: String,
        mensaje: String,
        tiempo: Long
    ) {
        this.keyChat = keyChat
        this.uidRecibimos = uidRecibimos
        this.urlImagenPerfil = urlImagenPerfil
        this.nombres = nombres
        this.ultimoMensaje = ultimoMensaje
        this.tipoMensaje = tipoMensaje
        this.emisorUid = emisorUid
        this.receptorUid = receptorUid
        this.idMensaje = idMensaje
        this.mensaje = mensaje
        this.tiempo = tiempo
    }
}