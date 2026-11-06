package uk.co.traynor.privategallery.core.backup

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.home2.WallpaperOwner
import uk.co.traynor.privategallery.home2.BackupHost

class BackupUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private class Picker:ActivityResultRegistry(){var request:Int?=null;override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?){request=requestCode};fun deliver(){assertTrue(dispatchResult(checkNotNull(request),Uri.parse("content://backup.fixture/document")))}}
 private fun picker(hidden:Boolean,relock:Boolean){
  val base=compose.activity;val root=File(base.cacheDir,"backup-ui-${UUID.randomUUID()}")
  val context=object:ContextWrapper(base){override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()};override fun getApplicationContext():Context=this}
  val session=HiddenSession{100L};check(session.complete(session.beginAuthentication(),ByteArray(32){17}));val authentication=session.authentication()
  val key=ByteArray(32){24};val accesses=mutableListOf<EvidenceAccess>();var current=true;var closed=false;var attached by mutableStateOf(true)
  val picker=Picker();val registry=object:ActivityResultRegistryOwner{override val activityResultRegistry=picker}
  val owner=WallpaperOwner(if(hidden)EvidenceCatalog.Owner.HIDDEN else EvidenceCatalog.Owner.PRIMARY,{current&&(!hidden||session.isAuthenticationCurrent(authentication))},{
   (if(hidden)EvidenceAccess.hidden(context,session){current}else EvidenceAccess.primary(context,key,{key},{current},{key.copyOf()},Any())).also{accesses+=it}
  })
  try {
   compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides context,LocalActivityResultRegistryOwner provides registry){MaterialTheme{BackupHost(owner,true,onClose={closed=true})}}}
   compose.waitUntil(10000){compose.onAllNodesWithTag("nova-backup").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Create backup").performScrollTo().performClick()
   compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{
    if(hidden){if(relock){session.lock();check(session.complete(session.beginAuthentication(),ByteArray(32){17}))}else session.revokeOperations()}
    picker.deliver()
   }
   compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
   if(relock){compose.waitUntil(5000){closed};assertEquals(1,accesses.size)}else{
    compose.waitUntil(10000){accesses.size==2&&compose.onAllNodesWithText("I saved the recovery key").fetchSemanticsNodes().isNotEmpty()}
    compose.runOnIdle{assertFalse(accesses.first().current());assertTrue(accesses.last().current());assertFalse(closed)}
    compose.onNodeWithText("I saved the recovery key").performScrollTo().assertIsDisplayed()
   }
  }finally{compose.runOnIdle{attached=false;current=false};session.lock();accesses.forEach{it.close()};key.fill(0);root.deleteRecursively()}
 }
 @Test fun primaryPickerResumesWithFreshAuthority()=picker(false,false)
 @Test fun hiddenPickerResumesOnlyWithSamePinAuthentication()=picker(true,false)
 @Test fun hiddenLockAndReunlockRejectsOldPickerResult()=picker(true,true)
}
