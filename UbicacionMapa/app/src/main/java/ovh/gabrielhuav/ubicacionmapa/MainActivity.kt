package ovh.gabrielhuav.ubicacionmapa

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Inicializar el cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        webView = findViewById(R.id.webView)

        // Configurar el WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setGeolocationEnabled(true)
            // Configurar User-Agent personalizado para evitar bloqueos
            userAgentString = "UbicacionMapaApp/1.0 Android"
        }

        webView.webViewClient = WebViewClient()

        // Configurar el cliente Chrome para manejar solicitudes de geolocalización
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                // Verificar permisos de ubicación
                if (checkLocationPermission()) {
                    callback.invoke(origin, true, false)
                } else {
                    requestLocationPermission()
                }
            }
        }

        // Verificar permisos antes de cargar el mapa
        if (checkLocationPermission()) {
            loadMapWithCurrentLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    @SuppressLint("MissingPermission")
    private fun loadMapWithCurrentLocation() {
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        // Usar CartoDB para mostrar la ubicación actual
                        val html = createMapHtml(it.latitude, it.longitude)
                        webView.loadDataWithBaseURL("https://example.com", html, "text/html", "UTF-8", null)
                    } ?: run {
                        // Si la ubicación es nula, cargar un mapa predeterminado
                        loadDefaultMap()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                    loadDefaultMap()
                }
        } else {
            loadDefaultMap()
        }
    }

    private fun createMapHtml(latitude: Double, longitude: Double): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.7.1/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.7.1/dist/leaflet.js"></script>
                <style>
                    body, html, #map {
                        width: 100%;
                        height: 100%;
                        margin: 0;
                        padding: 0;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map').setView([${latitude}, ${longitude}], 15);
                    
                    // Usar CartoDB en lugar de OpenStreetMap para evitar bloqueos
                    L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
                        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
                        subdomains: 'abcd',
                        maxZoom: 19
                    }).addTo(map);
                    
                    var marker = L.marker([${latitude}, ${longitude}]).addTo(map);
                    marker.bindPopup("Mi ubicación").openPopup();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun loadDefaultMap() {
        // Cargar un mapa centrado en la Ciudad de México como ubicación predeterminada
        val defaultHtml = createMapHtml(19.4326, -99.1332)
        webView.loadDataWithBaseURL("https://example.com", defaultHtml, "text/html", "UTF-8", null)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMapWithCurrentLocation()
            } else {
                Toast.makeText(
                    this,
                    "Se requiere permiso de ubicación para mostrar tu posición",
                    Toast.LENGTH_SHORT
                ).show()
                loadDefaultMap()
            }
        }
    }
}