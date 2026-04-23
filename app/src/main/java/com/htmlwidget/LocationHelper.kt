package com.htmlwidget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

object LocationHelper {

    /**
     * Mevcut konumu alır. Önce cache'den dener, yoksa güncel konum ister.
     * callback: (lat, lon) veya null (izin yok / konum alınamadı)
     */
    fun getLocation(ctx: Context, callback: (Double, Double) -> Unit, onFail: () -> Unit) {
        // İzin kontrolü
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            onFail()
            return
        }

        try {
            // Önce LocationManager'dan son bilinen konumu dene (hızlı)
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var best: Location? = null
            for (provider in providers) {
                if (!lm.isProviderEnabled(provider)) continue
                try {
                    val loc = lm.getLastKnownLocation(provider) ?: continue
                    if (best == null || loc.accuracy < best.accuracy) {
                        best = loc
                    }
                } catch (_: SecurityException) {}
            }

            if (best != null) {
                callback(best.latitude, best.longitude)
            } else {
                // Son bilinen konum yoksa güncel konum iste
                requestFreshLocation(ctx, lm, callback, onFail)
            }
        } catch (e: Exception) {
            onFail()
        }
    }

    private fun requestFreshLocation(
        ctx: Context,
        lm: LocationManager,
        callback: (Double, Double) -> Unit,
        onFail: () -> Unit
    ) {
        try {
            val provider = when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> { onFail(); return }
            }

            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                    callback(location.latitude, location.longitude)
                }
                @Deprecated("Deprecated")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) { onFail() }
            }

            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())

            // 10 saniye timeout
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                try { lm.removeUpdates(listener) } catch (_: Exception) {}
            }, 10_000)

        } catch (e: SecurityException) {
            onFail()
        } catch (e: Exception) {
            onFail()
        }
    }

    /**
     * HTML içine konum bilgisini JavaScript olarak enjekte eder.
     * navigator.geolocation.getCurrentPosition() çağrısını override eder.
     */
    fun buildGeolocationJs(lat: Double, lon: Double, accuracy: Float = 50f): String {
        return """
(function() {
    var _mockLat = $lat;
    var _mockLon = $lon;
    var _mockAcc = $accuracy;
    
    if (navigator.geolocation) {
        var _origGet = navigator.geolocation.getCurrentPosition.bind(navigator.geolocation);
        var _origWatch = navigator.geolocation.watchPosition.bind(navigator.geolocation);
        
        navigator.geolocation.getCurrentPosition = function(success, error, options) {
            var pos = {
                coords: {
                    latitude: _mockLat,
                    longitude: _mockLon,
                    accuracy: _mockAcc,
                    altitude: null,
                    altitudeAccuracy: null,
                    heading: null,
                    speed: null
                },
                timestamp: Date.now()
            };
            if (typeof success === 'function') {
                setTimeout(function() { success(pos); }, 100);
            }
        };
        
        navigator.geolocation.watchPosition = function(success, error, options) {
            var pos = {
                coords: {
                    latitude: _mockLat,
                    longitude: _mockLon,
                    accuracy: _mockAcc,
                    altitude: null,
                    altitudeAccuracy: null,
                    heading: null,
                    speed: null
                },
                timestamp: Date.now()
            };
            if (typeof success === 'function') {
                setTimeout(function() { success(pos); }, 100);
            }
            return 1;
        };
    }
})();
        """.trimIndent()
    }
}
