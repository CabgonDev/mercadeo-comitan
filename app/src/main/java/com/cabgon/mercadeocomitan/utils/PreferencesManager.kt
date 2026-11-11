package com.cabgon.mercadeocomitan.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Utilidad para manejo seguro de preferencias locales
 */
class PreferencesManager private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "mercadeo_comitan_prefs"
        private const val SECURE_PREFS_NAME = "mercadeo_comitan_secure_prefs"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }

        // Keys para preferencias comunes
        const val KEY_FIRST_LAUNCH = "first_launch"
        const val KEY_LAST_LOCATION_LAT = "last_location_lat"
        const val KEY_LAST_LOCATION_LON = "last_location_lon"
        const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LANGUAGE = "language"
    }

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Suppress("DEPRECATION")
    private val securePreferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ===== Operaciones Normales =====

    fun putString(key: String, value: String) {
        sharedPreferences.edit { putString(key, value) }
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    fun putInt(key: String, value: Int) {
        sharedPreferences.edit { putInt(key, value) }
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        sharedPreferences.edit { putLong(key, value) }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        sharedPreferences.edit { putBoolean(key, value) }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun putFloat(key: String, value: Float) {
        sharedPreferences.edit { putFloat(key, value) }
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        return sharedPreferences.getFloat(key, defaultValue)
    }

    // ===== Operaciones Seguras (Encriptadas) =====

    fun putSecureString(key: String, value: String) {
        securePreferences.edit { putString(key, value) }
    }

    fun getSecureString(key: String, defaultValue: String = ""): String {
        return securePreferences.getString(key, defaultValue) ?: defaultValue
    }

    fun putSecureBoolean(key: String, value: Boolean) {
        securePreferences.edit { putBoolean(key, value) }
    }

    fun getSecureBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return securePreferences.getBoolean(key, defaultValue)
    }

    // ===== Utilidades =====

    fun remove(key: String) {
        sharedPreferences.edit { remove(key) }
    }

    fun removeSecure(key: String) {
        securePreferences.edit { remove(key) }
    }

    fun clear() {
        sharedPreferences.edit { clear() }
    }

    fun clearSecure() {
        securePreferences.edit { clear() }
    }

    fun contains(key: String): Boolean {
        return sharedPreferences.contains(key)
    }

    fun containsSecure(key: String): Boolean {
        return securePreferences.contains(key)
    }

    // ===== Helpers específicos de la app =====

    fun isFirstLaunch(): Boolean {
        return getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchComplete() {
        putBoolean(KEY_FIRST_LAUNCH, false)
    }

    fun saveLastLocation(latitude: Double, longitude: Double) {
        putFloat(KEY_LAST_LOCATION_LAT, latitude.toFloat())
        putFloat(KEY_LAST_LOCATION_LON, longitude.toFloat())
    }

    fun getLastLocation(): Pair<Double, Double>? {
        val lat = getFloat(KEY_LAST_LOCATION_LAT, Float.MAX_VALUE)
        val lon = getFloat(KEY_LAST_LOCATION_LON, Float.MAX_VALUE)

        return if (lat != Float.MAX_VALUE && lon != Float.MAX_VALUE) {
            Pair(lat.toDouble(), lon.toDouble())
        } else {
            null
        }
    }

    fun areNotificationsEnabled(): Boolean {
        return getBoolean(KEY_NOTIFICATION_ENABLED, true)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        putBoolean(KEY_NOTIFICATION_ENABLED, enabled)
    }
}
