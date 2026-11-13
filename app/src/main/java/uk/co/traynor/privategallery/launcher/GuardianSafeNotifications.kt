package uk.co.traynor.privategallery.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import uk.co.traynor.privategallery.core.guardian.*

internal object GuardianSafeNotifications {
    private const val CHANNEL = "guardian-safe-notifications"
    private const val TAG = "guardian-safe"
    private const val ID = 1

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

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(TAG, ID)
    }

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
        if (settings.getBoolean("quiet", false) && guardianNoticeQuietHour(
                java.time.LocalTime.now().hour, settings.getInt("quiet_start", 22), settings.getInt("quiet_end", 7),
            )) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val kind = guardianNoticeKind(row.assessment, row.patterns.orEmpty())
        val text = "${kind.title} · $source"
        val icon = if (kind == GuardianNoticeKind.LOW && settings.getBoolean("tick", true)) "✓ " else ""
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Nova Guardian", NotificationManager.IMPORTANCE_HIGH))
        val route = PendingIntent.getActivity(context, 0, GuardianInboxActivity.safeNotificationsIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Nova Guardian")
            .setContentText(icon + text)
            .setStyle(Notification.BigTextStyle().bigText("$icon$text\n${kind.description} Preview hidden."))
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setLocalOnly(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(settings.getInt("duration", 15).coerceIn(5, 60) * 1000L)
            .setContentIntent(route)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_lock_lock),
                "Open Safe Notifications", route).build())
            .build()
        manager.notify(TAG, ID, notification)
    }
}
