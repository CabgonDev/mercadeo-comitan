package com.cabgon.mercadeocomitan.Chat

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

internal object EstadoReciboMensaje {
    const val ENVIADO = "enviado"
    const val ENTREGADO = "entregado"
    const val LEIDO = "leido"

    fun puedeAvanzar(actual: String, destino: String): Boolean = when (actual) {
        ENVIADO -> destino == ENTREGADO || destino == LEIDO
        ENTREGADO -> destino == LEIDO
        else -> false
    }

    fun marcar(ref: DatabaseReference, uidReceptor: String, destino: String) {
        if (uidReceptor.isBlank() || destino !in setOf(ENTREGADO, LEIDO)) return

        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.value == null ||
                    currentData.child("idMensaje").value != ref.key ||
                    currentData.child("receptorUid").value != uidReceptor
                ) {
                    return Transaction.abort()
                }

                val actual = currentData.child("estado").getValue(String::class.java)
                    ?: ENVIADO
                if (!puedeAvanzar(actual, destino)) return Transaction.abort()

                currentData.child("estado").value = destino
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: com.google.firebase.database.DatabaseError?,
                committed: Boolean,
                currentData: com.google.firebase.database.DataSnapshot?,
            ) {
                if (error != null) {
                    android.util.Log.w("ChatReceipt", "No se pudo actualizar el recibo: ${error.message}")
                }
            }
        }, false)
    }
}
