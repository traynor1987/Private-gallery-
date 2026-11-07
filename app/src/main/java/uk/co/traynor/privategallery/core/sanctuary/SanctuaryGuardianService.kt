package uk.co.traynor.privategallery.core.sanctuary

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.R

/**
 * Observes package lifecycle events only while explicitly armed for a valid session.
 * A suspended or ambiguous foreground never receives an overlay.
 */
class SanctuaryGuardianService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitoring: Job? = null
    private var monitoredSessionId: String? = null
    private var overlay: SanctuaryBlockingOverlay? = null
    private var hiddenUntil = 0L
    private var failed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = SanctuaryStore.get(this)
        val session = store.refresh()
        if (session == null || !SanctuaryGuardian.isActivated(this, session.id) || !SanctuaryGuardian.hasPermissions(this)) {
            SanctuaryGuardian.clearActivation(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (monitoring?.isActive == true && monitoredSessionId == session.id) return START_STICKY
        monitoring?.cancel()
        hideOverlay()
        hiddenUntil = 0L
        monitoredSessionId = session.id
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (_: IllegalStateException) {
            fail()
            return START_NOT_STICKY
        } catch (_: SecurityException) {
            fail()
            return START_NOT_STICKY
        }
        SanctuaryGuardian.monitorState = SanctuaryGuardian.MonitorState.WAITING
        monitoring = scope.launch { monitor(store, session.id) }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        hideOverlay()
        if (!failed) SanctuaryGuardian.monitorState = SanctuaryGuardian.MonitorState.PAUSED
        super.onDestroy()
    }

    private suspend fun monitor(store: SanctuaryStore, sessionId: String) {
        val observer = SanctuaryUsageObserver(this)
        var essential = emptySet<String>()
        var refreshEssentialAt = 0L
        var suspended = false
        val keyguard = getSystemService(KeyguardManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        while (scope.isActive) {
            val session = store.refresh()
            if (session?.id != sessionId || !SanctuaryGuardian.isActivated(this, sessionId) ||
                !SanctuaryGuardian.hasPermissions(this)
            ) {
                hideOverlay()
                SanctuaryGuardian.clearActivation(this, sessionId)
                stopSelf()
                return
            }
            if (power?.isInteractive != true || keyguard?.isKeyguardLocked != false) {
                if (!suspended) observer.suspendTracking()
                suspended = true
                hideOverlay()
                SanctuaryGuardian.monitorState = SanctuaryGuardian.MonitorState.WAITING
                delay(POLL_INTERVAL)
                continue
            }
            suspended = false
            val elapsed = SystemClock.elapsedRealtime()
            if (elapsed >= refreshEssentialAt) {
                essential = withContext(Dispatchers.IO) {
                    try {
                        SanctuaryGuardian.essentialPackages(this@SanctuaryGuardianService)
                    } catch (_: SecurityException) {
                        null
                    }
                } ?: run {
                    fail()
                    return
                }
                refreshEssentialAt = elapsed + 5_000L
            }
            val foreground = withContext(Dispatchers.IO) { observer.foreground() }
            if (store.current.value?.id != sessionId || !SanctuaryGuardian.hasPermissions(this) ||
                power?.isInteractive != true || keyguard?.isKeyguardLocked != false
            ) {
                hideOverlay()
                continue
            }
            val block = observer.shouldBlock(foreground, session.androidApps, essential)
            if (block && elapsed >= hiddenUntil) {
                val remaining = store.remainingMillis(session)
                if (remaining <= 0) {
                    hideOverlay()
                    continue
                }
                if (!showOverlay(session, remaining)) {
                    fail()
                    return
                }
                SanctuaryGuardian.monitorState = SanctuaryGuardian.MonitorState.BLOCKING
            } else {
                hideOverlay()
                SanctuaryGuardian.monitorState = if (foreground == null) {
                    SanctuaryGuardian.MonitorState.WAITING
                } else {
                    SanctuaryGuardian.MonitorState.MONITORING
                }
            }
            delay(POLL_INTERVAL)
        }
    }

    private fun showOverlay(session: SanctuarySession, remaining: Long): Boolean {
        return try {
            val view = overlay ?: SanctuaryBlockingOverlay(
                this,
                session,
                onNova = { launchApproved { SanctuaryGuardian.openNova(this) } },
                onPhone = { launchApproved { SanctuaryGuardian.openPhone(this) } },
                onSettings = { launchApproved { SanctuaryGuardian.openSystemSettings(this) } },
                onApp = { pkg -> launchApproved { SanctuaryGuardian.launch(this, pkg) } },
            ).also { overlay = it }
            view.update(session, remaining)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: android.view.WindowManager.BadTokenException) {
            false
        } catch (_: android.view.WindowManager.InvalidDisplayException) {
            false
        }
    }

    private fun launchApproved(action: () -> Boolean) {
        if (action()) {
            hiddenUntil = SystemClock.elapsedRealtime() + 1_500L
            hideOverlay()
            SanctuaryGuardian.monitorState = SanctuaryGuardian.MonitorState.MONITORING
        }
    }

    private fun hideOverlay() {
        overlay?.close()
        overlay = null
    }

    private fun fail() {
        hideOverlay()
        failed = true
        SanctuaryGuardian.clearActivation(this)
        SanctuaryGuardian.monitorState = SanctuaryGuardian.MonitorState.FAILED
        stopSelf()
    }

    private fun notification(): Notification {
        val notifications = getSystemService(NotificationManager::class.java)
            ?: throw IllegalStateException("Notification service unavailable")
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sanctuary App Guardian", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when Nova Sanctuary is monitoring Android apps."
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
        val openNova = PendingIntent.getActivity(
            this, 17131, SanctuaryGuardian.novaIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Nova Sanctuary")
            .setContentText("App Guardian is running · tap to return to NovaOS")
            .setContentIntent(openNova)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .build()
    }

    internal companion object {
        const val CHANNEL = "sanctuary_guardian"
        const val NOTIFICATION_ID = 17131
        const val POLL_INTERVAL = 350L
    }
}

/** Short, overlapping queries tolerate delayed event delivery without retaining activity history. */
private class SanctuaryUsageObserver(context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val policy = SanctuaryForegroundPolicy()
    private val processed = mutableSetOf<Transition>()
    private val lastComponentTime = mutableMapOf<Pair<String, String>, Long>()
    private var lastWall = 0L
    private var lastElapsed = 0L
    private var clearedAt = 0L

    private data class Transition(val time: Long, val type: Int, val packageName: String, val activity: String)

    fun suspendTracking() {
        reset()
        lastWall = System.currentTimeMillis()
        lastElapsed = SystemClock.elapsedRealtime()
        clearedAt = lastWall
    }

    fun foreground(): String? {
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val discontinuity = lastWall != 0L && kotlin.math.abs((wall - lastWall) - (elapsed - lastElapsed)) > 2_000L
        if (discontinuity) {
            reset()
            lastWall = 0L
            clearedAt = 0L
        }
        val begin = maxOf(clearedAt, wall - 30_000L, lastWall - 3_000L, 0L)
        lastWall = wall
        lastElapsed = elapsed
        val query = UsageEventsQuery.Builder(begin, wall + 1)
            .setEventTypes(
                UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED,
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN,
            )
            .build()
        val events = try {
            usage?.queryEvents(query)
        } catch (_: SecurityException) {
            null
        }
        if (events == null) {
            reset()
            return null
        }
        processed.removeAll { it.time < begin }
        val event = UsageEvents.Event()
        while (events.hasNextEvent() && events.getNextEvent(event)) {
            val transition = Transition(event.timeStamp, event.eventType, event.packageName.orEmpty(), event.className.orEmpty())
            if (transition.time < clearedAt || !processed.add(transition)) continue
            if (transition.type == UsageEvents.Event.SCREEN_NON_INTERACTIVE || transition.type == UsageEvents.Event.KEYGUARD_SHOWN) {
                policy.reset()
                lastComponentTime.clear()
                clearedAt = transition.time
                continue
            }
            if (transition.packageName.isEmpty() || transition.activity.isEmpty()) continue
            val component = transition.packageName to transition.activity
            if (transition.time < (lastComponentTime[component] ?: Long.MIN_VALUE)) continue
            lastComponentTime[component] = transition.time
            if (transition.type == UsageEvents.Event.ACTIVITY_RESUMED) {
                policy.resumed(transition.packageName, transition.activity)
            } else {
                policy.paused(transition.packageName, transition.activity)
            }
        }
        lastComponentTime.entries.removeAll { it.value < begin }
        return policy.foreground()
    }

    fun shouldBlock(foreground: String?, approved: Set<String>, essential: Set<String>): Boolean =
        policy.shouldBlock(foreground, approved, essential)

    private fun reset() {
        policy.reset()
        processed.clear()
        lastComponentTime.clear()
    }
}
