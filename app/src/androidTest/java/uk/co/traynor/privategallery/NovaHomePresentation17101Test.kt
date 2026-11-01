package uk.co.traynor.privategallery
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import uk.co.traynor.privategallery.core.ui.AppTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.home.*
import uk.co.traynor.privategallery.ui.*
class NovaHomePresentation17101Test {
 @get:Rule val compose=createComposeRule()
 @Test fun tenApplicationsHaveAccessibleLaunchAndContextActionsOnCoverAndLargeText() {
  val launched=mutableSetOf<HomeLayout.Shortcut>()
  var preferences by mutableStateOf(HomePreferences())
  var scale by mutableFloatStateOf(1f)
  compose.setContent {
   CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,scale)) {
    PrivateGalleryTheme {Box(Modifier.width(360.dp).height(480.dp)) {
     PrivateHome(false,preferences,{preferences=it},search={emptyList()},onLaunch={launched.add(it)},onLock={},onPanic={},onExit={})
    }}
   }
  }
  compose.onNodeWithText("NovaOS").assertExists()
  for(app in HomeLayout.Shortcut.values()) {
   compose.onNodeWithTag("nova-app-grid").performScrollToNode(hasTestTag("nova-app-${app.name}"))
   compose.onNodeWithTag("nova-app-${app.name}").performClick()
  }
  assertEquals(HomeLayout.Shortcut.values().toSet(),launched)
  compose.runOnIdle {scale=1.8f}
  compose.onNodeWithTag("nova-app-grid").performScrollToNode(hasTestTag("nova-app-SETTINGS"))
  compose.onNodeWithContentDescription("Actions for Settings").performClick()
  compose.onNodeWithText("Pin favourite").performClick()
  compose.runOnIdle {assertTrue(HomeLayout.Shortcut.SETTINGS in preferences.layout.favourites())}
 }
 @Test fun coverInnerLandscapeSplitAndLargeTextRemainUsableInBothThemes() {
  var width by mutableIntStateOf(360);var height by mutableIntStateOf(720)
  var font by mutableFloatStateOf(1f);var dark by mutableStateOf(false)
  compose.setContent {
   val density=LocalDensity.current.density
   CompositionLocalProvider(LocalDensity provides Density(density,font)) {
    PrivateGalleryTheme(if(dark)AppTheme.DARK else AppTheme.LIGHT) {
     Box(Modifier.width(width.dp).height(height.dp).testTag("nova-preview")) {
      PrivateHome(false,HomePreferences(),{},search={emptyList()},onLaunch={},onLock={},onPanic={},onExit={})
     }
    }
   }
  }
  for(theme in listOf(false,true))for(size in listOf(360 to 720,840 to 720,840 to 360,280 to 480))for(scale in listOf(1f,1.8f)) {
   compose.runOnIdle{dark=theme;width=size.first;height=size.second;font=scale}
   val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
   compose.onNodeWithText("NovaOS").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){it(layouts)}
   assertEquals("Home text must inherit the current theme foreground",if(theme)androidx.compose.ui.graphics.Color(0xFFE4E6EA)else androidx.compose.ui.graphics.Color(0xFF1B1E23),layouts.single().layoutInput.style.color)
   compose.onNodeWithContentDescription("Lock Space").assertIsDisplayed()
   compose.onNodeWithContentDescription("Panic Exit").assertIsDisplayed()
   compose.onNodeWithTag("nova-app-grid").performScrollToNode(hasTestTag("nova-app-SETTINGS"))
   compose.onNodeWithTag("nova-app-SETTINGS").assertIsDisplayed()
   if(size.first!=280 && scale==1f) {
    compose.onNodeWithTag("nova-app-grid").performScrollToIndex(0)
    val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
    val output=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
     ?:java.io.File(instrumentation.targetContext.externalMediaDirs.first(),"additional_test_output").absolutePath
    val directory=java.io.File(output,"novaos").apply{mkdirs()}
    val bitmap=compose.onNodeWithTag("nova-preview").captureToImage().asAndroidBitmap()
    java.io.File(directory,"home-${if(theme)"dark"else"light"}-${size.first}x${size.second}.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   }
  }
 }
 @Test fun localBackAndNovaHomeStayReachableTogetherAtLargeText() {
  var homes=0;var backs=0
  compose.setContent {
   CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.8f)) {
    PrivateGalleryTheme(AppTheme.DARK) {Box(Modifier.width(328.dp)) {
     NovaAppHeader("Evidence Locker","Hidden Space",onHome={homes++},onBack={backs++},onLock={},onPanic={})
    }}
   }
  }
  compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
  compose.onNodeWithContentDescription("Nova Home").assertIsDisplayed().performClick()
  compose.onNodeWithContentDescription("Lock Space").assertIsDisplayed()
  compose.onNodeWithContentDescription("Panic Exit").assertIsDisplayed()
  compose.runOnIdle{assertEquals(1,homes);assertEquals(1,backs)}
 }
}
