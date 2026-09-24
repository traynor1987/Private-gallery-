package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.paging.PagingData
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.*
import uk.co.traynor.privategallery.core.ui.*
import uk.co.traynor.privategallery.core.security.AutoLockTimeout
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.*
import uk.co.traynor.privategallery.core.vpn.VpnConnectionState
import uk.co.traynor.privategallery.core.vault.VaultCollection

class ProfessionalUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun galleryMenuLight() = galleryMenu(AppTheme.LIGHT)
    @Test fun galleryMenuDark() = galleryMenu(AppTheme.DARK)
    private fun galleryMenu(theme: AppTheme) {
        compose.setContent { PrivateGalleryTheme(theme) {
            GalleryHome(true, {}, { flowOf(PagingData.empty()) }, { _, done -> done(null) }, { _, _ -> }, { _, _ -> }, { _, _ -> })
        } }
        compose.onNodeWithText("No accessible media").assertIsDisplayed()
        compose.onNodeWithContentDescription("Gallery menu").performClick()
        compose.onNodeWithText("Select items").assertIsNotEnabled()
        compose.onNodeWithText("Refresh").assertIsDisplayed()
        compose.onNodeWithText("Add with Photo Picker").assertIsDisplayed()
        compose.onNodeWithText("Large").performScrollTo().performClick().assertIsSelected()
    }

    @Test fun galleryPermissionAction() {
        var requested = false
        compose.setContent { PrivateGalleryTheme {
            GalleryHome(false, { requested = true }, { flowOf(PagingData.empty()) }, { _, done -> done(null) }, { _, _ -> }, { _, _ -> }, { _, _ -> })
        } }
        compose.onNodeWithText("Allow Gallery access").performClick()
        compose.runOnIdle { assertTrue(requested) }
    }

    @Test fun vaultMediaCollectionsAndMenuLight() = vaultStates(AppTheme.LIGHT)
    @Test fun vaultMediaCollectionsAndMenuDark() = vaultStates(AppTheme.DARK)
    private fun vaultStates(theme: AppTheme) {
        compose.setContent { PrivateGalleryTheme(theme) { VaultFixture() } }
        compose.onNodeWithText("Media", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Your vault is empty").assertIsDisplayed()
        compose.onNodeWithText("Collections").performClick().assertIsSelected()
        compose.onNodeWithText("Trips").assertIsDisplayed()
        compose.onNodeWithContentDescription("Vault menu").performClick()
        compose.onNodeWithText("New collection").assertIsDisplayed().performClick()
        compose.onNodeWithText("Collection name").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Collections").assertIsSelected()
    }

    @Test fun settingsLight() = settings(AppTheme.LIGHT)
    @Test fun settingsDark() = settings(AppTheme.DARK)
    private fun settings(theme: AppTheme) {
        var chosen = AutoLockTimeout.IMMEDIATELY
        compose.setContent { PrivateGalleryTheme(theme) { SettingsFixture(theme) { chosen = it } } }
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("After 30 seconds").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AutoLockTimeout.SECONDS_30, chosen) }
        compose.onNodeWithText("Check for updates").performScrollTo().assertIsDisplayed()
    }

    @Test fun everyDestinationKeepsTheSameNavigationGeometry() {
        var route by mutableStateOf(Route.GALLERY)
        compose.setContent { PrivateGalleryTheme { ProtectedAppShell(route, "Jenna", {}) {} } }
        val before = compose.onNodeWithText("Gallery").fetchSemanticsNode().boundsInRoot
        for (destination in listOf(Route.VAULT, Route.FAVOURITE, Route.BROWSER, Route.SETTINGS)) {
            compose.runOnIdle { route = destination }
            listOf("Gallery", "Vault", "Jenna", "Browser", "Settings").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
            assertEquals(before, compose.onNodeWithText("Gallery").fetchSemanticsNode().boundsInRoot)
        }
        compose.onNodeWithText("Settings").assertIsSelected()
    }

    @Test fun vpnTransitionLight() = vpnTransition(AppTheme.LIGHT)
    @Test fun vpnTransitionDark() = vpnTransition(AppTheme.DARK)
    private fun vpnTransition(theme: AppTheme) {
        var vpn by mutableStateOf(VpnConnectionState.DISCONNECTED)
        var attempts = 0
        val session = BrowserV2Session(compose.activity, BrowserVpnGate { vpn == VpnConnectionState.CONNECTED }, NoopBrowserV2Listener)
        compose.setContent { PrivateGalleryTheme(theme) {
            BrowserV2ProductionDestination(session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, false, {}, emptyList(), { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, {},
                staticContentHost = true,
                connectionPresentation = BrowserConnectionPresentation.from(true, vpn),
                onConnectVpn = { attempts++; vpn = VpnConnectionState.CONNECTING })
        } }
        compose.onNodeWithText("VPN not connected").assertIsDisplayed()
        val page = compose.onNodeWithTag("browser-v2-page-region").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Connect securely").performClick()
        compose.onNodeWithContentDescription("Connecting to VPN").assertIsDisplayed()
        compose.onNodeWithTag("browser-v2-address").assertIsNotEnabled()
        compose.onNodeWithTag("browser-v2-static-content-host").assertDoesNotExist()
        compose.runOnIdle { vpn = VpnConnectionState.FAILED }
        compose.onNodeWithText("Couldn't connect to VPN").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(2, attempts); vpn = VpnConnectionState.CONNECTED }
        compose.onNodeWithTag("browser-vpn-state").assertDoesNotExist()
        compose.onNodeWithTag("browser-v2-static-content-host").assertIsDisplayed()
        compose.onNodeWithTag("browser-v2-address").assertIsEnabled()
        assertEquals(page, compose.onNodeWithTag("browser-v2-page-region").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { session.destroyAll() }
    }
}

