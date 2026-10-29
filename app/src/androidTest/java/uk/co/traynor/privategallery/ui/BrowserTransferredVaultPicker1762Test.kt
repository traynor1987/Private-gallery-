package uk.co.traynor.privategallery.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.*
import uk.co.traynor.privategallery.core.hidden.*
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.vault.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Real encrypted transfer and real picker reopening. The isolated repository callback tests
 * metadata/requery/filtering; MainActivity's authentication and routing gates require separate tests.
 * No owner media, external file chooser, network destination or plaintext export is used. */
class BrowserTransferredVaultPicker1762Test {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private class Params:WebChromeClient.FileChooserParams() {
        override fun getMode()=MODE_OPEN
        override fun getAcceptTypes()=arrayOf("image/png")
        override fun getPermissionMode()=PERMISSION_MODE_READ
        override fun isCaptureEnabled()=false
        override fun getTitle():CharSequence?=null
        override fun getFilenameHint():String?=null
        override fun createIntent()=android.content.Intent()
    }

    private fun transferThenReopen(move:Boolean,restricted:Boolean) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val id=UUID.randomUUID().toString()
        val root=File(base.noBackupFilesDir,"transferred-picker-$id").apply {mkdirs()}
        val prefs=mutableSetOf<String>()
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=File(root,"primary").apply {mkdirs()}
            override fun getNoBackupFilesDir()=File(root,"hidden").apply {mkdirs()}
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                val scoped="transferred-picker-$id-$name";prefs+=scoped
                return base.getSharedPreferences(scoped,mode)
            }
        }
        val key=PinVaultKeyStore(context).create("123456".toCharArray())
        val hidden=HiddenSession(android.os.SystemClock::elapsedRealtime)
        assertTrue(hidden.complete(hidden.beginAuthentication(),ByteArray(32){7}))
        val source=HiddenRepository(context,hidden)
        val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
        val bytes=try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}} finally {bitmap.recycle()}
        val item=try {source.importMedia("Transferred synthetic.png","image/png",bytes.inputStream(),vaultOnly=restricted)} finally {bytes.fill(0)}
        val previous=BrowserUploadPreference.read(base)
        BrowserUploadPreference.write(base,BrowserUploadPolicy.VAULT_ONLY)
        val mounted=mutableStateOf(true)
        val session=BrowserV2Session(compose.activity,BrowserVpnGate{true},NoopBrowserV2Listener)
        val results=mutableListOf<List<Uri>?>()
        var queries=0
        try {
            compose.setContent {PrivateGalleryTheme {if(mounted.value)BrowserV2Home(
                session,BrowserSearchEngine.GOOGLE,{_,_->},{_,_->},false,{},emptyList(),
                {_,_,_->},{},{it(emptyList())},{it()},{},staticContentHost=true,
                onLoadVaultItems={done ->queries++;done(AndroidVaultRepository(context,key).items())},
            )}}
            fun open() {
                compose.runOnIdle {assertTrue(session.onShowFileChooser(session.tabs.activeTab.id,
                    ValueCallback {results+=it?.toList()},Params()))}
                compose.onNodeWithText("Choose from Vault").assertIsDisplayed()
            }
            // Prime the actual picker with an empty Primary index before transfer.
            open()
            compose.onNodeWithText("No compatible Vault items.",substring=true).assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            HiddenRestoreAccess(context,hidden).use {access ->
                access.authenticate("123456".toCharArray())
                val result=access.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,move){_,_->}
                assertEquals(1,result.verified);assertEquals(if(move)1 else 0,result.moved);assertEquals(0,result.failed)
            }
            val freshKey=PinVaultKeyStore(context).unlock("123456".toCharArray())
            val copy=try {
                val repository=AndroidVaultRepository(context,freshKey)
                repository.items().single().also {repository.verifyTransferDestination(it){false}}
            } finally {freshKey.fill(0)}
            assertNotEquals(item.id,copy.id)
            assertArrayEquals(item.plaintextSha256,copy.plaintextSha256)
            assertEquals(restricted,copy.vaultOnly)
            assertEquals(item.origin,copy.origin)
            assertEquals(VaultItemState.COMPLETE,copy.state)
            assertEquals(if(move)0 else 1,source.items().size)
            assertEquals(if(move)1 else 0,source.recentlyDeleted().size)
            repeat(2) {
                open()
                if(restricted) {
                    compose.onNodeWithContentDescription(item.displayName).assertDoesNotExist()
                    compose.onNodeWithText("1 Vault-only item(s) cannot leave the Vault and cannot be uploaded.").assertIsDisplayed()
                    compose.onNodeWithText("Continue").assertIsNotEnabled()
                } else {
                    compose.onNodeWithContentDescription(item.displayName).assertIsDisplayed().performClick()
                    compose.onNodeWithText("Continue").assertIsEnabled()
                }
                compose.onNodeWithText("Cancel").performClick()
            }
            compose.runOnIdle {
                assertEquals("Each reopen must query fresh Primary metadata",3,queries)
                assertEquals(listOf<List<Uri>?>(null,null,null),results)
            }
        } finally {
            compose.runOnIdle {mounted.value=false}
            compose.runOnIdle {session.destroyAll()}
            BrowserUploadPreference.write(base,previous)
            hidden.lock();key.fill(0);root.deleteRecursively()
            prefs.forEach {base.getSharedPreferences(it,0).edit().clear().commit()}
        }
    }

    @Test fun encryptedCopyAppearsAfterEmptyPickerIsClosedAndReopened()=transferThenReopen(false,false)
    @Test fun encryptedMoveAppearsAfterEmptyPickerIsClosedAndReopened()=transferThenReopen(true,false)
    @Test fun restrictedCopyRemainsExcludedWithExplanationAfterReopening()=transferThenReopen(false,true)
    @Test fun restrictedMoveRemainsExcludedWithExplanationAfterReopening()=transferThenReopen(true,true)
}
