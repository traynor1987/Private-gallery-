package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.ui.AppTheme

class BrowserToolbarLayout1762Test {
    private val compose=createAndroidComposeRule<ComponentActivity>()
    private val viewport=BrowserViewportRule {name ->if(name.contains("Landscape"))640 to 320 else if(name.contains("Inner"))840 to 1000 else 320 to 640}
    @get:Rule val rules:TestRule=RuleChain.outerRule(viewport).around(compose)
    private fun render(theme:AppTheme) {
        val actions=listOf("Back","Next","Reload","Tabs 8","New tab","Settings")
        var clicks=0;var minimum=0f
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                minimum=with(LocalDensity.current){48.dp.toPx()}
                PrivateGalleryTheme(theme) {GalleryBrowserToolbar {
                    actions.forEach {label ->TextButton(onClick={clicks++}){Text(label)}}
                }}
            }
        }
        actions.forEach {label ->
            val node=compose.onNodeWithText(label).assertIsDisplayed()
            assertTrue("Accessible target for $label",node.fetchSemanticsNode().boundsInRoot.height>=minimum)
            node.performClick()
        }
        compose.runOnIdle {assertEquals(actions.size,clicks)}
    }
    @Test fun coverLargeTextDark()=render(AppTheme.DARK)
    @Test fun coverLargeTextLight()=render(AppTheme.LIGHT)
    @Test fun shortLandscapeLargeTextDark()=render(AppTheme.DARK)
    @Test fun unfoldedInnerLargeTextLight()=render(AppTheme.LIGHT)
}
