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
import uk.co.traynor.privategallery.launcher.GuardianAlertDelivery
import uk.co.traynor.privategallery.launcher.GuardianSafeNotifications
import uk.co.traynor.privategallery.launcher.LauncherAppIdentity

/** Owner-started diagnostics and separately authorised Main WhatsApp capture. */
internal class GuardianNotificationListener : NotificationListenerService() {
    private val changedRetries = LinkedHashMap<String, Pair<Long, Int>>()
    private val mainAttempts = mutableMapOf<String, ExternalAttempt>()
    private val mainRetries = LinkedHashMap<Pair<String, Long>, Pair<Long, Int>>()
    private val deliveredReplacements = LinkedHashSet<String>()
    private val summaryPosts = LinkedHashMap<String, Long>()
    private val diagnosticSlots = LinkedHashMap<String, Long>()
    private var nextDiagnosticSlot = 0L
    private val callWorker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(16), ThreadPoolExecutor.DiscardPolicy())

    private fun trustedFriendsWhatsApp(sbn: StatusBarNotification, personal: Boolean, serial: Long): Boolean =
        probeAccount(personal, serial, configuredFriendsSerial(this)) == ProbeAccount.FRIENDS &&
            sbn.packageName == GUARDIAN_WHATSAPP && sbn.opPkg == GUARDIAN_WHATSAPP &&
            runCatching {
                getSystemService(LauncherApps::class.java)
                    ?.getApplicationInfo(GUARDIAN_WHATSAPP, 0, sbn.user)?.uid == sbn.uid
            }.getOrDefault(false)

    private fun friendsEvent(notification: Notification, serial: Long, observedAt: Long): GuardianCaptureEvent? {
        if (notification.category != null && notification.category != Notification.CATEGORY_MESSAGE) return null
        val identity = notification.shortcutId?.takeIf { it.isNotBlank() && it.length <= 480 }?.let { "shortcut:$it" }
            ?: guardianDirectMessageIdentity(notification) ?: return null
        val entries = guardianCaptureEntries(notification)
        if (entries.isEmpty()) return null
        return GuardianCaptureEvent(serial, identity, observedAt, entries, requiresContactConsent = true, captureAccount = "DUAL_MESSENGER")
    }

    private fun captureFriendsMessage(sbn: StatusBarNotification, serial: Long, enabled: Boolean, discovering: Boolean, trace: Long?) {
        val event = friendsEvent(sbn.notification, serial, System.currentTimeMillis()) ?: run {
            GuardianPipelineDiagnostics.update(trace) { it.copy(capture = GuardianCaptureStage.NO_MESSAGE.label) }
            return
        }
        val entries = event.entries
        GuardianPipelineDiagnostics.update(trace) { it.copy(entries = entries.size) }
        val key = GuardianConversationKey(GUARDIAN_WHATSAPP, serial, event.conversation)
        if (discovering) {
            val name = guardianSenderDisplayName(entries.mapNotNull { it.displayName }.distinct().singleOrNull())
            GuardianCapture.offerContact(GuardianContact(key, name ?: "Dual Messenger conversation"))
        }
        if (enabled) {
            GuardianCapture.record(this, event, key, trace) { outcome ->
                dismissStoredWhatsApp(sbn, event, null, outcome, friends = true, trace = trace)
            }
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
        if (GuardianCapture.isEnabled(this)) {
            GuardianCapture.refresh(this)
            val cutoff = System.currentTimeMillis() - 30_000
            runCatching {
                val ranking = currentRanking
                val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull()
                activeNotifications.asSequence().filter {
                    val category = it.notification.category
                    val messagingSource = it.packageName == GUARDIAN_WHATSAPP &&
                        (category == null || category == Notification.CATEGORY_MESSAGE) ||
                        it.packageName == sms && category == Notification.CATEGORY_MESSAGE
                    messagingSource && !isSummary(it) && it.postTime >= cutoff
                }.take(8).forEach { onNotificationPosted(it, ranking) }
            }.onFailure { reportMainDismissal("Android could not recover recent active notifications") }
            if (automaticDismissalEnabled(this)) runCatching {
                mainSnapshot().asSequence().filter { it.postTime < cutoff }
                    .mapNotNull { sbn -> reconciliationEvent(sbn)?.let { sbn to it } }
                    .take(16).forEach { (sbn, event) -> queueReconciliation(sbn, event) }
            }.onFailure { reportMainDismissal("Android could not recheck older active originals") }
        }
    }

    private fun mainSnapshot(): List<StatusBarNotification> =
        activeNotifications.filter { it.packageName == GUARDIAN_WHATSAPP && it.user == Process.myUserHandle() }

    private fun reconciliationEvent(sbn: StatusBarNotification): GuardianCaptureEvent? {
        if (sbn.packageName != GUARDIAN_WHATSAPP || sbn.opPkg != GUARDIAN_WHATSAPP ||
            sbn.user != Process.myUserHandle() || staticIneligibility(sbn) != null
        ) return null
        val uid = runCatching { packageManager.getApplicationInfo(GUARDIAN_WHATSAPP, 0).uid }.getOrNull()
        val serial = getSystemService(UserManager::class.java)?.getSerialNumberForUser(sbn.user) ?: return null
        val account = probeAccount(true, serial, configuredFriendsSerial(this))
        val event = guardianMainNotificationEvent(sbn.notification, account, sbn.packageName,
            uid != null && uid == sbn.uid, serial, sbn.key, System.currentTimeMillis()) ?: return null
        return event.takeIf { allNotificationMessagesRetained(sbn.notification, it) }
    }

    private fun queueReconciliation(sbn: StatusBarNotification, event: GuardianCaptureEvent) {
        if (connectedListener !== this || !hasAccess(this) || !automaticDismissalEnabled(this) ||
            !GuardianCapture.isEnabled(this)
        ) return
        GuardianCapture.record(this, event, GuardianConversationKey(event.app, event.profileSerial, event.conversation)) {
            outcome -> dismissStoredWhatsApp(sbn, event, null, outcome)
        }
    }

    @Synchronized
    private fun isSummary(sbn: StatusBarNotification): Boolean {
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            if (sbn.packageName == GUARDIAN_WHATSAPP) {
                summaryPosts.remove(sbn.key)
                summaryPosts[sbn.key] = sbn.postTime
                if (summaryPosts.size > 128) summaryPosts.remove(summaryPosts.keys.first())
            }
            return true
        }
        // Android can redispatch the same post without the summary flag when it regroups notifications.
        return summaryPosts[sbn.key] == sbn.postTime
    }

    private fun staticIneligibility(sbn: StatusBarNotification): String? =
        if (isSummary(sbn)) "Group summary excluded"
        else guardianOriginalIneligibility(sbn.notification, sbn.isClearable)

    private fun retryChangedOriginal(key: String, friends: Boolean, trace: Long?) {
        val now = SystemClock.elapsedRealtime()
        changedRetries.entries.removeAll { now - it.value.first >= 30_000 }
        val prior = changedRetries[key]
        val count = prior?.second ?: 0
        if (count >= 3 || (prior == null && changedRetries.size >= 64)) {
            val reason = "Changed original recheck limit reached"
            reportDismissal(friends, reason)
            GuardianPipelineDiagnostics.update(trace) { it.copy(removal = reason) }
            return
        }
        changedRetries[key] = (prior?.first ?: now) to (count + 1)
        handler.postDelayed({
            val automatic = if (friends) friendsDismissalEnabled(this) else automaticDismissalEnabled(this)
            if (connectedListener === this && hasAccess(this) && automatic && GuardianCapture.isEnabled(this)) {
                val latest = runCatching { getActiveNotifications(arrayOf(key)).singleOrNull() }.getOrNull()
                if (latest != null) onNotificationPosted(latest, currentRanking)
            }
        }, 300L shl count)
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
        val summary = isSummary(sbn)
        val trace = if (isWhatsApp && !call) {
            val slot = diagnosticSlots.getOrPut(sbn.key) { ++nextDiagnosticSlot }
            if (diagnosticSlots.size > 64) diagnosticSlots.remove(diagnosticSlots.keys.first())
            val profile = if (personal) account else runCatching {
                probeAccount(false, serial, configuredFriendsSerial(this))
            }.getOrDefault(account)
            GuardianPipelineDiagnostics.record(slot, profile,
                sbn.isGroup, summary, sbn.isClearable, locked, currentInterruptionFilter in DND_FILTERS,
                GuardianSafeNotifications.presentationMode(this))
        } else null
        fun traceCapture(reason: String) = GuardianPipelineDiagnostics.update(trace) { it.copy(capture = reason) }
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
            captureFriendsMessage(sbn, serial, captureEnabled, discovering, trace)
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
            if (summary) traceCapture("Group summary left to Android")
            else if (isWhatsApp && personal) traceCapture(GuardianCaptureStage.SOURCE.label)
            else if (!captureEnabled) traceCapture(GuardianCaptureStage.OFF.label)
            return
        }
        if (session == null && !captureEnabled && !discovering) {
            if (trustedPersonalMessage && isWhatsApp) GuardianCapture.noteMainCapture(GuardianCaptureStage.OFF)
            traceCapture(GuardianCaptureStage.OFF.label)
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
                    GuardianPipelineDiagnostics.update(trace) { it.copy(entries = received.entries.size) }
                    val armed = currentMainTest(this, sbn.key)?.takeIf { test ->
                        guardianMatchesLatestTestMessage(received, test.message)
                    }
                    val onDurable: ((GuardianDurableNotification) -> Unit)? = if (isWhatsApp && account == ProbeAccount.MAIN) {
                        { outcome -> dismissStoredWhatsApp(sbn, received, armed, outcome, trace = trace) }
                    } else null
                    GuardianCapture.record(this, received, GuardianConversationKey(received.app, serial, received.conversation), trace, onDurable)
                } else if (isWhatsApp) {
                    val stage = if (notification.visibility == Notification.VISIBILITY_SECRET) GuardianCaptureStage.PRIVATE
                        else GuardianCaptureStage.NO_MESSAGE
                    GuardianCapture.noteMainCapture(stage)
                    traceCapture(stage.label)
                }
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

    private fun dismissStoredWhatsApp(
        original: StatusBarNotification,
        captured: GuardianCaptureEvent,
        armed: GuardianMainDismissalTest?,
        outcome: GuardianDurableNotification,
        friends: Boolean = false,
        trace: Long? = null,
    ) {
        fun report(message: String) {
            reportDismissal(friends, message)
            GuardianPipelineDiagnostics.update(trace) { it.copy(removal = message) }
        }
        if (connectedListener !== this || !hasAccess(this) || !GuardianCapture.isEnabled(this)) {
            report("Listener access or capture unavailable · original kept")
            return
        }
        val test = armed != null
        val automatic = if (friends) friendsDismissalEnabled(this) else automaticDismissalEnabled(this)
        val mode = GuardianSafeNotifications.presentationMode(this)
        val selectedNotice = outcome.held ?: outcome.replacement.takeIf { mode == GuardianPresentationMode.GUARDIAN }
        val delivery = selectedNotice?.let {
            runCatching { GuardianSafeNotifications.replacementAvailability(this, it) }.getOrDefault(GuardianAlertDelivery.CHANNEL_BLOCKED)
        }
        val action = guardianPresentationAction(
            mode, automatic, outcome.quietCollection && (!friends || automatic),
            outcome.held != null, selectedNotice != null, delivery == GuardianAlertDelivery.READY,
        )
        GuardianPipelineDiagnostics.update(trace) {
            it.copy(mode = mode, action = action, delivery = delivery?.label)
        }
        if (armed != null) {
            if (currentMainTest(this, original.key) !== armed || !guardianMatchesLatestTestMessage(captured, armed.message)) return
        } else if (action == GuardianPresentationAction.KEEP_ORIGINAL) {
            val reason = when {
                !automatic -> "Automatic dismissal is off for this profile"
                selectedNotice != null && delivery != GuardianAlertDelivery.READY ->
                    "Guardian alert unavailable · ${delivery?.label}"
                outcome.latestAssessment == GuardianAssessment.UNCERTAIN -> "Uncertain with no active hold"
                outcome.latestAssessment == GuardianAssessment.LOW_RISK -> "Low risk with no active hold"
                else -> "No active hold or fully assessed bundle"
            }
            report("$reason · original kept")
            return
        }
        val now = SystemClock.elapsedRealtime()
        val current = runCatching { getActiveNotifications(arrayOf(original.key)).singleOrNull() }.getOrElse {
            report("Android could not check the active notification")
            return
        } ?: run {
            report("Original key is no longer active")
            return
        }
        val uid = runCatching { packageManager.getApplicationInfo(GUARDIAN_WHATSAPP, 0).uid }.getOrNull()
        val serial = getSystemService(UserManager::class.java)?.let { users ->
            runCatching { users.getSerialNumberForUser(current.user) }.getOrNull()
        } ?: return
        val personal = current.user == Process.myUserHandle()
        val trusted = if (friends) !personal && trustedFriendsWhatsApp(current, false, serial)
            else personal && uid != null && current.uid == uid
        val currentEvent = if (friends) friendsEvent(current.notification, serial, captured.observedAt)
            else guardianMainNotificationEvent(current.notification, ProbeAccount.MAIN, current.packageName,
                true, serial, current.key, captured.observedAt)
        val identityMatches = current.packageName == GUARDIAN_WHATSAPP &&
            current.opPkg == GUARDIAN_WHATSAPP && trusted && serial == captured.profileSerial
        val changed = current.postTime != original.postTime || (currentEvent != null && currentEvent != captured)
        val reason = when {
            !identityMatches -> "Original package, UID or profile did not match"
            staticIneligibility(current) != null -> staticIneligibility(current)
            currentEvent == null -> "Original has no supported message payload"
            changed -> "Active original changed after capture"
            !allNotificationMessagesRetained(current.notification, captured) -> "Original message coverage incomplete"
            else -> null
        }
        if (reason != null) {
            report(reason)
            if (identityMatches && changed && staticIneligibility(current) == null && !test) {
                retryChangedOriginal(current.key, friends, trace)
            }
            return
        }

        mainRetries.entries.removeAll { now - it.value.first >= 30_000 }
        val post = current.key to current.postTime
        val retries = mainRetries[post]
        if (mainAttempts.size >= 16 && current.key !in mainAttempts ||
            post !in mainRetries && mainRetries.size >= 64 ||
            (retries?.second ?: 0) >= 3) {
            report("Cancellation retry limit reached")
            return
        }
        if (current.key in mainAttempts) {
            // Wait for the active attempt to observe this repost and recheck its archived content.
            mainAttempts.getValue(current.key).repost = {
                val latest = runCatching { getActiveNotifications(arrayOf(current.key)).singleOrNull() }.getOrNull()
                if (latest != null) onNotificationPosted(latest, currentRanking)
            }
            report("Another archived update is waiting for the active cancellation attempt")
            return
        }
        if (armed != null && !armed.bind(current.key, now)) return
        mainRetries[post] = (retries?.first ?: now) to ((retries?.second ?: 0) + 1)
        val attempt = ExternalAttempt(current.key, current.postTime)
        mainAttempts[current.key] = attempt
        report("Cancellation requested after encrypted capture")
        if (test) status.value = status.value.copy(mainTestMessage = null)
        fun finish(message: String, verified: Boolean = false) {
            if (mainAttempts[attempt.key] !== attempt) return
            mainAttempts.remove(attempt.key)
            val retry = attempt.repost?.takeIf { !verified && connectedListener === this && hasAccess(this) }
            if (test && retry == null) {
                if (mainTest === armed) mainTest = null
                mainTestRemoved = verified
            }
            val result = if (verified && !test && action == GuardianPresentationAction.REPLACE && selectedNotice != null)
                "$message · " + deliverReplacement(selectedNotice, outcome.safe?.id == selectedNotice.id, mode, trace)
                else message
            report(result)
            status.value = status.value.copy(mainTestRemoved = mainTestRemoved)
            retry?.invoke()
        }
        if (runCatching { cancelNotification(current.key) }.isFailure) {
            finish("Android rejected the cancellation request")
            return
        }
        val until = SystemClock.elapsedRealtime() + 5_000
        fun verify() {
            if (mainAttempts[attempt.key] !== attempt) return
            if (connectedListener !== this || !hasAccess(this)) {
                finish("Notification listener disconnected")
                return
            }
            val active = runCatching { getActiveNotifications(arrayOf(attempt.key)).any { it.key == attempt.key } }.getOrNull()
            when (guardianRemovalObservation(active, attempt.removedByListener,
                attempt.repost != null, SystemClock.elapsedRealtime() >= until)) {
                GuardianRemovalObservation.CONFIRMED -> finish("Android confirmed exact notification removal", true)
                GuardianRemovalObservation.ABSENT_UNCONFIRMED ->
                    finish("Original notification is no longer active; removal reason unconfirmed")
                GuardianRemovalObservation.REPOSTED -> finish("Original notification reposted")
                GuardianRemovalObservation.UNVERIFIED -> {
                    val hasRepost = attempt.repost != null
                    finish("Android removal could not be verified")
                    if (active == true && !hasRepost && !test) retryChangedOriginal(attempt.key, friends, trace)
                }
                GuardianRemovalObservation.WAITING -> handler.postDelayed({ verify() }, 150)
            }
        }
        handler.postDelayed({ verify() }, 150)
    }

    private fun deliverReplacement(row: GuardianInboxRow, allowPreview: Boolean, mode: GuardianPresentationMode, trace: Long?): String {
        fun reportDelivery(message: String) = GuardianPipelineDiagnostics.update(trace) { it.copy(delivery = message) }
        fun active() = runCatching { GuardianSafeNotifications.replacementActive(this, row) }.getOrDefault(false)
        if (row.id in deliveredReplacements || active()) {
            reportDelivery("Replacement already delivered")
            return "Guardian alert already delivered"
        }
        val posted = runCatching { GuardianSafeNotifications.post(this, row, allowPreview, mode) }.getOrDefault(false)
        if (!posted) {
            val reason = runCatching { GuardianSafeNotifications.replacementAvailability(this, row) }.getOrNull()
            reportDelivery(reason?.takeUnless { it == GuardianAlertDelivery.READY }?.label ?: "Guardian alert could not be posted")
            return "Guardian alert unavailable"
        }
        deliveredReplacements.add(row.id)
        if (deliveredReplacements.size > 128) deliveredReplacements.remove(deliveredReplacements.first())
        reportDelivery("Android post requested")
        fun observe(remaining: Int) {
            if (active()) reportDelivery("Guardian alert active in Android")
            else if (remaining > 0) handler.postDelayed({ observe(remaining - 1) }, 250)
            else {
                deliveredReplacements.remove(row.id)
                reportDelivery("Posted but Android did not report an active alert")
            }
        }
        handler.postDelayed({ observe(3) }, 250)
        return "Guardian alert posted"
    }

    private fun reportMainDismissal(message: String) = reportDismissal(false, message)

    private fun reportDismissal(friends: Boolean, message: String) {
        android.util.Log.i("NovaGuardianDismissal", message)
        status.value = if (friends) status.value.copy(friendsDismissal = message)
            else status.value.copy(mainDismissal = message)
    }

    private fun allNotificationMessagesRetained(notification: Notification, captured: GuardianCaptureEvent): Boolean =
        runCatching {
            val bundles = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
                ?: return@runCatching false
            val entries = Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
            val self = notification.extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
            // A MessagingStyle entry without a sender is an outgoing reply by the style's owner.
            val hasMessagingOwner = notification.extras.getString(Notification.EXTRA_TEMPLATE) ==
                Notification.MessagingStyle::class.java.name && !self?.name.isNullOrBlank()
            entries.size == bundles.size &&
                entries.all { entry -> (entry.senderPerson != null || hasMessagingOwner) &&
                    !entry.text.isNullOrBlank() && entry.dataUri == null } &&
                entries.count { it.senderPerson != null && !guardianIsOwnMessage(self, it.senderPerson) } == captured.entries.size
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
        if (reason == REASON_LISTENER_CANCEL) {
            mainAttempts[sbn.key]?.takeIf { it.postTime == sbn.postTime }?.removedByListener = true
        }
    }

    companion object {
        private const val TEST_CHANNEL = "guardian-compatibility-test"
        private const val TEST_ID = 77
        private const val TEST_TEXT = "Harmless Nova Guardian compatibility notification."
        private const val OWN_TEST_PREFIX = "nova-guardian-presentation-"
        private const val EXTERNAL_FIXTURE_PREFIX = "nova-guardian-shell-fixture-"
        private data class SyntheticTest(val tag: String)
        private data class ExternalAttempt(val key: String, val postTime: Long, var removedByListener: Boolean = false) {
            var repost: (() -> Unit)? = null
        }
        private var synthetic: SyntheticTest? = null
        private var externalAttempt: ExternalAttempt? = null
        private var connectedListener: GuardianNotificationListener? = null

        internal fun recheckMainNotifications(context: Context, reconcile: Boolean = false): String {
            val listener = connectedListener?.takeIf { hasAccess(context) }
                ?: return "Notification listener is unavailable."
            if (reconcile && (!automaticDismissalEnabled(context) || !GuardianCapture.isEnabled(context))) {
                return "Enable encrypted capture and automatic Main dismissal before reconciling."
            }
            return runCatching {
                val active = listener.mainSnapshot()
                val reasons = active.groupingBy { original ->
                    listener.staticIneligibility(original) ?: if (listener.reconciliationEvent(original) == null)
                        "Unsupported payload, source or incomplete message coverage" else "Eligible for archive verification"
                }.eachCount()
                val candidates = if (reconcile) active.asSequence()
                    .mapNotNull { original -> listener.reconciliationEvent(original)?.let { original to it } }
                    .take(16).toList() else emptyList()
                candidates.forEach { (original, event) -> listener.queueReconciliation(original, event) }
                buildString {
                    append("Active Main WhatsApp originals: ${active.size}")
                    reasons.forEach { (reason, count) -> append("\n$reason: $count") }
                    if (reconcile) append("\nSubmitted for encrypted archive verification: ${candidates.size}")
                }
            }.getOrElse { "Android could not inspect active Main WhatsApp notifications." }
        }

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

        @Volatile private var mainTest: GuardianMainDismissalTest? = null
        private var mainTestRemoved = false

        fun mainDismissalAccepted(context: Context): Boolean =
            dismissalPrefs(context).getBoolean("main_fold_accepted", false)

        fun automaticDismissalEnabled(context: Context): Boolean =
            mainDismissalAccepted(context) && dismissalPrefs(context).getBoolean("main_enabled", false)

        fun friendsDismissalEnabled(context: Context): Boolean =
            dismissalPrefs(context).getBoolean("friends_enabled", false)

        fun setFriendsDismissal(context: Context, enabled: Boolean): Boolean =
            dismissalPrefs(context).edit().putBoolean("friends_enabled", enabled).commit()

        private fun currentMainTest(context: Context, key: String): GuardianMainDismissalTest? =
            mainTest?.takeIf { foldDismissalAccepted(context) && it.mayTarget(key, SystemClock.elapsedRealtime()) }

        fun armMainDismissalTest(context: Context): Boolean {
            if (!foldDismissalAccepted(context) || !hasAccess(context) || !status.value.connected ||
                !GuardianCapture.isEnabled(context) || connectedListener?.mainAttempts?.isNotEmpty() == true ||
                automaticDismissalEnabled(context)) return false
            val armed = GuardianMainDismissalTest("Nova Guardian test ${UUID.randomUUID().toString().take(12)}",
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
            val attempt = ExternalAttempt(target.key, target.postTime)
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
                connectedListener?.let { listener ->
                    if (listener.mainAttempts.isNotEmpty()) listener.reportMainDismissal("Notification listener disconnected")
                    listener.mainAttempts.clear()
                }
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
                setFriendsDismissal(context, false)
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
    val friendsDismissal: String? = null,
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


/** A test token in older bundled history must not authorize dismissal. */
internal fun guardianMatchesLatestTestMessage(event: GuardianCaptureEvent, token: String): Boolean {
    val latest = event.entries.maxOfOrNull { it.time } ?: return false
    return event.entries.singleOrNull { it.time == latest }?.text == token
}

/** Convert only Android-provided incoming MessagingStyle messages from the verified personal WhatsApp UID. */
internal fun guardianMainNotificationEvent(
    notification: Notification, account: ProbeAccount, packageName: String, uidMatches: Boolean,
    profileSerial: Long, notificationKey: String, now: Long,
): GuardianCaptureEvent? {
    val summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
    if (!canCaptureGuardianNotification(uidMatches, packageName, account, summary) || profileSerial < 0 ||
        notification.visibility == Notification.VISIBILITY_SECRET ||
        (notification.category != null && notification.category != Notification.CATEGORY_MESSAGE)) return null
    val messages = guardianCaptureEntries(notification)
    if (messages.isEmpty()) return null
    val conversation = notification.shortcutId?.takeIf { it.isNotBlank() && it.length <= 480 }?.let { "shortcut:$it" }
        ?: guardianDirectMessageIdentity(notification)
        ?: "notification:$notificationKey".takeIf { notificationKey.isNotBlank() && it.length <= 512 }
        ?: return null
    return GuardianCaptureEvent(profileSerial, conversation, now, messages, captureAccount = "MAIN_WHATSAPP")
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
                if (guardianIsOwnMessage(self, sender)) return@mapNotNull null
                GuardianCaptureEntry(entry.timestamp, sender.key ?: sender.uri ?: sender.name?.toString().orEmpty(),
                    entry.text?.toString().orEmpty(), sender.name?.toString())
            }
    }.getOrDefault(emptyList())

private fun guardianIsOwnMessage(self: Person?, sender: Person?): Boolean =
    (self?.key != null && self.key == sender?.key) || (self?.uri != null && self.uri == sender?.uri)

internal fun guardianOriginalIneligibility(notification: Notification, clearable: Boolean): String? = when {
    notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> "Group summary left visible"
    notification.flags and Notification.FLAG_ONGOING_EVENT != 0 -> "Ongoing original left visible"
    !clearable -> "Android marks the original as non-clearable"
    notification.visibility == Notification.VISIBILITY_SECRET -> "Secret original excluded"
    else -> null
}
