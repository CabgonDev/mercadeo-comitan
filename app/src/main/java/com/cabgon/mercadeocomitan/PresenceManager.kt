package com.cabgon.mercadeocomitan

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.atomic.AtomicBoolean

/** Tracks one process-wide presence and rearms it whenever RTDB reconnects. */
object PresenceManager {
    private const val OFFLINE_GRACE_MS = 700L
    private const val LOGOUT_WRITE_TIMEOUT_MS = 1500L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var installed = false
    private var startedActivities = 0
    private var connected = false
    private var currentUid: String? = null
    private var profileReadyUid: String? = null
    private var generation = 0L
    private var disconnectReadyUid: String? = null
    private var profileUidRef: DatabaseReference? = null
    private var profileUidListener: ValueEventListener? = null
    private var auth: FirebaseAuth? = null
    private var connectionRef: DatabaseReference? = null
    private var connectionListener: ValueEventListener? = null

    private val setOfflineAfterBackground = Runnable {
        if (startedActivities == 0) currentUid?.let(::writeOffline)
    }

    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
            if (startedActivities == 1) {
                mainHandler.removeCallbacks(setOfflineAfterBackground)
                publishOnlineWhenReady()
            }
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            if (startedActivities == 0) {
                mainHandler.removeCallbacks(setOfflineAfterBackground)
                mainHandler.postDelayed(setOfflineAfterBackground, OFFLINE_GRACE_MS)
            }
        }

        override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user?.uid == currentUid) return@AuthStateListener

        val previousUid = currentUid
        generation++
        currentUid = user?.uid
        profileReadyUid = null
        disconnectReadyUid = null
        removeProfileListener()
        if (previousUid != null) writeOffline(previousUid)
        if (user != null) observeProfile(user, generation)
    }

    private val connectedStateListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val isConnected = snapshot.getValue(Boolean::class.java) == true
            if (connected == isConnected) return

            connected = isConnected
            if (isConnected) {
                disconnectReadyUid = null
                publishOnlineWhenReady()
            } else {
                disconnectReadyUid = null
            }
        }

        override fun onCancelled(error: DatabaseError) {
            connected = false
            disconnectReadyUid = null
        }
    }

    fun install(application: Application) {
        if (installed) return
        installed = true
        application.registerActivityLifecycleCallbacks(callbacks)

        auth = FirebaseAuth.getInstance().also { it.addAuthStateListener(authStateListener) }
        connectionRef = FirebaseDatabase.getInstance().getReference(".info/connected").also {
            connectionListener = connectedStateListener
            it.addValueEventListener(connectedStateListener)
        }
    }

    fun setOffline(onComplete: () -> Unit = {}) {
        val uid = auth?.currentUser?.uid ?: FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank() || !connected) {
            onComplete()
            return
        }

        val completed = AtomicBoolean(false)
        val finish = { if (completed.compareAndSet(false, true)) onComplete() }
        val timeout = Runnable { finish() }
        mainHandler.postDelayed(timeout, LOGOUT_WRITE_TIMEOUT_MS)

        FirebaseDatabase.getInstance().reference.child("Usuarios").child(uid)
            .updateChildren(offlineValues())
            .addOnCompleteListener {
                mainHandler.removeCallbacks(timeout)
                finish()
            }
    }

    private fun observeProfile(user: FirebaseUser, expectedGeneration: Long) {
        val ref = FirebaseDatabase.getInstance().reference
            .child("Usuarios").child(user.uid).child("uid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (expectedGeneration != generation || currentUid != user.uid) return
                if (snapshot.getValue(String::class.java) != user.uid) return

                profileReadyUid = user.uid
                removeProfileListener()
                publishOnlineWhenReady()
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.w("Presence", "No se pudo verificar el perfil: ${error.message}")
            }
        }
        profileUidRef = ref
        profileUidListener = listener
        ref.addValueEventListener(listener)
    }

    private fun publishOnlineWhenReady() {
        val uid = currentUid ?: auth?.currentUser?.uid ?: return
        if (startedActivities == 0 || !connected) return
        if (profileReadyUid != uid) return
        if (profileUidListener != null) return

        val ref = FirebaseDatabase.getInstance().reference.child("Usuarios").child(uid)
        if (disconnectReadyUid == uid) {
            ref.updateChildren(onlineValues())
            return
        }

        val expectedGeneration = generation
        ref.onDisconnect().updateChildren(offlineValues())
            .addOnSuccessListener {
                if (expectedGeneration != generation || currentUid != uid ||
                    startedActivities == 0 || !connected
                ) return@addOnSuccessListener

                disconnectReadyUid = uid
                ref.updateChildren(onlineValues())
                    .addOnFailureListener { error ->
                        android.util.Log.w("Presence", "No se pudo publicar presencia: ${error.message}")
                    }
            }
            .addOnFailureListener { error ->
                android.util.Log.w("Presence", "No se pudo preparar onDisconnect: ${error.message}")
            }
    }

    private fun writeOffline(uid: String) {
        if (!connected) return
        FirebaseDatabase.getInstance().reference.child("Usuarios").child(uid)
            .updateChildren(offlineValues())
            .addOnFailureListener { error ->
                android.util.Log.w("Presence", "No se pudo publicar estado offline: ${error.message}")
            }
    }

    private fun onlineValues(): Map<String, Any> = mapOf(
        "online" to true,
        "estado" to "Online",
        "lastActive" to ServerValue.TIMESTAMP,
    )

    private fun offlineValues(): Map<String, Any> = mapOf(
        "online" to false,
        "estado" to "Offline",
        "lastActive" to ServerValue.TIMESTAMP,
    )

    private fun removeProfileListener() {
        val ref = profileUidRef
        val listener = profileUidListener
        if (ref != null && listener != null) ref.removeEventListener(listener)
        profileUidRef = null
        profileUidListener = null
    }
}
