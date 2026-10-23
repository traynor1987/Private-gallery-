package uk.co.traynor.privategallery.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.ui.AppTheme

class HiddenBrowser17LayoutTest {
    val compose=createAndroidComposeRule<MainActivity>()
    private val viewportRule = BrowserViewportRule { method -> when (method) {
        "foldCoverLight" -> 392 to 840
        "foldInnerDark" -> 840 to 900
        "foldCoverLandscapeDark" -> 840 to 392
        "foldInnerLandscapeLight" -> 900 to 840
        else -> null
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(viewportRule).around(compose)
    private fun layout(width:Int,height:Int,theme:AppTheme,fontScale:Float=1f) {
        val session=HiddenSession{android.os.SystemClock.elapsedRealtime()}
        session.complete(session.beginAuthentication(),ByteArray(32){6})
        val prefs=compose.activity.getSharedPreferences("hidden-browser-v16",0);val previous=prefs.getString("mode",null)
        try {
            prefs.edit().putString("mode","VPN_TOR").commit()
            compose.runOnIdle {compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
                    PrivateGalleryTheme(theme) {
                        Surface(Modifier.fillMaxSize().testTag("browser-layout")) {HiddenBrowserHome(session,{},{},{},{})}
                    }
                }
            }}
            compose.runOnIdle {
                val bounds = compose.activity.windowManager.currentWindowMetrics.bounds
                assertEquals("Actual dialog/window viewport width",width,bounds.width())
                assertEquals("Actual dialog/window viewport height",height,bounds.height())
                assertEquals("Viewport fixtures use 1px per dp",1f,compose.density.density,.01f)
            }
            compose.onNodeWithText("Hidden Browser").assertIsDisplayed()
            listOf("Hidden browser settings","Back","Forward","Refresh","New tab","Save media to Hidden").forEach {label->
                val node=compose.onNodeWithContentDescription(label);node.assertIsDisplayed()
                val bounds=node.fetchSemanticsNode().boundsInRoot
                val minimum = with(compose.density) { 48.dp.toPx() }
                assertTrue("$label must retain a 48dp target", bounds.width >= minimum - 1 && bounds.height >= minimum - 1)
            }
            capture("browser-$width-$height-$theme-$fontScale",width,height)
            compose.onNodeWithText("Tabs 0").performClick();compose.onNodeWithText("Hidden tabs").assertIsDisplayed();compose.onNodeWithText("Done").performClick()
            compose.onNodeWithContentDescription("Hidden browser settings").performClick()
            compose.onNodeWithText("VPN + Tor · unavailable").assertIsNotEnabled()
            compose.onNodeWithText("Privacy information").performScrollTo().performClick()
            compose.onNodeWithText("Connection diagnostics").performScrollTo().performClick()
            compose.onNodeWithText("Manage Hidden downloads").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Done").assertIsDisplayed()
            capture("hidden-settings-$width-$height-$theme-$fontScale",width,height,"hidden-browser-settings")
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Search or enter address").performClick()
            compose.onNodeWithContentDescription("Hidden browser settings").assertIsDisplayed()
        } finally {
            compose.runOnIdle {compose.activity.hiddenBrowserOwner?.close();session.lock()}
            prefs.edit().apply{if(previous==null)remove("mode")else putString("mode",previous)}.commit()
        }
    }
    private fun capture(name:String,width:Int,height:Int,tag:String="browser-layout") {
        compose.runOnIdle {compose.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val output=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: java.io.File(instrumentation.targetContext.externalMediaDirs.first(),"additional_test_output").absolutePath
        val folder=java.io.File(output,"release171").apply{mkdirs()}
        compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().let {bitmap->
            assertTrue("$name must fit the actual viewport width",bitmap.width in 1..width)
            assertTrue("$name must fit the actual viewport height",bitmap.height in 1..height)
            java.io.File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun unavailableCombinedModeCannotBeSelectedInPreferences() {
        compose.runOnIdle { compose.activity.setContent { PrivateGalleryTheme { HiddenBrowserPreferences() } } }
        compose.onNodeWithText("VPN + Tor · unavailable").assertIsNotEnabled()
    }
    @Test fun foldCoverLight()=layout(392,840,AppTheme.LIGHT)
    @Test fun foldInnerDark()=layout(840,900,AppTheme.DARK)
    @Test fun foldCoverLandscapeDark()=layout(840,392,AppTheme.DARK,1.3f)
    @Test fun foldInnerLandscapeLight()=layout(900,840,AppTheme.LIGHT)
}
