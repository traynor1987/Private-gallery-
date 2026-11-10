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
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.roundToInt

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

internal data class LauncherProfile(
    val user: UserHandle,
    val serial: Long,
    val label: String,
    val isClone: Boolean = false,
)

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
        LauncherProfile(
            handle, info.userSerialNumber.toLong(), label,
            isClone = info.userType == UserManager.USER_TYPE_PROFILE_CLONE,
        )
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
    private val cache = LruCache<Triple<String, Long, Int>, ImageBitmap>(96)

    fun get(context: Context, app: LauncherApp, densityDpi: Int): ImageBitmap? {
        val key = Triple(app.component, app.iconRevision, densityDpi)
        return cache.get(key) ?: runCatching {
            val component = app.launchComponent()
            val profile = app.profile
            val icon = if (profile == null) {
                context.packageManager.getActivityIcon(component)
            } else {
                val launcherApps = checkNotNull(context.getSystemService(LauncherApps::class.java))
                check(launcherApps.getLauncherUserInfo(profile.user)?.userSerialNumber?.toLong() == profile.serial)
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                val entry = checkNotNull(launcherApps.resolveActivity(intent, profile.user))
                check(entry.user == profile.user && entry.componentName == component)
                entry.getIcon(densityDpi)
            }.mutate()
            val size = (densityDpi * .55f).roundToInt().coerceIn(88, 256)
            renderLauncherArtwork(icon, size).asImageBitmap().also { cache.put(key, it) }
        }.getOrNull()
    }
}

/** Adaptive drawables apply Android's mask. Legacy drawables keep their intrinsic proportions. */
internal fun renderLauncherArtwork(icon: Drawable, size: Int): Bitmap {
    val sourceWidth = icon.intrinsicWidth
    val sourceHeight = icon.intrinsicHeight
    val scale = if (sourceWidth > 0 && sourceHeight > 0) size.toFloat() / maxOf(sourceWidth, sourceHeight) else 0f
    val width = if (scale > 0f) (sourceWidth * scale).roundToInt().coerceIn(1, size) else size
    val height = if (scale > 0f) (sourceHeight * scale).roundToInt().coerceIn(1, size) else size
    val left = (size - width) / 2
    val top = (size - height) / 2
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        icon.setBounds(left, top, left + width, top + height)
        icon.draw(Canvas(bitmap))
    }
}

@Composable
internal fun LauncherArtworkIcon(app: LauncherApp, appearance: LauncherAppearance, modifier: Modifier = Modifier) {
    if (appearance.iconStyle == LauncherIconStyle.FULL_ICONS) {
        LauncherAppIcon(app, modifier)
        return
    }
    val shape = when (appearance.iconShape) {
        LauncherIconShape.ROUNDED -> RoundedCornerShape(30)
        LauncherIconShape.CIRCLE -> CircleShape
        LauncherIconShape.SQUIRCLE -> RoundedCornerShape(41)
    }
    Box(
        modifier.clip(shape).background(launcherAccent(appearance.accent).copy(alpha = .29f)),
        contentAlignment = Alignment.Center,
    ) {
        LauncherAppIcon(app, Modifier.fillMaxSize(.69f))
    }
}

@Composable
internal fun LauncherAppIcon(app: LauncherApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val builtIn = if (app.packageName.startsWith("nova:")) LauncherBuiltIn.find(context.packageName, app.component) else null
    if (builtIn != null) {
        val (glyph, color) = when (builtIn) {
            LauncherBuiltIn.SANCTUARY -> Icons.Default.Shield to Color(0xFF3260CC)
            LauncherBuiltIn.CALCULATOR -> Icons.Default.Calculate to Color(0xFF7952BE)
            LauncherBuiltIn.ARCADE -> Icons.Default.SportsEsports to Color(0xFF9A387E)
            LauncherBuiltIn.STORE -> Icons.Default.Storefront to Color(0xFF147E7F)
            LauncherBuiltIn.PRIMARY_GALLERY -> Icons.Default.PhotoLibrary to Color(0xFFAD533C)
        }
        Box(
            modifier.clip(RoundedCornerShape(24)).background(Brush.linearGradient(listOf(color, Color(0xFF192440)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(glyph, null, Modifier.fillMaxSize(.59f), tint = Color.White)
        }
        return
    }
    val densityDpi = LocalConfiguration.current.densityDpi
    val image by key(app.component, app.iconRevision, densityDpi) {
        produceState<ImageBitmap?>(null) {
            value = withContext(Dispatchers.IO) { LauncherIcons.get(context, app, densityDpi) }
        }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val artworkSize = minOf(maxWidth, maxHeight)
        val icon = image
        if (icon == null) {
            val initial = app.label.firstOrNull(Char::isLetterOrDigit)?.uppercaseChar()?.toString() ?: "•"
            val size = artworkSize.value * .48f
            Box(
                Modifier.size(artworkSize).clip(RoundedCornerShape(24)).background(Color(0xFF3A5272)),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, Modifier.clearAndSetSemantics {}, color = Color.White, fontSize = size.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        } else Image(icon, null, Modifier.size(artworkSize))
        // Some Android clone profiles return an unbadged icon. Mark every secondary profile,
        // including the fallback artwork, from the exact profile identity in the catalogue.
        app.profile?.let { profile ->
            Box(
                Modifier.align(Alignment.BottomEnd).size(artworkSize * .32f)
                    .background(Color(0xFF17273C), CircleShape)
                    .testTag("launcher-profile-badge-${app.component}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (profile.isClone) Icons.Default.ContentCopy else Icons.Default.Badge,
                    null, Modifier.fillMaxSize(.65f), tint = Color.White,
                )
            }
        }
    }
}
