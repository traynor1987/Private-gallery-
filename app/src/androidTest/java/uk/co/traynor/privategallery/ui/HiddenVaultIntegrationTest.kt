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
        compose.onNodeWithText("Hidden PIN").performTextInput("728491")
        compose.onNodeWithText("Unlock",substring=false).performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithText("Return to Primary").fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(30_000){compose.onAllNodesWithContentDescription("disposable.png").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun normalVaultGridViewerAndFavouriteUsePersistedHiddenData() {
        seedPhoto()
        val shown=mutableStateOf(true)
        compose.setContent {MaterialTheme {if(shown.value)HiddenVaultDialog(context,onDismiss={shown.value=false})}}
        unlock()
        compose.onNodeWithContentDescription("disposable.png").performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithTag("viewer-image").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("viewer-image").assertIsDisplayed()
        // The viewer is in a Dialog with its own Back dispatcher; exercise its actual toolbar.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithText("Test favourites",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Test favourites",substring=false).performClick()
        compose.onNodeWithContentDescription("disposable.png").assertIsDisplayed()
        compose.onNodeWithText("Return to Primary").performClick()
        compose.runOnIdle {assertFalse(shown.value)}
        PinVaultKeyStore(context).unlock("123456".toCharArray()).fill(0)
    }
    @Test fun wrongPinDoesNotExposeGalleryAndBackgroundRemovesUnlockedViewer() {
        seedPhoto()
        val shown=mutableStateOf(true)
        compose.setContent {MaterialTheme {if(shown.value)HiddenVaultDialog(context,onDismiss={shown.value=false})}}
        compose.onNodeWithText("Hidden PIN").performTextInput("123456")
        compose.onNodeWithText("Unlock",substring=false).performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithText("Could not unlock or create Hidden.",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("disposable.png").assertDoesNotExist()
        unlock()
        compose.onNodeWithContentDescription("disposable.png").performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(shown.value)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle {shown.value=true}
        compose.onNodeWithText("Hidden PIN").assertIsDisplayed()
        compose.onNodeWithTag("viewer-image").assertDoesNotExist()
    }
    @Test fun primaryLockHidesHiddenAndRequiresHiddenReauthentication() {
        seedPhoto()
        val primaryAvailable=mutableStateOf(true)
        compose.setContent {MaterialTheme {HiddenVaultDialog(context,primaryAvailable=primaryAvailable.value,onDismiss={})}}
        unlock()
        compose.runOnIdle {primaryAvailable.value=false}
        compose.onNodeWithContentDescription("disposable.png").assertDoesNotExist()
        compose.onNodeWithText("Hidden PIN").assertDoesNotExist()
        compose.runOnIdle {primaryAvailable.value=true}
        compose.onNodeWithText("Hidden PIN").assertIsDisplayed()
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
        assertFalse(shown.value)
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
            compose.onNodeWithText("Hidden PIN").assertDoesNotExist()
            compose.runOnIdle {primaryAvailable.value=true}
            compose.onNodeWithText("Hidden PIN").assertIsDisplayed()
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

}
