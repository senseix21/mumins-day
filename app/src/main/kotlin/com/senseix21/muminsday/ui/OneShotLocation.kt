package com.senseix21.muminsday.ui

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

/**
 * One coarse position, read once, only while a screen that needs it is open (INV-P2). The screen calls [stop] when it
 * pauses, so nothing is read in the background, and nothing here keeps the position: it goes to the callback and is gone.
 * Only the platform's `LocationManager` is used, with the network (or fused) provider that `ACCESS_COARSE_LOCATION`
 * allows; the GPS provider needs the fine permission, which the app does not declare (ADR-19).
 */
class OneShotLocation(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var signal: CancellationSignal? = null
    private var listener: LocationListener? = null
    private var timeout: Runnable? = null

    fun hasPermission(): Boolean = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** [onResult] gets the position, or null when there is none to be had (location off, no provider, no fix in time). */
    @SuppressLint("MissingPermission") // hasPermission() is checked first; the caller asks for it at the tap
    fun start(onResult: (Location?) -> Unit) {
        stop()
        val manager = context.getSystemService(LocationManager::class.java)
        val provider = if (manager == null || !hasPermission()) null else coarseProvider(manager)
        if (manager == null || provider == null) {
            onResult(null)
            return
        }
        var delivered = false
        fun deliver(location: Location?) {
            if (delivered) return
            delivered = true
            stop()
            onResult(location)
        }
        timeout = Runnable { deliver(null) }.also { handler.postDelayed(it, TIMEOUT_MILLIS) }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancel = CancellationSignal().also { signal = it }
                manager.getCurrentLocation(provider, cancel, context.mainExecutor) { location -> deliver(location) }
            } else {
                requestOnce(manager, provider, ::deliver)
            }
        } catch (_: SecurityException) {
            // The permission was withdrawn between the check and the call.
            deliver(null)
        }
    }

    /** Safe to call at any time, and twice. Cancels a read in progress and its timer. */
    fun stop() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        signal?.cancel()
        signal = null
        listener?.let { context.getSystemService(LocationManager::class.java)?.removeUpdates(it) }
        listener = null
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // requestSingleUpdate is the one-shot call before API 30, where getCurrentLocation arrives
    private fun requestOnce(manager: LocationManager, provider: String, deliver: (Location?) -> Unit) {
        val once = object : LocationListener {
            override fun onLocationChanged(location: Location) = deliver(location)

            // Abstract before API 30, so a phone older than that fails with AbstractMethodError if these are left out.
            @Deprecated("Called only before API 29")
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = deliver(null)
        }
        listener = once
        manager.requestSingleUpdate(provider, once, Looper.getMainLooper())
    }

    /** The fused provider where it exists (API 31), else the network provider, whichever is switched on. */
    private fun coarseProvider(manager: LocationManager): String? {
        val enabled = manager.getProviders(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in enabled) return LocationManager.FUSED_PROVIDER
        return LocationManager.NETWORK_PROVIDER.takeIf { it in enabled }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
    }
}
