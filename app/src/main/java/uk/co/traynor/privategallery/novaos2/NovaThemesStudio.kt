package uk.co.traynor.privategallery.novaos2

import androidx.compose.runtime.Composable
import uk.co.traynor.privategallery.launcher.LauncherThemesStudio
import uk.co.traynor.privategallery.launcher.forLauncherApp

@Composable
internal fun NovaThemesStudio(host: NovaSystemHost) {
    LauncherThemesStudio(
        appearance = host.state.appearance,
        previewApps = host.apps.filter { host.decisions.forLauncherApp(it)?.canLaunch == true }.take(5),
        onApply = { host.actions.save(host.state.copy(appearance = it)) },
        onDeviceWallpaper = host.actions.chooseWallpaper,
    )
}
