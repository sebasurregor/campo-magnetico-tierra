package com.fisica2.campomagnetico

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Obtiene UNA ubicación aproximada (ACCESS_COARSE_LOCATION) cuando el usuario la pide, con la app
 * abierta. No hay seguimiento continuo ni ubicación en segundo plano.
 */
class LocationReader(private val context: Context) {
    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Llama a [onResult] una sola vez, en el hilo principal, con la ubicación o null. */
    @SuppressLint("MissingPermission")
    fun requestOnce(onResult: (Location?) -> Unit) {
        val lm = manager
        if (lm == null || !hasPermission()) {
            onResult(null)
            return
        }

        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            // Antes de Android 12 el GPS exige ubicación precisa; desde Android 12 entrega una aproximada.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.GPS_PROVIDER)
        }
        val lastKnown = (candidates + LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        val provider = candidates.firstOrNull { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }
        if (provider == null) {
            onResult(lastKnown)
            return
        }

        var done = false
        fun finish(loc: Location?) {
            if (done) return
            done = true
            onResult(loc ?: lastKnown)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                handler.postDelayed({ signal.cancel(); finish(null) }, TIMEOUT_MS)
                lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                    handler.removeCallbacksAndMessages(null)
                    finish(loc)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        handler.removeCallbacksAndMessages(null)
                        lm.removeUpdates(this)
                        finish(location)
                    }

                    // En Android 8–10 estos métodos no tienen implementación por defecto.
                    @Deprecated("Requerido en API < 29")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                handler.postDelayed({ lm.removeUpdates(listener); finish(null) }, TIMEOUT_MS)
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            handler.removeCallbacksAndMessages(null)
            finish(null)
        } catch (e: IllegalArgumentException) {
            handler.removeCallbacksAndMessages(null)
            finish(null)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
