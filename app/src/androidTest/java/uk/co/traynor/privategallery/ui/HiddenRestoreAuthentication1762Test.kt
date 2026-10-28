package uk.co.traynor.privategallery.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
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
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.*

/** Real encrypted reverse-transfer dialog, not a synthetic nonexistent item. */
class HiddenRestoreAuthentication1762Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun fixture(block:(HiddenSession,HiddenRestoreAccess,HiddenRepository,AndroidVaultRepository,VaultItem,()->Int,(Long)->Unit)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val id=UUID.randomUUID().toString()
        val root=File(base.noBackupFilesDir,"restore-auth-ui-$id").apply {mkdirs()}
        val prefs=mutableSetOf<String>()
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=File(root,"primary").apply {mkdirs()}
            override fun getNoBackupFilesDir()=File(root,"hidden").apply {mkdirs()}
            override fun getPackageName()="${base.packageName}.restoreui.$id"
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                val n="restore-ui-$id-$name";prefs+=n;return base.getSharedPreferences(n,mode)
            }
        }
        val primaryKey=PinVaultKeyStore(context).create("123456".toCharArray())
        var now=1000L
        val hidden=HiddenSession {now}
        assertTrue(hidden.complete(hidden.beginAuthentication(),ByteArray(32){7}))
        val access=HiddenRestoreAccess(context,hidden)
        val shown=mutableStateOf(true)
        var completions=0
        try {
            val bytes=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).let {b ->
                try {ByteArrayOutputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}} finally {b.recycle()}
            }
            val repo=HiddenRepository(context,hidden)
            val item=try {repo.importMedia("reverse-transfer.png","image/png",ByteArrayInputStream(bytes))} finally {bytes.fill(0)}
            compose.setContent {PrivateGalleryTheme(AppTheme.DARK) {
                if(shown.value)HiddenRestoreDialog(access,listOf(item.id)){completions++;shown.value=false}
            }}
            block(hidden,access,repo,AndroidVaultRepository(context,primaryKey),item,{completions},{now=it})
        } finally {
            compose.runOnIdle {shown.value=false;access.close();hidden.lock()}
            primaryKey.fill(0);root.deleteRecursively()
            prefs.forEach {base.getSharedPreferences(it,Context.MODE_PRIVATE).edit().clear().commit()}
        }
    }
    private fun enter(pin:String) {
        pin.forEach {compose.onNodeWithText(it.toString()).performScrollTo().performClick()}
        compose.onNodeWithText("Continue").performScrollTo().performClick()
    }
    @Test fun wrongThenCorrectKeypadPinCopiesVerifiedMediaAndKeepsHiddenOriginal()=fixture {hidden,_,repo,primary,item,count,_ ->
        compose.onNodeWithText("Copy to Primary Vault").performScrollTo().performClick()
        enter("999999")
        compose.waitUntil(30_000){compose.onAllNodesWithText("Primary authentication failed.",substring=true).fetchSemanticsNodes().isNotEmpty()}
        assertTrue(primary.items().isEmpty());assertEquals(item.id,repo.items().single().id)
        enter("123456")
        compose.waitUntil(30_000){compose.onAllNodesWithText("Copy to Primary Vault").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Copy to Primary Vault").performScrollTo().performClick()
        compose.waitUntil(30_000){count()==1}
        assertTrue(hidden.isUnlocked)
        val copy=primary.items().single();assertNotEquals(item.id,copy.id);assertArrayEquals(item.plaintextSha256,copy.plaintextSha256)
        assertEquals(item.id,repo.items().single().id)
        val bytes=primary.readForEditing(copy);try {assertTrue(bytes.isNotEmpty())} finally {bytes.fill(0)}
    }
    @Test fun correctKeypadPinMovesOnlyAfterSeparateConfirmation()=fixture {_,_,repo,primary,item,count,_ ->
        compose.onNodeWithText("Move to Primary Vault").performScrollTo().performClick();enter("123456")
        compose.waitUntil(30_000){compose.onAllNodesWithText("Confirm Move to Primary Vault").fetchSemanticsNodes().isNotEmpty()}
        assertTrue(primary.items().isEmpty());assertEquals(item.id,repo.items().single().id)
        compose.onNodeWithText("Confirm Move to Primary Vault").performScrollTo().performClick()
        compose.waitUntil(30_000){count()==1}
        assertArrayEquals(item.plaintextSha256,primary.items().single().plaintextSha256)
        assertTrue(repo.items().isEmpty());assertEquals(item.id,repo.recentlyDeleted().single().id)
    }
    @Test fun cancellationAtKeypadDoesNotPublishOrDelete()=fixture {hidden,access,repo,primary,item,count,_ ->
        compose.onNodeWithText("Move to Primary Vault").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick();compose.waitUntil(3000){count()==1}
        assertFalse(access.isActive());assertTrue(hidden.isUnlocked);assertTrue(primary.items().isEmpty());assertEquals(item.id,repo.items().single().id)
    }
    @Test fun lockWhileEnteringPrimaryPinRevokesWithoutPublishing()=fixture {hidden,access,_,primary,_,count,_ ->
        compose.onNodeWithText("Copy to Primary Vault").performScrollTo().performClick()
        compose.runOnIdle {hidden.lock()};compose.waitUntil(3000){count()==1}
        assertFalse(access.isActive());assertTrue(primary.items().isEmpty())
    }
    @Test fun expiredHiddenGraceRevokesPrimaryAuthenticationDialog()=fixture {hidden,access,_,primary,_,count,setTime ->
        compose.onNodeWithText("Copy to Primary Vault").performScrollTo().performClick()
        compose.runOnIdle {hidden.onBackground(30_000);setTime(31_001);hidden.onForeground()}
        compose.waitUntil(3000){count()==1}
        assertFalse(hidden.isUnlocked);assertFalse(access.isActive());assertTrue(primary.items().isEmpty())
    }
}
