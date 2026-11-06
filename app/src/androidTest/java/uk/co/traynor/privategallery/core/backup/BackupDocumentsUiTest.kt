package uk.co.traynor.privategallery.core.backup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.home2.WallpaperOwner
import uk.co.traynor.privategallery.home2.BackupHost

class BackupDocumentsUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun androidCreateDocumentResultResumesAndWritesActualGrantedDestination(){
  val root=BackupFixture62.temporary("documents-ui62");val f=BackupFixture62(root);var attached by mutableStateOf(true)
  val owner=WallpaperOwner(EvidenceCatalog.Owner.PRIMARY,{f.live.get()},{EvidenceAccess.primary(f.context,f.key,{f.key},{f.live.get()},{f.key.copyOf()},Any())})
  var document:android.net.Uri?=null
  try{
   compose.setContent{if(attached)CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity,LocalContext provides f.context){MaterialTheme{BackupHost(owner,true,{attached=false})}}}
   compose.waitUntil(10000){compose.onAllNodesWithTag("nova-backup").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("Create backup").performScrollTo().performClick()
   BackupDocumentsUi62.create();val id=checkNotNull(BackupDocuments62.call("latest").getString("id"));document=android.provider.DocumentsContract.buildDocumentUri(BackupDocumentsFixtureProvider.AUTHORITY,id)
   compose.waitUntil(10000){compose.onAllNodesWithText("I saved the recovery key").fetchSemanticsNodes().isNotEmpty()}
   // The first checkbox here is the explicit recovery-key acknowledgement, not a scheduling control.
   compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick();compose.onNodeWithText("Encrypt and verify destination").performScrollTo().performClick()
   compose.waitUntil(15000){compose.onAllNodes(hasText("Verified actual destination bytes",substring=true)).fetchSemanticsNodes().isNotEmpty()}
   assertTrue(f.context.contentResolver.openFileDescriptor(document,"r")!!.use{it.statSize}>0)
  }finally{compose.runOnIdle{attached=false;f.live.set(false)};document?.let{BackupDocuments62.delete(it)};f.close();root.deleteRecursively()}
 }
 @Test fun actualTreePickerRequiresRecoveryAcknowledgementBeforeArmingEncryptedAutomaticSchedule(){
  val root=BackupFixture62.temporary("tree-ui62");val f=BackupFixture62(root);var attached by mutableStateOf(true)
  val prefs=f.context.getSharedPreferences("nova-backup-due",0);val prior=prefs.all.toMap();val scheduler=f.context.getSystemService(android.app.job.JobScheduler::class.java);val priorJob=scheduler.getPendingJob(171162)
  val owner=WallpaperOwner(EvidenceCatalog.Owner.PRIMARY,{f.live.get()},{EvidenceAccess.primary(f.context,f.key,{f.key},{f.live.get()},{f.key.copyOf()},Any())})
  try{
   compose.setContent{if(attached)CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity,LocalContext provides f.context){MaterialTheme{BackupHost(owner,true,{attached=false})}}}
   compose.waitUntil(10000){compose.onAllNodesWithTag("nova-backup").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Choose automatic backup folder").performScrollTo().performClick();BackupDocumentsUi62.folder()
   compose.waitUntil(10000){compose.onAllNodesWithTag("automatic-key-saved").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Enable automatic backup").performScrollTo().assertIsNotEnabled();assertFalse(AutomaticBackupSettings.armed(f.coordinator.space))
   compose.onNodeWithTag("automatic-key-saved").performScrollTo().performClick();compose.onNodeWithText("Enable automatic backup").performScrollTo().performClick()
   compose.waitUntil(10000){AutomaticBackupSettings.armed(f.coordinator.space)}
   assertTrue(f.context.contentResolver.persistedUriPermissions.any{it.isReadPermission&&it.isWritePermission&&it.uri.authority==BackupDocumentsFixtureProvider.AUTHORITY})
   compose.onNodeWithText("Turn automatic backup off").performScrollTo().performClick();compose.waitUntil(10000){!AutomaticBackupSettings.armed(f.coordinator.space)}
  }finally{compose.runOnIdle{attached=false};if(f.access.current())runCatching{AutomaticBackupSettings.disable(f.context,f.coordinator.space)};scheduler.cancel(171162);if(priorJob!=null)scheduler.schedule(priorJob);prefs.edit().clear().also{e->prior.forEach{(k,v)->when(v){is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Boolean->e.putBoolean(k,v);is String->e.putString(k,v)}}}.commit();f.close();root.deleteRecursively()}
 }

}
