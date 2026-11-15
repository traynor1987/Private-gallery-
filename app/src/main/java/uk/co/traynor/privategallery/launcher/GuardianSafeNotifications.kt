package uk.co.traynor.privategallery.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uk.co.traynor.privategallery.core.guardian.*

internal object GuardianSafeNotifications {
    private const val CHANNEL = "guardian-safe-notifications"
    private const val SILENT_CHANNEL = "guardian-safe-silent"
    private const val TAG = "guardian-safe"
    private const val ROW_TAG = "$TAG:"
    private const val ID = 1
    private const val DISMISS = "uk.co.traynor.privategallery.GUARDIAN_REVIEW_LATER"

    fun prefs(context: Context) = context.getSharedPreferences("guardian_safe_notices", Context.MODE_PRIVATE)

    fun retention(context: Context) = GuardianAlertRetention.read(prefs(context).getString("alert_retention", null))

    fun senderMode(context: Context) = GuardianAlertSender.entries.firstOrNull {
        it.name == prefs(context).getString("alert_sender", null)
    } ?: GuardianAlertSender.NAME

    fun lockScreen(context: Context) = GuardianAlertLockScreen.entries.firstOrNull {
        it.name == prefs(context).getString("alert_lock_screen", null)
    } ?: GuardianAlertLockScreen.HIDDEN

    fun source(context: Context, conversation: GuardianConversationKey?): String {
        if (conversation == null) return "Unknown source"
        if (conversation.app != GUARDIAN_WHATSAPP) return "SMS/MMS"
        val own = context.getSystemService(android.os.UserManager::class.java)
            ?.getSerialNumberForUser(android.os.Process.myUserHandle())
        return when (guardianNoticeProfile(conversation.profile, own,
            GuardianNotificationListener.configuredFriendsSerial(context))) {
            ProbeAccount.MAIN -> "Main WhatsApp"
            ProbeAccount.FRIENDS -> "Friends WhatsApp"
            ProbeAccount.UNRESOLVED -> "WhatsApp · profile unknown"
        }
    }

    fun mayPost(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true

    @Synchronized
    fun cancel(context: Context) {
        prefs(context).edit().remove("posted_token").apply()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(TAG, ID)
        manager.activeNotifications.filter { it.id == ID && it.tag?.startsWith(ROW_TAG) == true }
            .forEach { manager.cancel(it.tag, ID) }
    }

    private fun ensureChannels(manager: NotificationManager) {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Nova Guardian", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(SILENT_CHANNEL, "Nova Guardian silent alerts", NotificationManager.IMPORTANCE_LOW))
    }

    fun channelSettings(context: Context): android.content.Intent {
        context.getSystemService(NotificationManager::class.java)?.let(::ensureChannels)
        return android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL)
    }

    @Synchronized
    fun reviewLater(context: Context, intent: Intent) {
        val tag = intent.identifier ?: return
        if (intent.action == DISMISS && tag.startsWith(ROW_TAG)) {
            context.getSystemService(NotificationManager::class.java)?.cancel(tag, ID)
        }
    }

