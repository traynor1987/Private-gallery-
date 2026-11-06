package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import java.util.UUID

class PrivateAppImmersiveControlsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun app()=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Music fixture","https://www.youtube.com/","https://www.youtube.com/","standalone","",false,true,false)

    @Test fun overflowFollowsBackAtBottomAndHomeRetainsWhileExitCloses() {
        var homes=0;var closes=0;var browsers=0;var backs=0;var appStarts=0
        compose.setContent {PrivateGalleryTheme {
            Box(Modifier.fillMaxSize().testTag("immersive-root")) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    PrivateAppControls(app(),"https://m.youtube.com/watch?v=fixture","Primary Space",true,true,true,false,
                        {backs++},{},{},{appStarts++},{},{},{},onCloseApp={closes++},onExitGallery={homes++},
                        immersive=true,onOpenInBrowser={browsers++})
                }
            }
        }}
        compose.onNodeWithText("Music fixture").assertDoesNotExist()
        compose.onNodeWithText("Browsing outside this app's saved scope").assertDoesNotExist()
        val root=compose.onNodeWithTag("immersive-root").fetchSemanticsNode().boundsInRoot
        val button=compose.onNodeWithContentDescription("App menu").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val back=compose.onNodeWithTag("nova-back").fetchSemanticsNode().boundsInRoot
        val navigation=compose.onNodeWithTag("nova-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(button.left>=back.right);assertEquals(root.bottom,navigation.bottom,1f)
        assertTrue(button.top>=navigation.top)
        for(action in listOf("Back","Nova Home","Open in Browser","Exit App")) {
            compose.onNodeWithContentDescription("App menu").performClick()
            compose.onNodeWithText(action).performScrollTo().performClick()
        }
        compose.runOnIdle {assertEquals(1,backs);assertEquals(1,homes);assertEquals(1,closes);assertEquals(1,browsers);assertEquals(0,appStarts)}
    }

    @Test fun unrelatedOriginWarningRemainsVisibleWhileHistoryControlsStayDisabledAtRoot() {
        compose.setContent {PrivateGalleryTheme {
            PrivateAppControls(app(),"https://unrelated.invalid/","Primary Space",true,false,false,false,
                {},{},{},{},{},{},{},immersive=true,onOpenInBrowser={})
        }}
        compose.onNodeWithText("Browsing outside this app's saved scope").assertIsDisplayed()
        compose.onNodeWithContentDescription("App menu").performClick()
        compose.onNodeWithText("Back").assertIsNotEnabled()
        compose.onNodeWithText("Forward").assertIsNotEnabled()
        listOf("Refresh","Nova Home","Open in Browser","App Settings","Exit App").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }
}
