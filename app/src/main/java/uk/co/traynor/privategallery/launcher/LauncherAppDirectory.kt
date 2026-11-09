package uk.co.traynor.privategallery.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
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
)

internal data class LauncherShortcut(
    val packageName: String,
    val id: String,
    val label: String,
    val activityComponent: String?,
)

/** Catalogue and launch authority are limited to public launcher entries in the current Android profile. */
internal class LauncherAppDirectory(private val context: Context) {
    private val manager = context.packageManager
    val launcherApps: LauncherApps = checkNotNull(context.getSystemService(LauncherApps::class.java))
    val user = Process.myUserHandle()

    fun apps(): List<LauncherApp> {
        val entries = manager.queryIntentActivities(mainIntent(), 0)
        val versions = mutableMapOf<String, Long>()
        val collator = Collator.getInstance()
        return entries.mapNotNull { match ->
            val info = match.activityInfo ?: return@mapNotNull null
            if (!info.exported || !info.enabled || info.packageName == context.packageName) return@mapNotNull null
            val component = ComponentName(info.packageName, info.name).flattenToString()
            val version = versions.getOrPut(info.packageName) {
                runCatching { manager.getPackageInfo(info.packageName, 0).lastUpdateTime }.getOrDefault(0L)
            }
            LauncherApp(component, info.packageName,
                match.loadLabel(manager).toString().trim().ifEmpty { info.packageName }, version)
        }.distinctBy(LauncherApp::component).sortedWith { a, b ->
            collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.component.compareTo(b.component)
        }
    }

    fun shortcuts(apps: List<LauncherApp>): List<LauncherShortcut> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val packages = apps.mapTo(mutableSetOf(), LauncherApp::packageName)
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
        val component = checkNotNull(ComponentName.unflattenFromString(app.component))
        check(component.packageName == app.packageName && app.packageName != context.packageName)
        launcherApps.startMainActivity(component, user, null, null)
    }.isSuccess

    fun openAppInfo(app: LauncherApp): Boolean = runCatching {
        val component = checkNotNull(ComponentName.unflattenFromString(app.component))
        check(component.packageName == app.packageName && app.packageName != context.packageName)
        launcherApps.startAppDetailsActivity(component, user, null, null)
    }.isSuccess

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
            val component = checkNotNull(ComponentName.unflattenFromString(app.component))
            val icon = context.packageManager.getActivityIcon(component).mutate()
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
