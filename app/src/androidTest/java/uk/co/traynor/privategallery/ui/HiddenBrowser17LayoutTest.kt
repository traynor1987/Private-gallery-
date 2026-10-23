package uk.co.traynor.privategallery.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.ui.AppTheme

class HiddenBrowser17LayoutTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun layout(width:Int,height:Int,theme:AppTheme) {
        val session=HiddenSession{android.os.SystemClock.elapsedRealtime()}
        session.complete(session.beginAuthentication(),ByteArray(32){6})
        val prefs=compose.activity.getSharedPreferences("hidden-browser-v16",0);val previous=prefs.getString("mode",null)
        try {
            prefs.edit().putString("mode","VPN_TOR").commit()
            compose.runOnIdle {compose.activity.setContent {PrivateGalleryTheme(theme) {
                Box(Modifier.requiredSize(width.dp,height.dp)) {HiddenBrowserHome(session,{},{},{},{})}
            }}}
            compose.onNodeWithText("Hidden Browser").assertIsDisplayed()
            listOf("Hidden browser settings","Back","Forward","Refresh","New tab","Save media to Hidden").forEach {label->
                val node=compose.onNodeWithContentDescription(label);node.assertIsDisplayed()
                val bounds=node.fetchSemanticsNode().boundsInRoot
                assertTrue("$label clipped",bounds.width>0&&bounds.height>0)
            }
            capture("browser-$width-$height-$theme")
            compose.onNodeWithText("Tabs 0").performClick();compose.onNodeWithText("Hidden tabs").assertIsDisplayed();compose.onNodeWithText("Done").performClick()
            compose.onNodeWithContentDescription("Hidden browser settings").performClick()
            compose.onNodeWithText("VPN + Tor · unavailable").assertIsNotEnabled()
            compose.onNodeWithText("Privacy information").performScrollTo().performClick()
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Search or enter address").performClick()
            compose.onNodeWithContentDescription("Hidden browser settings").assertIsDisplayed()
        } finally {
            compose.runOnIdle {compose.activity.hiddenBrowserOwner?.close();session.lock()}
            prefs.edit().apply{if(previous==null)remove("mode")else putString("mode",previous)}.commit()
        }
    }
    private fun capture(name:String) {
        compose.runOnIdle {compose.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val output=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: java.io.File(instrumentation.targetContext.externalMediaDirs.first(),"additional_test_output").absolutePath
        val folder=java.io.File(output,"release17").apply{mkdirs()}
        androidx.test.core.app.takeScreenshot().let {bitmap->
            java.io.File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun foldCoverLight()=layout(392,840,AppTheme.LIGHT)
    @Test fun foldInnerDark()=layout(840,900,AppTheme.DARK)
    @Test fun foldCoverLandscapeDark()=layout(840,392,AppTheme.DARK)
    @Test fun foldInnerLandscapeLight()=layout(900,840,AppTheme.LIGHT)
}
