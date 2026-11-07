package uk.co.traynor.privategallery.core.sanctuary

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.ApplicationExitInfo
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.core.home2.SpaceContext

/** User-facing capability checks and explicit, allowlisted launch operations. */
object SanctuaryGuardian {
    internal enum class MonitorState { PAUSED, STARTING, WAITING, MONITORING, BLOCKING, FAILED }

    @Volatile internal var monitorState = MonitorState.PAUSED
    internal val overlayNovaApps = mapOf("arcade" to "Nova Arcade", "mind" to "Nova Mind", "nova-calculator" to "Nova Calculator")

    private data class NovaDestination(val route: String, val session: String, val deadline: Long)
    @Volatile private var novaDestination: NovaDestination? = null

    internal fun openApprovedNova(context: Context, route: String): Boolean {
        val session = SanctuaryStore.get(context).refresh() ?: return false
        if (route !in overlayNovaApps || route !in session.novaApps) return false
        val request = NovaDestination(route, session.id, SystemClock.elapsedRealtime() + 60_000L)
        novaDestination = request
        return openNova(context).also { if (!it && novaDestination == request) novaDestination = null }
    }

    internal fun takeApprovedNova(context: Context): String? {
        val request = novaDestination ?: return null
        novaDestination = null
        val session = SanctuaryStore.get(context).refresh() ?: return null
        return request.route.takeIf {
            request.session == session.id && SystemClock.elapsedRealtime() < request.deadline &&
                it in overlayNovaApps && it in session.novaApps
        }
    }

    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean =
        context.getSystemService(AppOpsManager::class.java)
            ?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED

