package uk.co.traynor.privategallery.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

internal data class LauncherApp(
    val component: String,
    val packageName: String,
    val label: String,
    val iconRevision: Long = 0,
    val category: Int? = null,
    val firstInstallTime: Long? = null,
    val profile: LauncherProfile? = null,
) {
    val profileSerial: Long? get() = profile?.serial
    val profileLabel: String? get() = profile?.label
}

internal data class LauncherProfile(val user: UserHandle, val serial: Long, val label: String)

private fun LauncherApp.launchComponent(): ComponentName {
    val serial = profileSerial
    val flattened = if (serial == null) {
        check('#' !in component)
        component
    } else {
        val identity = checkNotNull(LauncherAppIdentity.parse(component))
        check(identity.profileSerial == serial)
        identity.flattenedComponent
    }
    return checkNotNull(ComponentName.unflattenFromString(flattened))
}

internal data class LauncherShortcut(
    val packageName: String,
    val id: String,
    val label: String,
    val activityComponent: String?,
)

/** Catalogue and launch authority are limited to public launcher entries in accessible Android profiles. */
internal class LauncherAppDirectory(private val context: Context) {
    private val manager = context.packageManager
    val launcherApps: LauncherApps = checkNotNull(context.getSystemService(LauncherApps::class.java))
    val user = Process.myUserHandle()

    fun apps(): List<LauncherApp> {
        val collator = Collator.getInstance()
        return (currentProfileApps() + otherProfiles().flatMap(::profileApps))
            .distinctBy(LauncherApp::component)
            .sortedWith { a, b ->
                collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.component.compareTo(b.component)
            }
    }

    private fun currentProfileApps(): List<LauncherApp> {
        val packages = mutableMapOf<String, PackageInfo?>()
        return manager.queryIntentActivities(mainIntent(), 0).mapNotNull { match ->
            val info = match.activityInfo ?: return@mapNotNull null
            if (!info.exported || !info.enabled || info.packageName == context.packageName) return@mapNotNull null
            val component = ComponentName(info.packageName, info.name).flattenToString()
            val pkg = packages.getOrPut(info.packageName) {
                runCatching { manager.getPackageInfo(info.packageName, 0) }.getOrNull()
            }
            LauncherApp(
                component, info.packageName, match.loadLabel(manager).toString().trim().ifEmpty { info.packageName },
                iconRevision = pkg?.lastUpdateTime ?: 0L,
                category = info.applicationInfo.category.takeUnless { it == ApplicationInfo.CATEGORY_UNDEFINED },
                firstInstallTime = pkg?.firstInstallTime?.takeIf { it > 0L },
            )
        }
    }

    private fun otherProfiles(): List<LauncherProfile> =
        runCatching { launcherApps.profiles }.getOrDefault(emptyList())
            .filter { it != user }
            .mapNotNull(::visibleProfile)

    private fun visibleProfile(handle: UserHandle): LauncherProfile? = runCatching {
        val info = launcherApps.getLauncherUserInfo(handle) ?: return null
        if (info.userType == UserManager.USER_TYPE_PROFILE_PRIVATE || info.userSerialNumber < 0) return null
        val label = when (info.userType) {
            UserManager.USER_TYPE_PROFILE_CLONE -> "Cloned app"
            UserManager.USER_TYPE_PROFILE_MANAGED -> "Work profile"
            else -> "Other profile"
        }
        LauncherProfile(handle, info.userSerialNumber.toLong(), label)
    }.getOrNull()

