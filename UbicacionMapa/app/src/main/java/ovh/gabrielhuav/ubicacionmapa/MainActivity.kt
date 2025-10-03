package ovh.gabrielhuav.ubicacionmapa

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.webkit.WebSettings
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        webView = findViewById(R.id.webView)

        // Configurar WebView para cargar INEGI
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            allowContentAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            // User agent para que INEGI funcione correctamente
            userAgentString = "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Toast.makeText(
                    this@MainActivity,
                    "Error al cargar INEGI: Verifica tu conexión",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Toast.makeText(
                    this@MainActivity,
                    "Mapa INEGI cargado. Puedes buscar tu ubicación en el buscador.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        if (!isInternetAvailable()) {
            Toast.makeText(this, "No hay conexión a internet", Toast.LENGTH_LONG).show()
            return
        }

        if (checkLocationPermission()) {
            loadMapWithCurrentLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
                        // Cargar INEGI MDM6 con parámetros de coordenadas
                        val html = createINEGIIframeHtml(it.latitude, it.longitude)
                        webView.loadDataWithBaseURL("https://gaia.inegi.org.mx", html, "text/html", "UTF-8", null)
                    } ?: run {
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

    private fun createINEGIIframeHtml(latitude: Double, longitude: Double): String {
        // INEGI MDM6 con parámetros para centrar en coordenadas
        // Formato: ?v=coordX,coordY,zoom
        val zoom = 15
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    body, html {
                        width: 100%;
                        height: 100%;
                        overflow: hidden;
                    }
                    .header {
                        position: absolute;
                        top: 0;
                        left: 0;
                        right: 0;
                        background: linear-gradient(135deg, #8B4513 0%, #A0522D 100%);
                        color: white;
                        padding: 12px;
                        text-align: center;
                        font-family: Arial, sans-serif;
                        font-size: 14px;
                        font-weight: bold;
                        z-index: 1000;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.2);
                    }
                    .coords-info {
                        position: absolute;
                        bottom: 10px;
                        left: 10px;
                        background: white;
                        padding: 10px;
                        border-radius: 8px;
                        box-shadow: 0 2px 6px rgba(0,0,0,0.3);
                        z-index: 1000;
                        font-size: 11px;
                        font-family: Arial, sans-serif;
                    }
                    .coords-info strong {
                        color: #8B4513;
                    }
                    iframe {
                        width: 100%;
                        height: 100%;
                        border: none;
                        padding-top: 44px;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    🗺️ INEGI - Mapa Digital de México V6
                </div>
                <div class="coords-info">
                    <strong>📍 Mi Ubicación</strong><br>
                    Lat: ${String.format("%.6f", latitude)}<br>
                    Lng: ${String.format("%.6f", longitude)}
                </div>
                <iframe
                    src="https://gaia.inegi.org.mx/mdm6/?v=${longitude},${latitude},$zoom"
                    allowfullscreen
                    loading="lazy">
                </iframe>
            </body>
            </html>
        """.trimIndent()
    }

    private fun loadDefaultMap() {
        val defaultHtml = createINEGIIframeHtml(19.4326, -99.1332)
        webView.loadDataWithBaseURL("https://gaia.inegi.org.mx", defaultHtml, "text/html", "UTF-8", null)
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
                    "Se requiere permiso de ubicación",
                    Toast.LENGTH_SHORT
                ).show()
                loadDefaultMap()
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}