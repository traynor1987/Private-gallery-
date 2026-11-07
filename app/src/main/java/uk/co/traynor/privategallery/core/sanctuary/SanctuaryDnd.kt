package uk.co.traynor.privategallery.core.sanctuary

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import uk.co.traynor.privategallery.LauncherActivity
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal enum class SanctuaryDndState(val label: String) {
    OFF("Sanctuary Do Not Disturb is off"),
    ON("Sanctuary Do Not Disturb is on"),
    UNAVAILABLE("Do Not Disturb is unavailable · check Notification Policy Access"),
    PAUSED("Sanctuary Do Not Disturb was paused or is unavailable"),
    CLEANUP_PENDING("Sanctuary DND cleanup is pending · review Android settings"),
}

internal fun sanctuaryDndState(
    enabled: Boolean, access: Boolean, ownedSession: Boolean, ruleState: Int?,
): SanctuaryDndState = when {
    !enabled -> SanctuaryDndState.OFF
    !access -> SanctuaryDndState.UNAVAILABLE
    ownedSession && ruleState == Condition.STATE_TRUE -> SanctuaryDndState.ON
    else -> SanctuaryDndState.PAUSED
}

internal fun sanctuaryDndShouldRelease(owned: String, active: String?, enabled: Boolean): Boolean =
    owned != active || !enabled

/** Preserve configured people exceptions, with starred contacts as Sanctuary's minimum. */
internal fun sanctuaryDndPeople(categories: Int, category: Int, senders: Int): Int =
    if (categories and category == 0) ZenPolicy.PEOPLE_TYPE_STARRED
    else when (senders) {
        Policy.PRIORITY_SENDERS_ANY -> ZenPolicy.PEOPLE_TYPE_ANYONE
        Policy.PRIORITY_SENDERS_CONTACTS -> ZenPolicy.PEOPLE_TYPE_CONTACTS
        else -> ZenPolicy.PEOPLE_TYPE_STARRED
    }

/**
 * Uses only an app-owned priority rule. Its initial policy permits calls, messages, conversations
 * and alarms. Android can apply stricter owner-selected policies or snooze the rule independently.
 * The app never restores a device-wide snapshot and never modifies an existing rule's settings.
 */
internal object SanctuaryDnd {
    private const val PREFS = "sanctuary-dnd"
    private const val RULE = "rule"
    private const val SESSION = "session"
    private const val ACTION_CLEANUP = "uk.co.traynor.privategallery.SANCTUARY_DND_CLEANUP"
    private val handler = Handler(Looper.getMainLooper())
    private var cleanup: Runnable? = null
    private var scheduled: Pair<String, Long>? = null

