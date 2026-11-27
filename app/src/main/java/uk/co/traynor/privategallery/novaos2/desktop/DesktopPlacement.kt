package uk.co.traynor.privategallery.novaos2.desktop

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.view.Display
import uk.co.traynor.privategallery.launcher.LauncherApp

internal sealed interface DesktopPlacement {
    data object Adjacent : DesktopPlacement
    data class External(val displayId: Int) : DesktopPlacement
}

internal fun publicDesktopDisplays(context: Context): List<Display> {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)) {
        return emptyList()
    }
    return context.getSystemService(DisplayManager::class.java)
        ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).orEmpty()
        .filter { it.isValid && it.displayId != Display.DEFAULT_DISPLAY && it.displayId != context.display?.displayId &&
            it.flags and Display.FLAG_PRIVATE == 0 }
}

internal fun desktopLauncherIntent(component: ComponentName, adjacent: Boolean): Intent =
    Intent.makeMainActivity(component).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (adjacent) addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
    }

/** The Home activity checks foreground and Sanctuary admission before calling this. */
internal fun Activity.startDesktopApp(app: LauncherApp, placement: DesktopPlacement): Boolean = runCatching {
    require(app.profile == null && app.packageName != packageName)
    val component = requireNotNull(ComponentName.unflattenFromString(app.component))
    require(component.packageName == app.packageName)
    val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.packageName)
    check(packageManager.queryIntentActivities(launcherQuery, 0).any { entry ->
        val info = entry.activityInfo
        info.enabled && info.exported && ComponentName(info.packageName, info.name) == component
    })
    val intent = desktopLauncherIntent(component, placement == DesktopPlacement.Adjacent)
    val options = when (placement) {
        DesktopPlacement.Adjacent -> null
        is DesktopPlacement.External -> {
            check(publicDesktopDisplays(this).any { it.displayId == placement.displayId })
            check(getSystemService(ActivityManager::class.java).isActivityStartAllowedOnDisplay(this, placement.displayId, intent))
            ActivityOptions.makeBasic().setLaunchDisplayId(placement.displayId).toBundle()
        }
    }
    startActivity(intent, options)
}.isSuccess
