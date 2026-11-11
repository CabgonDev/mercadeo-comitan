package com.cabgon.mercadeocomitan.Fragmentos

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorAnuncio
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.ModeloAnuncio
import com.cabgon.mercadeocomitan.databinding.FragmentInicioBinding
import com.google.android.gms.ads.*
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.cabgon.mercadeocomitan.utils.NetworkUtils
import com.cabgon.mercadeocomitan.utils.MessageUtils
import com.cabgon.mercadeocomitan.utils.PreferencesManager

class FragmentInicio : Fragment() {

    private lateinit var binding: FragmentInicioBinding

    private companion object {
        private const val MAX_DISTANCIA_MOSTRAR_ANUNCIO = 40 // km
        private const val LAT_COMITAN = 16.206656
        private const val LONG_COMITAN = -92.11078
        private const val LIMITE_INICIALES = 30 // Reducido de 120 a 30 para paginación
        private const val LIMITE_POR_PAGINA = 20 // Items por cada carga adicional
    }

    private lateinit var mContext: Context
    private lateinit var locacionSP: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    private var actualLatitude = LAT_COMITAN
    private var actualLongitude = LONG_COMITAN
    private var actualDireccion = "Comitán"
    private var usarUbicacionComitan = true // true = filtrar desde Comitán; false = desde ubicación real

    private var anuncioArrayList = arrayListOf<ModeloAnuncio>()
    private var adaptadorAnuncio: AdaptadorAnuncio? = null

    // Paginación
    private var ultimoKeyVisible: String? = null
    private var estaCargandoMas = false
    private var hayMasAnuncios = true

    // RT updates
    private var anunciosRef: Query? = null
    private var anunciosListener: ChildEventListener? = null
    private var versionConsulta = 0L
    private var bannerAd: AdView? = null

