package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.ui.AppTheme

class SettingsPolish1761Test {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun settings(hidden:Boolean=false){compose.setContent {PrivateGalleryTheme {SettingsFixture(AppTheme.DARK,allowSecretPin=true,onTimeout={},initialDiscovered=true,initialHidden=hidden,storagePanel={androidx.compose.material3.TextButton(onClick={}){androidx.compose.material3.Text("Storage")}})}}}
 @Test fun decoyAboutRestoresGenuineVersionOnlyAfterAuthenticatedReveal() {
  settings(true)
  compose.onNodeWithText("0.0.1 · Build 4").assertExists()
  compose.onNodeWithText("Gallery & Vault").performClick()
  compose.onNodeWithText("Storage").assertDoesNotExist()
  compose.onNodeWithContentDescription("Back to Settings").performClick()
  compose.onNodeWithText("Updates & About").performClick()
  compose.onNodeWithText("0.0.1 · Build 4").assertExists()
  compose.onNodeWithText("Private Gallery 0.0.1").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Basic gallery functionality. More features coming soon.").assertExists()
  compose.onNodeWithText("Private Gallery ${BuildConfig.VERSION_NAME}").assertDoesNotExist()
  val info=compose.activity.packageManager.getPackageInfo(compose.activity.packageName,0)
  assertEquals(BuildConfig.VERSION_NAME,info.versionName);assertEquals(BuildConfig.VERSION_CODE.toLong(),info.longVersionCode)
  compose.onNodeWithContentDescription("Back to Settings").performClick()
  compose.onNodeWithText("Privacy & Security").performClick()
  compose.onNodeWithText("Secret").performScrollTo().performClick();compose.onNodeWithText("Confirm").performClick()
  compose.onNodeWithTag("privacy-hide-content").performScrollTo().performClick()
  compose.onNodeWithText("Confirm owner identity").assertIsDisplayed();compose.onNodeWithText("Confirm").performClick()
  compose.onNodeWithContentDescription("Back to Security").performClick();compose.onNodeWithContentDescription("Back to Settings").performClick()
  compose.onNodeWithText("${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}").assertExists()
  compose.onNodeWithText("Updates & About").performClick();compose.onNodeWithText("Private Gallery ${BuildConfig.VERSION_NAME}").performScrollTo().assertIsDisplayed()
 }
 @Test fun panicConfigurationIsAbsentFromSecretWhileHideContentAccessRemains() {
  settings()
  compose.onNodeWithText("Privacy & Security").performClick()
  compose.onAllNodesWithText("Enable Panic Exit").assertCountEquals(1)
  compose.onNodeWithTag("privacy-allow-screenshots").performScrollTo().assertExists()
  compose.onNodeWithText("Secret").performScrollTo().performClick();compose.onNodeWithText("Confirm").performClick()
  compose.onNodeWithTag("privacy-hide-content").assertExists()
  compose.onNodeWithText("Enable Panic Exit").assertDoesNotExist()
  compose.onNodeWithTag("privacy-allow-screenshots").assertDoesNotExist()
  compose.onNodeWithText("Hide Secret settings again").performScrollTo().assertExists()
 }
 @Test fun primaryScreenshotEnableStillRequiresFreshAuthenticationInSecurity() {
  settings()
  compose.onNodeWithText("Privacy & Security").performClick()
  compose.onNodeWithTag("privacy-allow-screenshots").performScrollTo().performClick()
  compose.onNodeWithText("Confirm owner identity").assertIsDisplayed();compose.onNodeWithText("Cancel").performClick()
  compose.onNodeWithTag("privacy-allow-screenshots").assertIsOff()
 }
}
