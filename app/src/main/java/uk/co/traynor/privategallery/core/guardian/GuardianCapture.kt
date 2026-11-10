package uk.co.traynor.privategallery.core.guardian

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal data class GuardianCaptureHealth(
    val enabled: Boolean = false,
    val retentionDays: Int = 7,
    val retained: Int? = null,
    val limited: Boolean = false,
    val busy: Boolean = false,
)

/** Serialises settings, archive writes and deletion without blocking Android's listener callbacks. */
internal object GuardianCapture {
    private val worker = Executors.newSingleThreadExecutor()
    private val pendingEvents = AtomicInteger()
    private val generation = AtomicInteger()
    private val status = MutableStateFlow(GuardianCaptureHealth())
    val state = status.asStateFlow()

    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", false)

    fun refresh(context: Context) = manage(context) { _, _ -> }

    fun setEnabled(context: Context, enabled: Boolean) {
        generation.incrementAndGet()
        manage(context) { app, _ ->
            check(!enabled || GuardianNotificationListener.hasAccess(app))
            check(prefs(app).edit().putBoolean("enabled", enabled).commit())
        }
    }

    fun setRetention(context: Context, days: Int) = manage(context) { app, _ ->
        require(days in GuardianCaptureStore.RETENTION_DAYS)
        check(prefs(app).edit().putInt("retention", days).commit())
    }

    fun clear(context: Context) {
        generation.incrementAndGet()
        manage(context) { _, store -> store.clear() }
    }

    fun record(context: Context, event: GuardianCaptureEvent) {
        val app = context.applicationContext
        val expected = generation.get()
        if (pendingEvents.incrementAndGet() > 16) {
            pendingEvents.decrementAndGet()
            status.update { it.copy(limited = true) }
            return
        }
        worker.execute {
            try {
                if (generation.get() != expected || !isEnabled(app) || !GuardianNotificationListener.hasAccess(app)) {
                    return@execute
                }
                val days = retention(app)
                val result = runCatching { GuardianCaptureStore(app).append(event, days, System.currentTimeMillis()) }
                val captured = result.getOrNull()
                status.value = GuardianCaptureHealth(
                    enabled = true, retentionDays = days, retained = captured?.retained,
                    limited = captured == null || captured.partial || captured.retained == 0,
                )
            } finally {
                pendingEvents.decrementAndGet()
            }
        }
    }

    private fun manage(context: Context, change: (Context, GuardianCaptureStore) -> Unit) {
        val app = context.applicationContext
        status.update { it.copy(busy = true) }
        worker.execute {
            val store = GuardianCaptureStore(app)
            val retained = runCatching {
                change(app, store)
                store.summary(retention(app), System.currentTimeMillis())
            }
            status.value = GuardianCaptureHealth(isEnabled(app), retention(app), retained.getOrNull(), retained.isFailure)
        }
    }

    private fun retention(context: Context) = prefs(context).getInt("retention", 7)

    private fun prefs(context: Context) = context.getSharedPreferences("nova_guardian_capture", Context.MODE_PRIVATE)
}