    private var isViewAlive = false
    private var ubicacionMonitoreada = false
    private var categoriaSeleccionada = "Todos"
    private var headerBasePaddingTop = 0

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (!isAdded || !isViewAlive) return@registerForActivityResult

        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        when {
            granted -> {
                mostrarSnackbar("Permiso de ubicación concedido ✅", android.R.color.holo_green_dark)
                obtenerUbicacionActual()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                mostrarExplicacionPermiso()
            }
            else -> {
                mostrarDialogoConfiguracion()
                usarUbicacionComitan = true
                mostrarMensajeAdvertencia("Usaremos publicaciones de Comitán. Puedes activar la ubicación desde Ajustes.")
                cargarAnunciosInicial("Todos")
            }
        }
    }

    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!isAdded || !isViewAlive) return@registerForActivityResult

        val fine = ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            mostrarSnackbar("Permiso de ubicación activado ✅", android.R.color.holo_green_dark)
            obtenerUbicacionActual()
        } else {
            mostrarSnackbar("Aún no se ha otorgado el permiso ⚠️", android.R.color.holo_orange_light)
            usarUbicacionComitan = true
            mostrarMensajeAdvertencia("Usaremos publicaciones de Comitán mientras la ubicación esté desactivada.")
            cargarAnunciosInicial("Todos")
        }
    }

    override fun onAttach(context: Context) {
        mContext = context
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentInicioBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewAlive = true

        headerBasePaddingTop = binding.homeMarketplaceHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            if (!isViewAlive) return@setOnApplyWindowInsetsListener insets
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val sharedToolbarVisible = activity?.findViewById<View>(com.cabgon.mercadeocomitan.R.id.ToolbarRl)
                ?.visibility == View.VISIBLE
            val topInset = if (sharedToolbarVisible) 0 else bars.top
            binding.homeMarketplaceHeader.setPadding(
                binding.homeMarketplaceHeader.paddingLeft,
                headerBasePaddingTop + topInset,
                binding.homeMarketplaceHeader.paddingRight,
                binding.homeMarketplaceHeader.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        MobileAds.initialize(mContext) {}
        cargarBannerAd()

        locacionSP = mContext.getSharedPreferences("LOACACION_SP", Context.MODE_PRIVATE)
        actualDireccion = locacionSP.getString("ACTUAL_DIRECCION", "Comitán")
            ?: "Comitán"
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext)

        // Usar PreferencesManager para cargar última ubicación guardada
        val prefs = PreferencesManager.getInstance(mContext)
        prefs.getLastLocation()?.let { (lat, lon) ->
            actualLatitude = lat
            actualLongitude = lon
            Log.d("FragmentInicio", "Ubicación cargada desde preferencias: $lat, $lon")
        }

        // Monitorear estado de red
        viewLifecycleOwner.lifecycleScope.launch {
            NetworkUtils.observeNetworkState(mContext).collect { isConnected ->
                if (!isConnected && isViewAlive) {
                    MessageUtils.showWarning(
                        binding.root,
                        "Sin conexión. Mostrando datos en caché."
                    )
                }
            }
        }

        cargarCategorias()
        configurarBuscador()

        mostrarShimmer(true)
        verificarPermisosUbicacion()
    }

    override fun onResume() {
        super.onResume()
        bannerAd?.resume()
        if (ubicacionMonitoreada) {
            iniciarActualizacionUbicacion()
        }
    }

    override fun onPause() {
        bannerAd?.pause()
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        super.onPause()
    }

    override fun onDestroyView() {
        versionConsulta++ // Invalida respuestas de Firebase ya obsoletas.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, null)
        bannerAd?.destroy()
        bannerAd = null
        binding.adViewContainer.removeAllViews()
        binding.shimmerLayout.stopShimmer()
        isViewAlive = false
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        detachAnunciosListener()
        adaptadorAnuncio = null
        super.onDestroyView()
    }

    // ================== PERMISOS ==================
    private fun verificarPermisosUbicacion() {
        val fine = ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION)

        when {
            fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED -> obtenerUbicacionActual()
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> mostrarExplicacionPermiso()
            else -> locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    private fun mostrarExplicacionPermiso() {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Permiso de ubicación requerido")
            .setMessage("Mercadeo Comitán necesita acceder a tu ubicación para mostrar publicaciones cercanas.")
            .setPositiveButton("Conceder permiso") { _, _ ->
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            }
            .setNegativeButton("Cancelar") { _, _ ->
                usarUbicacionComitan = true
                mostrarMensajeAdvertencia("No se concedió el permiso. Se mostrarán publicaciones de Comitán.")
                cargarAnunciosInicial("Todos")
            }
            .setCancelable(false)
            .show()
    }

    private fun mostrarDialogoConfiguracion() {
        if (!isAdded) return
        val snackbar = Snackbar.make(binding.root, "Permiso de ubicación bloqueado ❌", Snackbar.LENGTH_LONG)
            .setAction("Ir a ajustes") {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                appSettingsLauncher.launch(intent)
            }
        snackbar.view.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
        snackbar.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snackbar.show()
    }

    // ================== UBICACIÓN ==================
    @SuppressLint("MissingPermission")
    private fun obtenerUbicacionActual() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (!isViewAlive) return@addOnSuccessListener

            if (location != null) {
                actualLatitude = location.latitude
                actualLongitude = location.longitude

                // Guardar ubicación en PreferencesManager
                val prefs = PreferencesManager.getInstance(mContext)
                prefs.saveLastLocation(actualLatitude, actualLongitude)

                val distancia = calcularDistanciaKm(actualLatitude, actualLongitude, LAT_COMITAN, LONG_COMITAN)
                usarUbicacionComitan = distancia > MAX_DISTANCIA_MOSTRAR_ANUNCIO

                if (usarUbicacionComitan) {
                    mostrarMensajeAdvertencia("Te encuentras fuera de Comitán. Solo se mostrarán publicaciones dentro o cerca de Comitán.")
                } else {
                    ocultarMensajeAdvertencia()
                }
            } else {
                usarUbicacionComitan = true
                mostrarMensajeAdvertencia("No pudimos determinar tu ubicación. Se mostrarán publicaciones cercanas en Comitán.")
            }

            guardarUbicacion()
            iniciarActualizacionUbicacion()
            cargarAnunciosInicial("Todos")
        }.addOnFailureListener {
            if (!isViewAlive) return@addOnFailureListener
            usarUbicacionComitan = true
            mostrarMensajeAdvertencia("No pudimos determinar tu ubicación. Se mostrarán publicaciones cercanas en Comitán.")
            cargarAnunciosInicial("Todos")
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarActualizacionUbicacion() {
        val fine = ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return

        ubicacionMonitoreada = true
        if (!::locationRequest.isInitialized) {
            locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10000)
                .setMinUpdateDistanceMeters(50f).build()
        }

        if (!::locationCallback.isInitialized) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (!isViewAlive) return
                    val loc = result.lastLocation ?: return
                    actualLatitude = loc.latitude
                    actualLongitude = loc.longitude

                    val distancia = calcularDistanciaKm(actualLatitude, actualLongitude, LAT_COMITAN, LONG_COMITAN)
                    usarUbicacionComitan = distancia > MAX_DISTANCIA_MOSTRAR_ANUNCIO

                    if (usarUbicacionComitan) {
                        mostrarMensajeAdvertencia("Te encuentras fuera de Comitán. Solo se mostrarán publicaciones dentro o cerca de Comitán.")
                    } else {
                        ocultarMensajeAdvertencia()
                    }

                    guardarUbicacion()
                }
            }
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    // ================== CATEGORÍAS ==================
    private fun cargarCategorias() {
        cargarCategoriasTabs()
    }

    private fun cargarCategoriasTabs() {
        binding.categoriasTabsContainer.removeAllViews()
        binding.categoriasTabsScroll.scrollTo(0, 0)
        Constantes.categorias.forEach { categoria ->
            val tab = TextView(mContext).apply {
                text = categoria
                gravity = Gravity.CENTER
                minHeight = dp(34)
                setPadding(dp(14), 0, dp(14), 0)
                setTextSize(12f)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                setOnClickListener {
                    categoriaSeleccionada = categoria
                    actualizarTabCategoria()
                    cargarAnunciosInicial(categoria)
                }
            }
            binding.categoriasTabsContainer.addView(tab)
        }
        binding.categoriasTabsScroll.post { binding.categoriasTabsScroll.scrollTo(0, 0) }
        actualizarTabCategoria()
    }

    private fun actualizarTabCategoria() {
        for (index in 0 until binding.categoriasTabsContainer.childCount) {
            val tab = binding.categoriasTabsContainer.getChildAt(index) as? TextView ?: continue
            val selected = tab.text.toString().equals(categoriaSeleccionada, ignoreCase = true)
            tab.background = null
            tab.paintFlags = if (selected) {
                tab.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            } else {
                tab.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }
            tab.setTypeface(
                Typeface.create(
                    "sans-serif-medium",
                    if (selected) Typeface.BOLD else Typeface.NORMAL
                )
            )
            tab.setTextColor(
                ContextCompat.getColor(
                    mContext,
                    if (selected) com.cabgon.mercadeocomitan.R.color.white else com.cabgon.mercadeocomitan.R.color.brand_navy
                )
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    // ================== ANUNCIOS ==================
    private fun mostrarShimmer(mostrar: Boolean) {
        if (!isViewAlive) return
        binding.shimmerLayout.isVisible = mostrar
        val tieneResultados = (adaptadorAnuncio?.itemCount ?: 0) > 0
        binding.anunciosRv.isVisible = !mostrar && tieneResultados
        binding.tvEstadoCatalogo.isVisible = !mostrar && !tieneResultados
        if (mostrar) binding.shimmerLayout.startShimmer() else binding.shimmerLayout.stopShimmer()
    }

    private fun cargarAnunciosInicial(categoria: String) {
        if (!isViewAlive) return
        val estaConsulta = ++versionConsulta
        mostrarShimmer(true)
        detachAnunciosListener()

        // Resetear variables de paginación
        ultimoKeyVisible = null
        estaCargandoMas = false
        hayMasAnuncios = true

        if (adaptadorAnuncio == null) {
            adaptadorAnuncio = AdaptadorAnuncio(mContext, anuncioArrayList).also { adapter ->
                adapter.onResultsChanged = { cantidad ->
                    if (isViewAlive && !binding.shimmerLayout.isVisible) {
                        val hayResultados = cantidad > 0
                        binding.anunciosRv.isVisible = hayResultados
                        binding.tvEstadoCatalogo.isVisible = !hayResultados
                        if (!hayResultados) {
                            binding.tvEstadoCatalogo.text = if (binding.EtBuscar.text.isNullOrBlank()) {
                                getString(com.cabgon.mercadeocomitan.R.string.home_empty_catalog)
                            } else {
                                getString(com.cabgon.mercadeocomitan.R.string.home_empty_search)
                            }
                        }
                    }
                }
            }
            binding.anunciosRv.adapter = adaptadorAnuncio

            // Configurar scroll listener para paginación
            binding.anunciosRv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    // Solo cargar si estamos scrolleando hacia abajo
                    if (dy > 0) {
                        val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                        layoutManager?.let { lm ->
                            val totalItems = lm.itemCount
                            val lastVisible = lm.findLastVisibleItemPosition()

                            // Cargar más cuando estamos a 5 items del final
                            if (!estaCargandoMas && hayMasAnuncios && (lastVisible >= totalItems - 5)) {
                                cargarMasAnuncios(categoria)
                            }
                        }
                    }
                }
            })
        }

        val refBase = FirebaseDatabase.getInstance().getReference("Anuncios")
        val query = refBase.orderByChild("tiempo").limitToLast(LIMITE_INICIALES)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isViewAlive || estaConsulta != versionConsulta) return
                anuncioArrayList.clear()

                val (latRef, lonRef) = if (usarUbicacionComitan) {
                    LAT_COMITAN to LONG_COMITAN
                } else actualLatitude to actualLongitude

                for (ds in snapshot.children.reversed()) {
                    val m = parseAnuncio(ds) ?: continue
                    if (!pasaFiltros(m, categoria, latRef, lonRef)) continue
                    anuncioArrayList.add(m)
                }

                refrescarResultados()
                mostrarShimmer(false)

                // Guardar el primer key visible para paginación
                if (snapshot.childrenCount > 0) {
                    ultimoKeyVisible = snapshot.children.first().key
                    hayMasAnuncios = snapshot.childrenCount.toInt() >= LIMITE_INICIALES
                }

                // Adjunta RT sobre la misma ventana temporal
                adjuntarCambiosTiempoReal(refBase.orderByChild("tiempo").limitToLast(LIMITE_INICIALES), categoria, latRef, lonRef, estaConsulta)
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isViewAlive || estaConsulta != versionConsulta) return
                mostrarShimmer(false)
            }
        })
    }

    /**
     * Carga más anuncios cuando el usuario hace scroll hacia abajo (paginación)
     */
    private fun cargarMasAnuncios(categoria: String) {
        if (!isViewAlive || estaCargandoMas || !hayMasAnuncios || ultimoKeyVisible == null) return

        estaCargandoMas = true
        Log.d("FragmentInicio", "Cargando más anuncios... ultimoKey=$ultimoKeyVisible")

        val (latRef, lonRef) = if (usarUbicacionComitan) {
            LAT_COMITAN to LONG_COMITAN
        } else actualLatitude to actualLongitude

        val refBase = FirebaseDatabase.getInstance().getReference("Anuncios")
        val query = refBase.orderByChild("tiempo")
            .endBefore(null, ultimoKeyVisible)
            .limitToLast(LIMITE_POR_PAGINA)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isViewAlive) return

                val nuevosAnuncios = mutableListOf<ModeloAnuncio>()

                for (ds in snapshot.children.reversed()) {
                    // Evitar duplicados
                    if (anuncioArrayList.any { it.id == ds.key }) continue

                    val m = parseAnuncio(ds) ?: continue
                    if (!pasaFiltros(m, categoria, latRef, lonRef)) continue
                    nuevosAnuncios.add(m)
                }

                if (nuevosAnuncios.isNotEmpty()) {
                    anuncioArrayList.addAll(nuevosAnuncios)
                    adaptadorAnuncio?.notifyItemRangeInserted(
                        anuncioArrayList.size - nuevosAnuncios.size,
                        nuevosAnuncios.size
                    )

                    // Actualizar el último key visible
                    ultimoKeyVisible = snapshot.children.firstOrNull()?.key
                    Log.d("FragmentInicio", "Cargados ${nuevosAnuncios.size} anuncios más")
                } else {
                    Log.d("FragmentInicio", "No hay más anuncios después de filtros")
                }

                // Si obtuvimos menos de lo esperado, probablemente no hay más
                hayMasAnuncios = snapshot.childrenCount.toInt() >= LIMITE_POR_PAGINA

                estaCargandoMas = false
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isViewAlive) return
                estaCargandoMas = false
                Log.e("FragmentInicio", "Error cargando más anuncios: ${error.message}")
                MessageUtils.showError(binding.root, "Error cargando más anuncios")
            }
        })
    }

    private fun adjuntarCambiosTiempoReal(query: Query, categoria: String, latRef: Double, lonRef: Double, consulta: Long) {
        anunciosRef = query
        anunciosListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (!isViewAlive || consulta != versionConsulta) return
                val m = parseAnuncio(snapshot) ?: return
                if (!pasaFiltros(m, categoria, latRef, lonRef)) return

                val idx = anuncioArrayList.indexOfFirst { it.id == m.id }
                if (idx == -1) {
                    anuncioArrayList.add(0, m)
                } else {
                    anuncioArrayList[idx] = m
                }
                refrescarResultados()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                if (!isViewAlive || consulta != versionConsulta) return
                val m = parseAnuncio(snapshot) ?: return
                val idx = anuncioArrayList.indexOfFirst { it.id == m.id }
                if (!pasaFiltros(m, categoria, latRef, lonRef)) {
                    if (idx != -1) {
                        anuncioArrayList.removeAt(idx)
                        refrescarResultados()
                    }
                    return
                }
                if (idx != -1) {
                    anuncioArrayList[idx] = m
                } else {
                    anuncioArrayList.add(0, m)
                }
                refrescarResultados()
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                if (!isViewAlive || consulta != versionConsulta) return
                val id = snapshot.child("id").getValue(String::class.java) ?: return
                val idx = anuncioArrayList.indexOfFirst { it.id == id }
                if (idx != -1) {
                    anuncioArrayList.removeAt(idx)
                    refrescarResultados()
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        anunciosRef?.addChildEventListener(anunciosListener as ChildEventListener)
    }

    private fun detachAnunciosListener() {
        anunciosListener?.let { l -> anunciosRef?.removeEventListener(l) }
        anunciosListener = null
        anunciosRef = null
    }

    private fun pasaFiltros(m: ModeloAnuncio, categoria: String, latRef: Double, lonRef: Double): Boolean {
        if (categoria != "Todos" && !m.categoria.equals(categoria, true)) return false
        if (!m.latitud.isFinite() || !m.longitud.isFinite() ||
            (m.latitud == 0.0 && m.longitud == 0.0)
        ) return true
        val distKm = calcularDistanciaKm(m.latitud, m.longitud, latRef, lonRef)
        if (distKm > MAX_DISTANCIA_MOSTRAR_ANUNCIO) return false
        return true
    }

    private fun parseAnuncio(ds: DataSnapshot): ModeloAnuncio? {
        val d = ds.value as? Map<*, *> ?: return null
        return try {
            ModeloAnuncio(
                id = d["id"]?.toString() ?: "",
                uid = d["uid"]?.toString() ?: "",
                marca = d["marca"]?.toString() ?: "",
                categoria = d["categoria"]?.toString() ?: "",
                condicion = d["condicion"]?.toString() ?: "",
                direccion = d["direccion"]?.toString() ?: "",
                precio = d["precio"]?.toString() ?: "",
                titulo = d["titulo"]?.toString() ?: "",
                descripcion = d["descripcion"]?.toString() ?: "",
                estado = d["estado"]?.toString() ?: "",
                tiempo = d["tiempo"]?.toString()?.toLongOrNull() ?: 0L,
                latitud = d["latitud"]?.toString()?.toDoubleOrNull() ?: 0.0,
                longitud = d["longitud"]?.toString()?.toDoubleOrNull() ?: 0.0,
                favorito = d["favorito"]?.toString()?.toBoolean() ?: false,
                contadorVistas = d["contadorVistas"]?.toString()?.toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            Log.e("ParseAnuncio", "Error: ${e.message}")
            null
        }
    }

    private fun calcularDistanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val loc1 = Location(LocationManager.NETWORK_PROVIDER).apply { latitude = lat1; longitude = lon1 }
        val loc2 = Location(LocationManager.NETWORK_PROVIDER).apply { latitude = lat2; longitude = lon2 }
        return loc1.distanceTo(loc2) / 1000.0
    }

    // ================== UI AUX ==================
    private fun mostrarSnackbar(mensaje: String, color: Int) {
        if (!isAdded) return
        val snackbar = Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG)
        val view = snackbar.view
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), color))
        snackbar.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snackbar.show()
    }

    private fun mostrarMensajeAdvertencia(msg: String) {
        if (!isViewAlive) return
        binding.tvUbicacionAdvertencia.text = msg
        if (!binding.tvUbicacionAdvertencia.isVisible) {
            binding.tvUbicacionAdvertencia.apply {
                alpha = 0f
                isVisible = true
                animate().alpha(1f).setDuration(300).start()
            }
        }
    }

    private fun ocultarMensajeAdvertencia() {
        if (!isViewAlive) return
        if (binding.tvUbicacionAdvertencia.isVisible) {
            binding.tvUbicacionAdvertencia.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction { if (isViewAlive) binding.tvUbicacionAdvertencia.isVisible = false }
                .start()
        }
    }

    private fun guardarUbicacion() {
        locacionSP.edit {
            putFloat("ACTUAL_LATITUD", actualLatitude.toFloat())
            putFloat("ACTUAL_LONGITUD", actualLongitude.toFloat())
            putString("ACTUAL_DIRECCION", actualDireccion)
        }
    }

    // ================== ADS ==================
    private fun cargarBannerAd() {
        bannerAd?.destroy()
        val adView = AdView(mContext)
        bannerAd = adView
        adView.adUnitId = "ca-app-pub-7205378719919520/4714839549"
        adView.setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(mContext, 360))
        binding.adViewContainer.removeAllViews()
        binding.adViewContainer.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun configurarBuscador() {
        binding.EtBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(txt: CharSequence?, start: Int, before: Int, count: Int) {
                binding.IbLimpiar.isVisible = !txt.isNullOrBlank()
                adaptadorAnuncio?.filter?.filter(txt.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.IbLimpiar.setOnClickListener { binding.EtBuscar.text.clear() }
    }

    /** Reaplica la búsqueda sobre la lista fuente después de cualquier cambio RTDB. */
    private fun refrescarResultados() {
        adaptadorAnuncio?.filter?.filter(binding.EtBuscar.text?.toString().orEmpty())
    }
}
