package uk.co.traynor.privategallery.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.privateapps.*
import uk.co.traynor.privategallery.core.ui.AppTheme

/** Synthetic UI proof only; transport and real Hidden process checks have their own fixture. */
class PrivateAppControls178Test {
    @get:Rule val compose=createComposeRule()
    private fun fixture()=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Fixture App","https://example.test/app/","https://example.test/app/","standalone","",false,true,false)

    @Test fun controls_keep_origin_visible_and_delegate_to_existing_owner() {
        val app=fixture();var homes=0;var switches=0;var locks=0
        compose.setContent { PrivateGalleryTheme(AppTheme.LIGHT) {
            PrivateAppControls(app,"https://secret:password@example.test/elsewhere","Hidden Vault · Tor",true,false,true,false,
                {},{},{},{homes++},{},{switches++},{},onQuickLock={locks++})
        } }
        compose.onNodeWithContentDescription("App home").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expand app controls").performClick()
        compose.onNodeWithText("Fixture App").assertIsDisplayed()
        compose.onNodeWithText("https://example.test").assertIsDisplayed()
        compose.onNodeWithText("Browsing outside this app's saved scope").assertIsDisplayed()
        compose.onNodeWithContentDescription("App back").assertIsNotEnabled()
        compose.onNodeWithContentDescription("App forward").assertIsEnabled()
        compose.onNodeWithContentDescription("App home").performClick()
        compose.onNodeWithContentDescription("Switch private app").performClick()
        compose.onNodeWithContentDescription("Quick lock").performClick()
        compose.runOnIdle { assertEquals(1,homes);assertEquals(1,switches);assertEquals(1,locks) }
    }

    @Test fun settings_and_switcher_render_save_and_close_on_revocation() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val root=File(context.cacheDir,"privateapps-controls-ui").apply {deleteRecursively();mkdirs()}
        val key=ByteArray(32){9};var live=true;var closed=0;var updates=0
        val app=fixture()
        val store=PrimaryPrivateAppStore(root,key,{live},{check(live);it()})
        val controller=PrivateAppsController(store.owner(),{live},{a,b,id->PrivateAppCommands.execute(store,a,b,id)},{})
        controller.save(app)
        var panel by mutableStateOf("settings")
        try {
            compose.setContent {PrivateGalleryTheme(AppTheme.LIGHT) {
                if(panel=="settings")PrivateAppSettings(controller,app.id(),{updates++},{},{closed++;panel="switcher"})
                else if(panel=="switcher")PrivateAppSwitcher(listOf("tab" to app),"tab",{}, {},{panel=""})
            }}
            compose.waitUntil(5000){compose.onAllNodesWithTag("private-app-address-toggle").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("private-app-address-toggle").performClick()
            compose.waitUntil(5000){updates==1}
            assertFalse(store.read().apps.single().addressBar())
            compose.runOnIdle {live=false}
            compose.waitUntil(5000){closed==1}
            compose.onNodeWithText("Switch private app").assertIsDisplayed()
            compose.onNodeWithText("Fixture App · Current").assertIsDisplayed()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Switch private app").assertDoesNotExist()
        } finally {key.fill(0);root.deleteRecursively()}
    }
    @Test fun controls_adapt_between_cover_inner_and_large_text_in_both_themes() {
        var width by mutableStateOf(360)
        var dark by mutableStateOf(false)
        var large by mutableStateOf(false)
        val app=fixture()
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)1.6f else 1f)) {
                PrivateGalleryTheme(if(dark)AppTheme.DARK else AppTheme.LIGHT) {
                    Box(Modifier.width(width.dp)) {
                        PrivateAppControls(app,"https://example.test/app/","Hidden Vault · VPN",true,true,true,false,{},{},{},{},{},{},{},{})
                    }
                }
            }
        }
        for(theme in listOf(false,true))for(size in listOf(360,840,600,360))for(font in listOf(false,true)) {
            compose.runOnIdle{dark=theme;width=size;large=font}
            if(compose.onAllNodesWithContentDescription("Expand app controls").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Expand app controls").performClick()
            listOf("App back","App forward","Refresh app","App home","Private Apps library","Switch private app","Private app settings","Screenshot to Vault").forEach{
                compose.onNodeWithContentDescription(it).assertIsDisplayed()
            }
        }
    }

}
