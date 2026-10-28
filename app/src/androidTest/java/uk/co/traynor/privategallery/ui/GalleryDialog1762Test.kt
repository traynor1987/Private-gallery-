package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.ui.AppTheme

/** Real dialog windows, explicit app theme and large text; no owner media or network. */
class GalleryDialog1762Test {
 private val compose=createAndroidComposeRule<ComponentActivity>()
 private val viewport=BrowserViewportRule {name->if(name.contains("Landscape"))840 to 392 else if(name.contains("Inner"))840 to 900 else 392 to 840}
 @get:Rule val rules:TestRule=RuleChain.outerRule(viewport).around(compose)
 private fun verify(theme:AppTheme) {
  var confirmed=false;var cancelled=false
  compose.setContent {
   val density=LocalDensity.current
   CompositionLocalProvider(LocalDensity provides Density(density.density,1.6f)) {
    PrivateGalleryTheme(theme) {
     GalleryAlertDialog(onDismissRequest={cancelled=true},modifier=Modifier.testTag("fixture-dialog"),
      title={Text("Private Gallery")},
      text={Column {repeat(40){Text("Option $it")}}},
      confirmButton={TextButton(onClick={confirmed=true}){Text("Continue")}},
      dismissButton={TextButton(onClick={cancelled=true}){Text("Cancel")}})
    }
   }
  }
  compose.onNodeWithText("Continue").assertIsDisplayed()
  compose.onNodeWithText("Option 39").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Continue").assertIsDisplayed()
  val image=compose.onNodeWithTag("fixture-dialog").captureToImage().asAndroidBitmap()
  try {
   assertTrue(image.width in 1..840);assertTrue(image.height in 1..900)
   val pixel=image.getPixel(image.width/2,8)
   val luminance=(android.graphics.Color.red(pixel)+android.graphics.Color.green(pixel)+android.graphics.Color.blue(pixel))/3
   if(theme==AppTheme.DARK)assertTrue("Dark app theme must not show a white surface",luminance<100)
   else assertTrue("Light app theme must use a light surface",luminance>180)
  } finally {image.recycle()}
  compose.onNodeWithText("Cancel").performClick()
  compose.runOnIdle {assertTrue(cancelled);assertFalse(confirmed)}
 }
 @Test fun foldCoverDark()=verify(AppTheme.DARK)
 @Test fun foldCoverLight()=verify(AppTheme.LIGHT)
 @Test fun foldInnerDark()=verify(AppTheme.DARK)
 @Test fun foldLandscapeLight()=verify(AppTheme.LIGHT)
}
