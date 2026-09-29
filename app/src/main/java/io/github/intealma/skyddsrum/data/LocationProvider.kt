package io.github.intealma.skyddsrum.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-shot location using the platform LocationManager, so the app needs no Google Play services
 * (works on de-Googled phones and with the emulator's Extended controls → Location).
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(timeoutMs: Long = 15_000): Location? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val lastKnown = lm.allProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        // In an emergency speed matters more than a perfect fix: a fix from the last two minutes is good enough.
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < RECENT_MS) return lastKnown

        // With an older fix to fall back on, don't keep people waiting long for a new one.
        val fresh = withTimeoutOrNull(if (lastKnown != null) FALLBACK_WAIT_MS else timeoutMs) {
            providers.firstNotNullOfOrNull { provider -> requestOnce(lm, provider) }
        }
        return fresh ?: lastKnown
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOnce(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                if (cont.isActive) cont.resume(location)
            }
        }

    private companion object {
        const val RECENT_MS = 2 * 60 * 1000L
        const val FALLBACK_WAIT_MS = 3_000L
    }
}