    fun hasOverlayAccess(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasNotificationAccess(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true &&
            context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(SanctuaryGuardianService.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    internal fun hasPermissions(context: Context): Boolean =
        hasUsageAccess(context) && hasOverlayAccess(context) && hasNotificationAccess(context)

    fun status(context: Context): String = when {
        SanctuaryStore.get(context).current.value == null -> "Guardian off"
        SanctuaryStore.get(context).current.value?.enableGuardian == false -> "Guardian off · Disabled for this session"
        !hasUsageAccess(context) -> "Protection paused · Usage Access is off"
        !hasOverlayAccess(context) -> "Protection paused · Display over other apps is off"
        !hasNotificationAccess(context) -> "Protection paused · Notifications are off"
        else -> when (monitorState) {
            MonitorState.PAUSED -> "Protection paused · Start App Guardian to resume"
            MonitorState.STARTING -> "Starting App Guardian"
            MonitorState.WAITING -> "Monitoring · waiting for a foreground app"
            MonitorState.MONITORING -> "Monitoring Android apps"
            MonitorState.BLOCKING -> "Sanctuary is covering an unapproved app"
            MonitorState.FAILED -> "Protection paused · Android monitoring unavailable"
        }
    }

    fun setup(context: Context): Boolean = when {
        !hasUsageAccess(context) -> openUsageSettings(context)
        !hasOverlayAccess(context) -> openOverlaySettings(context)
        else -> openNotificationSettings(context)
    }

    fun openUsageSettings(context: Context): Boolean =
        openSystem(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openOverlaySettings(context: Context): Boolean =
        openSystem(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))

    fun openNotificationSettings(context: Context): Boolean =
        openSystem(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))

    fun openSystemSettings(context: Context): Boolean = openSystem(context, Intent(Settings.ACTION_SETTINGS))

    fun openPhone(context: Context): Boolean {
        val dial = Intent(Intent.ACTION_DIAL)
        val defaultDialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        return if (defaultDialer != null && open(context, Intent(dial).setPackage(defaultDialer))) {
            true
        } else {
            openSystem(context, dial)
        }
    }

    internal fun novaIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun openNova(context: Context): Boolean = open(context, novaIntent(context))

    fun installedApps(context: Context): Map<String, String> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
            .asSequence()
            .filter { it.activityInfo.exported && it.activityInfo.packageName != context.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
            .toMap()
    }

    fun launch(context: Context, packageName: String): Boolean {
        val session = SanctuaryStore.get(context).refresh() ?: return false
        if ((!session.allowExternalApps || packageName !in session.androidApps) && packageName !in essentialPackages(context)) return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return open(context, intent)
    }

    internal fun essentialPackages(context: Context): Set<String> = buildSet {
        add(context.packageName)
        add("android")
        add("com.android.systemui")
        val pm = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ordinaryDefault = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        pm.resolveActivity(home, ordinaryDefault)?.activityInfo?.packageName?.let(::add)
        pm.queryIntentActivities(home, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY.toLong()))
            .forEach { add(it.activityInfo.packageName) }
        for (intent in listOf(
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS),
            Intent(Intent.ACTION_DIAL),
        )) {
            resolveSystemActivity(context, intent)?.packageName?.let(::add)
        }
        val telecom = context.getSystemService(TelecomManager::class.java)
        telecom?.defaultDialerPackage?.let(::add)
        for (packageName in listOf(
            "com.android.phone", "com.android.server.telecom", "com.android.incallui",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.samsung.android.dialer", "com.samsung.android.incallui", "com.samsung.android.app.telephonyui",
        )) {
            val info = runCatching { pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0)) }.getOrNull()
            if (info != null && (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
                add(packageName)
            }
        }
    }

    fun start(context: Context): Boolean {
        val app = application(context)
        val session = SanctuaryStore.get(app).refresh() ?: return false
        if (!session.enableGuardian || !hasPermissions(app)) return false
        if (!writeActivation(app, session.id)) return false
        monitorState = MonitorState.STARTING
        return requestStart(app)
    }

    fun stop(context: Context) {
        val app = application(context)
        clearActivation(app)
        novaDestination = null
        monitorState = MonitorState.PAUSED
        app.stopService(Intent(app, SanctuaryGuardianService::class.java))
    }

    internal fun resumeAfterBoot(context: Context) {
        SanctuaryDnd.reconcile(context)
        val session = SanctuaryStore.get(context).refresh() ?: return
        val boot = bootCount(context)
        val activation = preferences(context)
        if (!session.enableGuardian || boot < 0 || activation.getInt("boot", -1) !in 0 until boot ||
            !isActivated(context, session.id) || !hasPermissions(context) || wasStoppedByUser(context, session.startedAt)
        ) return
        if (writeActivation(context, session.id)) requestStart(context)
    }

    internal fun isActivated(context: Context, sessionId: String): Boolean =
        preferences(context).getString("session", null) == sessionId

    internal fun clearActivation(context: Context, sessionId: String? = null) {
        if (sessionId == null || isActivated(context, sessionId)) preferences(context).edit().clear().commit()
    }

    private fun requestStart(context: Context): Boolean = try {
        context.startForegroundService(Intent(context, SanctuaryGuardianService::class.java))
        true
    } catch (_: IllegalStateException) {
        clearActivation(context)
        monitorState = MonitorState.FAILED
        false
    } catch (_: SecurityException) {
        clearActivation(context)
        monitorState = MonitorState.FAILED
        false
    }

    private fun openSystem(context: Context, intent: Intent): Boolean {
        val activity = resolveSystemActivity(context, intent) ?: return false
        return open(context, intent.setComponent(activity))
    }

    private fun resolveSystemActivity(context: Context, intent: Intent): ComponentName? =
        context.packageManager.resolveActivity(
            intent,
            PackageManager.ResolveInfoFlags.of(
                (PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY).toLong(),
            ),
        )?.activityInfo?.let { ComponentName(it.packageName, it.name) }

    private fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: android.content.ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun application(context: Context): Context {
        var app = context.applicationContext ?: context
        while (app is SpaceContext) app = app.baseContext.applicationContext ?: app.baseContext
        return app
    }

    private fun preferences(context: Context) =
        application(context).getSharedPreferences("sanctuary-guardian", Context.MODE_PRIVATE)

    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun writeActivation(context: Context, sessionId: String): Boolean =
        preferences(context).edit().putString("session", sessionId).putInt("boot", bootCount(context)).commit()

    private fun wasStoppedByUser(context: Context, since: Long): Boolean =
        runCatching {
            context.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(context.packageName, 0, 10)
                ?.any { it.timestamp >= since && it.reason == ApplicationExitInfo.REASON_USER_REQUESTED } ?: true
        }.getOrDefault(true)
}

class SanctuaryBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) SanctuaryGuardian.resumeAfterBoot(context)
    }
}
