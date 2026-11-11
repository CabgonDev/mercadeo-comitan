package com.cabgon.mercadeocomitan

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.ClearCredentialException
import com.google.firebase.auth.FirebaseAuth

object AuthManager {

    /** Cerrar sesión normal (Firebase + Google), limpia prefs y navega a login sin back-stack */
    fun logout(activity: Activity) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.uid

        val cerrarSesion = {
            auth.signOut()

            // Limpiar CredentialManager (Google SignOut)
            val credentialManager = CredentialManager.create(activity)
            credentialManager.clearCredentialStateAsync(
                ClearCredentialStateRequest(),
                null,
                ContextCompat.getMainExecutor(activity),
                object : CredentialManagerCallback<Void?, ClearCredentialException> {
                    override fun onResult(result: Void?) {
                        completarLogout(activity)
                    }

                    override fun onError(e: ClearCredentialException) {
                        completarLogout(activity)
                    }
                }
            )
        }

        // Guardar Offline antes de invalidar la sesión local. Si no hay red,
        // onDisconnect conserva la garantía en el servidor.
        if (uid != null) PresenceManager.setOffline(cerrarSesion) else cerrarSesion()
    }

    private fun completarLogout(activity: Activity) {
        // 4) Limpiar preferencias locales (ej. ubicación)
        activity.getSharedPreferences("LOACACION_SP", Context.MODE_PRIVATE).edit { clear() }

        // 5) Ir a login con back stack limpio
        val intent = Intent(activity, OpcionesLogin::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }

    /** “Cambiar de cuenta”: además revoca / limpia el acceso de Google */
    fun revokeAndLogout(activity: Activity) {
        logout(activity)
    }
}
