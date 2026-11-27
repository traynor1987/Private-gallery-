package uk.co.traynor.privategallery.novaos2

import android.content.Context
import android.content.pm.PackageManager
import android.health.connect.AggregateRecordsRequest
import android.health.connect.AggregateRecordsResponse
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.AggregationType
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.RestingHeartRateRecord
import android.health.connect.datatypes.SleepSessionRecord
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
                        Text("Nova Health & Mind can show today’s steps, exercise, sleep or average heart rate when you select a measurement. Each permission is optional and you can revoke access in Health Connect.")
                        Text("The result is shown only in your unlocked Primary workspace. NovaOS never writes to Health Connect, stores the imported value, or shares it with Guardian or another service.")
                        TextButton(onClick = ::finish) { Text("Close") }
                    }
                }
            }
        }
    }
}

internal enum class ConnectedMetric(
    val label: String,
    val permission: String,
    val aggregation: AggregationType<Long>,
) {
    STEPS("Steps", HEALTH_READ_STEPS, StepsRecord.STEPS_COUNT_TOTAL),
    EXERCISE("Exercise duration", "android.permission.health.READ_EXERCISE", ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
    SLEEP("Sleep duration", "android.permission.health.READ_SLEEP", SleepSessionRecord.SLEEP_DURATION_TOTAL),
    HEART("Average heart rate", "android.permission.health.READ_HEART_RATE", HeartRateRecord.BPM_AVG),
    RESTING("Average resting heart rate", "android.permission.health.READ_RESTING_HEART_RATE", RestingHeartRateRecord.BPM_AVG);

    fun format(value: Long): String = when (this) {
        STEPS -> "$value steps"
        EXERCISE, SLEEP -> "${java.time.Duration.ofMillis(value).toMinutes()} min"
        HEART, RESTING -> "$value bpm"
    }
}

internal sealed interface HealthReadResult {
    data class Available(val value: Long) : HealthReadResult
    data object None : HealthReadResult
    data object Unavailable : HealthReadResult
    data object PermissionDenied : HealthReadResult
}

internal fun metricPermissionGranted(context: Context, metric: ConnectedMetric): Boolean =
    context.checkSelfPermission(metric.permission) == PackageManager.PERMISSION_GRANTED

internal suspend fun readHealthMetric(
    context: Context,
    metric: ConnectedMetric,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): HealthReadResult = suspendCancellableCoroutine { continuation ->
    if (!metricPermissionGranted(context, metric)) {
        continuation.resume(HealthReadResult.PermissionDenied)
        return@suspendCancellableCoroutine
    }
    val manager = context.getSystemService(HealthConnectManager::class.java)
    val start = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    if (manager == null || start >= now) {
        continuation.resume(if (manager == null) HealthReadResult.Unavailable else HealthReadResult.None)
        return@suspendCancellableCoroutine
    }
    val filter = TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(now).build()
    val request = AggregateRecordsRequest.Builder<Long>(filter).addAggregationType(metric.aggregation).build()
    try {
        manager.aggregate(request, context.mainExecutor, object : OutcomeReceiver<AggregateRecordsResponse<Long>, HealthConnectException> {
            override fun onResult(result: AggregateRecordsResponse<Long>) {
                if (!continuation.isActive) return
                val value = result.get(metric.aggregation)
                continuation.resume(if (!metricPermissionGranted(context, metric)) HealthReadResult.PermissionDenied
                else value?.takeIf { it >= 0 }?.let { HealthReadResult.Available(it) } ?: HealthReadResult.None)
            }

            override fun onError(error: HealthConnectException) {
                if (continuation.isActive) continuation.resume(
                    if (metricPermissionGranted(context, metric)) HealthReadResult.Unavailable else HealthReadResult.PermissionDenied,
                )
            }
        })
    } catch (_: SecurityException) {
        if (continuation.isActive) continuation.resume(HealthReadResult.PermissionDenied)
    } catch (_: RuntimeException) {
        if (continuation.isActive) continuation.resume(HealthReadResult.Unavailable)
    }
}
