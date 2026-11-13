package uk.co.traynor.privategallery.core.guardian

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.Person
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.provider.Telephony
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
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
    private var mainAttempt: ExternalAttempt? = null
    private val callWorker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(16), ThreadPoolExecutor.DiscardPolicy())

    private fun trustedFriendsWhatsApp(sbn: StatusBarNotification, personal: Boolean, serial: Long): Boolean =
        probeAccount(personal, serial, configuredFriendsSerial(this)) == ProbeAccount.FRIENDS &&
            sbn.packageName == GUARDIAN_WHATSAPP && sbn.opPkg == GUARDIAN_WHATSAPP &&
            runCatching {
                getSystemService(LauncherApps::class.java)
                    ?.getApplicationInfo(GUARDIAN_WHATSAPP, 0, sbn.user)?.uid == sbn.uid
            }.getOrDefault(false)

    private fun captureFriendsMessage(sbn: StatusBarNotification, serial: Long, enabled: Boolean, discovering: Boolean) {
        val notification = sbn.notification
        if (notification.category != null && notification.category != Notification.CATEGORY_MESSAGE) return
        val identity = notification.shortcutId?.takeIf { it.isNotBlank() && it.length <= 480 }?.let { "shortcut:$it" }
            ?: guardianDirectMessageIdentity(notification) ?: return
        val entries = guardianCaptureEntries(notification)
        if (entries.isEmpty()) return
        val key = GuardianConversationKey(GUARDIAN_WHATSAPP, serial, identity)
        if (discovering) {
            val name = guardianSenderDisplayName(entries.mapNotNull { it.displayName }.distinct().singleOrNull())
            GuardianCapture.offerContact(GuardianContact(key, name ?: "Dual Messenger conversation"))
        }
        if (enabled) {
            GuardianCapture.record(this, GuardianCaptureEvent(serial, identity, System.currentTimeMillis(),
                entries, requiresContactConsent = true), key)
        }
    }

    private fun incomingWhatsAppCaller(sbn: StatusBarNotification): String? {
        val notification = sbn.notification
        if (sbn.packageName != GUARDIAN_WHATSAPP || sbn.opPkg != GUARDIAN_WHATSAPP || !sbn.isClearable ||
            notification.category != Notification.CATEGORY_CALL ||
            notification.visibility == Notification.VISIBILITY_SECRET ||
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        return runCatching {
            val extras = notification.extras
            if (extras.getInt(androidx.core.app.NotificationCompat.EXTRA_CALL_TYPE) != Notification.CallStyle.CALL_TYPE_INCOMING) null
            else extras.getParcelable(androidx.core.app.NotificationCompat.EXTRA_CALL_PERSON, Person::class.java)?.uri
        }.getOrNull()
    }

    private fun screenWhatsAppCall(sbn: StatusBarNotification, personal: Boolean, serial: Long) {
        val caller = incomingWhatsAppCaller(sbn) ?: return
        if (personal && runCatching { packageManager.getApplicationInfo(GUARDIAN_WHATSAPP, 0).uid }
                .getOrNull() != sbn.uid) return
        callWorker.execute {
            runCatching {
                val account = probeAccount(personal, serial, configuredFriendsSerial(this))
                if (!personal && !trustedFriendsWhatsApp(sbn, personal, serial)) return@runCatching
                val archive = GuardianCaptureStore(this)
                val (enabled, rules) = archive.phoneShieldSettings()
                val mode = guardianWhatsAppCallAction(enabled, account, true, caller, rules)
                if (mode != GuardianCallMode.ALLOW &&
                    connectedListener === this && hasAccess(this)) {
                    val current = getActiveNotifications(arrayOf(sbn.key)).singleOrNull()
                    if (current != null && current.postTime == sbn.postTime && current.uid == sbn.uid &&
                        current.user == sbn.user && incomingWhatsAppCaller(current) == caller) {
                        cancelNotification(sbn.key)
                        val number = guardianPhoneNumber(caller.removePrefix("tel:")) ?: return@runCatching
                        val source = guardianCallSource(GUARDIAN_WHATSAPP, null, personal, account) ?: return@runCatching
                        archive.recordCallAttempt(GuardianCallActivity(number, source, System.currentTimeMillis(), mode))
                    }
                }
            }
        }
    }

    override fun onListenerConnected() {
        connectedListener = this
        if (SystemClock.elapsedRealtime() >= deadline && probe != null) stopProbe()
        status.value = status.value.copy(connected = true, filter = currentInterruptionFilter)
        if (GuardianCapture.isEnabled(this)) GuardianCapture.refresh(this)
    }

    override fun onListenerDisconnected() = disconnect(this)

    override fun onDestroy() {
        disconnect(this)
        callWorker.shutdownNow()
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
        val isWhatsApp = sbn.packageName == GUARDIAN_WHATSAPP
        val discovering = GuardianCapture.isDiscoveringContacts()
        if (probe == null && !isWhatsApp && !discovering && !GuardianCapture.isEnabled(this)) return
        if (!hasAccess(this)) return
        if (probe != null && SystemClock.elapsedRealtime() >= deadline) stopProbe()
        val session = probe
        val captureEnabled = GuardianCapture.isEnabled(this)
        if (session == null && !captureEnabled && !discovering && !isWhatsApp) return
        val notification = sbn.notification
        val call = notification.category == Notification.CATEGORY_CALL || notification.category == Notification.CATEGORY_MISSED_CALL
        val isSms = !isWhatsApp && notification.category == Notification.CATEGORY_MESSAGE &&
            (captureEnabled || discovering) && notification.visibility != Notification.VISIBILITY_SECRET &&
            runCatching { Telephony.Sms.getDefaultSmsPackage(this) == sbn.packageName }.getOrDefault(false)
        if (!isWhatsApp && !isSms && !call) return
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
            if (isWhatsApp && !summary) screenWhatsAppCall(sbn, personal, serial)
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
        if (isWhatsApp && !personal && !summary && (captureEnabled || discovering) &&
            notification.visibility != Notification.VISIBILITY_SECRET && trustedFriendsWhatsApp(sbn, personal, serial)) {
            captureFriendsMessage(sbn, serial, captureEnabled, discovering)
        }
        val personalUid = if ((isWhatsApp || captureEnabled || discovering) && personal && account == ProbeAccount.MAIN) runCatching {
            packageManager.getApplicationInfo(sbn.packageName, 0).uid
        }.getOrNull() else null
        val trustedPersonalMessage = personalUid != null && sbn.uid == personalUid && sbn.opPkg == sbn.packageName &&
            !summary && (notification.category == Notification.CATEGORY_MESSAGE || isWhatsApp && notification.category == null)
        if (isWhatsApp) {
            val observed = when {
                account == ProbeAccount.MAIN && trustedPersonalMessage -> ProbeAccount.MAIN
                account == ProbeAccount.FRIENDS && sbn.opPkg == GUARDIAN_WHATSAPP -> ProbeAccount.FRIENDS
                else -> null
            }
            if (observed != null) status.value = status.value.copy(observedProfiles = status.value.observedProfiles + observed)
        }
        if ((!isWhatsApp || session == null) && !trustedPersonalMessage) {
            if (isWhatsApp && personal && captureEnabled && !summary) GuardianCapture.noteMainCapture(GuardianCaptureStage.SOURCE)
            return
        }
        if (session == null && !captureEnabled && !discovering) {
            if (trustedPersonalMessage && isWhatsApp) GuardianCapture.noteMainCapture(GuardianCaptureStage.OFF)
            return
        }
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
        if (trustedPersonalMessage) {
            val group = notification.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
            val people = entries.mapNotNull { it.senderPerson }.distinctBy { it.key ?: it.uri ?: it.name?.toString() }
            val person = people.singleOrNull()
            val identity = guardianConversationId(notification.shortcutId, person?.key, person?.uri, group || people.size > 1)
            if (identity != null && discovering) {
                val name = guardianSenderDisplayName(if (group || people.size > 1)
                    notification.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                    else person?.name?.toString())
                GuardianCapture.offerContact(GuardianContact(GuardianConversationKey(sbn.packageName, serial, identity),
                    name ?: "Unnamed conversation"))
            }
            if (captureEnabled) {
                val received = if (isWhatsApp) guardianMainNotificationEvent(notification, account, sbn.packageName,
                    personalUid != null && sbn.uid == personalUid && sbn.opPkg == sbn.packageName,
                    serial, sbn.key, System.currentTimeMillis())
                else if (identity == null) null
                else GuardianCaptureEvent(serial, identity, System.currentTimeMillis(),
                    guardianCaptureEntries(notification).filter { guardianMayKeepSms(it.text) }, sbn.packageName)
                if (received != null && received.entries.isNotEmpty()) {
                    val armed = currentMainTest(this)?.takeIf { test ->
                        received.entries.singleOrNull()?.text == test.message
                    }
                    val onDurable: ((Boolean) -> Unit)? = if (isWhatsApp && account == ProbeAccount.MAIN) {
                        { quietCollection -> dismissStoredMain(sbn, received, armed, quietCollection) }
                    } else null
                    GuardianCapture.record(this, received, GuardianConversationKey(received.app, serial, received.conversation), onDurable)
                } else if (isWhatsApp) GuardianCapture.noteMainCapture(GuardianCaptureStage.NO_MESSAGE)
            } else if (isWhatsApp) GuardianCapture.noteMainCapture(GuardianCaptureStage.OFF)
        }
        if (session != null && isWhatsApp) {
            val ranking = Ranking()
            val quiet = rankingMap.getRanking(sbn.key, ranking) && ranking.importance in
                NotificationManager.IMPORTANCE_MIN..NotificationManager.IMPORTANCE_LOW
            val filter = currentInterruptionFilter
            session.record(account, sbn.key, hasText, filter in DND_FILTERS, quiet, sbn.isGroup,
                summary, conversation, estimates, locked)
            status.value = status.value.copy(filter = filter, results = session.results)
        }
    }

    private fun dismissStoredMain(
        original: StatusBarNotification,
        captured: GuardianCaptureEvent,
        armed: MainDismissalTest?,
        quietCollection: Boolean,
    ) {
        if (connectedListener !== this || !hasAccess(this) || mainAttempt != null ||
            !GuardianCapture.isEnabled(this)) return
        val test = armed != null
        if (test) {
            if (currentMainTest(this) !== armed || captured.entries.singleOrNull()?.text != armed?.message) return
        } else if (!quietCollection && !automaticDismissalEnabled(this)) return
        val current = runCatching { activeNotifications.singleOrNull { it.key == original.key } }.getOrNull()
            ?: return
        val uid = runCatching { packageManager.getApplicationInfo(GUARDIAN_WHATSAPP, 0).uid }.getOrNull()
        val serial = getSystemService(UserManager::class.java)?.let { users ->
            runCatching { users.getSerialNumberForUser(current.user) }.getOrNull()
        } ?: return
        if (current.packageName != GUARDIAN_WHATSAPP || current.opPkg != GUARDIAN_WHATSAPP ||
            uid == null || current.uid != uid || current.user != Process.myUserHandle() ||
            serial != captured.profileSerial || current.postTime != original.postTime || !current.isClearable ||
            current.notification.visibility == Notification.VISIBILITY_SECRET ||
            current.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 ||
            current.notification.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) ||
            !allNotificationMessagesRetained(current.notification, captured) ||
            guardianMainNotificationEvent(current.notification, ProbeAccount.MAIN, current.packageName,
                true, serial, current.key, captured.observedAt) != captured
        ) return

        if (test) mainTest = null
        val attempt = ExternalAttempt(current.key)
        mainAttempt = attempt
        status.value = status.value.copy(mainDismissal = "Cancellation requested after encrypted capture", mainTestMessage = null)
        fun finish(verified: Boolean) {
            if (mainAttempt !== attempt) return
            mainAttempt = null
            if (test && verified) mainTestRemoved = true
            status.value = status.value.copy(
                mainDismissal = if (verified) "Android confirmed exact notification removal"
                    else "Android removal could not be verified",
                mainTestRemoved = mainTestRemoved,
            )
        }
        if (runCatching { cancelNotification(current.key) }.isFailure) {
            finish(false)
            return
        }
        val until = SystemClock.elapsedRealtime() + 5_000
        fun verify() {
            if (mainAttempt !== attempt) return
            val absent = runCatching { activeNotifications.none { it.key == attempt.key } }.getOrDefault(false)
            if (connectedListener !== this || !hasAccess(this)) finish(false)
            else if (absent && attempt.removedByListener) finish(true)
            else if (SystemClock.elapsedRealtime() >= until) finish(false)
            else handler.postDelayed({ verify() }, 150)
        }
        handler.postDelayed({ verify() }, 150)
    }

    private fun allNotificationMessagesRetained(notification: Notification, captured: GuardianCaptureEvent): Boolean =
        runCatching {
            val bundles = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
                ?: return@runCatching false
            val entries = Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
            entries.size == bundles.size && entries.size == captured.entries.size &&
                entries.none { it.text.isNullOrBlank() || it.dataUri != null }
        }.getOrDefault(false)

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
        if (mainAttempt?.key == sbn.key && reason == REASON_LISTENER_CANCEL) mainAttempt?.removedByListener = true
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

        fun confirmFoldDismissal(context: Context): Boolean =
            foldFixtureVerified && hasAccess(context) && status.value.connected &&
                dismissalPrefs(context).edit().putBoolean("fold_accepted", true).commit()

        private data class MainDismissalTest(val message: String, val until: Long)
        @Volatile private var mainTest: MainDismissalTest? = null
        private var mainTestRemoved = false

        fun mainDismissalAccepted(context: Context): Boolean =
            dismissalPrefs(context).getBoolean("main_fold_accepted", false)

        fun automaticDismissalEnabled(context: Context): Boolean =
            mainDismissalAccepted(context) && dismissalPrefs(context).getBoolean("main_enabled", false)

        private fun currentMainTest(context: Context): MainDismissalTest? =
            mainTest?.takeIf { foldDismissalAccepted(context) && SystemClock.elapsedRealtime() < it.until }

        fun armMainDismissalTest(context: Context): Boolean {
            if (!foldDismissalAccepted(context) || !hasAccess(context) || !status.value.connected ||
                !GuardianCapture.isEnabled(context) || connectedListener?.mainAttempt != null ||
                automaticDismissalEnabled(context)) return false
            val armed = MainDismissalTest("Nova Guardian test ${UUID.randomUUID().toString().take(12)}",
                SystemClock.elapsedRealtime() + 60_000)
            mainTest = armed
            mainTestRemoved = false
            status.value = status.value.copy(
                mainDismissal = "Waiting for the exact Main WhatsApp test message",
                mainTestRemoved = false,
                mainTestMessage = armed.message,
            )
            handler.postDelayed({
                if (mainTest === armed) {
                    mainTest = null
                    status.value = status.value.copy(mainDismissal = "Main WhatsApp test expired", mainTestMessage = null)
                }
            }, 60_000)
            return true
        }

        fun confirmMainDismissal(context: Context): Boolean =
            mainTestRemoved && hasAccess(context) && status.value.connected &&
                dismissalPrefs(context).edit().putBoolean("main_fold_accepted", true).commit()

        fun setAutomaticDismissal(context: Context, enabled: Boolean): Boolean {
            if (enabled && (!mainDismissalAccepted(context) || !hasAccess(context) ||
                    !status.value.connected || !GuardianCapture.isEnabled(context))) return false
            if (!enabled) mainTest = null
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

        internal fun configuredFriendsSerial(context: Context): Long? =
            SanctuaryStore.get(context).launcherSettings().friendsWhatsApp
                ?.let(LauncherAppIdentity::parse)
                ?.takeIf { it.flattenedComponent.substringBefore('/') == GUARDIAN_WHATSAPP }
                ?.profileSerial

        fun startProbe(context: Context, discoveredCloneSerial: Long? = null) {
            if (!hasAccess(context) || !status.value.connected) return
            handler.removeCallbacks(timeout)
            probe = NotificationProbe()
            probeFriendsSerial = discoveredCloneSerial ?: configuredFriendsSerial(context)
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
            status.value = status.value.copy(results = emptyMap(), calls = emptyMap(), observedProfiles = emptySet())
        }

        private fun disconnect(context: Context) {
            if (connectedListener === context) {
                connectedListener = null
                mainTest = null
                mainTestRemoved = false
            }
            if (externalAttempt != null) dismissalFailed("Notification listener disconnected.")
            if (synthetic != null) finishSynthetic(SyntheticResult.DISCONNECTED)
            if (!hasAccess(context)) {
                uk.co.traynor.privategallery.launcher.GuardianSafeNotifications.cancel(context)
                foldFixtureVerified = false
                setAutomaticDismissal(context, false)
                if (GuardianCapture.isEnabled(context)) GuardianCapture.setEnabled(context, false)
                handler.removeCallbacks(timeout)
                probe = null
                probeFriendsSerial = null
                status.value = ListenerProbeStatus()
            } else {
                status.value = status.value.copy(connected = false, filter = NotificationManager.INTERRUPTION_FILTER_UNKNOWN, mainTestRemoved = false, mainTestMessage = null)
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
    val mainTestRemoved: Boolean = false,
    val mainTestMessage: String? = null,
    val observedProfiles: Set<ProbeAccount> = emptySet(),
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


/** Convert only Android-provided incoming MessagingStyle messages from the verified personal WhatsApp UID. */
internal fun guardianMainNotificationEvent(
    notification: Notification, account: ProbeAccount, packageName: String, uidMatches: Boolean,
    profileSerial: Long, notificationKey: String, now: Long,
): GuardianCaptureEvent? {
    val summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
    if (!canCaptureGuardianNotification(uidMatches, packageName, account, summary) || profileSerial < 0 ||
        (notification.category != null && notification.category != Notification.CATEGORY_MESSAGE)) return null
    val messages = guardianCaptureEntries(notification)
    if (messages.isEmpty()) return null
    val conversation = notification.shortcutId?.takeIf { it.isNotBlank() && it.length <= 480 }?.let { "shortcut:$it" }
        ?: guardianDirectMessageIdentity(notification)
        ?: "notification:$notificationKey".takeIf { notificationKey.isNotBlank() && it.length <= 512 }
        ?: return null
    return GuardianCaptureEvent(profileSerial, conversation, now, messages)
}

private fun guardianDirectMessageIdentity(notification: Notification): String? = runCatching {
    val extras = notification.extras
    val people = extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
        ?.let(Notification.MessagingStyle.Message::getMessagesFromBundleArray).orEmpty().mapNotNull { it.senderPerson }
        .distinctBy { it.key ?: it.uri ?: it.name?.toString() }
    val person = people.singleOrNull()
    guardianConversationId(null, person?.key, person?.uri,
        extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) || people.size > 1)
}.getOrNull()

private fun guardianCaptureEntries(notification: Notification): List<GuardianCaptureEntry> = runCatching {
        val extras = notification.extras
        val self = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
        extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
            ?.let(Notification.MessagingStyle.Message::getMessagesFromBundleArray).orEmpty().mapNotNull { entry ->
                val sender = entry.senderPerson ?: return@mapNotNull null
                if ((self?.key != null && self.key == sender.key) || (self?.uri != null && self.uri == sender.uri)) {
                    return@mapNotNull null
                }
                GuardianCaptureEntry(entry.timestamp, sender.key ?: sender.uri ?: sender.name?.toString().orEmpty(),
                    entry.text?.toString().orEmpty(), sender.name?.toString())
            }
    }.getOrDefault(emptyList())
