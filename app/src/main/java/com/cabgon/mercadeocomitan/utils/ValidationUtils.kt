package com.cabgon.mercadeocomitan.utils

import android.util.Patterns
import java.util.regex.Pattern

/**
 * Utilidades de validación para formularios y datos de entrada
 */
object ValidationUtils {

    /**
     * Valida un email
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /**
     * Valida un número de teléfono (formato flexible)
     */
    fun isValidPhone(phone: String): Boolean {
        val cleaned = phone.replace(Regex("[\\s\\-()]+"), "")
        return cleaned.length in 10..15 && cleaned.all { it.isDigit() || it == '+' }
    }

    /**
     * Valida el formato de un precio
     */
    fun isValidPrice(price: String): Boolean {
        if (price.isBlank()) return false

        val cleaned = price.trim()
            .replace("$", "")
            .replace(",", "")
            .trim()

        return try {
            val value = cleaned.toDoubleOrNull()
            value != null && value > 0.0 && value.isFinite()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Valida que un texto tenga una longitud mínima y máxima
     */
    fun isValidLength(text: String, minLength: Int = 0, maxLength: Int = Int.MAX_VALUE): Boolean {
        val trimmed = text.trim()
        return trimmed.length in minLength..maxLength
    }

    /**
     * Valida que un texto no contenga caracteres peligrosos
     */
    fun isSafeText(text: String): Boolean {
        // Rechaza caracteres de control excepto espacios y saltos de línea
        return text.none { char ->
            char.code < 32 && char != '\n' && char != '\r' && char != '\t'
        }
    }

    /**
     * Sanitiza un texto removiendo caracteres potencialmente peligrosos
     */
    fun sanitizeText(text: String): String {
        return text.filter { char ->
            char.code >= 32 || char == '\n' || char == '\r' || char == '\t'
        }.trim()
    }

    /**
     * Valida coordenadas geográficas
     */
    fun isValidLatitude(lat: Double): Boolean {
        return lat.isFinite() && lat in -90.0..90.0
    }

    fun isValidLongitude(lon: Double): Boolean {
        return lon.isFinite() && lon in -180.0..180.0
    }

    /**
     * Valida que una URL sea HTTPS
     */
    fun isSecureUrl(url: String): Boolean {
        return url.startsWith("https://", ignoreCase = true) &&
            Patterns.WEB_URL.matcher(url).matches()
    }

    /**
     * Valida una contraseña (mínimo 6 caracteres, al menos una letra y un número)
     */
    fun isValidPassword(password: String): Boolean {
        if (password.length < 6) return false

        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }

        return hasLetter && hasDigit
    }

    /**
     * Valida el formato de un nombre (sin números ni caracteres especiales)
     */
    fun isValidName(name: String): Boolean {
        if (name.isBlank()) return false
        val pattern = Pattern.compile("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s'-]+$")
        return pattern.matcher(name.trim()).matches() && name.trim().length in 2..100
    }
}
