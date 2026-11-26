package uk.co.traynor.privategallery.novaos2

import android.content.Context
import android.content.pm.PackageManager
import android.health.connect.AggregateRecordsRequest
import android.health.connect.AggregateRecordsResponse
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.StepsRecord
import android.os.Bundle
import android.os.OutcomeReceiver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal const val HEALTH_READ_STEPS = "android.permission.health.READ_STEPS"
internal const val HEALTH_CONNECT_SETTINGS = "android.health.connect.action.HEALTH_HOME_SETTINGS"

/** This activity is only reachable externally through the system-protected permission-usage alias. */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Health Connect in NovaOS", style = MaterialTheme.typography.headlineSmall)
                        Text("Nova Health & Mind can show today’s aggregate step count when you request it. Permission is optional and you can revoke it in Health Connect.")
                        Text("The count is shown only in your unlocked Primary workspace. NovaOS never writes to Health Connect, stores the imported count, or shares it with Guardian or another service.")
                        TextButton(onClick = ::finish) { Text("Close") }
                    }
                }
            }
        }
    }
}

internal sealed interface HealthStepsResult {
    data class Available(val steps: Long) : HealthStepsResult
    data object None : HealthStepsResult
    data object Unavailable : HealthStepsResult
    data object PermissionDenied : HealthStepsResult
}

internal fun healthStepsPermissionGranted(context: Context): Boolean =
    context.checkSelfPermission(HEALTH_READ_STEPS) == PackageManager.PERMISSION_GRANTED

internal suspend fun readHealthSteps(
    context: Context,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): HealthStepsResult = suspendCancellableCoroutine { continuation ->
    if (!healthStepsPermissionGranted(context)) {
        continuation.resume(HealthStepsResult.PermissionDenied)
        return@suspendCancellableCoroutine
    }
    val manager = context.getSystemService(HealthConnectManager::class.java)
    val start = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    if (manager == null || start >= now) {
        continuation.resume(if (manager == null) HealthStepsResult.Unavailable else HealthStepsResult.None)
        return@suspendCancellableCoroutine
    }
    val filter = TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(now).build()
    val request = AggregateRecordsRequest.Builder<Long>(filter).addAggregationType(StepsRecord.STEPS_COUNT_TOTAL).build()
    try {
        manager.aggregate(request, context.mainExecutor, object : OutcomeReceiver<AggregateRecordsResponse<Long>, HealthConnectException> {
            override fun onResult(result: AggregateRecordsResponse<Long>) {
                if (!continuation.isActive) return
                val steps = result.get(StepsRecord.STEPS_COUNT_TOTAL)
                continuation.resume(if (!healthStepsPermissionGranted(context)) HealthStepsResult.PermissionDenied
                else steps?.takeIf { it >= 0 }?.let { HealthStepsResult.Available(it) } ?: HealthStepsResult.None)
            }

            override fun onError(error: HealthConnectException) {
                if (continuation.isActive) continuation.resume(
                    if (healthStepsPermissionGranted(context)) HealthStepsResult.Unavailable else HealthStepsResult.PermissionDenied,
                )
            }
        })
    } catch (_: SecurityException) {
        if (continuation.isActive) continuation.resume(HealthStepsResult.PermissionDenied)
    } catch (_: RuntimeException) {
        if (continuation.isActive) continuation.resume(HealthStepsResult.Unavailable)
    }
}
