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
    private const val ID = 1
    private const val DISMISS = "uk.co.traynor.privategallery.GUARDIAN_REVIEW_LATER"

    fun prefs(context: Context) = context.getSharedPreferences("guardian_safe_notices", Context.MODE_PRIVATE)

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
        context.getSystemService(NotificationManager::class.java)?.cancel(TAG, ID)
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
        val posted = prefs(context).getString("posted_token", null)
        if (intent.action == DISMISS && posted != null && intent.identifier == posted) cancel(context)
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
        val text = "${kind.title} · $source"
        val icon = if (kind == GuardianNoticeKind.LOW && settings.getBoolean("tick", true)) "✓ " else ""
        val route = PendingIntent.getActivity(context, 0, GuardianInboxActivity.safeNotificationsIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val token = java.util.UUID.randomUUID().toString()
        val reviewLater = PendingIntent.getBroadcast(context, 0,
            Intent(context, GuardianReviewLaterReceiver::class.java).setAction(DISMISS).setIdentifier(token),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
        val notification = Notification.Builder(context, if (silent) SILENT_CHANNEL else CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Nova Guardian")
            .setContentText(icon + text)
            .setStyle(Notification.BigTextStyle().bigText("$icon$text\n${kind.description} Preview hidden."))
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setLocalOnly(true)
            .setOnlyAlertOnce(true)
            .setColor(when (kind) {
                GuardianNoticeKind.LOW -> 0xFF137D57.toInt()
                GuardianNoticeKind.UPSETTING, GuardianNoticeKind.HIGH -> 0xFF996000.toInt()
                GuardianNoticeKind.UNCERTAIN -> 0xFF596477.toInt()
            })
            .setAutoCancel(true)
            .setTimeoutAfter(settings.getInt("duration", 15).coerceIn(5, 60) * 1000L)
            .setContentIntent(route)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_lock_lock),
                "Open Safe Notifications", route).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_recent_history),
                "Review later", reviewLater).build())
            .build()
        settings.edit().putString("posted_token", token).apply()
        manager.notify(TAG, ID, notification)
    }
}


/** Dismisses only the currently posted Nova alert. The encrypted message stays in Guardian. */
class GuardianReviewLaterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GuardianSafeNotifications.reviewLater(context, intent)
    }
}
