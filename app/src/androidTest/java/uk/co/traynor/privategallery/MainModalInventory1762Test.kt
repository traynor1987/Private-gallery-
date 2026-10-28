package uk.co.traynor.privategallery

import android.accessibilityservice.AccessibilityServiceInfo
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.util.concurrent.atomic.AtomicInteger
import uk.co.traynor.privategallery.core.crypto.InvalidPinException
import uk.co.traynor.privategallery.core.gallery.DeviceAlbum
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultItemState
import uk.co.traynor.privategallery.ui.BrowserViewportRule
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme

/** Every component below is called by the actual Main production path.
 * All callbacks are synthetic boundary stubs: no credential, backup or media store is changed. */
class MainModalInventory1762Test {
    private val compose=createAndroidComposeRule<ComponentActivity>()
    private val viewport=BrowserViewportRule(fontScale=2f) {name->if(name.contains("landscape",true))840 to 392 else 320 to 640}
    @get:Rule val rules:TestRule=RuleChain.outerRule(viewport).around(compose)
    private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
    private var oldKeyboard="null"
    private var oldFlags=0
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {it.readBytes().toString(Charsets.UTF_8).trim()}
    @Before fun keyboardPolicy() {
        oldKeyboard=shell("settings get secure show_ime_with_hard_keyboard");oldFlags=automation.serviceInfo.flags
        shell("settings put secure show_ime_with_hard_keyboard 1")
        automation.serviceInfo=automation.serviceInfo.apply {flags=flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
    }
    @After fun restoreKeyboardPolicy() {
        if(automation.windows.any {it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD})automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        automation.serviceInfo=automation.serviceInfo.apply {flags=oldFlags}
        if(oldKeyboard=="null")shell("settings delete secure show_ime_with_hard_keyboard")
        else {require(oldKeyboard.matches(Regex("[01]")));shell("settings put secure show_ime_with_hard_keyboard $oldKeyboard")}
    }
    private fun content(theme:AppTheme,body:@Composable ()->Unit) {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){PrivateGalleryTheme(theme){body()}}
        }
    }
    private fun fill(label:String,value:String) {
        compose.onNodeWithText(label).performScrollTo().performClick().performTextReplacement(value)
    }
    private fun assertKeyboard()=compose.waitUntil(10_000){automation.windows.any {it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD}}
    private fun bottom() {
        val scroll=compose.onNode(hasScrollAction())
        scroll.performSemanticsAction(SemanticsActions.ScrollBy){it(0f,100_000f)}
        // Fetching semantics synchronizes with Compose and must happen off the Main thread.
        val range=scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        compose.runOnIdle {
            assertEquals("The actual long body can reach its end",range.maxValue(),range.value(),1f)
        }
    }
    @Test fun coverLightAlbumMenuScrollsAndSelectsBeyondFirstViewport() {
        val albums=(0 until 100).map {DeviceAlbum("album-$it","Album $it")}
        var selected:String?="album-42"
        content(AppTheme.LIGHT) {
            var current by remember {mutableStateOf(selected)}
            DeviceAlbumSelector(albums,current){selected=it;current=it}
        }
        compose.onNodeWithText("Album: Album 42").performClick()
        compose.onNodeWithText("Album 99").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Album: Album 99").assertIsDisplayed()
        compose.runOnIdle {assertEquals("album-99",selected)}
    }
    @Test fun coverDarkBackupRestoreValidatesThenDisablesActionsUntilCompletion() {
        val calls=AtomicInteger();var completion:((Boolean)->Unit)?=null;var cancelled=false
        content(AppTheme.DARK) {
            RestoreEncryptedVaultDialog(Uri.parse("content://synthetic/backup"),{cancelled=true}){key,pin,done->
                try {assertEquals("synthetic recovery key",key.concatToString());assertEquals("123456",pin.concatToString());calls.incrementAndGet();completion=done}
                finally {key.fill('\u0000');pin.fill('\u0000')}
            }
        }
        compose.onNodeWithText("Restore Vault").performClick()
        compose.onNodeWithText("Enter your recovery key and confirm a PIN of at least six digits.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {assertEquals(0,calls.get())}
        fill("Recovery key","synthetic recovery key");fill("New PIN","123456");fill("Confirm PIN","123456")
        assertKeyboard()
        compose.onNodeWithText("Restore Vault").assertIsDisplayed().performClick()
        compose.onNodeWithText("Restoring…").assertIsNotEnabled();compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(1,calls.get());checkNotNull(completion)(false)}
        compose.onNodeWithText("Unable to authenticate this backup. Check the key and archive.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.runOnIdle {assertTrue(cancelled)}
    }
    @Test fun coverLightBackupExportRequiresEnteredRecoveryKeyAndExplicitExport() {
        val exports=AtomicInteger();var cancelled=false
        content(AppTheme.LIGHT) {VerifyRecoveryExportDialog(Uri.parse("content://synthetic/export"),{key->
            try {assertEquals("synthetic export recovery",key.concatToString());exports.incrementAndGet()} finally {key.fill('\u0000')}
        },{cancelled=true})}
        compose.onNodeWithText("Export").performClick()
        compose.onNodeWithText("Enter the recovery key.").performScrollTo().assertIsDisplayed()
        fill("Offline recovery key","synthetic export recovery");assertKeyboard()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.runOnIdle {assertEquals(0,exports.get())}
        compose.onNodeWithText("Export").performClick()
        compose.runOnIdle {assertEquals(1,exports.get());assertFalse(cancelled)}
    }
    @Test fun landscapeDarkLicencesLongContentScrollsWhileCloseStaysVisible() {
        var closed=false
        content(AppTheme.DARK) {ThirdPartyLicencesDialog((0 until 150).joinToString("\n"){"Synthetic licence paragraph $it"}){closed=true}}
        compose.onNodeWithText("Close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        bottom()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        compose.runOnIdle {assertTrue(closed)}
    }
    @Test fun coverDarkOwnerConfirmationMasksInputAndRejectsWrongPinBeforeCorrectPin() {
        val verifies=AtomicInteger();val done=mutableListOf<Boolean>()
        content(AppTheme.DARK) {SecretAuthenticationDialog("synthetic-owner",false,{fail("Biometrics were not enabled")},{pin->
            try {verifies.incrementAndGet();pin.concatToString()=="123456"} finally {pin.fill('\u0000')}
        },{done+=it})}
        fill("PIN","111111");assertKeyboard()
        compose.onNodeWithText("PIN").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("Confirm").assertIsDisplayed().performClick()
        compose.onNodeWithText("Authentication failed.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {assertTrue(done.isEmpty())}
        fill("PIN","123456")
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle {assertEquals(2,verifies.get());assertEquals(listOf(true),done)}
    }
    @Test fun coverLightChangePinValidatesMismatchAndPreservesCallbackBoundary() {
        val changes=AtomicInteger();var dismissed=false
        content(AppTheme.LIGHT) {ChangePinDialog({current,replacement->
            try {assertEquals("111111",current.concatToString());assertEquals("654321",replacement.concatToString());changes.incrementAndGet();Result.failure(InvalidPinException())}
            finally {current.fill('\u0000');replacement.fill('\u0000')}
        },{dismissed=true})}
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Use and confirm a PIN of at least six digits.").performScrollTo().assertIsDisplayed()
        fill("Current PIN","111111");fill("New PIN","654321");fill("Confirm new PIN","654321");assertKeyboard()
        compose.onNodeWithText("Save").assertIsDisplayed().performClick()
        compose.onNodeWithText("Current PIN is incorrect.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(1,changes.get());assertTrue(dismissed)}
    }
    private fun transfer(theme:AppTheme,move:Boolean) {
        val accepted=AtomicInteger();val dismissed=AtomicInteger()
        content(theme) {
            var open by remember {mutableStateOf(true)}
            Column {
                Button(onClick={open=true}){Text("Open confirmation")}
                if(open)ConfirmHiddenTransferDialog(1000,move,false,{dismissed.incrementAndGet();open=false},{accepted.incrementAndGet();open=false})
            }
        }
        val action=if(move)"Move to Hidden" else "Copy to Hidden"
        compose.onNodeWithText(action).assertIsDisplayed()
        bottom();compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(0,accepted.get());assertEquals(1,dismissed.get())}
        compose.onNodeWithText("Open confirmation").performClick()
        compose.onNodeWithText(action).performClick()
        compose.runOnIdle {assertEquals(1,accepted.get())}
    }
    @Test fun coverDarkCopyConfirmationRequiresExplicitAcceptance()=transfer(AppTheme.DARK,false)
    @Test fun landscapeLightMoveConfirmationRequiresExplicitAcceptance()=transfer(AppTheme.LIGHT,true)
    @Test fun coverDarkTransferProgressCanRequestSafeStopOnceWithoutDismissing() {
        val stops=AtomicInteger()
        content(AppTheme.DARK) {
            var stopping by remember {mutableStateOf(false)}
            HiddenTransferProgressDialog(9,1000,stopping){stops.incrementAndGet();stopping=true}
        }
        bottom();compose.onNodeWithText("Cancel transfer").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cancel transfer").assertIsNotEnabled()
        compose.onNodeWithText("Stopping after the current copy is safely verified…").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {assertEquals(1,stops.get())}
    }
    private fun destructive(theme:AppTheme,kind:VaultActionKind,action:String) {
        val accepted=AtomicInteger();val dismissed=AtomicInteger()
        content(theme) {
            var open by remember {mutableStateOf(true)}
            Column {
                Button(onClick={open=true}){Text("Open confirmation")}
                if(open)VaultActionConfirmationDialog(kind,1000,{dismissed.incrementAndGet();open=false},{accepted.incrementAndGet();open=false})
            }
        }
        compose.onNodeWithText(action).assertIsDisplayed()
        bottom();compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {assertEquals(0,accepted.get());assertEquals(1,dismissed.get())}
        compose.onNodeWithText("Open confirmation").performClick()
        compose.onNodeWithText(action).assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(1,accepted.get())}
    }
    @Test fun coverLightRecentlyDeletedConfirmationKeepsCancelAndMoveReachable()=destructive(AppTheme.LIGHT,VaultActionKind.RECENTLY_DELETED,"Move to Recently Deleted")
    @Test fun landscapeDarkRestoreConfirmationKeepsRestrictionExplanationAndChoice()=destructive(AppTheme.DARK,VaultActionKind.RESTORE_GALLERY,"Restore copies")
    @Test fun coverDarkPermanentDeleteRequiresSeparateExplicitChoice()=destructive(AppTheme.DARK,VaultActionKind.PERMANENT_DELETE,"Delete now")
    @Test fun coverLightCollectionCoverShowsSelectionAndReachesLastOfLargeList() {
        val media=(0 until 300).map {VaultItem("cover-$it","image/png","Photo $it",0,1,ByteArray(32),ByteArray(12),VaultItemState.COMPLETE)}
        var chosen:String?=null
        content(AppTheme.LIGHT) {CollectionCoverDialog(media,"cover-42",{chosen=it},{})}
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(43)
        compose.onNodeWithText("Photo 42").assertIsSelected()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(300)
        compose.onNodeWithText("Photo 299").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals("cover-299",chosen)}
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }
    @Test fun coverDarkNewCollectionUsesActualFieldAndExplicitCreateWithKeyboard() {
        val created=mutableListOf<String>();var cancelled=false
        content(AppTheme.DARK) {NewCollectionDialog({created+=it},{cancelled=true})}
        compose.onNodeWithText("Create").performClick()
        compose.runOnIdle {assertTrue(created.isEmpty())}
        fill("Collection name","Synthetic collection");assertKeyboard()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Create").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(listOf("Synthetic collection"),created);assertFalse(cancelled)}
    }
}
