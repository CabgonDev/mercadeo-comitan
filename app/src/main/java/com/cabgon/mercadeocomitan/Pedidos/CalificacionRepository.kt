package com.cabgon.mercadeocomitan.Pedidos

import com.cabgon.mercadeocomitan.Modelo.EstadosOrden
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

data class ResumenCalificaciones(val promedio: Double = 0.0, val total: Int = 0)

/** Guarda la reputación posterior a una orden completada. */
class CalificacionRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    fun guardar(
        ordenId: String,
        anuncioId: String,
        autorId: String,
        usuarioCalificadoId: String,
        estrellas: Int,
        comentario: String,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        if (ordenId.isBlank() || anuncioId.isBlank() || autorId.isBlank() ||
            usuarioCalificadoId.isBlank() || autorId == usuarioCalificadoId
        ) {
            onComplete(Result.failure(IllegalArgumentException("Participantes no válidos")))
            return
        }
        if (auth.currentUser?.uid != autorId) {
            onComplete(Result.failure(SecurityException("Inicia sesión con la cuenta que participó en la orden")))
            return
        }
        if (estrellas !in 1..5) {
            onComplete(Result.failure(IllegalArgumentException("La calificación debe estar entre 1 y 5")))
            return
        }

        database.getReference("Ordenes").child(ordenId).get()
            .addOnSuccessListener { existing ->
                val compradorId = existing.child("compradorId").getValue(String::class.java).orEmpty()
                val vendedorId = existing.child("vendedorId").getValue(String::class.java).orEmpty()
                val contraparte = when (autorId) {
                    compradorId -> vendedorId
                    vendedorId -> compradorId
                    else -> ""
                }
                if (!existing.exists() ||
                    existing.child("estado").getValue(String::class.java) != EstadosOrden.COMPLETADA.name ||
                    existing.child("anuncioId").getValue(String::class.java) != anuncioId ||
                    contraparte.isBlank() || contraparte != usuarioCalificadoId
                ) {
                    onComplete(Result.failure(SecurityException("Solo los participantes pueden calificar una orden completada")))
                    return@addOnSuccessListener
                }

                val id = "${ordenId}_$autorId"
                val ahora = System.currentTimeMillis()
                val calificacion = mapOf<String, Any>(
                    "id" to id,
                    "ordenId" to ordenId,
                    "anuncioId" to anuncioId,
                    "autorId" to autorId,
                    "usuarioCalificadoId" to usuarioCalificadoId,
                    "estrellas" to estrellas,
                    "comentario" to comentario.trim().take(1000),
                    "creadaEn" to ahora,
                    "reportada" to false,
                )
                val ratingRef = database.getReference("Calificaciones")
                    .child(usuarioCalificadoId)
                    .child(id)
                var duplicada = false
                ratingRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
                    override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                        if (currentData.value != null) {
                            duplicada = true
                            return com.google.firebase.database.Transaction.abort()
                        }
                        currentData.value = calificacion
                        return com.google.firebase.database.Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: com.google.firebase.database.DatabaseError?,
                        committed: Boolean,
                        snapshot: com.google.firebase.database.DataSnapshot?,
                    ) {
                        when {
                            error != null -> onComplete(Result.failure(error.toException()))
                            committed -> onComplete(Result.success(Unit))
                            duplicada -> onComplete(Result.failure(IllegalStateException("Esta orden ya fue calificada por este usuario")))
                            else -> onComplete(Result.failure(IllegalStateException("No se pudo guardar la calificación")))
                        }
                    }
                })
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun yaCalifico(
        ordenId: String,
        autorId: String,
        usuarioCalificadoId: String,
        onComplete: (Result<Boolean>) -> Unit,
    ) {
        if (auth.currentUser?.uid != autorId || ordenId.isBlank() || usuarioCalificadoId.isBlank()) {
            onComplete(Result.failure(SecurityException("Participantes no válidos")))
            return
        }
        database.getReference("Calificaciones")
            .child(usuarioCalificadoId)
            .child("${ordenId}_$autorId")
            .get()
            .addOnSuccessListener { onComplete(Result.success(it.exists())) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun cargarResumen(usuarioId: String, onComplete: (Result<ResumenCalificaciones>) -> Unit) {
        if (usuarioId.isBlank()) {
            onComplete(Result.failure(IllegalArgumentException("Usuario no válido")))
            return
        }
        database.getReference("Calificaciones").child(usuarioId).get()
            .addOnSuccessListener { snapshot ->
                val estrellas = snapshot.children.mapNotNull {
                    if (it.child("reportada").getValue(Boolean::class.java) == true) return@mapNotNull null
                    it.child("estrellas").getValue(Int::class.java)?.takeIf { valor -> valor in 1..5 }
                }
                val promedio = if (estrellas.isEmpty()) 0.0 else estrellas.average()
                onComplete(Result.success(ResumenCalificaciones(promedio, estrellas.size)))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }
}