@Composable
private fun VaultFixture() {
    VaultHome(onLock = {}, onImport = { _, _ -> }, onLoadItems = { it(emptyList()) },
        onLoadCollections = { it(listOf(VaultCollection("trips", "Trips", 0L))) },
        onCreateCollection = { _, _ -> }, onAddItemsToCollection = { _, _, _ -> }, onRemoveItemsFromCollection = { _, _, _ -> },
        onRenameCollection = { _, _, _ -> }, onDeleteCollection = { _, _ -> }, onLoadCollectionItems = { _, done -> done(emptyList()) },
        onLoadPreview = { _, _ -> }, cropRevision = 0, biometricEnabled = true, onEnrollBiometrics = {},
        onSetFavouriteCollection = { _, _ -> }, onFavouriteStateChanged = {}, onOpenViewer = { _, _, _ -> })
}

@Composable
private fun SettingsFixture(theme: AppTheme, onTimeout: (AutoLockTimeout) -> Unit) {
    SettingsHome(autoLockTimeout = AutoLockTimeout.IMMEDIATELY, appTheme = theme, allowScreenshots = false,
        updateStatus = "Up to date", updateLastChecked = "Today", updateAvailable = false, biometricEnabled = true,
        recoveryKeyConfigured = true, browserSearchEngine = BrowserSearchEngine.GOOGLE, clearBrowserDataOnLock = true,
        onAutoLockTimeoutChanged = onTimeout, onThemeChanged = {}, onAllowScreenshotsChanged = {}, onBrowserSearchEngineChanged = {},
        onClearBrowserDataOnLockChanged = {}, onClearBrowserData = {}, onCheckForUpdates = {}, onDownloadUpdate = {},
        onChangePin = { _, _ -> Result.success(Unit) }, onLock = {}, browserAutoConnectVpn = true, browserRequireVpn = true,
        onBrowserAutoConnectVpnChanged = {}, onBrowserRequireVpnChanged = {}, onImportWireGuardProfile = {},
        vpnProfileStatus = "", vpnConnectionState = VpnConnectionState.DISCONNECTED, vpnProfiles = emptyList(), onSelectVpnProfile = {}, onRemoveVpnProfile = {})
}
