package uk.co.traynor.privategallery.ui

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.PhotoRenderer
import uk.co.traynor.privategallery.core.hidden.*
import uk.co.traynor.privategallery.core.vault.AndroidVaultRepository

class HiddenEditorUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun sharedEditorSavesHiddenCopyAndOffersExistingCreationControls() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.cacheDir,"hidden-edit-ui-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=File(root,"primary").apply {mkdirs()}
            override fun getNoBackupFilesDir()=File(root,"hidden").apply {mkdirs()}
        }
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        session.complete(session.beginAuthentication(),ByteArray(32){7})
        val repo=HiddenRepository(context,session)
        val bitmap=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
        val source=try {PhotoRenderer.encode(bitmap)} finally {bitmap.recycle()}
        val original=repo.importMedia("hidden-original.png","image/png",ByteArrayInputStream(source))
        try {
            compose.setContent {PrivateGalleryTheme {HiddenVaultDialog(context,externalSession=session,onDismiss={})}}
            compose.onNodeWithContentDescription("hidden-original.png").performClick()
            compose.onNodeWithText("Edit").performClick()
            compose.onNodeWithTag("photo-editor").assertIsDisplayed()
            compose.onNode(hasText("Hidden Vault") and hasAnyAncestor(hasTestTag("photo-editor"))).assertIsDisplayed()
            compose.onNodeWithText("Adjust").performClick()
            compose.onNodeWithText("Brightness").assertIsDisplayed()
            compose.waitUntil(10000) {runCatching {compose.onNodeWithText("Save copy").assertIsEnabled()}.isSuccess}
            compose.onNodeWithText("Save copy").performClick()
            compose.waitUntil(10000) {repo.items().size==2}
            assertArrayEquals(source,repo.read(original.id))
            assertTrue(AndroidVaultRepository(context,ByteArray(32){9}).items().isEmpty())
            compose.waitUntil(10000) {compose.onAllNodesWithTag("photo-editor").fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithContentDescription("Hidden Vault menu").performClick()
            compose.onNodeWithText("Create image").performClick()
            compose.onNodeWithText("Saves to Hidden Vault").assertIsDisplayed()
        } finally {compose.runOnIdle {session.lock()};source.fill(0);root.deleteRecursively()}
    }
}
