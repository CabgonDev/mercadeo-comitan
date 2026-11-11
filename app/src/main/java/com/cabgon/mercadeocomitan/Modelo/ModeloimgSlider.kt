@file:Suppress("PropertyName")
package com.cabgon.mercadeocomitan.Modelo

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
class ModeloimgSlider {

    var id : String = ""
    var imagenUrl : String = ""

    constructor()

    constructor(id: String, imagenUrl: String) {
        this.id = id
        this.imagenUrl = imagenUrl
    }

}