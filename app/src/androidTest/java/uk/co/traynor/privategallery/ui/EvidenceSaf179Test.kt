package uk.co.traynor.privategallery.ui

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.ui.AppTheme

/** Real Activity lifecycle, Host/SAF contract and Android document IO with synthetic provider grants. */
class EvidenceSaf179Test {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private class Picker:ActivityResultRegistry(){
  var request:Int?=null;var contract="";var launches=0
  override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?){request=requestCode;this.contract=contract.javaClass.simpleName;launches++}
  fun returnDocument(uri:Uri){assertTrue(dispatchResult(checkNotNull(request),uri))}
 }
 private class Provider(private val file:File):ContentProvider(){
  val reads=AtomicInteger()
  override fun onCreate()=true
  override fun getType(uri:Uri)="text/plain"
  override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor=MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME,DocumentsContract.Document.COLUMN_LAST_MODIFIED)).apply{addRow(arrayOf("picker179.txt",1700000000000L))}
  override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {require(mode=="r");reads.incrementAndGet();return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)}
  override fun insert(uri:Uri,values:ContentValues?):Uri?=null
  override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
  override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
 }
 @Test fun importPickerRecreatesAuthorityOnlyAfterResumedOriginalAuthentication()=flow(false)
 @Test fun latePickerAfterReauthenticationCannotReadOrImportItsDocument()=flow(true)
 private fun flow(stale:Boolean){
  val root=File(compose.activity.cacheDir,"evidence-saf179-${UUID.randomUUID()}").apply{mkdirs()}
  val source=File(root,"synthetic-provider-source.txt").apply{writeText("Synthetic private picker179 transcript; no external evidence")}
  val provider=Provider(source).apply{attachInfo(compose.activity,ProviderInfo().apply{authority="evidence179.fixture";exported=false})}
  val resolver=ContentResolver.wrap(provider)
  val context=object:ContextWrapper(compose.activity){override fun getContentResolver()=resolver;override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()};override fun getApplicationContext():Context=this}
  val epoch=AtomicLong(1);val generation=AtomicLong();val accesses=CopyOnWriteArrayList<EvidenceAccess>();val key=ByteArray(32){49};val picker=Picker();val dismissals=AtomicInteger();val gate=Any()
  lateinit var owner:EvidenceOwnerSession
  InstrumentationRegistry.getInstrumentation().runOnMainSync {
   owner=EvidenceOwnerSession(EvidenceCatalog.Owner.PRIMARY,{epoch.get()==1L},accessFactory={
    if(epoch.get()!=1L||!compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))null else {
     val operation=generation.incrementAndGet()
     EvidenceAccess.primary(context,key,{key},{epoch.get()==1L&&generation.get()==operation&&compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)},{key.copyOf()},gate).also{accesses.add(it)}
    }
   },routeFactory={error("SAF fixture must not create network routes")},primarySystemAllowed={false},ciphertextStaging=File(root,"cipher-staging"),lockOriginating={epoch.incrementAndGet()},panic={epoch.incrementAndGet()},hide={epoch.incrementAndGet()})
  }
  try {
   val registryOwner=object:ActivityResultRegistryOwner{override val activityResultRegistry=picker}
   compose.setContent{CompositionLocalProvider(LocalContext provides context,LocalActivityResultRegistryOwner provides registryOwner){PrivateGalleryTheme(AppTheme.DARK){EvidenceLockerHost(owner,true){dismissals.incrementAndGet()}}}}
   compose.waitUntil(15000){compose.onAllNodesWithText("Import document / image").fetchSemanticsNodes().isNotEmpty()}
   val old=accesses.single()
   compose.onNodeWithText("Import document / image").performScrollTo().performClick()
   assertEquals("OpenDocument",picker.contract);assertEquals(1,picker.launches);assertFalse(old.current());assertEquals(0,provider.reads.get())
   compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
   if(stale)epoch.incrementAndGet()
   InstrumentationRegistry.getInstrumentation().runOnMainSync{picker.returnDocument(Uri.parse("content://evidence179.fixture/source"))}
   assertEquals("No provider read while stopped",0,provider.reads.get())
   compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
   if(stale){
    compose.waitUntil(15000){dismissals.get()>0};assertEquals(0,provider.reads.get());assertEquals(1,accesses.size);assertFalse(owner.sameAuthentication())
   }else{
    compose.waitUntil(15000){provider.reads.get()>0&&accesses.size==2&&runCatching{accesses.last().store.read().evidence().singleOrNull()?.ocr()?.isNotEmpty()==true}.getOrDefault(false)}
    val fresh=accesses.last();assertTrue(fresh.current());assertFalse(old.current())
    val record=fresh.store.read().evidence().single();assertEquals("picker179.txt",record.metadata().sourceName());assertEquals(1700000000000L,record.metadata().fileModifiedAt());assertTrue(record.metadata().embedded().values.any{it.contains("provider",true)})
    val bytes=fresh.reader().readVerified(record.sourceId()){_,input->input.readBytes()};try{assertArrayEquals(source.readBytes(),bytes)}finally{bytes.fill(0)}
    assertTrue(source.isFile);assertEquals(1,picker.launches)
   }
  }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync{owner.close()};accesses.forEach{it.close()};key.fill(0);root.deleteRecursively()}
 }
}
