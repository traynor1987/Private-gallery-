package uk.co.traynor.privategallery.ui

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.vault.VaultItem

class CreateImageCancellationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun lateCancelledGenerationCannotPublishOrReplaceNewRequestState() {
        val base=compose.activity
        val prefix="creation-ui-${UUID.randomUUID()}"
        val root=File(base.cacheDir,prefix).apply {mkdirs()}
        val prefs=mutableSetOf<String>()
        val context=object:ContextWrapper(base) {
            override fun getNoBackupFilesDir()=root
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                val qualified="$prefix-$name";prefs+=qualified;return base.getSharedPreferences(qualified,mode)
            }
        }
        // Synthetic credential only; the injected generator never uses a transport.
        AiCredentialStore(context).save(ReplicateSeedreamProvider.ID,byteArrayOf(1,2,3))
        AiConsentStore(context).remember(ReplicateSeedreamProvider.ID)
        val callbacks=mutableListOf<(Result<VaultItem>)->Unit>()
        var cancellations=0
        var saved=0
        try {
            compose.setContent {CompositionLocalProvider(LocalContext provides context) {PrivateGalleryTheme {
                CreateImageSheet(onGenerate={_,_,done ->callbacks+=done;{cancellations++}},
                    onSaved={saved++},onDismiss={},vaultLabel="Hidden Vault")
            }}}
            compose.onNodeWithText("Describe the image").performScrollTo().performTextInput("Synthetic fixture")
            compose.onNodeWithText("Generate").performScrollTo().performClick()
            compose.onNodeWithText("Cancel").performScrollTo().performClick()
            compose.runOnIdle {assertEquals(1,cancellations);callbacks[0](Result.failure(IllegalStateException("STALE RESULT")))}
            compose.onNodeWithText("STALE RESULT").assertDoesNotExist()
            compose.onNodeWithText("Generate").performScrollTo().performClick()
            compose.runOnIdle {callbacks[0](Result.failure(IllegalStateException("STALE RESULT")))}
            compose.onNodeWithText("STALE RESULT").assertDoesNotExist()
            compose.runOnIdle {callbacks[1](Result.failure(IllegalStateException("Current request failed")))}
            compose.onNodeWithText("Current request failed").performScrollTo().assertIsDisplayed()
            assertEquals(0,saved)
        } finally {root.deleteRecursively();prefs.forEach {base.getSharedPreferences(it,0).edit().clear().commit()}}
    }
}
