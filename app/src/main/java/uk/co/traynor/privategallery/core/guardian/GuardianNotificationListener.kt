package uk.co.traynor.privategallery.core.guardian

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.launcher.LauncherAppIdentity

/** Phase-one diagnostics. Reads WhatsApp text availability only during an owner-started test. */
internal class GuardianNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        status.value = ListenerProbeStatus(connected = true, filter = currentInterruptionFilter)
    }

    override fun onListenerDisconnected() = disconnect()

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        if (status.value.connected) status.value = status.value.copy(filter = interruptionFilter)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val session = probe ?: return
        if (SystemClock.elapsedRealtime() >= deadline) {
            stopProbe()
            return
        }
        if (sbn.packageName != WHATSAPP || !hasAccess(this)) return
        val keyguard = getSystemService(KeyguardManager::class.java) ?: return
        if (keyguard.isDeviceLocked || keyguard.isKeyguardLocked) return

        val user = sbn.user
        val serial = getSystemService(UserManager::class.java)?.let { users ->
            runCatching { users.getSerialNumberForUser(user) }.getOrDefault(-1)
        } ?: -1

        val account = probeAccount(user == Process.myUserHandle(), serial, probeFriendsSerial)
        val ranking = Ranking()
        val quiet = rankingMap.getRanking(sbn.key, ranking) && ranking.importance in
            NotificationManager.IMPORTANCE_MIN..NotificationManager.IMPORTANCE_LOW
        val notification = sbn.notification
        val hasText = runCatching {
            listOf(Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT).any {
                !notification.extras.getCharSequence(it).isNullOrBlank()
            } || notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
                ?.let(Notification.MessagingStyle.Message::getMessagesFromBundleArray)
                ?.any { !it.text.isNullOrBlank() } == true
        }.getOrDefault(false)
        val filter = currentInterruptionFilter
        session.record(account, sbn.key, hasText, filter in DND_FILTERS, quiet, sbn.isGroup)
        status.value = status.value.copy(filter = filter, results = session.results)
    }

    companion object {
        private const val WHATSAPP = "com.whatsapp"
        private const val PROBE_MILLIS = 5 * 60 * 1000L
        private val DND_FILTERS = setOf(
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_ALARMS,
            NotificationManager.INTERRUPTION_FILTER_NONE,
        )
        private val handler = Handler(Looper.getMainLooper())
        private val status = MutableStateFlow(ListenerProbeStatus())
        val state = status.asStateFlow()
        private var probe: NotificationProbe? = null
        private var deadline = 0L
        private var probeFriendsSerial: Long? = null
        private val timeout = Runnable { stopProbe() }

        fun hasAccess(context: Context): Boolean = context.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(ComponentName(context, GuardianNotificationListener::class.java)) == true

        fun startProbe(context: Context, discoveredCloneSerial: Long? = null) {
            if (!hasAccess(context) || !status.value.connected) return
            handler.removeCallbacks(timeout)
            probe = NotificationProbe()
            probeFriendsSerial = discoveredCloneSerial ?: SanctuaryStore.get(context).launcherSettings().friendsWhatsApp
                ?.let(LauncherAppIdentity::parse)?.takeIf { it.flattenedComponent.substringBefore('/') == WHATSAPP }
                ?.profileSerial
            deadline = SystemClock.elapsedRealtime() + PROBE_MILLIS
            status.value = status.value.copy(running = true, results = emptyMap())
            handler.postDelayed(timeout, PROBE_MILLIS)
        }

        fun stopProbe() {
            handler.removeCallbacks(timeout)
            probe = null
            probeFriendsSerial = null
            status.value = status.value.copy(running = false)
        }

        fun clearProbe() {
            stopProbe()
            status.value = status.value.copy(results = emptyMap())
        }

        private fun disconnect() {
            handler.removeCallbacks(timeout)
            probe = null
            probeFriendsSerial = null
            status.value = ListenerProbeStatus()
        }
    }
}

internal data class ListenerProbeStatus(
    val connected: Boolean = false,
    val filter: Int = NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
    val running: Boolean = false,
    val results: Map<ProbeAccount, ProbeResult> = emptyMap(),
)