    private fun profileApps(profile: LauncherProfile): List<LauncherApp> =
        runCatching { launcherApps.getActivityList(null, profile.user) }.getOrDefault(emptyList())
            .mapNotNull { entry ->
                runCatching {
                    val component = entry.componentName
                    val pkg = component.packageName
                    val activity = entry.activityInfo
                    if (entry.user != profile.user || pkg == context.packageName || !activity.enabled || !activity.exported) {
                        return@runCatching null
                    }
                    // getActivityList can also contain synthesized Settings entries.
                    if (!launcherApps.isActivityEnabled(component, profile.user)) return@runCatching null
                    val identity = LauncherAppIdentity.forComponent(component.flattenToString(), profile.serial)
                        ?: return@runCatching null
                    val info = entry.applicationInfo
                    LauncherApp(
                        identity.key, pkg, entry.label.toString().trim().ifEmpty { pkg },
                        iconRevision = info.sourceDir?.hashCode()?.toLong() ?: 0L,
                        category = info.category.takeUnless { it == ApplicationInfo.CATEGORY_UNDEFINED },
                        // LauncherActivityInfo's install-time lookup can use the caller's profile.
                        profile = profile,
                    )
                }.getOrNull()
            }

    fun shortcuts(apps: List<LauncherApp>): List<LauncherShortcut> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val packages = apps.filter { it.profile == null }.mapTo(mutableSetOf(), LauncherApp::packageName)
        val query = ShortcutQuery().setQueryFlags(
            ShortcutQuery.FLAG_MATCH_DYNAMIC or ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_PINNED,
        )
        launcherApps.getShortcuts(query, user).orEmpty().asSequence()
            .filter { it.isEnabled && it.getPackage() in packages }
            .map { LauncherShortcut(it.getPackage(), it.id, it.shortLabel?.toString().orEmpty(),
                it.activity?.flattenToString()) }
            .filter { it.label.isNotBlank() }
            .distinctBy { it.packageName to it.id }
            .take(300)
            .toList()
    }.getOrDefault(emptyList())

    fun start(app: LauncherApp): Boolean = runCatching {
        val (component, profileUser) = target(app)
        launcherApps.startMainActivity(component, profileUser, null, null)
    }.isSuccess

    fun openAppInfo(app: LauncherApp): Boolean = runCatching {
        val (component, profileUser) = target(app)
        launcherApps.startAppDetailsActivity(component, profileUser, null, null)
    }.isSuccess

    private fun target(app: LauncherApp): Pair<ComponentName, UserHandle> {
        val component = app.launchComponent()
        check(component.packageName == app.packageName && app.packageName != context.packageName)
        val profile = app.profile ?: return component to user
        check(profile.user != user && profile.user in launcherApps.profiles &&
            visibleProfile(profile.user)?.serial == profile.serial)
        return component to profile.user
    }

    fun start(shortcut: LauncherShortcut): Boolean = runCatching {
        check(shortcut.packageName != context.packageName && launcherApps.hasShortcutHostPermission())
        launcherApps.startShortcut(shortcut.packageName, shortcut.id, null, null, user)
    }.isSuccess

    private fun mainIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
}

private object LauncherIcons {
    private val cache = LruCache<Pair<String, Long>, ImageBitmap>(96)

    fun get(context: Context, app: LauncherApp): ImageBitmap? {
        val key = app.component to app.iconRevision
        return cache.get(key) ?: runCatching {
            val component = app.launchComponent()
            val profile = app.profile
            val icon = if (profile == null) {
                context.packageManager.getActivityIcon(component)
            } else {
                val launcherApps = checkNotNull(context.getSystemService(LauncherApps::class.java))
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                val entry = checkNotNull(launcherApps.resolveActivity(intent, profile.user))
                check(entry.user == profile.user && entry.componentName == component)
                entry.getBadgedIcon(context.resources.displayMetrics.densityDpi)
            }.mutate()
            val size = (context.resources.displayMetrics.density * 64).toInt().coerceIn(64, 192)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, size, size)
            icon.draw(Canvas(bitmap))
            bitmap.asImageBitmap().also { cache.put(key, it) }
        }.getOrNull()
    }
}

@Composable
internal fun LauncherAppIcon(app: LauncherApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val image by produceState<ImageBitmap?>(null, app) {
        value = withContext(Dispatchers.IO) { LauncherIcons.get(context, app) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val icon = image
        if (icon == null) Icon(Icons.Default.Apps, null)
        else Image(icon, null, Modifier.matchParentSize())
    }
}