    @Synchronized
    fun reviewed(context: Context, rowId: String) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ROW_TAG + rowId, ID)
    }

    @Synchronized
    fun post(context: Context, row: GuardianInboxRow) {
        val settings = prefs(context)
        if (!settings.getBoolean("enabled", false) || !GuardianNotificationListener.hasAccess(context)) return
        val source = source(context, row.conversation)
        val key = when (source) {
            "Main WhatsApp" -> "main"
            "Friends WhatsApp" -> "friends"
            "SMS/MMS" -> "sms"
            else -> return
        }
        if (!settings.getBoolean(key, false) || !mayPost(context)) return
        val now = java.time.LocalTime.now()
        val kind = guardianNoticeKind(row.assessment, row.patterns.orEmpty())
        val quiet = settings.getBoolean("quiet", false) && guardianNoticeQuietMinute(
            now.hour * 60 + now.minute,
            settings.getInt("quiet_start_minute", settings.getInt("quiet_start", 22) * 60),
            settings.getInt("quiet_end_minute", settings.getInt("quiet_end", 7) * 60),
        )
        val quietRule = GuardianQuietRule.entries.firstOrNull {
            it.name == settings.getString("quiet_rule_${kind.name}", null)
        } ?: GuardianQuietRule.HIDE
        if (quiet && quietRule == GuardianQuietRule.HIDE) return
        val silent = !settings.getBoolean("heads_up", true) || quiet && quietRule == GuardianQuietRule.SILENT
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(manager)
        val store = GuardianCaptureStore(context)
        val contact = runCatching { store.monitoredContacts().singleOrNull { it.key == row.conversation } }
            .getOrElse { return }
        val sender = guardianAlertSender(contact?.alertSender ?: senderMode(context), row.senderName, contact?.alertAlias)
        val lockScreen = lockScreen(context)
        val optedIn = settings.getBoolean("alert_validated_preview", false)
        val eligible = key != "sms" && row.section == GuardianInboxSection.READY &&
            row.ownerLabel != GuardianAssessment.UPSETTING
        val preview = if (optedIn && eligible && kind == GuardianNoticeKind.LOW &&
            uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore.get(context).refresh() == null) {
            runCatching {
                if (guardianAlertMayPreview(optedIn, eligible, kind, store.knowledgeValidation())) {
                    store.revealInboxMessage(row.id, GuardianCapture.retention(context), guardianInboxClock(context))
                        ?.take(320)?.filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }
                        ?.takeIf(String::isNotBlank)
                } else null
            }.getOrNull()
        } else null
        val text = listOfNotNull(sender, source).joinToString(" · ")
        val icon = if (kind == GuardianNoticeKind.LOW && settings.getBoolean("tick", true)) "✓ " else ""
        val route = PendingIntent.getActivity(context, 0, GuardianInboxActivity.safeNotificationsIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val tag = ROW_TAG + row.id
        val reviewLater = PendingIntent.getBroadcast(context, 0,
            Intent(context, GuardianReviewLaterReceiver::class.java).setAction(DISMISS).setIdentifier(tag),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
        val channel = if (silent) SILENT_CHANNEL else CHANNEL
        val publicVersion = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Nova Guardian")
            .setContentText(listOfNotNull(sender, source.takeIf { sender != null }).joinToString(" · ")
                .ifBlank { "Protected notification" })
            .setLocalOnly(true)
            .build()
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Nova Guardian")
            .setContentText(icon + text)
            .setStyle(Notification.BigTextStyle().bigText("$icon$text\n${kind.title}\n" +
                (preview ?: "Message protected until Guardian review.")))
            .applyGuardianAlertPolicy(retention(context), lockScreen, publicVersion)
            .setLocalOnly(true)
            .setOnlyAlertOnce(true)
            .setColor(when (kind) {
                GuardianNoticeKind.LOW -> 0xFF137D57.toInt()
                GuardianNoticeKind.UPSETTING, GuardianNoticeKind.HIGH -> 0xFF996000.toInt()
                GuardianNoticeKind.UNCERTAIN -> 0xFF596477.toInt()
            })
            .setContentIntent(route)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_lock_lock),
                "Open Safe Notifications", route).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_recent_history),
                "Review later", reviewLater).build())
            .build()
        manager.cancel(TAG, ID)
        manager.notify(tag, ID, notification)
    }
}


internal fun Notification.Builder.applyGuardianAlertPolicy(
    retention: GuardianAlertRetention,
    lockScreen: GuardianAlertLockScreen,
    redacted: Notification,
): Notification.Builder = apply {
    setVisibility(when (lockScreen) {
        GuardianAlertLockScreen.HIDDEN -> Notification.VISIBILITY_SECRET
        GuardianAlertLockScreen.SENDER -> Notification.VISIBILITY_PRIVATE
        GuardianAlertLockScreen.FOLLOW -> Notification.VISIBILITY_PUBLIC
    })
    if (lockScreen == GuardianAlertLockScreen.SENDER) setPublicVersion(redacted)
    retention.timeoutMillis?.let { setTimeoutAfter(it) }
}

/** Dismisses only the selected Nova alert. The encrypted message stays in Guardian. */
class GuardianReviewLaterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GuardianSafeNotifications.reviewLater(context, intent)
    }
}
