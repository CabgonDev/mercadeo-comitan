package com.cabgon.mercadeocomitan

import android.Manifest
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cabgon.mercadeocomitan.databinding.ActivitySeleccionarUbicacionBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.widget.PlaceAutocomplete
import java.util.Locale
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.Executors

class SeleccionarUbicacion : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivitySeleccionarUbicacionBinding

    private companion object {
        private const val DEFAULT_ZOOM = 14.5f
        private val COMITAN = LatLng(16.251, -92.134)
    }

    private var mMap: GoogleMap? = null
    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null
    private var selectedLatitud: Double? = null
    private var selectedLongitud: Double? = null
    private var direccion = ""
    private var mapLoaded = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val geocoderExecutor = Executors.newSingleThreadExecutor()

    private val autocompleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data ?: return@registerForActivityResult
            val prediction = PlaceAutocomplete.getPredictionFromIntent(intent)
            if (prediction != null) {
                val placesClient = Places.createClient(this)
                val request = FetchPlaceRequest.newInstance(
                    prediction.placeId,
                    listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION)
                )
                placesClient.fetchPlace(request).addOnSuccessListener { response ->
                    val place = response.place
                    val latLng = place.location
                    if (latLng != null) {
                        selectedLatitud = latLng.latitude
                        selectedLongitud = latLng.longitude
                        direccion = place.formattedAddress.orEmpty().ifBlank { place.displayName.orEmpty() }
                        mostrarSeleccion(latLng, place.displayName.orEmpty(), direccion)
                    }
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "No se pudo obtener la información del lugar.", Toast.LENGTH_SHORT).show()
                    android.util.Log.w("SeleccionarUbicacion", "fetchPlace error: ${e.message}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySeleccionarUbicacionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbarBaseHeight = binding.ToolbarRL.layoutParams.height
        val bottomBaseMargin = (binding.listoLl.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootUbicacion) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarParams = binding.ToolbarRL.layoutParams
            toolbarParams.height = toolbarBaseHeight + bars.top
            binding.ToolbarRL.layoutParams = toolbarParams
            binding.ToolbarRL.setPadding(
                binding.ToolbarRL.paddingLeft,
                bars.top,
                binding.ToolbarRL.paddingRight,
                binding.ToolbarRL.paddingBottom
            )

            val bottomParams = binding.listoLl.layoutParams as android.view.ViewGroup.MarginLayoutParams
            bottomParams.bottomMargin = bottomBaseMargin + bars.bottom
            binding.listoLl.layoutParams = bottomParams
            insets
        }

        binding.listoLl.visibility = View.GONE

        runCatching {
            MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, null)
        }.onFailure {
            runCatching {
                MapsInitializer.initialize(applicationContext)
            }
        }

        val mapFragment = (supportFragmentManager.findFragmentById(R.id.MapFragment) as? SupportMapFragment)
            ?: SupportMapFragment.newInstance().also { fragment ->
                supportFragmentManager.beginTransaction()
                    .replace(R.id.MapFragment, fragment)
                    .commit()
            }
        mapFragment.getMapAsync(this)

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, getString(R.string.mi_google_maps_api_key))
        }
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        configurarAutocompletado()

        binding.IbGps.setOnClickListener {
            if (esGpsActivado()) {
                solicitarPermisoLocacion.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            } else {
                Toast.makeText(this, "Activa la ubicación del dispositivo para usar tu posición actual.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.BtnListo.setOnClickListener { devolverSeleccion() }
        mainHandler.postDelayed({
            if (!mapLoaded && !isFinishing) {
                binding.tvMapaEstado.text = "El mapa tarda en cargar. Puedes buscar una dirección o tocar el mapa."
                binding.tvMapaEstado.visibility = View.VISIBLE
            }
        }, 8_000L)
    }

    private fun configurarAutocompletado() {
        binding.btnBuscarLugar.setOnClickListener {
            val intent = PlaceAutocomplete.createIntent(this) {
                setCountries(listOf("MX"))
            }
            autocompleteLauncher.launch(intent)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(COMITAN, 12.5f))

        val fine = androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == android.content.pm.PackageManager.PERMISSION_GRANTED || coarse == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            runCatching { googleMap.isMyLocationEnabled = true }
        }

        googleMap.setOnMapLoadedCallback {
            mapLoaded = true
            binding.tvMapaEstado.visibility = View.GONE
        }
        googleMap.setOnMapClickListener { latLng ->
            selectedLatitud = latLng.latitude
            selectedLongitud = latLng.longitude
            direccion = "Ubicación seleccionada en Comitán"
            mostrarSeleccion(latLng, "Ubicación seleccionada", direccion)
            resolverDireccion(latLng)
        }
    }

    private fun mostrarSeleccion(latLng: LatLng, titulo: String, textoDireccion: String) {
        mMap?.let { map ->
            map.clear()
            map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(titulo.ifBlank { "Ubicación seleccionada" })
                    .snippet(textoDireccion)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
        }
        binding.lugarSelecTv.text = textoDireccion.ifBlank { "Ubicación seleccionada" }
        binding.listoLl.visibility = View.VISIBLE
    }

    // API asíncrona equivalente solo está disponible desde Android 13; en las
    // versiones mínimas soportadas se conserva la ruta síncrona de Geocoder.
    @Suppress("DEPRECATION")
    private fun resolverDireccion(latLng: LatLng) {
        if (!Geocoder.isPresent()) return
        geocoderExecutor.execute {
            val resultado = runCatching {
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(latLng.latitude, latLng.longitude, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
                    .orEmpty()
            }.getOrDefault("")
            if (resultado.isBlank() || isFinishing) return@execute
            mainHandler.post {
                if (selectedLatitud == latLng.latitude && selectedLongitud == latLng.longitude) {
                    direccion = resultado
                    binding.lugarSelecTv.text = resultado
                }
            }
        }
    }

    private fun devolverSeleccion() {
        val lat = selectedLatitud
        val lng = selectedLongitud
        if (lat == null || lng == null) {
            Toast.makeText(this, "Busca o toca el mapa para elegir una ubicación.", Toast.LENGTH_SHORT).show()
            return
        }
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra("latitud", lat)
                putExtra("longitud", lng)
                putExtra("direccion", direccion.ifBlank { "Ubicación seleccionada en Comitán" })
            }
        )
        finish()
    }

    private fun elegirLugarActual() {
        try {
            mFusedLocationProviderClient?.lastLocation
                ?.addOnSuccessListener { location ->
                    if (location != null) {
                        aplicarUbicacionActual(location)
                    } else {
                        val tokenSource = CancellationTokenSource()
                        mFusedLocationProviderClient?.getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            tokenSource.token
                        )?.addOnSuccessListener { current ->
                            if (current != null) aplicarUbicacionActual(current)
                            else Toast.makeText(this, "No se pudo obtener tu posición. Toca el mapa para elegirla.", Toast.LENGTH_SHORT).show()
                        }?.addOnFailureListener { error ->
                            android.util.Log.w("SeleccionarUbicacion", "No se pudo obtener posición actual", error)
                            Toast.makeText(this, "No se pudo obtener tu posición actual.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                ?.addOnFailureListener { error ->
                    android.util.Log.w("SeleccionarUbicacion", "No se pudo obtener ubicación", error)
                    Toast.makeText(this, "No se pudo obtener tu ubicación actual.", Toast.LENGTH_SHORT).show()
                }
        } catch (_: SecurityException) {
            Toast.makeText(this, "No hay permiso para usar tu ubicación.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun aplicarUbicacionActual(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        selectedLatitud = latLng.latitude
        selectedLongitud = latLng.longitude
        direccion = "Mi ubicación actual"
        mostrarSeleccion(latLng, "Mi ubicación", direccion)
    }

    private fun esGpsActivado(): Boolean {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        return runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    @Suppress("MissingPermission")
    private val solicitarPermisoLocacion: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val map = mMap ?: return@registerForActivityResult
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                runCatching { map.isMyLocationEnabled = true }
                elegirLugarActual()
            } else {
                Toast.makeText(this, "Permiso denegado. Puedes elegir el punto directamente en el mapa.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        geocoderExecutor.shutdownNow()
        super.onDestroy()
    }
}
