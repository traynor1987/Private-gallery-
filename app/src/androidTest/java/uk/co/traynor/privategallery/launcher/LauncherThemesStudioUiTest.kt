package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.novaos2.captureNovaSystem
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class LauncherThemesStudioUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule = RuleChain.outerRule(BrowserViewportRule {
        if (it.startsWith("inner")) 820 to 920 else 375 to 850
    }).around(compose)

    private val original = NovaLauncherState(
        configured = true,
        cover = LauncherLayout(pages = listOf(listOf(LauncherItem.App("example.calendar/.Main")))),
        inner = LauncherLayout(pages = listOf(listOf(LauncherItem.Workspace))),
    )
    private val state = mutableStateOf(original)

    @Test fun coverPreviewsOriginalAndAppliesRoundedWithoutChangingLayout() {
        show()
        compose.onNodeWithTag("theme-preview-cover").assertIsDisplayed()
        compose.onNodeWithText("Apply theme").assertIsNotEnabled()
        compose.onNodeWithText("One UI-inspired").performClick()
        compose.onNodeWithText("Apply theme").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(LauncherLook.ROUNDED, state.value.appearance.studio.look)
            assertEquals(original, state.value.copy(appearance = original.appearance))
        }
        captureNovaSystem("themes-cover")
    }

    @Test fun innerPreviewAndDefaultRestoreKeepBothDisplaysIntact() {
        show()
        compose.onNodeWithText("Inner preview").performClick()
        compose.onNodeWithTag("theme-preview-inner").assertIsDisplayed()
        compose.onNodeWithText("iOS-inspired").performClick()
        compose.onNodeWithText("Apply theme").performClick()
        compose.runOnIdle { assertEquals(LauncherLook.FLOATING, state.value.appearance.studio.look) }
        captureNovaSystem("themes-inner")
        compose.onNodeWithText("Restore default").performClick()
        compose.onNodeWithText("Apply theme").performClick()
        compose.runOnIdle {
            assertEquals(LauncherLook.NOVA, state.value.appearance.studio.look)
            assertEquals(original, state.value.copy(appearance = original.appearance))
        }
    }

    private fun show() {
        val packageName = compose.activity.packageName
        val apps = listOf(LauncherBuiltIn.NOTES, LauncherBuiltIn.TASKS, LauncherBuiltIn.CALENDAR,
            LauncherBuiltIn.FILES, LauncherBuiltIn.CALCULATOR).map { it.app(packageName) }
        compose.setContent {
            LauncherStudioTheme(state.value.appearance) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        LauncherThemesStudio(state.value.appearance, apps, {
                            state.value = state.value.copy(appearance = it)
                            true
                        }, onDeviceWallpaper = {})
                    }
                }
            }
        }
    }
}
