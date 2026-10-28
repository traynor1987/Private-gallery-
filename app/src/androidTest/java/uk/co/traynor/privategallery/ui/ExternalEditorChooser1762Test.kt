package uk.co.traynor.privategallery.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.media.ExternalEditorIntents
import uk.co.traynor.privategallery.core.ui.AppTheme

class ExternalEditorChooser1762Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun multipleEditorsRenderAndChooseWithoutNestedScrollCrash() {
        val samsung = ComponentName("com.sec.android.gallery3d", "SyntheticEdit")
        val other = ComponentName("example.editor", "SyntheticEdit")
        var chosen: ComponentName? = null
        compose.setContent { PrivateGalleryTheme(AppTheme.DARK) {
            ExternalEditingDialog(label="Hidden Vault",prepared=true,result=null,saved=false,status="",exportChosen=false,
                prepare={},launch={},export={},writeExport={},pick={},preview={},save={_,_,_->},cleanupPublic={},close={},
                editors=listOf(ExternalEditorIntents.EditorTarget(samsung,"Samsung Gallery"),ExternalEditorIntents.EditorTarget(other,"Other photo editor")),
                chooseEditor={chosen=it})
        } }
        compose.onNodeWithText("Choose photo editor").assertIsDisplayed()
        compose.onNodeWithText("Samsung Gallery").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(samsung, chosen) }
    }
}
