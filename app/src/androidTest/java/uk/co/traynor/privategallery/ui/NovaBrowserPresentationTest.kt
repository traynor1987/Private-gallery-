package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.ui.AppTheme

class NovaBrowserPresentationTest {
 @get:Rule val compose=createComposeRule()
 @Test fun connectionShellKeepsRetrySettingsAndPrivateDiagnosticsReachable() {
  var retries=0;var settings=0
  compose.setContent {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.8f)) {
   PrivateGalleryTheme(AppTheme.DARK){Surface{Box(Modifier.width(328.dp).height(480.dp)) {
    NovaBrowserConnectionSurface(true,"Connection failed","synthetic diagnostic",{retries++},{settings++})
   }}}
  }}
  compose.onNodeWithText("synthetic diagnostic",substring=true).assertDoesNotExist()
  compose.onNodeWithText("Retry connection").performScrollTo().performClick()
  compose.onNodeWithText("Connection settings").performScrollTo().performClick()
  compose.onNodeWithText("Connection details").performScrollTo().performClick()
  compose.onNodeWithText("synthetic diagnostic",substring=true).performScrollTo().assertIsDisplayed()
  compose.runOnIdle{assertEquals(1,retries);assertEquals(1,settings)}
 }
}
