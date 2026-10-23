package uk.co.traynor.privategallery.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.hidden.*
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.vault.*

class PrimaryCopyDialogTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun deviceSourceUsesFullGalleryWithoutLockingHiddenWhenHostProvidesIt() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val hidden=HiddenSession(android.os.SystemClock::elapsedRealtime)
        hidden.complete(hidden.beginAuthentication(),ByteArray(32){5})
        val root=File(base.noBackupFilesDir,"gallery-route-${UUID.randomUUID()}").apply {mkdirs()}
        val isolated=object:ContextWrapper(base) {
            override fun getNoBackupFilesDir()=root
        }
        var opened=false
        compose.setContent {MaterialTheme {HiddenVaultDialog(isolated,externalSession=hidden,
            onBrowseDeviceGallery={opened=true},onDismiss={})}}
        try {
            compose.onNodeWithText("Add media").performClick()
            compose.runOnIdle {assertTrue(opened);assertTrue(hidden.isUnlocked)}
        } finally {compose.runOnIdle {hidden.lock()};root.deleteRecursively()}
    }
    @Test fun galleryProvidesPrimaryAccessWhileShowingDeviceMedia() {
        var requested=false
        compose.setContent { MaterialTheme {
            uk.co.traynor.privategallery.GalleryHome(false, {},
                { kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty()) }, {emptyList()},
                {_,done ->done(null)}, {_,_ ->}, {_,_ ->}, {_,_ ->},
                hiddenDestination=true,onBrowsePrimary={requested=true})
        } }
        compose.onNodeWithText("Device Gallery · Copy to Hidden").assertIsDisplayed()
        compose.onNodeWithText("Primary Vault").performClick()
        compose.runOnIdle {assertTrue(requested)}
        compose.onNodeWithText("Allow Gallery access").assertIsDisplayed()
    }
    @Test fun sourceChoiceFreshPinSelectionCopyAndReturnStayInHidden() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val id=UUID.randomUUID().toString()
        val root=File(base.noBackupFilesDir,"primary-copy-ui-$id").apply {mkdirs()}
        val prefs=mutableSetOf<String>()
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=File(root,"primary").apply {mkdirs()}
            override fun getNoBackupFilesDir()=File(root,"hidden").apply {mkdirs()}
            override fun getPackageName()="${base.packageName}.copyui.$id"
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                val n="copy-ui-$id-$name";prefs+=n;return base.getSharedPreferences(n,mode)
            }
        }
        val primaryKey=PinVaultKeyStore(context).create("123456".toCharArray())
        val hidden=HiddenSession(android.os.SystemClock::elapsedRealtime)
        hidden.complete(hidden.beginAuthentication(),ByteArray(32){7})
        val shown=mutableStateOf(true)
        try {
            val primary=AndroidVaultRepository(context,primaryKey)
            val bytes=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).let {b ->
                try {ByteArrayOutputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}} finally {b.recycle()}
            }
            primary.importVerified(VaultImportSource("copy-photo.png","image/png",{ByteArrayInputStream(bytes)}));bytes.fill(0)
            compose.setContent {MaterialTheme {if(shown.value) HiddenVaultDialog(context,externalSession=hidden,
                createPrimaryCopy={PrimaryCopyAccess(context,it)},onDismiss={shown.value=false})}}
            compose.onNodeWithContentDescription("Hidden Vault menu").performClick()
            compose.onNodeWithText("Copy from Primary Vault").performClick()
            compose.onNodeWithText("Primary PIN").assertIsDisplayed()
            compose.onNodeWithText("copy-photo.png").assertDoesNotExist()
            compose.onNodeWithText("Primary PIN").performTextInput("999999")
            compose.onNodeWithText("Continue").performClick()
            compose.waitUntil(30_000){compose.onAllNodesWithText("Primary authentication failed.",substring=true).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("copy-photo.png").assertDoesNotExist()
            compose.onNodeWithText("Primary PIN").performTextInput("123456")
            compose.onNodeWithText("Continue").performClick()
            compose.waitUntil(30_000){compose.onAllNodesWithText("copy-photo.png").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("copy-photo.png").performClick()
            compose.onNodeWithText("Copy to Hidden (1)").performClick()
            try {
                compose.waitUntil(30_000){compose.onAllNodesWithText("Verified 1 of 1",substring=true).fetchSemanticsNodes().isNotEmpty()}
            } catch (failure: Throwable) {
                // Synthetic fixture only: retain the actual terminal UI before teardown.
                compose.onAllNodes(isRoot(), useUnmergedTree = true).printToLog("PrimaryCopyFailure")
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                    ?: File(instrumentation.targetContext.externalMediaDirs.first(), "additional_test_output").absolutePath
                val folder = File(output, "primary-copy-diagnostics").apply { mkdirs() }
                androidx.test.core.app.takeScreenshot().let { bitmap ->
                    try { File(folder, "completion-failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    finally { bitmap.recycle() }
                }
                throw failure
            }
            compose.onNodeWithContentDescription("copy-photo.png").assertIsDisplayed()
            assertEquals(1,primary.items().size);assertEquals(1,HiddenRepository(context,hidden).items().size)
        } finally {
            compose.runOnIdle {shown.value=false}
            hidden.lock();primaryKey.fill(0);root.deleteRecursively()
            prefs.forEach {base.getSharedPreferences(it,Context.MODE_PRIVATE).edit().clear().commit()}
        }
    }
}
