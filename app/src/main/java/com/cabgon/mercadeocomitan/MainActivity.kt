package com.cabgon.mercadeocomitan

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.cabgon.mercadeocomitan.Anuncios.CrearAnuncio
import com.cabgon.mercadeocomitan.Fragmentos.FragmentChats
import com.cabgon.mercadeocomitan.Fragmentos.FragmentCuenta
import com.cabgon.mercadeocomitan.Fragmentos.FragmentInicio
import com.cabgon.mercadeocomitan.Fragmentos.FragmentMisAnuncios
import com.cabgon.mercadeocomitan.billing.BillingHelper
import com.cabgon.mercadeocomitan.databinding.ActivityMainBinding
import com.cabgon.mercadeocomitan.databinding.BottomsheetProRestauradoBinding
import com.cabgon.mercadeocomitan.databinding.BottomsheetUpdateDisponibleBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.cabgon.mercadeocomitan.utils.NetworkUtils
import com.cabgon.mercadeocomitan.utils.MessageUtils
import com.cabgon.mercadeocomitan.utils.PreferencesManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth

    private var yaMostreSheetEstaSesion = false
    private var yaMostreRestoreEstaSesion = false
    private lateinit var appUpdateManager: AppUpdateManager
    private var yaMostreMensajeGlobalEstaSesion = false
    private var mensajeGlobalEnCarga = false
    private var mensajeGlobalDialog: AlertDialog? = null
    private var updateSheetDialog: BottomSheetDialog? = null
    private var restoreSheetDialog: BottomSheetDialog? = null
    private lateinit var billing: BillingHelper

    // para saber si ya contestó el permiso de notificaciones
    private var yaRespondioPermisoNotificacion = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mostrarToolbarCompartido(false)

        val toolbarBaseHeight = binding.ToolbarRl.layoutParams.height
        val toolbarBaseTop = binding.ToolbarRl.paddingTop
        val bottomNavBaseHeight = binding.BottomNV.layoutParams.height
        val fabBaseBottom = (binding.FAB.layoutParams as android.widget.RelativeLayout.LayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarParams = binding.ToolbarRl.layoutParams
            toolbarParams.height = toolbarBaseHeight + bars.top
            binding.ToolbarRl.layoutParams = toolbarParams
            binding.ToolbarRl.setPadding(
                binding.ToolbarRl.paddingLeft,
                toolbarBaseTop + bars.top,
                binding.ToolbarRl.paddingRight,
                binding.ToolbarRl.paddingBottom
            )

            val navParams = binding.BottomNV.layoutParams
            navParams.height = bottomNavBaseHeight + bars.bottom
            binding.BottomNV.layoutParams = navParams
            binding.BottomNV.setPadding(
                binding.BottomNV.paddingLeft,
                binding.BottomNV.paddingTop,
                binding.BottomNV.paddingRight,
                bars.bottom
            )

            val fabParams = binding.FAB.layoutParams as android.widget.RelativeLayout.LayoutParams
            fabParams.bottomMargin = fabBaseBottom + bars.bottom
            binding.FAB.layoutParams = fabParams
            insets
        }

        // 👇 FORZAMOS QUE EL MENSAJE QUEDE ARRIBA DE TODO
        binding.tvPermisosStatus.bringToFront()
        binding.tvPermisosStatus.elevation = 50f

        firebaseAuth = FirebaseAuth.getInstance()
        if (!comprobarSesion()) return

        // Monitorear estado de red en tiempo real
        lifecycleScope.launch {
            NetworkUtils.observeNetworkState(this@MainActivity).collect { isConnected ->
                if (!isConnected) {
                    MessageUtils.showNoConnection(binding.root) {
                        // El usuario presionó "Reintentar"
                        if (NetworkUtils.isNetworkAvailable(this@MainActivity)) {
                            MessageUtils.showSuccess(binding.root, "Conexión restaurada")
                        }
                    }
                }
            }
        }

        if (firebaseAuth.currentUser != null) {
            cargarMensajeGlobalSiAplica()
        }

        billing = BillingHelper(this, BillingHelper.PRODUCT_ID)
        billing.startConnection { ok ->
            if (!ok) return@startConnection
            billing.queryAndRestoreEntitlement { hasPurchase, wasGrantedNow ->
                if (hasPurchase) {
                    if (wasGrantedNow && !yaMostreRestoreEstaSesion) {
                        yaMostreRestoreEstaSesion = true
                        mostrarSheetProRestaurado()
                    }
                }
            }
        }

        // La ubicación la gestiona únicamente el catálogo. La actividad sólo solicita
        // la autorización independiente de notificaciones.
        solicitarPermisoNotificacion()

        binding.BottomNV.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.Item_Inicio -> { actualizarFab(true); verFragmentInicio(); true }
                R.id.Item_Chats -> { actualizarFab(false); verFragmentChats(); true }
                R.id.Item_Mis_Anuncios -> { actualizarFab(true); verFragmentMisAnuncios(); true }
                R.id.Item_Cuenta -> { actualizarFab(false); verFragmentCuenta(); true }
                else -> false
            }
        }

        actualizarFab(true)

        binding.FAB.setOnClickListener {
            val intent = Intent(this, CrearAnuncio::class.java)
            intent.putExtra("Edicion", false)
            startActivity(intent)
        }

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checarActualizacionConSheet()
    }

    private fun comprobarSesion(): Boolean {
        if (firebaseAuth.currentUser == null) {
            startActivity(Intent(this, OpcionesLogin::class.java))
            finishAffinity()
            return false
        } else {
            agregarFcmToken()
            return true
        }
    }

    private fun actualizarFab(mostrar: Boolean) {
        if (mostrar) {
            binding.FAB.show()
        } else {
            binding.FAB.hide()
        }
    }

    /** Inicio tiene su propio encabezado de catálogo; las demás secciones usan el toolbar compartido. */
    private fun mostrarToolbarCompartido(mostrar: Boolean) {
        binding.ToolbarRl.visibility = if (mostrar) android.view.View.VISIBLE else android.view.View.GONE
        binding.root.setBackgroundColor(
            ContextCompat.getColor(this, R.color.mi_color)
        )
        actualizarBarraEstado(mostrar)

        val params = binding.FragmentL1.layoutParams as android.widget.RelativeLayout.LayoutParams
        if (mostrar) {
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP, 0)
            params.addRule(android.widget.RelativeLayout.BELOW, R.id.ToolbarRl)
        } else {
            params.addRule(android.widget.RelativeLayout.BELOW, 0)
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP, android.widget.RelativeLayout.TRUE)
        }
        binding.FragmentL1.layoutParams = params

        // El toolbar cambia la geometría del contenedor. Redistribuir los insets
        // evita que FragmentInicio conserve el padding de la pestaña anterior.
        ViewCompat.requestApplyInsets(binding.root)
        binding.root.post { ViewCompat.requestApplyInsets(binding.root) }
    }

    @Suppress("DEPRECATION")
    private fun actualizarBarraEstado(mostrarToolbar: Boolean) {
        window.statusBarColor = ContextCompat.getColor(
            this,
            if (mostrarToolbar) R.color.md_theme_surface else R.color.mi_color
        )
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = mostrarToolbar
    }

    // ================== MENSAJE GLOBAL ==================
    private fun cargarMensajeGlobalSiAplica() {
        if (yaMostreMensajeGlobalEstaSesion || mensajeGlobalEnCarga) return
        mensajeGlobalEnCarga = true

        val ref = FirebaseDatabase.getInstance()
            .getReference("configApp")
            .child("mensajeGlobal")

        ref.get().addOnSuccessListener { snap ->
            if (!snap.exists()) return@addOnSuccessListener
            val activo = snap.child("activo").getValue(Boolean::class.java) ?: false
            if (!activo) return@addOnSuccessListener

            val titulo = snap.child("titulo").getValue(String::class.java) ?: "Aviso"
            val cuerpo = snap.child("cuerpo").getValue(String::class.java) ?: ""

            if (isFinishing || isDestroyed || mensajeGlobalDialog?.isShowing == true) return@addOnSuccessListener

            yaMostreMensajeGlobalEstaSesion = true
            mensajeGlobalDialog = AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(cuerpo)
                .setPositiveButton("OK", null)
                .create()
                .also { dialog ->
                    dialog.setOnDismissListener { mensajeGlobalDialog = null }
                    dialog.show()
                }
        }.addOnFailureListener {
            // Permitir reintento si la lectura falló.
        }.addOnCompleteListener {
            mensajeGlobalEnCarga = false
        }
    }

    private fun verFragmentInicio() {
        mostrarFragment("FragmentInicio", "Inicio") { FragmentInicio() }
    }

    private fun verFragmentChats() {
        mostrarFragment("FragmentChats", "Mensajes") { FragmentChats() }
    }

    private fun verFragmentMisAnuncios() {
        mostrarFragment("FragmentMisAnuncios", "Mis anuncios") { FragmentMisAnuncios() }
    }

    private fun verFragmentCuenta() {
        mostrarFragment("FragmentCuenta", "Cuenta") { FragmentCuenta() }
    }

    private fun mostrarFragment(
        tag: String,
        titulo: String,
        crear: () -> androidx.fragment.app.Fragment
    ) {
        mostrarToolbarCompartido(tag != "FragmentInicio")
        binding.TituloRl.text = titulo
        val manager = supportFragmentManager
        val destino = manager.findFragmentByTag(tag) ?: crear()

        // Hay varias pestañas montadas en el mismo contenedor para conservar su
        // estado. Ocultamos todas las demás explícitamente: findFragmentById()
        // puede devolver una pestaña antigua que ya está oculta.
        val transaccion = manager.beginTransaction().apply {
            manager.fragments
                .filter { it.id == binding.FragmentL1.id && it != destino && !it.isHidden }
                .forEach { hide(it) }

            if (destino.isAdded) {
                show(destino)
            } else {
                add(binding.FragmentL1.id, destino, tag)
            }
        }

        if (manager.isStateSaved) {
            transaccion.commitAllowingStateLoss()
        } else {
            // La navegación se dispara desde el hilo principal. Aplicar la
            // operación aquí evita que dos toques rápidos dejen dos pestañas visibles.
            transaccion.commitNow()
        }
    }

    private fun seleccionarInicioSiNecesario() {
        if (supportFragmentManager.findFragmentById(binding.FragmentL1.id) == null) {
            verFragmentInicio()
        }
    }

    // ================== FCM TOKEN ==================
    // Firebase Messaging mantiene esta API para obtener el registration token
    // que usa el backend FCM actual; se conserva hasta migrar el contrato remoto.
    @Suppress("DEPRECATION")
    private fun agregarFcmToken() {
        val uid = firebaseAuth.currentUser?.uid ?: return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isNullOrBlank()) {
                    FirebaseMessaging.getInstance().deleteToken()
                        .addOnCompleteListener {
                            FirebaseMessaging.getInstance().token.addOnSuccessListener { t2 ->
                                guardarToken(uid, t2)
                            }
                        }
                } else {
                    guardarToken(uid, token)
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FCM", "Error obteniendo FCM token: ${e.message}")
            }
    }

    private fun guardarToken(uid: String, token: String) {
        val ref = FirebaseDatabase.getInstance().getReference("Usuarios").child(uid)
        val tokenId = tokenId(token)
        ref.updateChildren(
            mapOf(
                // Campo legado para versiones antiguas del backend.
                "fcmToken" to token,
                "fcmTokens/$tokenId/token" to token,
                "fcmTokens/$tokenId/updatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
            )
        ).addOnFailureListener { error ->
            android.util.Log.w("FCM", "No se pudo guardar el token: ${error.message}")
        }
    }

    private fun tokenId(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    // ================== PERMISO DE NOTIFICACIÓN (AJUSTADO) ==================
    private fun solicitarPermisoNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val yaTieneNotif = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (yaTieneNotif) {
                yaRespondioPermisoNotificacion = true
                mostrarMensajePermisos(false)
                seleccionarInicioSiNecesario()
            } else {
                mostrarMensajePermisos(true)
                permisoNotificacion.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            yaRespondioPermisoNotificacion = true
            mostrarMensajePermisos(false)
            seleccionarInicioSiNecesario()
        }
    }

    private val permisoNotificacion =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            yaRespondioPermisoNotificacion = true
            mostrarMensajePermisos(false)
            seleccionarInicioSiNecesario()
        }

    // 👇 helper visual para “Configurando permisos...”
    private fun mostrarMensajePermisos(mostrar: Boolean) {
        val view = binding.tvPermisosStatus
        if (mostrar) {
            view.clearAnimation()
            view.alpha = 1f
            view.visibility = android.view.View.VISIBLE
            view.bringToFront()
            // si tienes las anims, las usamos; si no, no pasa nada
            try {
                val anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in)
                view.startAnimation(anim)
            } catch (_: Exception) {
                // sin anim, no pasa nada
            }
        } else {
            try {
                val anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_out)
                view.startAnimation(anim)
            } catch (_: Exception) {
                // nada
            }
            view.postDelayed({
                view.visibility = android.view.View.GONE
            }, 250)
        }
    }

    // ================== NUEVO CHECK DE ACTUALIZACIONES ==================
    private fun checarActualizacionConSheet() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                if (!yaMostreSheetEstaSesion) {
                    yaMostreSheetEstaSesion = true
                    mostrarBottomSheetUpdate()
                }
            }
        }
    }

    private fun mostrarBottomSheetUpdate() {
        if (isFinishing || isDestroyed || updateSheetDialog?.isShowing == true) return
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomsheetUpdateDisponibleBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.txtSubtitulo.text = getString(R.string.version_instalada, obtenerVersionActual())

        sheetBinding.btnActualizar.setOnClickListener {
            dialog.dismiss()
            abrirPlayStore()
        }

        sheetBinding.btnMasTarde.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener { updateSheetDialog = null }
        updateSheetDialog = dialog
        dialog.show()
    }


    private fun obtenerVersionActual(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "—"
        } catch (_: Exception) {
            "—"
        }
    }

    private fun abrirPlayStore() {
        val appPackageName = packageName
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "market://details?id=$appPackageName".toUri()
                )
            )
        } catch (_: ActivityNotFoundException) {
            runCatching {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=$appPackageName".toUri()
                    )
                )
            }
        }
    }

    private fun mostrarSheetProRestaurado() {
        if (isFinishing || isDestroyed || restoreSheetDialog?.isShowing == true) return
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomsheetProRestauradoBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        sheetBinding.btnOk.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { restoreSheetDialog = null }
        restoreSheetDialog = dialog
        dialog.show()
    }

    override fun onResume() {
        super.onResume()

        if (firebaseAuth.currentUser != null && !yaMostreMensajeGlobalEstaSesion) {
            cargarMensajeGlobalSiAplica()
        }

        if (!yaMostreSheetEstaSesion) {
            checarActualizacionConSheet()
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        mensajeGlobalDialog?.dismiss()
        mensajeGlobalDialog = null
        updateSheetDialog?.dismiss()
        updateSheetDialog = null
        restoreSheetDialog?.dismiss()
        restoreSheetDialog = null
        if (::billing.isInitialized) billing.endConnection()
        super.onDestroy()
    }
}
