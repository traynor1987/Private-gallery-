package uk.co.traynor.privategallery.novaos2.working

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.TimeUnit

internal enum class WorkingLocationStatus(val description: String) {
    OFF("GPS is off outside Driving Mode."),
    PERMISSION("Allow foreground location to see the speed reading."),
    DISABLED("Location services are off."),
    WAITING("Waiting for a fresh location reading."),
    RECEIVING("Location active on this screen."),
    UNAVAILABLE("The phone could not start location updates."),
}

internal data class WorkingLocation(val status: WorkingLocationStatus, val speed: WorkingSpeed? = null)

internal fun hasWorkingLocation(context: Context) = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
).any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

@Composable
internal fun workingLocation(active: Boolean, permissionRevision: Int, allowed: () -> Boolean): State<WorkingLocation> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestAllowed = androidx.compose.runtime.rememberUpdatedState(allowed)
    return produceState(WorkingLocation(WorkingLocationStatus.OFF), active, permissionRevision, lifecycle) {
        value = WorkingLocation(WorkingLocationStatus.OFF)
        if (!active) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!latestAllowed.value()) return@repeatOnLifecycle
            if (!hasWorkingLocation(context)) {
                value = WorkingLocation(WorkingLocationStatus.PERMISSION)
                return@repeatOnLifecycle
            }
            val manager = context.getSystemService(LocationManager::class.java)
            if (manager == null || !manager.isLocationEnabled) {
                value = WorkingLocation(WorkingLocationStatus.DISABLED)
                return@repeatOnLifecycle
            }
            val precise = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val provider = if (precise && LocationManager.GPS_PROVIDER in manager.getProviders(true)) {
                LocationManager.GPS_PROVIDER
            } else if (LocationManager.NETWORK_PROVIDER in manager.getProviders(true)) {
                LocationManager.NETWORK_PROVIDER
            } else {
                value = WorkingLocation(WorkingLocationStatus.UNAVAILABLE)
                return@repeatOnLifecycle
            }
            var subscribed = true
            val listener = LocationListener { location ->
                if (subscribed && latestAllowed.value()) {
                    // Coordinates never leave the callback. Only sensor measurements are retained.
                    val measuredAt = TimeUnit.NANOSECONDS.toMillis(location.elapsedRealtimeNanos)
                    if (measuredAt <= SystemClock.elapsedRealtime()) {
                        value = WorkingLocation(WorkingLocationStatus.RECEIVING, WorkingSpeed(
                            measuredAt, location.speed.takeIf { location.hasSpeed() && precise },
                            location.accuracy.takeIf { location.hasAccuracy() },
                            location.speedAccuracyMetersPerSecond.takeIf { location.hasSpeedAccuracy() && precise },
                        ))
                    }
                }
            }
            val acquired = runCatching {
                manager.requestLocationUpdates(provider, 1_000L, 0f, context.mainExecutor, listener)
            }.isSuccess
            if (acquired) {
                value = WorkingLocation(WorkingLocationStatus.WAITING)
                try { awaitCancellation() } finally {
                    subscribed = false
                    manager.removeUpdates(listener)
                    value = WorkingLocation(WorkingLocationStatus.OFF)
                }
            } else value = WorkingLocation(WorkingLocationStatus.UNAVAILABLE)
        }
    }
}