    @Synchronized
    fun start(context: Context): Boolean {
        val app = application(context)
        val store = SanctuaryStore.get(app)
        val session = store.refresh()?.takeIf { it.enableDnd } ?: return false
        val manager = manager(app) ?: return false
        if (!manager.isNotificationPolicyAccessGranted) return false
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            if (prefs.getString(SESSION, null) == session.id) return status(app) == SanctuaryDndState.ON
            val owned = prefs.getString(SESSION, null)
            if (owned != null && !release(app, manager, prefs.getString(RULE, null))) return false
            val id = prefs.getString(RULE, null)
                ?.let { existing -> manager.getAutomaticZenRule(existing)?.let { existing } }
                ?: manager.addAutomaticZenRule(newRule(app, manager.notificationPolicy))
            val rule = manager.getAutomaticZenRule(id) ?: return false
            if (!rule.isEnabled || rule.conditionId != conditionId(app) ||
                manager.getAutomaticZenRuleState(id) == Condition.STATE_TRUE
            ) return false
            // Persist the ownership and arm a platform alarm before contributing DND.
            if (!prefs.edit().putString(RULE, id).putString(SESSION, session.id).commit()) return false
            if (!schedule(app, session, store.remainingMillis(session))) {
                prefs.edit().remove(SESSION).commit()
                return false
            }
            manager.setAutomaticZenRuleState(id, condition(app, Condition.STATE_TRUE))
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    @Synchronized
    fun reconcile(context: Context) {
        val app = application(context)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val owned = prefs.getString(SESSION, null) ?: return
        val store = SanctuaryStore.get(app)
        val session = store.refresh()
        val manager = manager(app) ?: return
        if (!manager.isNotificationPolicyAccessGranted) return
        try {
            val id = prefs.getString(RULE, null)
            val rule = id?.let(manager::getAutomaticZenRule)
            if (rule == null || !rule.isEnabled || rule.conditionId != conditionId(app)) {
                clearOwnership(app)
                return
            }
            if (session == null || sanctuaryDndShouldRelease(owned, session.id, session.enableDnd) ||
                !schedule(app, session, store.remainingMillis(session))
            ) release(app, manager, id)
        } catch (_: SecurityException) {
            // Revocation wins even if it happened after the capability check.
        } catch (_: IllegalStateException) {
            // The original ownership stays available for a subsequent cleanup attempt.
        } catch (_: IllegalArgumentException) {
            // The rule may have been removed while Android was applying the state change.
        }
    }

    fun status(context: Context): SanctuaryDndState {
        val app = application(context)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedSession = prefs.getString(SESSION, null)
        val session = SanctuaryStore.get(app).current.value
        if (session == null) {
            return if (storedSession == null) SanctuaryDndState.OFF else SanctuaryDndState.CLEANUP_PENDING
        }
        val manager = manager(app) ?: return sanctuaryDndState(session.enableDnd, false, false, null)
        val owned = storedSession == session.id
        return try {
            val access = manager.isNotificationPolicyAccessGranted
            val ruleState = if (access && owned) {
                prefs.getString(RULE, null)?.let(manager::getAutomaticZenRuleState)
            } else null
            sanctuaryDndState(session.enableDnd, access, owned, ruleState)
        } catch (_: SecurityException) {
            sanctuaryDndState(session.enableDnd, false, owned, null)
        }
    }

    fun openPolicyAccess(context: Context): Boolean =
        openSettings(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun openAndroidControls(context: Context): Boolean =
        openSettings(context, Settings.ACTION_SOUND_SETTINGS) || openSettings(context, Settings.ACTION_SETTINGS)

    private fun newRule(context: Context, policy: Policy): AutomaticZenRule =
        AutomaticZenRule.Builder("Nova Sanctuary", conditionId(context))
            .setConfigurationActivity(ComponentName(context, LauncherActivity::class.java))
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setEnabled(true)
            .setManualInvocationAllowed(false)
            .setZenPolicy(
                ZenPolicy.Builder()
                    .allowAlarms(true)
                    .allowCalls(sanctuaryDndPeople(policy.priorityCategories, Policy.PRIORITY_CATEGORY_CALLS, policy.priorityCallSenders))
                    .allowMessages(sanctuaryDndPeople(policy.priorityCategories, Policy.PRIORITY_CATEGORY_MESSAGES, policy.priorityMessageSenders))
                    .allowConversations(
                        if (policy.priorityCategories and Policy.PRIORITY_CATEGORY_CONVERSATIONS != 0 &&
                            policy.priorityConversationSenders == Policy.CONVERSATION_SENDERS_ANYONE
                        ) ZenPolicy.CONVERSATION_SENDERS_ANYONE else ZenPolicy.CONVERSATION_SENDERS_IMPORTANT,
                    )
                    .allowRepeatCallers(true)
                    .build(),
            )
            .build()

    private fun schedule(context: Context, session: SanctuarySession, remaining: Long): Boolean {
        if (remaining <= 0) return false
        val deadline = session.id to session.expiresAt
        if (scheduled == deadline) return true
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        val alarm = PendingIntent.getBroadcast(
            context, 17132, Intent(context, SanctuaryDndCleanupReceiver::class.java).setAction(ACTION_CLEANUP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + remaining, alarm)
        cleanup?.let(handler::removeCallbacks)
        cleanup = Runnable {
            synchronized(this) { scheduled = null }
            reconcile(context)
        }.also { handler.postDelayed(it, remaining) }
        scheduled = deadline
        return true
    }

    private fun release(context: Context, manager: NotificationManager, id: String?): Boolean {
        val rule = id?.let(manager::getAutomaticZenRule)
        if (rule != null && rule.conditionId == conditionId(context) && rule.isEnabled) {
            // A scheduled condition cannot override an explicit Android user action.
            manager.setAutomaticZenRuleState(checkNotNull(id), condition(context, Condition.STATE_FALSE))
        }
        return clearOwnership(context)
    }

    private fun clearOwnership(context: Context): Boolean {
        val cleared = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(SESSION).commit()
        if (!cleared) return false
        cleanup?.let(handler::removeCallbacks)
        cleanup = null
        scheduled = null
        PendingIntent.getBroadcast(
            context, 17132, Intent(context, SanctuaryDndCleanupReceiver::class.java).setAction(ACTION_CLEANUP),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { context.getSystemService(AlarmManager::class.java)?.cancel(it) }
        return true
    }

    private fun conditionId(context: Context): Uri =
        Uri.Builder().scheme(Condition.SCHEME).authority(context.packageName).appendPath("sanctuary").build()

    private fun condition(context: Context, state: Int): Condition =
        Condition(
            conditionId(context), "Nova Sanctuary", "", "", 0, state,
            Condition.SOURCE_SCHEDULE, Condition.FLAG_RELEVANT_NOW,
        )

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun application(context: Context): Context =
        ((context as? SpaceContext)?.baseContext ?: context).applicationContext

    private fun openSettings(context: Context, action: String): Boolean = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    internal fun onCleanup(context: Context, action: String?) {
        if (action != ACTION_CLEANUP) return
        synchronized(this) { scheduled = null }
        reconcile(context)
    }
}

class SanctuaryDndCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SanctuaryDnd.onCleanup(context, intent.action)
    }
}
