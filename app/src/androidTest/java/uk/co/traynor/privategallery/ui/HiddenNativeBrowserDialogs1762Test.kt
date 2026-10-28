package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.ui.AppTheme

/** Direct production native child content, selected theme and 2x text. No network bypass. */
class HiddenNativeBrowserDialogs1762Test {
    private val compose=createAndroidComposeRule<ComponentActivity>()
    private val viewport=BrowserViewportRule(fontScale=2f) {name ->if(name.contains("Landscape"))840 to 392 else 320 to 640}
    @get:Rule val rules:TestRule=RuleChain.outerRule(viewport).around(compose)
    private fun content(theme:AppTheme,body:@Composable ()->Unit) {compose.setContent {
        val density=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){PrivateGalleryTheme(theme){body()}}
    }}
    @Test fun authLightCoverKeyboardAndActions()=auth(AppTheme.LIGHT)
    @Test fun authDarkLandscapeKeyboardAndActions()=auth(AppTheme.DARK)
    private fun auth(theme:AppTheme) {
        var submitted="";var cancelled=false
        content(theme) {
            var user by remember {mutableStateOf("")};var password by remember {mutableStateOf("")}
            GalleryAlertDialog(onDismissRequest={cancelled=true},title={Text("Sign in")},
                text={HiddenNativeAuthContent("Realm ".repeat(100),user,password,{user=it},{password=it})},
                confirmButton={TextButton(onClick={submitted="$user:$password"}){Text("Sign in")}},dismissButton={TextButton(onClick={cancelled=true}){Text("Cancel")}})
        }
        compose.onNodeWithText("Username").assert(hasImeAction(androidx.compose.ui.text.input.ImeAction.Next)).performScrollTo().performTextInput("test-user")
        compose.onNodeWithText("Password").assert(hasImeAction(androidx.compose.ui.text.input.ImeAction.Done)).performScrollTo().performTextInput("test-secret")
        compose.onAllNodesWithText("Sign in").filter(hasClickAction()).onFirst().performClick()
        compose.runOnIdle {assertEquals("test-user:test-secret",submitted)}
        compose.onNodeWithText("Cancel").performClick();compose.runOnIdle {assertTrue(cancelled)}
    }
    @Test fun promptLightLandscapeKeyboardAndLongMessage()=prompt(AppTheme.LIGHT)
    @Test fun promptDarkCoverKeyboardAndLongMessage()=prompt(AppTheme.DARK)
    private fun prompt(theme:AppTheme) {
        var confirmed=""
        content(theme) {
            var response by remember {mutableStateOf("")}
            GalleryAlertDialog(onDismissRequest={},title={Text("Website message")},text={HiddenNativeWebsiteContent("https://fixture.invalid/"+"path/".repeat(300),"Long message ".repeat(80),true,response,{response=it})},confirmButton={TextButton(onClick={confirmed=response}){Text("OK")}})
        }
        compose.onNodeWithText("Response").performScrollTo().performTextInput("confirmed response")
        compose.onNodeWithText("OK").performClick();compose.runOnIdle {assertEquals("confirmed response",confirmed)}
    }
    @Test fun settingsLightCoverScrollAndDesktopAction()=settings(AppTheme.LIGHT)
    @Test fun settingsDarkLandscapeScrollAndDesktopAction()=settings(AppTheme.DARK)
    private fun settings(theme:AppTheme) {
        var toggled=false;var history=false
        content(theme) {GalleryAlertDialog(onDismissRequest={},title={Text("Hidden settings")},text={
            HiddenNativeSettingsContent(true,"Tor Only",false,false,{},{},{},{history=it},{},{toggled=true},{},{})
        },confirmButton={TextButton(onClick={}){Text("Done")}})}
        compose.onNodeWithContentDescription("Save Hidden Tor Only history").performScrollTo().performClick()
        compose.onNodeWithText("Desktop layout off").performScrollTo().performClick()
        compose.runOnIdle {assertTrue(toggled);assertTrue(history)}
        compose.onNodeWithText("Done").assertIsDisplayed()
    }
    @Test fun libraryDarkCoverLongUrlScrollAndSelection() {
        val urls=(0 until 100).map {"https://fixture.invalid/$it/"+"a/".repeat(1000)};var selected=""
        content(AppTheme.DARK) {GalleryAlertDialog(scrollBody=false,onDismissRequest={},title={Text("Bookmarks")},text={HiddenNativeLibraryContent(urls,true,{selected=it},{})},confirmButton={TextButton(onClick={}){Text("Done")}})}
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(99)
        compose.onNodeWithText(urls.last()).performClick();compose.runOnIdle {assertEquals(urls.last(),selected)}
    }
    @Test fun tabsLightLandscapeSelectionAndClose() {
        var selected=-1;var closed=-1
        content(AppTheme.LIGHT) {GalleryAlertDialog(scrollBody=false,onDismissRequest={},title={Text("Tabs")},text={HiddenNativeTabsContent((0..7).map {"Tab $it "+"long ".repeat(50)},{selected=it},{closed=it},selectedIndex=7)},confirmButton={TextButton(onClick={}){Text("Done")}})}
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(7)
        compose.onNodeWithText("Tab 7 "+"long ".repeat(50)).assertIsSelected().performClick()
        compose.onAllNodesWithText("Close").onLast().performClick();compose.runOnIdle {assertEquals(7,selected);assertEquals(7,closed)}
    }
    @Test fun uploadLightCoverConsentAndExclusions()=upload(AppTheme.LIGHT)
    @Test fun uploadDarkLandscapeConsentAndExclusions()=upload(AppTheme.DARK)
    private fun upload(theme:AppTheme) {
        var sent=false
        content(theme) {var review by remember {mutableStateOf(false)}
            GalleryAlertDialog(onDismissRequest={},title={Text(if(review)"Send media outside Hidden Vault?" else "Choose from Hidden Vault")},text={
                if(review)HiddenNativeUploadConsentContent("fixture.invalid",4)
                else HiddenNativePickerIntro(true,501,7,false,600,600,null,false)
            },confirmButton={TextButton(onClick={if(review)sent=true else review=true}){Text(if(review)"Allow upload" else "Review upload")}},dismissButton={TextButton(onClick={}){Text("Cancel")}})
        }
        compose.onNodeWithText("501 Vault-only restricted item(s) cannot be uploaded. 7 other item(s) do not match this website's accepted file types.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review upload").performClick()
        compose.onNodeWithText("Allow upload").assertIsDisplayed().performClick();compose.runOnIdle {assertTrue(sent)}
    }
    @Test fun resubmitAndConfirmDarkLandscapeWarnings() {
        var accepted=false
        content(AppTheme.DARK) {var resubmit by remember {mutableStateOf(true)}
            GalleryAlertDialog(onDismissRequest={},title={Text(if(resubmit)"Resend form data?" else "Website message")},text={
                if(resubmit)HiddenNativeResubmitContent() else HiddenNativeWebsiteContent("https://fixture.invalid","Confirm ".repeat(300),false,"",{})
            },confirmButton={TextButton(onClick={if(resubmit)resubmit=false else accepted=true}){Text(if(resubmit)"Resend" else "OK")}},dismissButton={TextButton(onClick={}){Text("Cancel")}})
        }
        compose.onNodeWithText("Reloading this page can repeat a previous action, upload or purchase.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Resend").performClick();compose.onNodeWithText("OK").performClick();compose.runOnIdle {assertTrue(accepted)}
    }

    @Test fun emptyLibraryHasClearAccessibleState() {
        content(AppTheme.LIGHT) {GalleryAlertDialog(scrollBody=false,onDismissRequest={},title={Text("Bookmarks")},text={HiddenNativeLibraryContent(emptyList(),true,{},{})},confirmButton={TextButton(onClick={}){Text("Done")}})}
        compose.onNodeWithText("No saved pages in this mode.").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test fun uploadGridDarkCoverLoadsThenSelectsBeyondFiveHundredAtLargeText()=uploadGrid(AppTheme.DARK)
    @Test fun uploadGridLightLandscapeLoadsThenSelectsBeyondFiveHundredAtLargeText()=uploadGrid(AppTheme.LIGHT)
    private fun uploadGrid(theme:AppTheme) {
        val entries=(0..500).map {HiddenNativeUploadItem("photo-$it","Photo $it","image/png")}
        val loading=mutableStateOf(true);val selected=mutableStateOf<Set<String>>(emptySet())
        var reviews=0
        content(theme) {GalleryAlertDialog(scrollBody=false,onDismissRequest={},title={Text("Choose from Hidden Vault")},text={
            HiddenNativeUploadPickerGrid(entries,loading.value,true,3,7,501,null,selected.value,
                preview={_,callback ->callback(Result.failure(IllegalStateException("Synthetic unavailable preview")))},choose={selected.value=selected.value+it.id})
        },confirmButton={TextButton(enabled=selected.value.isNotEmpty(),onClick={reviews++}){Text("Review upload")}},dismissButton={TextButton(onClick={}){Text("Cancel")}})}
        compose.onNodeWithText("Review upload").assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("Photo 500").assertCountEquals(0)
        compose.runOnIdle {loading.value=false}
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(501)
        compose.onNodeWithContentDescription("Photo 500").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Photo 500").assertIsSelected()
        compose.runOnIdle {assertEquals(setOf("photo-500"),selected.value);assertEquals(0,reviews)}
        compose.onNodeWithText("Review upload").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(1,reviews)}
    }

}
