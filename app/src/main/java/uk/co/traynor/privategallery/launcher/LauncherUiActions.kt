package uk.co.traynor.privategallery.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal data class LauncherUiActions(
    val save: (NovaLauncherState) -> Boolean,
    val launchApp: (String) -> Unit,
    val launchShortcut: (LauncherShortcut) -> Unit,
    val openWorkspace: () -> Unit,
    val selectHome: () -> Unit,
    val continueWithCurrentHome: () -> Unit,
    val emergencyPhone: () -> Unit,
    val reviewSanctuary: () -> Unit,
    val setOutsideProtection: (Boolean) -> Unit,
    val chooseWallpaper: () -> Unit,
    val addWidget: (Boolean) -> Unit,
    val removeWidget: (LauncherWidget) -> Unit,
    val exportLayout: () -> Unit,
    val importLayout: () -> Unit,
    val renderWidget: @Composable (LauncherWidget, Modifier) -> Unit,
    val openSanctuary: () -> Unit = reviewSanctuary,
    val openAppInfo: ((String) -> Unit)? = null,
    val renameApp: ((String) -> Unit)? = null,
    val toggleRecentPin: ((String) -> Unit)? = null,
    val checkDefaultHome: () -> Unit = {},
    val setAskForDefaultHome: (Boolean) -> Unit = {},
    val claimDefaultHomePrompt: () -> Boolean = { false },
    val requestDefaultHome: () -> Unit = selectHome,
    val reloadWorkspaceLayout: (() -> Unit)? = null,
    val configureExitProtection: (() -> Unit)? = null,
    val setSmartFolderPresentation: ((LauncherSmartFolderPresentation) -> Unit)? = null,
)
