package com.htmlwidget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

object LocationHelper {

    fun getLocation(ctx: Context, callback: (Double, Double) -> Unit, onFail: () -> Unit) {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            onFail(); return
        }

        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Önce son bilinen konumu dene
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var best: Location? = null
            for (p in providers) {
                try {
                    if (!lm.isProviderEnabled(p)) continue
                    val loc = lm.getLastKnownLocation(p) ?: continue
                    if (best == null || loc.accuracy < best.accuracy) best = loc
                } catch (_: SecurityException) {}
            }

            if (best != null) {
                callback(best.latitude, best.longitude)
                return
            }

            // Son konum yoksa anlık iste
            val provider = when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> { onFail(); return }
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                    callback(location.latitude, location.longitude)
                }
                @Deprecated("Deprecated")
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) { onFail() }
            }

            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())

            // 10 sn timeout
            Handler(Looper.getMainLooper()).postDelayed({
                try { lm.removeUpdates(listener) } catch (_: Exception) {}
            }, 10_000)

        } catch (e: Exception) {
            onFail()
        }
    }

    fun buildGeolocationJs(lat: Double, lon: Double, accuracy: Float = 50f): String = """
(function() {
    var lat = $lat, lon = $lon, acc = $accuracy;
    if (!navigator.geolocation) return;
    var mkPos = function() {
        return { coords: { latitude: lat, longitude: lon, accuracy: acc,
            altitude: null, altitudeAccuracy: null, heading: null, speed: null },
            timestamp: Date.now() };
    };
    navigator.geolocation.getCurrentPosition = function(ok, err, opt) {
        setTimeout(function() { if (typeof ok === 'function') ok(mkPos()); }, 100);
    };
    navigator.geolocation.watchPosition = function(ok, err, opt) {
        setTimeout(function() { if (typeof ok === 'function') ok(mkPos()); }, 100);
        return 1;
    };
})();
    """.trimIndent()
}
