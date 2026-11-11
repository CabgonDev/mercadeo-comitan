package com.cabgon.mercadeocomitan.utils

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

/**
 * Utilidades para mostrar mensajes al usuario de forma consistente
 */
object MessageUtils {

    /**
     * Muestra un Snackbar de éxito
     */
    fun showSuccess(view: View, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(view, message, duration).apply {
            setBackgroundTint(ContextCompat.getColor(view.context, android.R.color.holo_green_dark))
            setTextColor(ContextCompat.getColor(view.context, android.R.color.white))
            show()
        }
    }

    /**
     * Muestra un Snackbar de error
     */
    fun showError(view: View, message: String, duration: Int = Snackbar.LENGTH_LONG) {
        Snackbar.make(view, message, duration).apply {
            setBackgroundTint(ContextCompat.getColor(view.context, android.R.color.holo_red_dark))
            setTextColor(ContextCompat.getColor(view.context, android.R.color.white))
            show()
        }
    }

    /**
     * Muestra un Snackbar de advertencia
     */
    fun showWarning(view: View, message: String, duration: Int = Snackbar.LENGTH_LONG) {
        Snackbar.make(view, message, duration).apply {
            setBackgroundTint(ContextCompat.getColor(view.context, android.R.color.holo_orange_dark))
            setTextColor(ContextCompat.getColor(view.context, android.R.color.white))
            show()
        }
    }

    /**
     * Muestra un Snackbar informativo
     */
    fun showInfo(view: View, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(view, message, duration).apply {
            setBackgroundTint(ContextCompat.getColor(view.context, android.R.color.holo_blue_dark))
            setTextColor(ContextCompat.getColor(view.context, android.R.color.white))
            show()
        }
    }

    /**
     * Muestra un Snackbar con acción
     */
    fun showWithAction(
        view: View,
        message: String,
        actionText: String,
        duration: Int = Snackbar.LENGTH_LONG,
        action: () -> Unit
    ) {
        Snackbar.make(view, message, duration).apply {
            setAction(actionText) { action() }
            show()
        }
    }

    /**
     * Muestra un Snackbar de sin conexión con opción de reintentar
     */
    fun showNoConnection(view: View, onRetry: () -> Unit) {
        Snackbar.make(view, "Sin conexión a internet", Snackbar.LENGTH_INDEFINITE).apply {
            setBackgroundTint(ContextCompat.getColor(view.context, android.R.color.holo_red_dark))
            setTextColor(ContextCompat.getColor(view.context, android.R.color.white))
            setAction("Reintentar") {
                onRetry()
                dismiss()
            }
            show()
        }
    }
}

/**
 * Extensión para obtener mensajes de error amigables desde excepciones
 */
fun Exception.toUserFriendlyMessage(): String {
    return when {
        message?.contains("network", ignoreCase = true) == true ->
            "Error de conexión. Verifica tu internet."
        message?.contains("timeout", ignoreCase = true) == true ->
            "La operación tardó demasiado. Intenta de nuevo."
        message?.contains("permission", ignoreCase = true) == true ->
            "No tienes permisos para realizar esta acción."
        message?.contains("firebase", ignoreCase = true) == true ->
            "Error del servidor. Intenta más tarde."
        else ->
            "Ocurrió un error inesperado."
    }
}
