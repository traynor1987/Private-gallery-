package uk.co.traynor.privategallery.core.guardian

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.NotificationChannel
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
import android.telecom.TelecomManager
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.launcher.LauncherAppIdentity

/** Owner-started diagnostics and separately authorised Main WhatsApp capture. */
internal class GuardianNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        if (SystemClock.elapsedRealtime() >= deadline && probe != null) stopProbe()
        status.value = status.value.copy(connected = true, filter = currentInterruptionFilter)
        if (GuardianCapture.isEnabled(this)) GuardianCapture.refresh(this)
    }

    override fun onListenerDisconnected() = disconnect(this)

    override fun onDestroy() {
        disconnect(this)
        super.onDestroy()
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        if (status.value.connected) status.value = status.value.copy(filter = interruptionFilter)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (sbn.packageName == packageName) {
            receiveSynthetic(sbn, rankingMap)
            return
        }
        if (probe == null && sbn.packageName != GUARDIAN_WHATSAPP) return
        if (!hasAccess(this)) return
        if (probe != null && SystemClock.elapsedRealtime() >= deadline) stopProbe()
        val session = probe
        val captureEnabled = GuardianCapture.isEnabled(this)
        if (session == null && !captureEnabled) return
        val notification = sbn.notification
        val call = notification.category == Notification.CATEGORY_CALL || notification.category == Notification.CATEGORY_MISSED_CALL
        if (sbn.packageName != GUARDIAN_WHATSAPP && !call) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        val locked = keyguard?.let { it.isDeviceLocked || it.isKeyguardLocked } ?: true

        val user = sbn.user
        val serial = getSystemService(UserManager::class.java)?.let { users ->
            runCatching { users.getSerialNumberForUser(user) }.getOrDefault(-1)
        } ?: -1

        val personal = user == Process.myUserHandle()
        val account = probeAccount(personal, serial, probeFriendsSerial)
        val summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (call) {
            if (session != null && !summary) {
                val dialer = getSystemService(TelecomManager::class.java)?.defaultDialerPackage
                val source = guardianCallSource(sbn.packageName, dialer, personal, account) ?: return
                val filter = currentInterruptionFilter
                val previous = status.value.calls[source] ?: GuardianCallObservations()
                status.value = status.value.copy(filter = filter, calls = status.value.calls + (source to previous.copy(
                    events = previous.events + 1,
                    missed = previous.missed + if (notification.category == Notification.CATEGORY_MISSED_CALL) 1 else 0,
                    underDnd = previous.underDnd + if (filter in DND_FILTERS) 1 else 0,
                )))
            }
            return
        }
        if (sbn.packageName != GUARDIAN_WHATSAPP) return
        val personalUid = if (captureEnabled && account == ProbeAccount.MAIN) runCatching {
            packageManager.getApplicationInfo(GUARDIAN_WHATSAPP, 0).uid
        }.getOrNull() else null
        val capture = canCaptureGuardianNotification(captureEnabled, sbn.packageName, account, summary) &&
            personalUid != null && sbn.uid == personalUid && sbn.opPkg == GUARDIAN_WHATSAPP
        if (session == null && !capture) return
        val entries = runCatching {
            notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
                ?.let(Notification.MessagingStyle.Message::getMessagesFromBundleArray).orEmpty()
        }.getOrDefault(emptyList())
        val hasText = entries.any { !it.text.isNullOrBlank() } || runCatching {
            listOf(Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT).any {
                !notification.extras.getCharSequence(it).isNullOrBlank()
            }
        }.getOrDefault(false)
        val estimates = entries.map { entry ->
            val sender = entry.senderPerson
            ProbeMessage(entry.timestamp, sender?.key ?: sender?.uri ?: sender?.name?.toString().orEmpty(),
                entry.text ?: "")
        }
        val conversation = notification.shortcutId?.takeIf { it.isNotBlank() }?.let { "shortcut:$it" }
            ?: "notification:${sbn.key}"
        if (capture) {
            GuardianCapture.record(this, GuardianCaptureEvent(serial, conversation, System.currentTimeMillis(),
                estimates.map { GuardianCaptureEntry(it.time, it.sender, it.text.toString()) }))
        }
        if (session != null) {
            val ranking = Ranking()
            val quiet = rankingMap.getRanking(sbn.key, ranking) && ranking.importance in
                NotificationManager.IMPORTANCE_MIN..NotificationManager.IMPORTANCE_LOW
            val filter = currentInterruptionFilter
            session.record(account, sbn.key, hasText, filter in DND_FILTERS, quiet, sbn.isGroup,
                summary, conversation, estimates, locked)
            status.value = status.value.copy(filter = filter, results = session.results)
        }
    }

    private fun receiveSynthetic(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val test = synthetic ?: return
        if (test.key != null || sbn.tag != test.tag || sbn.id != TEST_ID ||
            sbn.user != Process.myUserHandle() || sbn.notification.channelId != TEST_CHANNEL ||
            !hasAccess(this)
        ) return
        val ranking = Ranking()
        val matchedFilter = if (rankingMap.getRanking(sbn.key, ranking)) ranking.matchesInterruptionFilter() else null
        val captured = runCatching {
            val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)
            check(text?.toString() == TEST_TEXT)
            val plain = TEST_TEXT.toByteArray(Charsets.UTF_8)
            try {
                val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val encrypted = cipher.doFinal(plain)
                val iv = cipher.iv
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                val verified = cipher.doFinal(encrypted)
                try { check(plain.contentEquals(verified)) } finally { verified.fill(0); encrypted.fill(0) }
            } finally {
                plain.fill(0)
            }
        }.isSuccess
        status.value = status.value.copy(synthetic = when {
            !captured -> SyntheticResult.CAPTURE_FAILED
            !test.dismiss -> SyntheticResult.PRESENTED
            !sbn.isClearable -> SyntheticResult.UNSUPPORTED
            else -> SyntheticResult.DISMISSING
        }, syntheticDndMatch = matchedFilter)
        if (status.value.synthetic != SyntheticResult.DISMISSING) {
            synthetic = null
            return
        }
        test.key = sbn.key
        if (runCatching { cancelNotification(sbn.key) }.isFailure) finishSynthetic(SyntheticResult.DISMISS_FAILED)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (synthetic?.key != sbn.key) return
        val verified = reason == REASON_LISTENER_CANCEL && runCatching {
            getActiveNotifications(arrayOf(sbn.key)).none { it.key == sbn.key }
        }.getOrDefault(false)
        finishSynthetic(if (verified) SyntheticResult.DISMISSED else SyntheticResult.DISMISS_FAILED)
    }

    companion object {
        private const val TEST_CHANNEL = "guardian-compatibility-test"
        private const val TEST_ID = 77
        private const val TEST_TEXT = "Harmless Nova Guardian compatibility notification."
        private data class SyntheticTest(val tag: String, val dismiss: Boolean, var key: String? = null)
        private var synthetic: SyntheticTest? = null

        fun postSynthetic(context: Context, dismiss: Boolean) {
            if (!hasAccess(context) || !status.value.connected || synthetic != null) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (!manager.areNotificationsEnabled()) {
                finishSynthetic(SyntheticResult.PERMISSION_REQUIRED)
                return
            }
            manager.createNotificationChannel(NotificationChannel(TEST_CHANNEL, "Guardian compatibility tests",
                NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(TEST_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
                finishSynthetic(SyntheticResult.UNSUPPORTED)
                return
            }
            val test = SyntheticTest(UUID.randomUUID().toString(), dismiss)
            synthetic = test
            status.value = status.value.copy(synthetic = SyntheticResult.WAITING, syntheticDndMatch = null)
            val notification = Notification.Builder(context, TEST_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Nova Guardian test")
                .setContentText(TEST_TEXT)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setTimeoutAfter(30_000)
                .build()
            if (runCatching { manager.notify(test.tag, TEST_ID, notification) }.isFailure) {
                finishSynthetic(SyntheticResult.UNSUPPORTED)
                return
            }
            handler.postDelayed({
                if (synthetic === test) finishSynthetic(
                    if (test.key == null) SyntheticResult.NOT_RECEIVED else SyntheticResult.DISMISS_FAILED,
                )
            }, 10_000)
        }

        private fun finishSynthetic(result: SyntheticResult) {
            synthetic = null
            status.value = status.value.copy(synthetic = result)
        }

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
                ?.let(LauncherAppIdentity::parse)?.takeIf { it.flattenedComponent.substringBefore('/') == GUARDIAN_WHATSAPP }
                ?.profileSerial
            deadline = SystemClock.elapsedRealtime() + PROBE_MILLIS
            status.value = status.value.copy(running = true, results = emptyMap(), calls = emptyMap())
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
            status.value = status.value.copy(results = emptyMap(), calls = emptyMap())
        }

        private fun disconnect(context: Context) {
            if (synthetic != null) finishSynthetic(SyntheticResult.DISCONNECTED)
            if (!hasAccess(context)) {
                if (GuardianCapture.isEnabled(context)) GuardianCapture.setEnabled(context, false)
                handler.removeCallbacks(timeout)
                probe = null
                probeFriendsSerial = null
                status.value = ListenerProbeStatus()
            } else {
                status.value = status.value.copy(connected = false, filter = NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
            }
        }
    }
}

internal data class ListenerProbeStatus(
    val connected: Boolean = false,
    val filter: Int = NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
    val running: Boolean = false,
    val results: Map<ProbeAccount, ProbeResult> = emptyMap(),
    val synthetic: SyntheticResult = SyntheticResult.NOT_TESTED,
    val syntheticDndMatch: Boolean? = null,
    val calls: Map<GuardianCallSource, GuardianCallObservations> = emptyMap(),
)

internal data class GuardianCallObservations(val events: Int = 0, val missed: Int = 0, val underDnd: Int = 0)

internal enum class SyntheticResult(val label: String) {
    NOT_TESTED("Not tested"),
    WAITING("Waiting for Android listener"),
    PRESENTED("Received and verified with encrypted in-memory capture"),
    DISMISSING("Capture verified · waiting for Android dismissal"),
    DISMISSED("Dismissed and absent from Android’s active notifications"),
    CAPTURE_FAILED("Secure test capture failed"),
    DISMISS_FAILED("Android dismissal could not be verified"),
    NOT_RECEIVED("Test notification was not received"),
    UNSUPPORTED("Test channel or notification is unavailable"),
    PERMISSION_REQUIRED("Notification posting permission required"),
    DISCONNECTED("Listener disconnected during the test"),
}
