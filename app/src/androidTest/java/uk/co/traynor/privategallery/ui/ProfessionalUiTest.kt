package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.paging.*
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
import uk.co.traynor.privategallery.core.vault.*
import uk.co.traynor.privategallery.core.gallery.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ProfessionalUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun vaultHeaderHasCreateImageActionWithoutRemovingSearchControls() {
        var opened = 0
        compose.setContent { FixtureTheme { MediaHeader("Vault", "0 photos · 0 videos",
            onAdd = {}, onGenerate = { opened++ }, onLock = {}, onMenu = {}) } }
        compose.onNodeWithContentDescription("Create image").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        compose.onNodeWithContentDescription("Add media").assertExists()
        compose.onNodeWithContentDescription("Lock Vault").assertExists()
    }

    @Test fun mainBiometricPromptCanBeCancelledWithoutLosingPinFallback() {
        var biometricRequests = 0
        var pinAccepted = false
        compose.setContent { FixtureTheme { PinUnlock(onUnlock = { pin ->
            pinAccepted = pin.concatToString() == "123456"; pin.fill('\u0000');
            if (pinAccepted) Result.success(Unit) else Result.failure(IllegalArgumentException())
        }, biometricEnabled = true, onBiometricUnlock = { biometricRequests++ }, onForgotPin = {}) } }
        compose.onNodeWithText("Use biometrics").performClick()
        compose.runOnIdle { assertEquals(1, biometricRequests); assertFalse(pinAccepted) }
        compose.onNodeWithText("PIN").performTextInput("123456")
        compose.onNodeWithText("Unlock").performClick()
        compose.runOnIdle { assertTrue(pinAccepted) }
    }

    @Test fun galleryMenuLight() = galleryMenu(AppTheme.LIGHT)
    @Test fun galleryMenuDark() = galleryMenu(AppTheme.DARK)
    @Test fun secretDiscoveryNeverOpensWithoutOwnerAuthentication() {
        compose.setContent { FixtureTheme { SettingsFixture(AppTheme.DARK, allowSecretPin = false, onTimeout = {}) } }
        compose.onNodeWithText("Security & privacy").performClick()
        compose.onNodeWithText("Secret").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Updates & About").performClick()
        repeat(10) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithText("Protected settings unlocked").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Updates & About").performClick()
        repeat(5) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithText("Private Gallery ${BuildConfig.VERSION_NAME}").performClick()
        repeat(4) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithText("Protected settings unlocked").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Security & privacy").performClick()
        compose.onNodeWithText("Secret").performClick()
        compose.onNodeWithText("Confirm owner identity").assertIsDisplayed()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Authentication failed.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Hide content").assertDoesNotExist()
    }

    @Test fun secretDiscoveryWrongActionAndNavigationResetProgress() {
        compose.setContent { FixtureTheme { SettingsFixture(AppTheme.DARK, allowSecretPin = false, onTimeout = {}) } }
        compose.onNodeWithText("Updates & About").performClick()
        repeat(5) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithText("Check for updates").performClick()
        compose.onNodeWithText("Private Gallery ${BuildConfig.VERSION_NAME}").performScrollTo().performClick()
        repeat(4) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithText("Protected settings unlocked").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Updates & About").performClick()
        repeat(5) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Updates & About").performClick()
        compose.onNodeWithText("Private Gallery ${BuildConfig.VERSION_NAME}").performScrollTo().performClick()
        repeat(4) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithText("Protected settings unlocked").assertDoesNotExist()
    }

    @Test fun secretRevealAndScreenshotEnableRequireFreshPin() {
        compose.setContent { FixtureTheme { SettingsFixture(AppTheme.DARK, allowSecretPin = true, onTimeout = {}) } }
        compose.onNodeWithText("Updates & About").performClick()
        repeat(5) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithText("Private Gallery ${BuildConfig.VERSION_NAME}").performClick()
        repeat(4) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Security & privacy").performClick()
        compose.onNodeWithText("Secret").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Hide content").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onAllNodes(isToggleable()).onFirst().assertIsOn()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("Confirm owner identity").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Confirm owner identity").assertDoesNotExist()
        compose.onAllNodes(isToggleable()).onFirst().assertIsOn()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onAllNodes(isToggleable()).onFirst().assertIsOff()
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.onNodeWithText("Confirm owner identity").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Allow screenshots?").assertDoesNotExist()
        compose.onAllNodes(isToggleable())[1].assertIsOff()
    }

    @Test fun concealingSecretDoesNotRevealHiddenMedia() {
        var hidden = false
        compose.setContent { FixtureTheme { SettingsFixture(AppTheme.DARK, allowSecretPin = true,
            onHiddenChanged = { hidden = it }, onTimeout = {}) } }
        compose.onNodeWithText("Updates & About").performClick()
        repeat(5) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithText("Private Gallery ${BuildConfig.VERSION_NAME}").performClick()
        repeat(4) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Security & privacy").performClick()
        compose.onNodeWithText("Secret").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.runOnIdle { assertTrue(hidden) }
        compose.onNodeWithText("Hide Secret settings again").performScrollTo().performClick()
        compose.onNodeWithText("Secret").assertDoesNotExist()
        compose.runOnIdle { assertTrue(hidden) }
    }

    @Test fun cancelledBiometricResponseCannotRevealLaterRequest() {
        val requests = mutableListOf<(Boolean) -> Unit>()
        var hidden = false
        compose.setContent { FixtureTheme { SettingsFixture(AppTheme.DARK, allowSecretPin = false,
            onHiddenChanged = { hidden = it }, onBiometricRequest = { requests += it }, onTimeout = {}) } }
        compose.onNodeWithText("Updates & About").performClick()
        repeat(5) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithText("Private Gallery ${BuildConfig.VERSION_NAME}").performClick()
        repeat(4) { compose.onNodeWithText("Installed").performClick() }
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Security & privacy").performClick()
        compose.onNodeWithText("Secret").performClick()
        compose.runOnIdle { requests[0](true) }
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.runOnIdle { assertTrue(hidden) }
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.runOnIdle { requests[1](true) }
        compose.onNodeWithText("Confirm owner identity").assertIsDisplayed()
        compose.runOnIdle { assertTrue(hidden); requests[2](true) }
        compose.runOnIdle { assertFalse(hidden) }
    }
    private fun galleryMenu(theme: AppTheme) {
        compose.setContent { FixtureTheme(theme) {
            GalleryHome(true, {}, { galleryFixturePages() }, { _, done -> done(sampleBitmap().asImageBitmap()) }, { _, _ -> }, { _, _ -> }, { _, _ -> })
        } }
        // Paging's asynchronous differ is not covered by Compose's UI-idle synchronization.
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Sample photo").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithContentDescription("Sample photo").assertIsDisplayed()
        capture("gallery-$theme")
        compose.onNodeWithContentDescription("Gallery menu").performClick()
        compose.onNodeWithText("Select items").assertIsEnabled()
        compose.onNodeWithText("Refresh").assertIsDisplayed()
        compose.onNodeWithText("Add with Photo Picker").assertIsDisplayed()
        capture("gallery-menu-$theme")
        compose.onNodeWithText("Large").performScrollTo().performClick().assertIsSelected()
    }

    // Test-owned fixtures only; never capture real Vault or Browser data.
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val output = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: java.io.File(instrumentation.targetContext.externalMediaDirs.first(), "additional_test_output").absolutePath
        val folder = java.io.File(output, "ui-consistency").apply { mkdirs() }
        // AndroidX temporarily enables rendering on Google ATD for this synthetic capture.
        androidx.test.core.app.takeScreenshot().let { bitmap ->
            java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun favouriteUsesGenericCollection() {
        compose.setContent { FixtureTheme(AppTheme.DARK) {
            FavouriteHome({ it(VaultCollection("favourite", "Jenna", 0L)) }, { it(listOf(sampleItem())) }, { _, done -> done(listOf(sampleItem())) },
                { _, _, _ -> }, { _, _, _ -> }, { _, done -> done(Result.success(sampleBitmap())) }, 0, {}, { _, _, _ -> })
        } }
        compose.onNodeWithText("Jenna").assertIsDisplayed()
        compose.onNodeWithText("1 items").assertIsDisplayed()
        capture("favourite-DARK")
    }

    @Test fun galleryPermissionAction() {
        var requested = false
        compose.setContent { FixtureTheme {
            GalleryHome(false, { requested = true }, { flowOf(PagingData.empty()) }, { _, done -> done(null) }, { _, _ -> }, { _, _ -> }, { _, _ -> })
        } }
        compose.onNodeWithText("Allow Gallery access").performClick()
        compose.runOnIdle { assertTrue(requested) }
    }

    @Test fun vaultMediaCollectionsAndMenuLight() = vaultStates(AppTheme.LIGHT)
    @Test fun vaultMediaCollectionsAndMenuDark() = vaultStates(AppTheme.DARK)
    private fun vaultStates(theme: AppTheme) {
        compose.setContent { FixtureTheme(theme) { VaultFixture() } }
        compose.onNodeWithText("Media", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1 photos · 0 videos").assertIsDisplayed()
        compose.onNodeWithText("Search filenames").assertIsDisplayed()
        capture("vault-media-$theme")
        compose.onNodeWithText("Collections").performClick().assertIsSelected()
        compose.onNodeWithText("Trips").assertIsDisplayed()
        capture("vault-collections-$theme")
        compose.onNodeWithContentDescription("Vault menu").performClick()
        compose.onNodeWithText("New collection").assertIsDisplayed()
        capture("vault-menu-$theme")
        compose.onNodeWithText("New collection").performClick()
        compose.onNodeWithText("Collection name").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Collections").assertIsSelected()
    }

    @Test fun settingsLight() = settings(AppTheme.LIGHT)
    @Test fun settingsDark() = settings(AppTheme.DARK)
    private fun settings(theme: AppTheme) {
        var chosen = AutoLockTimeout.IMMEDIATELY
        compose.setContent { FixtureTheme(theme) { SettingsFixture(theme) { chosen = it } } }
        compose.onNodeWithText("Settings").assertIsDisplayed()
        capture("settings-$theme")
        compose.onNodeWithText("Security & privacy").performClick()
        compose.onNodeWithText("After 30 seconds").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AutoLockTimeout.SECONDS_30, chosen) }
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Updates & About").performScrollTo().performClick()
        compose.onNodeWithText("Check for updates").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        for (title in listOf("Gallery & Vault", "Browser", "VPN", "AI editing", "Appearance", "Debug / Acceptance")) {
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithContentDescription("Back to Settings").assertExists().performClick()
        }
    }

    @Test fun galleryDestinationsKeepNavigationAndBrowserUsesItsOwnToolbar() {
        var route by mutableStateOf(Route.GALLERY)
        compose.setContent { FixtureTheme { ProtectedAppShell(route, "Jenna", {}) {} } }
        val before = compose.onNodeWithText("Gallery").fetchSemanticsNode().boundsInRoot
        for (destination in listOf(Route.VAULT, Route.FAVOURITE, Route.SETTINGS)) {
            compose.runOnIdle { route = destination }
            listOf("Gallery", "Vault", "Jenna", "Browser", "Settings").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
            assertEquals(before, compose.onNodeWithText("Gallery").fetchSemanticsNode().boundsInRoot)
        }
        compose.onNodeWithText("Settings").assertIsSelected()
        compose.runOnIdle { route = Route.BROWSER }
        compose.onNodeWithText("Gallery").assertDoesNotExist()
    }

    @Test fun requiredVpnDoesNotCreateOrAttachWebViewUntilConnected() {
        var vpn by mutableStateOf(VpnConnectionState.CONNECTING)
        var creations = 0
        val session = BrowserV2Session(compose.activity, BrowserVpnGate { vpn == VpnConnectionState.CONNECTED }, NoopBrowserV2Listener,
            webViewFactory = BrowserV2WebViewFactory { context, _, _, _ -> creations++; android.webkit.WebView(context) })
        compose.setContent { FixtureTheme {
            BrowserV2ProductionDestination(session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, false, {}, emptyList(), { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, {},
                connectionPresentation = BrowserConnectionPresentation.from(true, vpn))
        } }
        compose.onNodeWithTag("browser-v2-webview-host").assertDoesNotExist()
        compose.runOnIdle {
            session.navigateActive("https://example.invalid/blocked")
            assertEquals(0, creations)
            assertEquals("", session.tabs.activeTab.url)
            vpn = VpnConnectionState.CONNECTED
        }
        compose.onNodeWithTag("browser-v2-webview-host").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, creations); vpn = VpnConnectionState.FAILED }
        compose.onNodeWithTag("browser-v2-webview-host").assertDoesNotExist()
        compose.onNodeWithText("Couldn't connect to VPN").assertIsDisplayed()
        compose.runOnIdle { session.destroyAll() }
    }

    @Test fun fullscreenHidesNavigationAndVpnLossClosesCustomView() {
        var vpn by mutableStateOf(VpnConnectionState.CONNECTED)
        var fullscreen by mutableStateOf(false)
        var exits = 0
        val session = BrowserV2Session(compose.activity, BrowserVpnGate { vpn == VpnConnectionState.CONNECTED }, NoopBrowserV2Listener)
        compose.setContent { FixtureTheme {
            ProtectedAppShell(Route.BROWSER, "Jenna", {}, hideNavigation = fullscreen) { padding ->
                BrowserV2ProductionDestination(session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, false, {}, emptyList(), { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, {},
                    modifier = Modifier.padding(padding), staticContentHost = true,
                    connectionPresentation = BrowserConnectionPresentation.from(true, vpn), onFullscreenChanged = { fullscreen = it })
            }
        } }
        compose.onNodeWithText("Gallery").assertIsDisplayed()
        compose.runOnIdle { session.onShowCustomView(session.tabs.activeTab.id, android.view.View(compose.activity), android.webkit.WebChromeClient.CustomViewCallback { exits++ }) }
        compose.onNodeWithText("Gallery").assertDoesNotExist()
        compose.runOnIdle { vpn = VpnConnectionState.FAILED }
        compose.onNodeWithText("Gallery").assertIsDisplayed()
        compose.onNodeWithText("Couldn't connect to VPN").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, exits); session.destroyAll() }
    }

    @Test fun permissionAndProfileActionsAreAvailable() {
        var presentation by mutableStateOf(BrowserConnectionPresentation.from(true, VpnConnectionState.FAILED, permissionRequired = true))
        var permissions = 0
        var settings = 0
        compose.setContent { FixtureTheme { BrowserConnectionState(presentation, { permissions++ }, { settings++ }) } }
        compose.onNodeWithText("Allow VPN connection").performClick()
        compose.runOnIdle { assertEquals(1, permissions); presentation = BrowserConnectionPresentation.from(true, VpnConnectionState.UNCONFIGURED) }
        compose.onNodeWithText("Set up VPN").performClick()
        compose.runOnIdle { assertEquals(1, settings) }
    }

    @Test fun vpnTransitionLight() = vpnTransition(AppTheme.LIGHT)
    @Test fun vpnTransitionDark() = vpnTransition(AppTheme.DARK)
    private fun vpnTransition(theme: AppTheme) {
        var vpn by mutableStateOf(VpnConnectionState.DISCONNECTED)
        var attempts = 0
        val session = BrowserV2Session(compose.activity, BrowserVpnGate { vpn == VpnConnectionState.CONNECTED }, NoopBrowserV2Listener)
        compose.setContent { FixtureTheme(theme) {
            BrowserV2ProductionDestination(session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, false, {}, emptyList(), { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, {},
                staticContentHost = true,
                connectionPresentation = BrowserConnectionPresentation.from(true, vpn),
                onConnectVpn = { attempts++; vpn = VpnConnectionState.CONNECTING })
        } }
        compose.onNodeWithText("VPN not connected").assertIsDisplayed()
        val page = compose.onNodeWithTag("browser-v2-page-region").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Connect securely").performClick()
        compose.onNodeWithContentDescription("Connecting to VPN").assertIsDisplayed()
        capture("vpn-connecting-$theme")
        compose.onNodeWithTag("browser-v2-address").assertIsNotEnabled()
        compose.onNodeWithTag("browser-v2-static-content-host").assertDoesNotExist()
        compose.runOnIdle { vpn = VpnConnectionState.FAILED }
        compose.onNodeWithText("Couldn't connect to VPN").assertIsDisplayed()
        capture("vpn-failed-$theme")
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
    VaultHome(onLock = {}, onImport = { _, _ -> }, onLoadItems = { it(listOf(sampleItem())) },
        onLoadCollections = { it(listOf(VaultCollection("trips", "Trips", 0L))) },
        onCreateCollection = { _, _ -> }, onAddItemsToCollection = { _, _, _ -> }, onRemoveItemsFromCollection = { _, _, _ -> },
        onRenameCollection = { _, _, _ -> }, onDeleteCollection = { _, _ -> }, onLoadCollectionItems = { _, done -> done(listOf(sampleItem())) },
        onLoadPreview = { _, done -> done(Result.success(sampleBitmap())) }, cropRevision = 0, biometricEnabled = true, onEnrollBiometrics = {},
        onSetFavouriteCollection = { _, _ -> }, onFavouriteStateChanged = {}, onOpenViewer = { _, _, _ -> })
}

@Composable
private fun SettingsFixture(theme: AppTheme, allowSecretPin: Boolean = false,
    onHiddenChanged: (Boolean) -> Unit = {}, onBiometricRequest: (((Boolean) -> Unit) -> Unit)? = null,
    onTimeout: (AutoLockTimeout) -> Unit) {
    var discovered by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    var screenshots by remember { mutableStateOf(false) }
    SettingsHome(autoLockTimeout = AutoLockTimeout.IMMEDIATELY, appTheme = theme, allowScreenshots = screenshots,
        updateStatus = "Up to date", updateLastChecked = "Today", updateAvailable = false, biometricEnabled = true,
        recoveryKeyConfigured = true, browserSearchEngine = BrowserSearchEngine.GOOGLE, clearBrowserDataOnLock = true,
        onAutoLockTimeoutChanged = onTimeout, onThemeChanged = {}, onAllowScreenshotsChanged = { screenshots = it }, onBrowserSearchEngineChanged = {},
        onClearBrowserDataOnLockChanged = {}, onClearBrowserData = {}, onCheckForUpdates = {}, onDownloadUpdate = {},
        onChangePin = { _, _ -> Result.success(Unit) }, onLock = {}, browserAutoConnectVpn = true, browserRequireVpn = true,
        onBrowserAutoConnectVpnChanged = {}, onBrowserRequireVpnChanged = {}, onImportWireGuardProfile = {},
        vpnProfileStatus = "", vpnConnectionState = VpnConnectionState.DISCONNECTED, vpnProfiles = emptyList(), onSelectVpnProfile = {}, onRemoveVpnProfile = {},
        secretDiscovered = discovered, onSecretDiscoveryChanged = { discovered = it }, hideContent = hidden,
        onHideContentChanged = { hidden = it; onHiddenChanged(it) }, onVerifySecretPin = { pin -> pin.fill('\u0000'); allowSecretPin },
        onAuthenticateSensitive = onBiometricRequest ?: { it(false) })
}

private fun sampleItem() = VaultItem("sample", "image/jpeg", "Sample photo", 0L, 1L, byteArrayOf(), byteArrayOf(), VaultItemState.COMPLETE)
private fun sampleBitmap(): android.graphics.Bitmap = android.graphics.Bitmap.createBitmap(80, 80, android.graphics.Bitmap.Config.ARGB_8888).apply {
    eraseColor(android.graphics.Color.rgb(115, 135, 160))
}

@Composable
private fun FixtureTheme(theme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    PrivateGalleryTheme(theme) { androidx.compose.material3.Surface(Modifier.requiredSize(392.dp, 840.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() } }
}

private fun galleryFixturePages() = Pager(PagingConfig(pageSize = 20)) {
    object : PagingSource<Int, DeviceMediaItem>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DeviceMediaItem> = LoadResult.Page(
            data = listOf(DeviceMediaItem(1L, android.net.Uri.parse("content://test/media/1"), DeviceMediaKind.IMAGE, "Sample photo", "image/jpeg", 0L, 0L)),
            prevKey = null, nextKey = null,
        )
        override fun getRefreshKey(state: PagingState<Int, DeviceMediaItem>): Int? = null
    }
}.flow
