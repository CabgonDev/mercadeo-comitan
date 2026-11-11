package com.cabgon.mercadeocomitan.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utilidades para operaciones asíncronas y manejo de corutinas
 */
object CoroutineUtils {

    /**
     * Ejecuta una operación en IO thread y retorna el resultado
     */
    suspend fun <T> ioTask(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend () -> T
    ): T {
        return withContext(dispatcher) {
            block()
        }
    }

    /**
     * Ejecuta una operación en el hilo principal
     */
    suspend fun <T> mainTask(
        block: suspend () -> T
    ): T {
        return withContext(Dispatchers.Main) {
            block()
        }
    }

    /**
     * Ejecuta una operación con manejo de errores
     */
    suspend fun <T> safeCall(
        onError: (Exception) -> Unit = {},
        block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            onError(e)
            Result.failure(e)
        }
    }
}

/**
 * Envuelve una llamada suspendida en un Result para manejo seguro
 */
suspend inline fun <T> Result.Companion.suspendCatching(block: () -> T): Result<T> {
    return try {
        success(block())
    } catch (e: Exception) {
        failure(e)
    }
}
