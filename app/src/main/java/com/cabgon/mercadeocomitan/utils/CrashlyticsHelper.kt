package com.cabgon.mercadeocomitan.utils

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Helper para registrar eventos y errores en Firebase Crashlytics
 *
 * Uso:
 * - CrashlyticsHelper.logEvent("anuncio_publicado", "id_123")
 * - CrashlyticsHelper.logError(exception, "Error al cargar anuncios")
 */
object CrashlyticsHelper {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    /**
     * Registra un evento personalizado en Crashlytics
     * @param eventName Nombre del evento (ej: "anuncio_creado")
     * @param details Detalles opcionales del evento
     */
    fun logEvent(eventName: String, details: String? = null) {
        val message = if (details != null) {
            "$eventName: $details"
        } else {
            eventName
        }
        crashlytics.log(message)
    }

    /**
     * Registra una excepción con contexto adicional
     * @param exception La excepción a registrar
     * @param context Contexto donde ocurrió (ej: "FragmentInicio.cargarAnuncios()")
     */
    fun logError(exception: Exception, context: String) {
        crashlytics.log("Error en $context")
        crashlytics.recordException(exception)
    }

    /**
     * Registra información del usuario actual (para debug)
     * @param userId ID único del usuario
     * @param email Email del usuario (opcional)
     */
    fun setUserInfo(userId: String, email: String? = null) {
        crashlytics.setUserId(userId)
        email?.let {
            crashlytics.setCustomKey("user_email", it)
        }
    }

    /**
     * Registra un custom key para debugging
     * @param key Nombre de la clave
     * @param value Valor asociado
     */
    fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Fuerza un crash para testing (solo para desarrollo)
     * ⚠️ NO USAR EN PRODUCCIÓN
     */
    fun forceCrashForTesting() {
        crashlytics.log("⚠️ Test crash forzado")
        throw RuntimeException("Test crash - Firebase Crashlytics")
    }

    /**
     * Registra una acción del usuario
     * @param action Descripción de la acción (ej: "crear_anuncio", "buscar_producto")
     */
    fun logUserAction(action: String) {
        crashlytics.log("User action: $action")
    }

    /**
     * Registra métricas de rendimiento
     * @param operation Nombre de la operación
     * @param duration Duración en milisegundos
     */
    fun logPerformance(operation: String, duration: Long) {
        crashlytics.log("Performance: $operation took ${duration}ms")
        crashlytics.setCustomKey("perf_$operation", duration)
    }
}
