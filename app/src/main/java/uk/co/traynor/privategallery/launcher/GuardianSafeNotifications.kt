package uk.co.traynor.privategallery.launcher

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
        return when {
            own == null || own < 0 || conversation.profile < 0 -> "WhatsApp · profile unknown"
            conversation.profile == own -> "Main WhatsApp"
            conversation.profile == uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore.get(context)
                .launcherSettings().friendsWhatsApp -> "Friends WhatsApp"
            else -> "WhatsApp · profile unknown"
        }
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
        if (!settings.getBoolean(key, false)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
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
            .build()
        manager.notify(TAG, ID, notification)
    }
}
