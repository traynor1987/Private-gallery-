package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
    @Test fun sharedChromeSubmitsAndNavigatesThroughProductionCallbacks() {
        var submitted="";var back=0;var bookmarks=0;var stopped=0
        content(AppTheme.DARK) {
            var address by remember {mutableStateOf("")}
            androidx.compose.foundation.layout.Column {
                BrowserAddressBar(address,{address=it},true,true,{submitted=address},{stopped++},{})
                BrowserNavigationToolbar(true,false,2,{back++},{},{bookmarks++},{},{},{})
            }
        }
        compose.onNodeWithText("Search or enter address").performTextInput("fixture query")
        compose.onNode(hasImeAction(androidx.compose.ui.text.input.ImeAction.Go)).performImeAction()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Bookmarks").performClick()
        compose.onNodeWithContentDescription("Stop").performClick()
        compose.runOnIdle {assertEquals("fixture query",submitted);assertEquals(1,back);assertEquals(1,bookmarks);assertEquals(1,stopped)}
    }
    @Test fun sharedPageFailureOffersRealRetryAndSettingsCallbacks() {
        var retries=0;var settings=0
        content(AppTheme.LIGHT) {BrowserPageError("Fixture failure",{retries++},{settings++})}
        compose.onNodeWithText("Retry page").performClick()
        compose.onNodeWithText("Browser settings").performClick()
        compose.runOnIdle {assertEquals(1,retries);assertEquals(1,settings)}
    }
    @Test fun sharedLibrarySearchOpensMatchingTitleAndPreservesExactUrl() {
        var opened=""
        content(AppTheme.DARK) {BrowserBookmarksPanel(listOf(
            uk.co.traynor.privategallery.core.browser.BrowserBookmark("a","Example title","https://fixture.invalid/a",1),
            uk.co.traynor.privategallery.core.browser.BrowserBookmark("b","Other title","https://fixture.invalid/b",2)),{opened=it},{},{})}
        compose.onNodeWithText("Search bookmarks").performTextInput("Example")
        compose.onNodeWithText("Other title").assertDoesNotExist()
        compose.onNodeWithText("https://fixture.invalid/a").assertIsDisplayed()
        compose.onNodeWithText("Example title").performClick()
        compose.runOnIdle {assertEquals("https://fixture.invalid/a",opened)}
    }
    @Test fun downloadsLightCoverLongStatusAndExplicitActions()=downloads(AppTheme.LIGHT)
    @Test fun downloadsDarkLandscapeLongStatusAndExplicitActions()=downloads(AppTheme.DARK)
    private fun downloads(theme:AppTheme) {
        var manages=0;var closes=0
        val status="Transfer status ".repeat(300)
        content(theme) {HiddenDownloadsDialog(status,{manages++},{closes++})}
        compose.onNodeWithText("Supported GET image/video downloads are saved encrypted in Hidden Vault. Return to Hidden Vault to manage saved media. Returning closes all browser tabs and cancels transfers still in progress.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Keep browsing").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(0,manages);assertEquals(1,closes)}
        compose.onNodeWithText("Manage in Hidden Vault").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(1,manages);assertEquals(1,closes)}
    }
    @Test fun findLightCoverKeyboardAndActions()=find(AppTheme.LIGHT)
    @Test fun findDarkLandscapeKeyboardAndActions()=find(AppTheme.DARK)
    private fun find(theme:AppTheme) {
        var changed="";val directions=mutableListOf<Boolean>();var closed=0
        content(theme) {var query by remember {mutableStateOf("")}
            HiddenFindBar(query,"1 of 20 matches ".repeat(50),{query=it;changed=it},{directions+=it},{closed++})}
        compose.onNodeWithText("Find in page").performTextInput("fixture")
        compose.onNode(hasImeAction(androidx.compose.ui.text.input.ImeAction.Done)).performImeAction()
        compose.onNodeWithText("Previous").assertIsDisplayed().performClick()
        compose.onNodeWithText("Next").assertIsDisplayed().performClick()
        compose.onNodeWithText("Close find").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals("fixture",changed);assertEquals(listOf(false,true),directions);assertEquals(1,closed)}
    }
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
        content(theme) {BrowserPanel("Browser settings","Hidden Tor Only",{}) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f).then(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))) {
                HiddenNativeSettingsContent(true,"Tor Only",false,false,{},{},{},{history=it},{},{toggled=true},{},{})
            }
        }}
        compose.onNodeWithContentDescription("Save Hidden Tor Only history").performScrollTo().performClick()
        compose.onNodeWithText("Desktop layout off").performScrollTo().performClick()
        compose.runOnIdle {assertTrue(toggled);assertTrue(history)}
        compose.onNodeWithContentDescription("Back to Browser").assertIsDisplayed()
    }
    @Test fun libraryDarkCoverLongUrlScrollAndSelection() {
        val urls=(0 until 100).map {"https://fixture.invalid/$it/"+"a/".repeat(1000)};var selected=""
        content(AppTheme.DARK) {BrowserBookmarksPanel(urls.map {uk.co.traynor.privategallery.core.browser.BrowserBookmark(it,"Saved page",it,1)},{selected=it},{},{})}
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(99)
        compose.onNodeWithText(urls.last()).performClick();compose.runOnIdle {assertEquals(urls.last(),selected)}
    }
    @Test fun tabsLightLandscapeSelectionAndClose() {
        var selected="";var closed=""
        val tabs=(0..7).map {uk.co.traynor.privategallery.core.browser.v2.BrowserTab(id="$it",title="Tab $it "+"long ".repeat(50))}
        content(AppTheme.LIGHT) {BrowserTabsPanel(tabs,"7",{selected=it},{closed=it},{},{})}
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(7)
        compose.onNodeWithText(tabs.last().title).assertIsSelected().performClick()
        compose.onAllNodesWithContentDescription("Close tab").onLast().performClick();compose.runOnIdle {assertEquals("7",selected);assertEquals("7",closed)}
    }
    @Test fun historyDarkCoverSearchAndClearRequireConfirmation() {
        var cleared=0
        content(AppTheme.DARK) {BrowserHistoryPanel(listOf(uk.co.traynor.privategallery.core.browser.v2.BrowserHistoryEntry("a","Visited title","https://fixture.invalid/a",0)),true,{},{cleared++},{})}
        compose.onNodeWithText("Date unavailable").assertIsDisplayed()
        compose.onNodeWithText("Search history").performTextInput("Visited")
        compose.onNodeWithText("Visited title").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear history").performClick()
        compose.runOnIdle {assertEquals(0,cleared)}
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {assertEquals(0,cleared)}
        compose.onNodeWithContentDescription("Clear history").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.runOnIdle {assertEquals(1,cleared)}
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
        content(AppTheme.LIGHT) {BrowserBookmarksPanel(emptyList(),{},{},{})}
        compose.onNodeWithText("No bookmarks yet.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to Browser").assertIsDisplayed()
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
