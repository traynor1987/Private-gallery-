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
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.launcher.LauncherAppIdentity

/** Owner-started diagnostics and separately authorised Main WhatsApp capture. */
internal class GuardianNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        connectedListener = this
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
            val event = GuardianCaptureEvent(serial, conversation, System.currentTimeMillis(), captureEntries(notification))
            val canDismiss = guardianMayDismissMessage(
                automaticDismissalEnabled(this), sbn.packageName, account, summary, call,
                true, sbn.isClearable, notification.category,
            ) && !locked
            GuardianCapture.record(this, event, if (canDismiss) {
                { dismissCaptured(sbn, event.entries) }
            } else null)
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

    private fun captureEntries(notification: Notification): List<GuardianCaptureEntry> = runCatching {
        notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
            ?.let(Notification.MessagingStyle.Message::getMessagesFromBundleArray).orEmpty().map { entry ->
                val sender = entry.senderPerson
                GuardianCaptureEntry(entry.timestamp, sender?.key ?: sender?.uri ?: sender?.name?.toString().orEmpty(),
                    entry.text?.toString().orEmpty(), sender?.name?.toString())
            }
    }.getOrDefault(emptyList())

    private fun dismissCaptured(original: StatusBarNotification, captured: List<GuardianCaptureEntry>) {
        if (connectedListener !== this || !automaticDismissalEnabled(this) || !hasAccess(this) ||
            !GuardianCapture.isEnabled(this) || captured.isEmpty()) return
        val keyguard = getSystemService(KeyguardManager::class.java) ?: return
        if (keyguard.isDeviceLocked || keyguard.isKeyguardLocked) return
        val exact = runCatching { getActiveNotifications(arrayOf(original.key)).singleOrNull() }.getOrNull()
            ?: return
        if (exact.key != original.key || exact.postTime != original.postTime || exact.uid != original.uid ||
            exact.user != Process.myUserHandle() || exact.packageName != GUARDIAN_WHATSAPP ||
            exact.opPkg != GUARDIAN_WHATSAPP || !exact.isClearable || exact.notification.category != Notification.CATEGORY_MESSAGE ||
            exact.notification.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT) != 0 ||
            captured != captureEntries(exact.notification)) return

        val requested = runCatching { cancelNotification(exact.key) }.isSuccess
        status.value = status.value.copy(mainDismissal = if (requested) "Requested · checking Android"
            else "Android rejected the request")
        if (requested) handler.postDelayed({
            if (connectedListener !== this || !hasAccess(this)) return@postDelayed
            val active = runCatching { getActiveNotifications(arrayOf(exact.key)).any { it.key == exact.key } }.getOrNull()
            status.value = status.value.copy(mainDismissal = when (active) {
                true -> "Still active or reposted"
                false -> "Exact key absent from Android"
                null -> "Removal could not be checked"
            })
        }, 1_000)
    }

    private fun receiveSynthetic(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val test = synthetic ?: return
        if (sbn.tag != test.tag || sbn.id != TEST_ID ||
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
        status.value = status.value.copy(
            synthetic = if (captured) SyntheticResult.PRESENTED else SyntheticResult.CAPTURE_FAILED,
            syntheticDndMatch = matchedFilter,
            dismissal = status.value.dismissal.copy(posted = true, matched = true),
        )
        synthetic = null
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (externalAttempt?.key == sbn.key && reason == REASON_LISTENER_CANCEL) externalAttempt?.removedByListener = true
    }

    companion object {
        private const val TEST_CHANNEL = "guardian-compatibility-test"
        private const val TEST_ID = 77
        private const val TEST_TEXT = "Harmless Nova Guardian compatibility notification."
        private const val OWN_TEST_PREFIX = "nova-guardian-presentation-"
        private const val EXTERNAL_FIXTURE_PREFIX = "nova-guardian-shell-fixture-"
        private data class SyntheticTest(val tag: String)
        private data class ExternalAttempt(val key: String, var removedByListener: Boolean = false)
        private var synthetic: SyntheticTest? = null
        private var externalAttempt: ExternalAttempt? = null
        private var connectedListener: GuardianNotificationListener? = null

        fun postSynthetic(context: Context) {
            if (!hasAccess(context) || !status.value.connected || synthetic != null ||
                status.value.synthetic == SyntheticResult.DISMISSING
            ) return
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
            val test = SyntheticTest(OWN_TEST_PREFIX + UUID.randomUUID())
            synthetic = test
            status.value = status.value.copy(synthetic = SyntheticResult.WAITING, syntheticDndMatch = null,
                dismissal = DismissalDiagnostic())
            val notification = Notification.Builder(context, TEST_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Nova Guardian test")
                .setContentText(TEST_TEXT)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setTimeoutAfter(5 * 60 * 1_000L)
                .build()
            if (runCatching { manager.notify(test.tag, TEST_ID, notification) }.isFailure) {
                finishSynthetic(SyntheticResult.UNSUPPORTED)
                return
            }
            handler.postDelayed({
                if (synthetic === test) finishSynthetic(SyntheticResult.NOT_RECEIVED)
            }, 10_000)
        }

        fun dismissPresentedTest(context: Context) {
            if (synthetic != null || status.value.synthetic == SyntheticResult.DISMISSING) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val active = runCatching { manager.activeNotifications }.getOrElse {
                dismissalFailed("Android could not list active NovaOS notifications.")
                return
            }
            val target = active.filter {
                it.packageName == context.packageName && it.user == Process.myUserHandle() &&
                    it.id == TEST_ID && it.tag?.startsWith(OWN_TEST_PREFIX) == true &&
                    it.notification.channelId == TEST_CHANNEL &&
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }.maxByOrNull { it.postTime }
            if (target == null) {
                dismissalFailed("No active NovaOS presentation test found. Post one first.")
                return
            }
            status.value = status.value.copy(synthetic = SyntheticResult.DISMISSING,
                dismissal = DismissalDiagnostic(true, true, true, "NotificationManager · tag and ID", true))
            if (runCatching { manager.cancel(target.tag, target.id) }.isFailure) {
                dismissalFailed("Android rejected the cancellation request.")
                return
            }
            verifyDismissal(
                active = { manager.activeNotifications.any { it.tag == target.tag && it.id == target.id } },
                removed = { true },
            )
        }

        const val FOLD_FIXTURE_TAG = "nova-guardian-fold-dismissal"
        private const val FOLD_FIXTURE_TEXT = "Harmless-Nova-Guardian-test"
        private const val FOLD_FIXTURE_TITLE = "Nova-Guardian-fixture"
        private var foldFixtureVerified = false

        private fun dismissalPrefs(context: Context) = context.getSharedPreferences("guardian_main_dismissal", Context.MODE_PRIVATE)

        fun foldDismissalAccepted(context: Context): Boolean = dismissalPrefs(context).getBoolean("fold_accepted", false)

        fun automaticDismissalEnabled(context: Context): Boolean =
            foldDismissalAccepted(context) && dismissalPrefs(context).getBoolean("main_enabled", false)

        fun confirmFoldDismissal(context: Context): Boolean =
            foldFixtureVerified && hasAccess(context) && status.value.connected &&
                dismissalPrefs(context).edit().putBoolean("fold_accepted", true).commit()

        fun setAutomaticDismissal(context: Context, enabled: Boolean): Boolean {
            if (enabled && (!foldDismissalAccepted(context) || !GuardianCapture.isEnabled(context) ||
                    !hasAccess(context) || !status.value.connected)) return false
            return dismissalPrefs(context).edit().putBoolean("main_enabled", enabled).commit()
        }

        /** Only the identified, harmless shell fixture is eligible from the compatibility screen. */
        internal fun dismissExternalTestFixture(context: Context, tag: String): Boolean {
            val fold = tag == FOLD_FIXTURE_TAG
            if ((!fold && (!BuildConfig.DEBUG || !tag.startsWith(EXTERNAL_FIXTURE_PREFIX))) || !hasAccess(context) ||
                !status.value.connected || synthetic != null || status.value.synthetic == SyntheticResult.DISMISSING
            ) return false
            val listener = connectedListener ?: return false
            val target = runCatching {
                listener.activeNotifications.singleOrNull {
                    it.packageName == "com.android.shell" && it.tag == tag &&
                        it.user == Process.myUserHandle() &&
                        it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                        (!fold || (it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == FOLD_FIXTURE_TITLE &&
                            it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == FOLD_FIXTURE_TEXT))
                }
            }.getOrNull() ?: return false
            if (!target.isClearable) return false
            val attempt = ExternalAttempt(target.key)
            if (fold) foldFixtureVerified = false
            externalAttempt = attempt
            status.value = status.value.copy(synthetic = SyntheticResult.DISMISSING,
                foldFixtureVerified = if (fold) false else status.value.foldFixtureVerified,
                dismissal = DismissalDiagnostic(true, true, false, "NotificationListenerService · exact key", true))
            GuardianCapture.verifyFixture(context) { secured ->
                if (externalAttempt !== attempt) return@verifyFixture
                val active = runCatching { listener.getActiveNotifications(arrayOf(target.key)).singleOrNull() }.getOrNull()
                if (!secured || connectedListener !== listener || !hasAccess(context) || active == null ||
                    active.key != target.key || active.postTime != target.postTime || active.user != target.user ||
                    active.packageName != target.packageName || !active.isClearable ||
                    active.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
                    active.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() !=
                        target.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()) {
                    dismissalFailed(if (!secured) "The encrypted fixture receipt could not be verified."
                        else "The external fixture changed or the listener disconnected.")
                    return@verifyFixture
                }
                status.value = status.value.copy(dismissal = status.value.dismissal.copy(requested = true))
                if (runCatching { listener.cancelNotification(target.key) }.isFailure) {
                    dismissalFailed("Android rejected the listener cancellation request.")
                    return@verifyFixture
                }
                verifyDismissal(
                    active = { listener.getActiveNotifications(arrayOf(target.key)).any { it.key == target.key } },
                    removed = { attempt.removedByListener },
                    onRemoved = {
                        if (fold) {
                            foldFixtureVerified = true
                            status.value = status.value.copy(foldFixtureVerified = true)
                        }
                    },
                )
            }
            return true
        }

        private fun verifyDismissal(active: () -> Boolean, removed: () -> Boolean, onRemoved: () -> Unit = {}) {
            val deadline = SystemClock.elapsedRealtime() + 5_000L
            fun checkActive() {
                if (status.value.synthetic != SyntheticResult.DISMISSING) return
                val present = runCatching(active).getOrNull()
                status.value = status.value.copy(dismissal = status.value.dismissal.copy(stillActive = present))
                if (present == false && removed()) {
                    externalAttempt = null
                    finishSynthetic(SyntheticResult.DISMISSED)
                    onRemoved()
                } else if (SystemClock.elapsedRealtime() >= deadline) {
                    dismissalFailed(if (present == true) "The exact notification remains active."
                        else "Removal could not be confirmed by Android.")
                } else handler.postDelayed({ checkActive() }, 150L)
            }
            handler.postDelayed({ checkActive() }, 150L)
        }

        private fun dismissalFailed(reason: String) {
            externalAttempt = null
            status.value = status.value.copy(dismissal = status.value.dismissal.copy(failure = reason))
            finishSynthetic(SyntheticResult.DISMISS_FAILED)
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
            if (connectedListener === context) connectedListener = null
            if (externalAttempt != null) dismissalFailed("Notification listener disconnected.")
            if (synthetic != null) finishSynthetic(SyntheticResult.DISCONNECTED)
            if (!hasAccess(context)) {
                foldFixtureVerified = false
                setAutomaticDismissal(context, false)
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
    val dismissal: DismissalDiagnostic = DismissalDiagnostic(),
    val foldFixtureVerified: Boolean = false,
    val mainDismissal: String? = null,
)

internal data class DismissalDiagnostic(
    val posted: Boolean = false,
    val matched: Boolean = false,
    val requested: Boolean = false,
    val mechanism: String? = null,
    val stillActive: Boolean? = null,
    val failure: String? = null,
)

internal data class GuardianCallObservations(val events: Int = 0, val missed: Int = 0, val underDnd: Int = 0)

internal enum class SyntheticResult(val label: String) {
    NOT_TESTED("Not tested"),
    WAITING("Waiting for Android listener"),
    PRESENTED("Received and verified with encrypted in-memory capture"),
    DISMISSING("Cancellation requested · checking active notifications"),
    DISMISSED("Dismissed and absent from Android’s active notifications"),
    CAPTURE_FAILED("Secure test capture failed"),
    DISMISS_FAILED("Android dismissal could not be verified"),
    NOT_RECEIVED("Test notification was not received"),
    UNSUPPORTED("Test channel or notification is unavailable"),
    PERMISSION_REQUIRED("Notification posting permission required"),
    DISCONNECTED("Listener disconnected during the test"),
}
