package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal data class LauncherDefaultHomeUi(
    val isDefault: Boolean?,
    val ask: Boolean,
    val canShow: Boolean,
    val windowFocused: Boolean,
    val opening: LauncherDefaultHomePromptSession,
)

internal fun launcherDefaultHomeStatus(isDefault: Boolean?): String = when (isDefault) {
    true -> "Nova Launcher is Android’s current Home app."
    false -> "Nova Launcher isn’t Android’s current Home app."
    null -> "Android’s current Home app could not be verified."
}

@Composable
internal fun LauncherDefaultHomePrompt(
    home: LauncherDefaultHomeUi?,
    safeSurface: Boolean,
    actions: LauncherUiActions,
) {
    val eligible = home != null && home.isDefault == false && home.ask && home.canShow && safeSurface
    var showing by remember(home?.opening, eligible) { mutableStateOf(false) }
    LaunchedEffect(home?.opening, eligible, home?.windowFocused) {
        if (eligible && home?.windowFocused == true && !showing) showing = actions.claimDefaultHomePrompt()
    }
    if (!showing) return

    LauncherDefaultHomeDialog(
        onDismiss = { showing = false },
        onSetDefault = {
            showing = false
            actions.requestDefaultHome()
        },
        onOptOut = {
            showing = false
            actions.setAskForDefaultHome(false)
        },
    )
}

@Composable
internal fun LauncherDefaultHomeDialog(
    onDismiss: () -> Unit,
    onSetDefault: () -> Unit,
    onOptOut: () -> Unit,
) {
    GuardianAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make NovaOS your default Home?") },
        text = { Text("NovaOS isn’t currently your default launcher. Set it as your Home app for the complete NovaOS experience.") },
        confirmButton = {
            TextButton(onClick = onSetDefault) { Text("Set as Default") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOptOut) { Text("Don’t Ask Again") }
                TextButton(onClick = onDismiss) { Text("Not Now") }
            }
        },
    )
}
