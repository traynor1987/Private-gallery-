package uk.co.traynor.privategallery.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import uk.co.traynor.privategallery.core.hidden.*
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore

class HiddenVaultIntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private val id=UUID.randomUUID().toString()
    private val root=File(base.noBackupFilesDir,"hidden-integration-$id")
    private val preferences=mutableSetOf<String>()
    private val context=object:ContextWrapper(base) {
        override fun getPackageName()="${base.packageName}.test.$id"
        override fun getNoBackupFilesDir()=root
        override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
            val qualified="hidden-integration-$id-$name";preferences.add(qualified);return base.getSharedPreferences(qualified,mode)
        }
    }
    @After fun cleanup() {
        preferences.forEach {base.getSharedPreferences(it,Context.MODE_PRIVATE).edit().clear().commit()}
        KeyStore.getInstance("AndroidKeyStore").apply {load(null);deleteEntry("${context.packageName}.hidden.v1.default")}
        root.deleteRecursively()
    }
    private fun seedPhoto() {
        PinVaultKeyStore(context).create("123456".toCharArray()).fill(0)
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        session.complete(session.beginAuthentication(),HiddenCredentialStore(context).create("728491".toCharArray()))
        try {
            val bytes=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).let {b ->try {ByteArrayOutputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}} finally {b.recycle()}}
            val repo=HiddenRepository(context,session)
            val item=repo.importMedia("disposable.png","image/png",ByteArrayInputStream(bytes))
            val collection=repo.createCollection("Test favourites");repo.addItemsToCollection(collection.id,listOf(item.id));repo.setFavouriteCollection(collection.id)
        } finally {session.lock()}
    }
    private fun unlock() {
        "728491".forEach { compose.onNodeWithText(it.toString(),substring=false).performClick() }
        compose.onNodeWithText("Unlock",substring=false).performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithContentDescription("Lock Vault").fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(30_000){compose.onAllNodesWithContentDescription("disposable.png").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun assertHiddenCaptureBlocked(blocked:Boolean) {
        compose.runOnIdle {
            val dialogs=android.view.inspector.WindowInspector.getGlobalWindowViews()
                .filter {it !== compose.activity.window.decorView && it.isShown}
                .mapNotNull {it.layoutParams as? android.view.WindowManager.LayoutParams}
            // Media uses the Activity window; PIN entry still owns a protected dialog.
            val activeWindows=dialogs.ifEmpty {listOf(compose.activity.window.attributes)}
            activeWindows.forEach {assertEquals(blocked,it.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)}
        }
    }
    private fun verifyCapturePreference(allowed:Boolean) {
        seedPhoto()
        compose.runOnIdle {
            if(allowed)compose.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            else compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        compose.setContent {MaterialTheme {HiddenVaultDialog(context,onDismiss={})}}
        compose.onNodeWithTag("passcode-indicator").assertIsDisplayed()
        assertHiddenCaptureBlocked(true) // Credentials remain protected even after explicit screenshot consent.
        unlock()
        assertHiddenCaptureBlocked(!allowed)
        compose.onNodeWithContentDescription("disposable.png").performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithTag("viewer-image").fetchSemanticsNodes().isNotEmpty()}
        assertHiddenCaptureBlocked(!allowed)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Lock Vault").performClick()
        compose.onNodeWithTag("passcode-indicator").assertIsDisplayed()
        assertHiddenCaptureBlocked(true)
    }
    @Test fun stopOnHiddenGridMustNotConcealNewlySelectedPhoto() {
        seedPhoto()
        compose.setContent {MaterialTheme {HiddenVaultDialog(context,onDismiss={})}}
        unlock()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(30_000){compose.onAllNodesWithContentDescription("disposable.png").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("disposable.png").performClick()
        compose.waitUntil(30_000){
            compose.onAllNodesWithTag("viewer-image").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("Preview hidden").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Preview hidden").assertDoesNotExist()
        compose.onNodeWithTag("viewer-image").assertExists()
    }

    @Test fun stopWithHiddenPhotoOpenMustKeepRetainedSelectionConcealed() {
        seedPhoto()
        compose.setContent {MaterialTheme {HiddenVaultDialog(context,onDismiss={})}}
        unlock()
        compose.onNodeWithContentDescription("disposable.png").performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithTag("viewer-image").fetchSemanticsNodes().isNotEmpty()}
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Preview hidden").assertIsDisplayed()
        compose.onNodeWithTag("viewer-image").assertDoesNotExist()
    }

    @Test fun explicitScreenshotConsentAppliesToHiddenGridAndViewerButNotPin() = verifyCapturePreference(true)
    @Test fun defaultScreenshotBlockingAppliesToHiddenGridViewerAndPin() = verifyCapturePreference(false)
    @Test fun hiddenBottomNavigationFitsActualActivityWindowNotItsOwnLayoutBounds() {
        seedPhoto()
        compose.setContent {MaterialTheme {HiddenVaultDialog(context,onDismiss={})}}
        unlock()
        compose.waitUntil(30_000){compose.onAllNodesWithText("Test favourites",substring=false).fetchSemanticsNodes().isNotEmpty()}
        var safeBottom=0f
        var width=0f
        compose.runOnIdle {
            val metrics=compose.activity.windowManager.currentWindowMetrics
            val bottom=metrics.windowInsets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime()).bottom
            safeBottom=(metrics.bounds.height()-bottom).toFloat()
            width=metrics.bounds.width().toFloat()
            val dialogs=android.view.inspector.WindowInspector.getGlobalWindowViews()
                .filter {it !== compose.activity.window.decorView && it.isShown}
            assertTrue("Unlocked Hidden must use the Activity window, not a separate full-screen dialog",dialogs.isEmpty())
        }
        listOf("Gallery","Vault","Test favourites","Browser","Settings").forEach {label ->
            compose.onNode(hasText(label) and hasAnyAncestor(isSelectable()),useUnmergedTree=true).assertIsDisplayed()
            val target=compose.onNode(hasText(label) and isSelectable())
            val node=target.fetchSemanticsNode()
            val item=target.getUnclippedBoundsInRoot()
            val density=compose.activity.resources.displayMetrics.density
            val offsetX=node.boundsInWindow.left-node.boundsInRoot.left
            val offsetY=node.boundsInWindow.top-node.boundsInRoot.top
            val left=item.left.value*density+offsetX
            val right=item.right.value*density+offsetX
            val bottom=item.bottom.value*density+offsetY
            assertTrue("$label extends past visible Activity safe bottom $safeBottom: $bottom",bottom<=safeBottom+1f)
            assertTrue("$label extends outside Activity width $width: $left..$right",left>=0f && right<=width+1f)
        }
    }
    @Test fun normalVaultGridViewerAndFavouriteUsePersistedHiddenData() {
        seedPhoto()
        val shown=mutableStateOf(true)
        compose.setContent {MaterialTheme {if(shown.value)HiddenVaultDialog(context,onDismiss={shown.value=false})}}
        unlock()
        compose.onNodeWithContentDescription("disposable.png").performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithTag("viewer-image").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("viewer-image").assertIsDisplayed()
        // Exercise the viewer toolbar inside the shared Activity viewport.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithText("Test favourites",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Test favourites",substring=false).performClick()
        compose.onNodeWithContentDescription("disposable.png").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lock Vault").performClick()
        compose.runOnIdle {assertTrue(shown.value)}
        compose.onNodeWithTag("passcode-indicator").assertIsDisplayed()
        PinVaultKeyStore(context).unlock("123456".toCharArray()).fill(0)
    }
    @Test fun wrongPinDoesNotExposeGalleryAndBackgroundRemovesUnlockedViewer() {
        seedPhoto()
        val shown=mutableStateOf(true)
        compose.setContent {MaterialTheme {if(shown.value)HiddenVaultDialog(context,onDismiss={shown.value=false})}}
        "123456".forEach { compose.onNodeWithText(it.toString(),substring=false).performClick() }
        compose.onNodeWithText("Unlock",substring=false).performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithText("Could not unlock or create Hidden.",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("disposable.png").assertDoesNotExist()
        unlock()
        compose.onNodeWithContentDescription("disposable.png").performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(shown.value)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(30_000){compose.onAllNodesWithContentDescription("disposable.png").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("passcode-indicator").assertDoesNotExist()
        compose.onNodeWithTag("viewer-image").assertDoesNotExist()
    }
    @Test fun primaryLockHidesHiddenAndRequiresHiddenReauthentication() {
        seedPhoto()
        val primaryAvailable=mutableStateOf(true)
        compose.setContent {MaterialTheme {HiddenVaultDialog(context,primaryAvailable=primaryAvailable.value,onDismiss={})}}
        unlock()
        compose.runOnIdle {primaryAvailable.value=false}
        compose.onNodeWithContentDescription("disposable.png").assertDoesNotExist()
        compose.onNodeWithTag("passcode-indicator").assertDoesNotExist()
        compose.runOnIdle {primaryAvailable.value=true}
        compose.onNodeWithTag("passcode-indicator").assertIsDisplayed()
        compose.onNodeWithContentDescription("disposable.png").assertDoesNotExist()
        unlock()
    }
    @Test fun persistedEncryptedVideoReachesFirstFrameAndBackgroundReleasesPlayer() {
        seedPhoto()
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        session.complete(session.beginAuthentication(),HiddenCredentialStore(context).unlock("728491".toCharArray()))
        try {
            val bytes=InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use {it.readBytes()}
            HiddenRepository(context,session).importMedia("disposable.mp4","video/mp4",ByteArrayInputStream(bytes))
        } finally {session.lock()}
        val shown=mutableStateOf(true)
        compose.setContent {MaterialTheme {if(shown.value)HiddenVaultDialog(context,onDismiss={shown.value=false})}}
        unlock()
        compose.onNodeWithContentDescription("disposable.mp4").performClick()
        compose.waitUntil(30_000){VaultPlaybackDiagnostics.summary().contains("FIRST_FRAME")}
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(shown.value)
        compose.waitUntil(10_000){VaultPlaybackDiagnostics.summary().contains("RELEASED")}
    }

    @Test fun pickerResultSurvivesPrimaryLockButImportRequiresFreshHiddenPin() {
        seedPhoto()
        val fixture=File(base.cacheDir,"updates/picker-fixture-$id.png").apply {parentFile!!.mkdirs()}
        val bitmap=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.RED)}
        try {fixture.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
        val uri=androidx.core.content.FileProvider.getUriForFile(base,"${base.packageName}.fileprovider",fixture)
        var requestCode=-1
        val registry=object:androidx.activity.result.ActivityResultRegistry() {
            override fun <I,O> onLaunch(code:Int,contract:androidx.activity.result.contract.ActivityResultContract<I,O>,input:I,options:androidx.core.app.ActivityOptionsCompat?) {requestCode=code}
        }
        val registryOwner=object:androidx.activity.result.ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        val shown=mutableStateOf(true)
        val primaryAvailable=mutableStateOf(true)
        try {
            compose.setContent {
                androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner) {
                    MaterialTheme {if(shown.value)HiddenVaultDialog(context,primaryAvailable=primaryAvailable.value,onDismiss={shown.value=false})}
                }
            }
            unlock()
            compose.onNodeWithContentDescription("Add media").performClick()
            compose.runOnIdle {assertTrue(requestCode>=0);primaryAvailable.value=false}
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue(shown.value)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnIdle {
                val data=android.content.ClipData.newRawUri("Synthetic picker test",uri)
                data.addItem(android.content.ClipData.Item(android.net.Uri.parse("content://synthetic-missing-provider/invalid.png")))
                registry.dispatchResult(requestCode,android.app.Activity.RESULT_OK,android.content.Intent().apply {clipData=data})
            }
            compose.onNodeWithTag("passcode-indicator").assertDoesNotExist()
            compose.runOnIdle {primaryAvailable.value=true}
            compose.onNodeWithTag("passcode-indicator").assertIsDisplayed()
            compose.onNodeWithContentDescription(fixture.name).assertDoesNotExist()
            unlock()
            compose.waitUntil(30_000){compose.onAllNodesWithContentDescription(fixture.name).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Imported 1 of 2 selected items. Original files were retained.").assertIsDisplayed()
            assertTrue(fixture.exists())
        } finally {fixture.delete()}
    }

    @Test fun activityBackOutsideViewerLocksHidden() {
        seedPhoto()
        val shown=mutableStateOf(true)
        compose.setContent {MaterialTheme {if(shown.value)HiddenVaultDialog(context,onDismiss={shown.value=false})}}
        unlock()
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.runOnIdle {assertFalse(shown.value)}
        compose.onNodeWithContentDescription("disposable.png").assertDoesNotExist()
    }

    private fun switchesThrough(destination:uk.co.traynor.privategallery.core.ui.AppNavigationDestination) {
        seedPhoto()
        val shown=mutableStateOf(true)
        val visible=mutableStateOf(true)
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        var exited:uk.co.traynor.privategallery.core.ui.AppNavigationDestination?=null
        try {
            compose.setContent {MaterialTheme {if(shown.value)HiddenVaultDialog(context,externalSession=session,showEntry=visible.value,
                onNavigateOutside={exited=it;visible.value=false},onDismiss={shown.value=false})}}
            unlock()
            listOf("Gallery","Vault","Browser","Settings").forEach {compose.onNodeWithText(it,substring=false).assertIsDisplayed()}
            compose.onNodeWithText("TEST ONLY",substring=true).assertIsDisplayed()
            compose.onNodeWithText(destination.label,substring=false).performClick()
            compose.runOnIdle {assertTrue(shown.value);assertTrue(session.isUnlocked);assertEquals(destination,exited)}
            compose.onNodeWithContentDescription("disposable.png").assertDoesNotExist()
            compose.runOnIdle {visible.value=true}
            compose.waitUntil(30_000){compose.onAllNodesWithContentDescription("disposable.png").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("passcode-indicator").assertDoesNotExist()
            compose.onNodeWithContentDescription("Lock Vault").performClick()
            compose.runOnIdle {assertTrue(shown.value);assertFalse(session.isUnlocked)}
            compose.onNodeWithTag("passcode-indicator").assertIsDisplayed()
        } finally {session.lock()}
    }
    @Test fun galleryNavigationRetainsAuthenticationButHidesHiddenMedia() = switchesThrough(uk.co.traynor.privategallery.core.ui.AppNavigationDestination.GALLERY)
    @Test fun browserNavigationRetainsAuthenticationButHidesHiddenMedia() = switchesThrough(uk.co.traynor.privategallery.core.ui.AppNavigationDestination.BROWSER)
    @Test fun settingsNavigationRetainsAuthenticationButHidesHiddenMedia() = switchesThrough(uk.co.traynor.privategallery.core.ui.AppNavigationDestination.SETTINGS)

    @Test fun expiredBackgroundGraceRequiresFreshPinAndKeepsEncryptedMedia() {
        seedPhoto()
        val clock=java.util.concurrent.atomic.AtomicLong(100)
        val session=HiddenSession(clock::get)
        try {
            compose.setContent {MaterialTheme {HiddenVaultDialog(context,externalSession=session,onDismiss={})}}
            unlock()
            compose.runOnIdle {session.onBackground(30_000)}
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            clock.addAndGet(30_000);session.onForeground()
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("passcode-indicator").assertIsDisplayed()
            compose.onNodeWithContentDescription("disposable.png").assertDoesNotExist()
            unlock()
        } finally {session.lock()}
    }

}
