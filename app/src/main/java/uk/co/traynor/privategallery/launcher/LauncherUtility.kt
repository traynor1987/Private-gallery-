package uk.co.traynor.privategallery.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import uk.co.traynor.privategallery.home2.ArcadeScreen
import uk.co.traynor.privategallery.home2.CalculatorScreen
import uk.co.traynor.privategallery.home2.PublicStoreScreen

/** Explicit public utility allowlist. The caller applies Sanctuary policy before opening it. */
internal enum class LauncherUtility(val route: String, val label: String) {
    CALCULATOR("nova-calculator", "Nova Calculator"),
    ARCADE("arcade", "Nova Arcade"),
    STORE("store", "Nova Store"),
}

@Composable
internal fun LauncherUtilityScreen(
    utility: LauncherUtility,
    onBack: () -> Unit,
    onOpenWorkspace: () -> Unit,
) {
    BackHandler(onBack = onBack)
    when (utility) {
        LauncherUtility.CALCULATOR -> CalculatorScreen(launcherSession = true, onHome = onBack)
        LauncherUtility.ARCADE -> ArcadeScreen(home = null, onHome = onBack)
        LauncherUtility.STORE -> PublicStoreScreen(onBack, onOpenWorkspace)
    }
}
