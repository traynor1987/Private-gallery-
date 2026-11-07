package uk.co.traynor.privategallery.core.backup

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.home2.WallpaperOwner
import uk.co.traynor.privategallery.home2.BackupHost
import uk.co.traynor.privategallery.home2.BackupDashboardTheme
import uk.co.traynor.privategallery.home2.BackupOverview
import uk.co.traynor.privategallery.home2.BackupOverviewDetails
import uk.co.traynor.privategallery.home2.BackupTopBar
import uk.co.traynor.privategallery.ReleaseScreenshots62
import uk.co.traynor.privategallery.ui.modalCaptureState1762

class BackupUi62Test {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private class Picker:ActivityResultRegistry(){var request=0;override fun<I,O>onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?){request=requestCode};fun deliver(uri:Uri?)=dispatchResult(request,uri)}
 private fun fixture(hidden:Boolean=false,action:(BackupFixture62,Picker,AtomicBoolean)->Unit){
  val root=BackupFixture62.temporary("backup-ui62");val f=BackupFixture62(root,hidden);val live=AtomicBoolean(true);val auth=f.session.authentication();val picker=Picker();var attached by mutableStateOf(true)
  val owner=WallpaperOwner(if(hidden)EvidenceCatalog.Owner.HIDDEN else EvidenceCatalog.Owner.PRIMARY,{live.get()&&(!hidden||f.session.isAuthenticationCurrent(auth))},{if(hidden)EvidenceAccess.hidden(f.context,f.session){live.get()}else EvidenceAccess.primary(f.context,f.key,{f.key},{live.get()},{f.key.copyOf()},Any())})
  try{compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides f.context,LocalActivityResultRegistryOwner provides object:ActivityResultRegistryOwner{override val activityResultRegistry=picker}){MaterialTheme{BackupHost(owner,true,onClose={attached=false})}}}
   compose.waitUntil(10000){compose.onAllNodesWithTag("nova-backup").fetchSemanticsNodes().isNotEmpty()};action(f,picker,live)
  }finally{compose.runOnIdle{attached=false;live.set(false)};f.close();root.deleteRecursively()}
 }
 @Test fun primaryScheduleHydratesPersistedCheckboxesAndHasNoHiddenDisclosure() {
  val prefs=compose.activity.getSharedPreferences("nova-backup-due",0);val charging=prefs.getBoolean("charging",true);val wifi=prefs.getBoolean("wifi",true)
  try{prefs.edit().putBoolean("charging",false).putBoolean("wifi",true).commit()
   fixture{_,_,_->compose.onNodeWithTag("backup-charging").performScrollTo().assertIsOff();compose.onNodeWithTag("backup-wifi").performScrollTo().assertIsOn();compose.onAllNodes(hasText("Hidden",substring=true,ignoreCase=true)).assertCountEquals(0)
    compose.onNodeWithText("Nova Backup").assertIsDisplayed()
    assertTrue("The real Backup window remains secure",modalCaptureState1762(compose.activity).secure)
   }
  }finally{prefs.edit().putBoolean("charging",charging).putBoolean("wifi",wifi).commit()}
 }
 @Test fun authenticatedHiddenHasNoScheduleControls()=fixture(true){_,_,_->compose.onNodeWithTag("backup-charging").assertDoesNotExist();compose.onAllNodesWithText("Scheduled due checks").assertCountEquals(0)}
 @Test fun primaryRetirementDuringPickerRejectsLateAndDuplicateResult()=fixture{_,picker,live->
  compose.onNodeWithText("Create backup").performScrollTo().performClick();compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  compose.runOnIdle{live.set(false);picker.deliver(Uri.parse("content://invalid/stale"));picker.deliver(Uri.parse("content://invalid/duplicate"))}
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.waitUntil(5000){compose.onAllNodesWithTag("nova-backup").fetchSemanticsNodes().isEmpty()};compose.onAllNodesWithText("I saved the recovery key").assertCountEquals(0)
 }
 @Test fun cancelledPickerAndLateDuplicateDoNotInstallARecoveryInput()=fixture{_,picker,_->
  compose.onNodeWithText("Create backup").performScrollTo().performClick();compose.runOnIdle{assertTrue(picker.deliver(null))};compose.waitForIdle();compose.runOnIdle{assertFalse("Completed launcher rejects an unsolicited duplicate",picker.deliver(Uri.parse("content://invalid/late")))};compose.waitForIdle();compose.onAllNodesWithText("I saved the recovery key").assertCountEquals(0)
 }
 private fun recreate(phase:String)=fixture{f,picker,_->
  val uri=BackupDocuments62.create();val key="synthetic preview key"
  try{
   if(phase=="preview")f.coordinator.export(SafBackupProvider(f.context,uri,f.access),key.toCharArray())
   compose.onNodeWithText(if(phase=="preview")"Restore"else "Create backup").performScrollTo().performClick()
   if(phase!="picker"){
    compose.runOnIdle{picker.deliver(uri)}
    if(phase=="preview"){
     compose.waitUntil(10000){compose.onAllNodesWithText("Recovery key").fetchSemanticsNodes().isNotEmpty()};compose.onNode(hasSetTextAction()).performScrollTo().performTextInput(key);compose.onNodeWithText("Validate and preview").performScrollTo().performClick()
     compose.waitUntil(15000){compose.onAllNodes(hasText("Restore preview",substring=true)).fetchSemanticsNodes().isNotEmpty()};compose.onAllNodes(hasText("Hidden",substring=true,ignoreCase=true)).assertCountEquals(0)
    }else compose.waitUntil(10000){compose.onAllNodesWithText("I saved the recovery key").fetchSemanticsNodes().isNotEmpty()}
   }
   compose.activityRule.scenario.recreate();compose.waitForIdle();compose.onAllNodesWithTag("nova-backup").assertCountEquals(0);compose.onAllNodesWithText(key).assertCountEquals(0)
  }finally{BackupDocuments62.delete(uri)}
 }
 @Test fun pendingPickerDoesNotSurviveActualActivityRecreation()=recreate("picker")
 @Test fun recoveryInputDoesNotSurviveActualActivityRecreation()=recreate("recovery")
 @Test fun authenticatedPreviewDoesNotSurviveActualActivityRecreation()=recreate("preview")

 @Test fun syntheticDashboardPreviewShowsReadableSizes() {
  val archive=BackupCoordinator.HistoryEntry("Verified","Backup","Synthetic archive.novabak",13_200_000,1_794_000_000_000)
  val history=BackupCoordinator.HistorySnapshot(archive,listOf(archive))
  var width by mutableIntStateOf(360)
  compose.setContent {
   MaterialTheme(colorScheme=darkColorScheme()) {
    BackupDashboardTheme {
     Surface(Modifier.size(width.dp,660.dp).testTag("backup-synthetic-preview"),color=MaterialTheme.colorScheme.background) {
      Column {
       BackupTopBar(false,{})
       Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        BackupOverview(history,false,{},{})
        BackupOverviewDetails(history,1_500_000_000)
       }
      }
     }
    }
   }
  }
  for (target in listOf(360,840)) {
   compose.runOnIdle{width=target}
   compose.onNodeWithText("13.2 MB").assertIsDisplayed()
   compose.onNodeWithText("1.5 GB").assertIsDisplayed()
   compose.onNodeWithText("Synthetic archive.novabak").assertIsDisplayed()
   ReleaseScreenshots62.capture(compose.onNodeWithTag("backup-synthetic-preview"),"backup-synthetic-$target")
  }
 }
}
