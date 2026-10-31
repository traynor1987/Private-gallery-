package uk.co.traynor.privategallery.ui

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
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
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.*

/** Actual Host/dialog composition, real Activity lifecycle and observable synthetic Android FDs. */
class EvidenceSaf179Test {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
 private class Picker:ActivityResultRegistry(){
  var request:Int?=null;var contract="";var launches=0;var input:Any?=null
  override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?){request=requestCode;this.contract=contract.javaClass.simpleName;this.input=input;launches++}
  fun returnDocument(uri:Uri?){assertTrue(dispatchResult(checkNotNull(request),uri))}
  fun returnPermission(accepted:Boolean){assertTrue(dispatchResult(checkNotNull(request),if(accepted)android.app.Activity.RESULT_OK else android.app.Activity.RESULT_CANCELED,null))}
 }
 private class Provider(val directory:File):DocumentsProvider(){
  val reads=AtomicInteger();val writes=AtomicInteger();val cancellations=AtomicInteger();val closes=AtomicInteger()
  val deleted=CopyOnWriteArrayList<String>();val writeEntered=CountDownLatch(1);val release=CountDownLatch(1)
  @Volatile var blockWrites=false
  @Volatile var blockReads=false
  val readEntered=CountDownLatch(1)
  private val workers=Executors.newCachedThreadPool()
  fun file(id:String)=File(directory,if(id=="archive-alias")"archive"else id)
  override fun onCreate()=true
  override fun queryRoots(projection:Array<out String>?):Cursor=MatrixCursor(arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))
  override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?):Cursor=MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
  override fun queryDocument(documentId:String,projection:Array<out String>?):Cursor {
   val columns=projection?:arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,OpenableColumns.DISPLAY_NAME,DocumentsContract.Document.COLUMN_LAST_MODIFIED,DocumentsContract.Document.COLUMN_MIME_TYPE)
   return MatrixCursor(columns).apply{addRow(columns.map{column->when(column){
    DocumentsContract.Document.COLUMN_DOCUMENT_ID->documentId
    OpenableColumns.DISPLAY_NAME->"picker179.txt"
    DocumentsContract.Document.COLUMN_LAST_MODIFIED->1700000000000L
    DocumentsContract.Document.COLUMN_MIME_TYPE->if(documentId.startsWith("archive"))"application/octet-stream"else"text/plain"
    else->null
   }})}
  }
  override fun deleteDocument(documentId:String){deleted.add(documentId);file(documentId).delete()}
  override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor {
   val input=mode=="r"
   if(input)reads.incrementAndGet()else writes.incrementAndGet()
   signal?.setOnCancelListener{cancellations.incrementAndGet();release.countDown()}
   if((!input&&blockWrites)||(input&&blockReads)) {
    val pipe=ParcelFileDescriptor.createReliablePipe()
    workers.execute{
     try {
      if(input){readEntered.countDown();check(release.await(30,TimeUnit.SECONDS));ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use{it.write(file(documentId).readBytes())}}
      else ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use{stream->if(stream.read()>=0)writeEntered.countDown();check(release.await(30,TimeUnit.SECONDS));val buffer=ByteArray(8192);while(stream.read(buffer)>=0){};buffer.fill(0)}
     }catch(_:Exception){}finally{closes.incrementAndGet();runCatching{if(input)pipe[1].close()else pipe[0].close()}}
    }
    return if(input)pipe[0]else pipe[1]
   }
   val flags=if(input)ParcelFileDescriptor.MODE_READ_ONLY else ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
   return ParcelFileDescriptor.open(file(documentId),flags,android.os.Handler(android.os.Looper.getMainLooper())){closes.incrementAndGet()}
  }
  fun uri(id:String):Uri = if(id=="archive-alias")DocumentsContract.buildDocumentUriUsingTree(DocumentsContract.buildTreeDocumentUri("evidence179.fixture","root"),"archive")else DocumentsContract.buildDocumentUri("evidence179.fixture",id)
  fun close(){release.countDown();workers.shutdownNow()}
 }
 private inner class Harness(val secret:Boolean=false,val seed:Boolean=false):AutoCloseable {
  val root=File(compose.activity.cacheDir,"evidence-saf179-${UUID.randomUUID()}").apply{mkdirs()}
  val provider=Provider(File(root,"provider").apply{mkdirs()}).apply{attachInfo(compose.activity,ProviderInfo().apply{authority="evidence179.fixture";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"})}
  val resolver=ContentResolver.wrap(provider)
  val context=object:ContextWrapper(compose.activity){
   override fun getContentResolver()=resolver
   override fun getFilesDir()=File(root,"files").apply{mkdirs()}
   override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()}
   override fun getApplicationContext():Context=this
  }
  val routeCreations=AtomicInteger()
  val epoch=AtomicLong(1);val clock=AtomicLong(1000);val generation=AtomicLong();val key=ByteArray(32){49}
  val hidden=if(secret)HiddenSession{clock.get()}.also{check(it.complete(it.beginAuthentication(),key.copyOf()))}else null
  val authentication=hidden?.authentication()
  val accesses=CopyOnWriteArrayList<EvidenceAccess>();val picker=Picker();val dismissals=AtomicInteger();val security=CopyOnWriteArrayList<String>();val gate=Any()
  val stageDirectory=File(root,"cipher-staging")
  lateinit var owner:EvidenceOwnerSession
  val same={epoch.get()==1L&&(hidden==null||hidden.isAuthenticationCurrent(authentication))}
  val lifecycleObserver=LifecycleEventObserver{_,event->
   if(event==Lifecycle.Event.ON_STOP)hidden?.onBackground(60_000)
   if(event==Lifecycle.Event.ON_START)hidden?.onForeground() // Same reconciliation order as Main, before RESUMED.
  }
  init {
   provider.file("source").writeText("Synthetic private picker179 transcript; no external evidence")
   if(seed){
    val bytes=ByteArray(256*1024);java.util.Random(179).nextBytes(bytes)
    val original=try{if(hidden==null)(AndroidVaultRepository(context,key).importVerified(VaultImportSource("seed179.txt","text/plain",{bytes.inputStream()}))as ImportResult.Imported).item else HiddenRepository(context,hidden).importMedia("seed179.txt","text/plain",bytes.inputStream())}finally{bytes.fill(0)}
    val initial=access()
    try {
     val record=EvidenceCatalog.Evidence(UUID.randomUUID().toString(),original.id,original.displayName,original.mimeType,EvidenceArchive.hex(original.plaintextSha256),original.plaintextSize,1,EvidenceMetadata.fromItem(original),"","",emptyList(),emptyList(),emptyList(),emptyList())
     initial.store.update{it.upsertEvidence(record,1,"REGISTER").upsertIncident(EvidenceCatalog.Incident(UUID.randomUUID().toString(),"Picker incident179",0,0,"","",emptyList(),listOf(record.id()),emptyList(),false,false,emptyList()),2,"CREATE")}
    }finally{initial.close()}
   }
   instrumentation.runOnMainSync{
    compose.activity.lifecycle.addObserver(lifecycleObserver)
    owner=EvidenceOwnerSession(if(secret)EvidenceCatalog.Owner.HIDDEN else EvidenceCatalog.Owner.PRIMARY,same,accessFactory={
     if(!same()||!compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))null else access().also{accesses.add(it)}
    },routeFactory={routeCreations.incrementAndGet();error("SAF fixture must not create routes")},primarySystemAllowed={false},ciphertextStaging=stageDirectory,
     lockOriginating={security.add("Lock Vault");hidden?.lock();epoch.incrementAndGet()},panic={security.add("Panic Exit");hidden?.lock();epoch.incrementAndGet()},hide={security.add("Hide Content");hidden?.revokeOperations();epoch.incrementAndGet()})
   }
  }
  private fun access():EvidenceAccess {
   if(hidden!=null)return EvidenceAccess.hidden(context,hidden){same()&&compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
   val operation=generation.incrementAndGet()
   return EvidenceAccess.primary(context,key,{key},{same()&&generation.get()==operation&&compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)},{key.copyOf()},gate)
  }
  fun show(){val registryOwner=object:ActivityResultRegistryOwner{override val activityResultRegistry=picker}
   compose.setContent{CompositionLocalProvider(LocalContext provides context,LocalActivityResultRegistryOwner provides registryOwner){PrivateGalleryTheme(AppTheme.DARK){EvidenceLockerHost(owner,true,preparePermission={Intent("synthetic.evidence179.PERMISSION")}){dismissals.incrementAndGet()}}}}
   compose.waitUntil(20000){compose.onAllNodesWithText("Import document / image").fetchSemanticsNodes().isNotEmpty()}
  }
  fun returnFromPicker(uri:Uri?,advance:Long=0){
   compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
   clock.addAndGet(advance)
   instrumentation.runOnMainSync{picker.returnDocument(uri)}
   compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  }
  fun exportDialog(){click("Incidents");click("Picker incident179");click("Export");click("Review export")
   compose.waitUntil(15000){compose.onAllNodesWithText("Export password (at least 12 characters)").fetchSemanticsNodes().isNotEmpty()}
  }
  fun encryptedPicker():ByteArray {
   exportDialog();assertEquals(0,picker.launches);assertEquals(0,provider.writes.get())
   field("Export password (at least 12 characters)").performTextReplacement(PASSWORD)
   field("Repeat password").performTextReplacement(PASSWORD);click("Prepare encrypted export")
   compose.waitUntil(45000){picker.launches==1}
   assertEquals("CreateDocument",picker.contract);assertEquals("private-gallery-evidence.pgevidence",picker.input)
   assertFalse(accesses.first().current())
   val stage=stageDirectory.listFiles()!!.single();assertEquals(256,android.system.Os.stat(stage.path).st_mode and 511)
   return stage.readBytes()
  }
  fun archiveBytes():ByteArray {
   val access=accesses.last();val catalog=access.store.read();val selected=catalog.incidents().single()
   return ByteArrayOutputStream().use{output->val password=PASSWORD.toCharArray();try{EvidenceArchive.write(EvidenceExportPlan(catalog,listOf(selected.id()),emptyList(),EvidenceEgress.digest(catalog),false),access.reader(),password,output);output.toByteArray()}finally{password.fill('\u0000')}}
  }
  fun openArchive(){provider.file("archive").writeBytes(archiveBytes());click("Open encrypted export");returnFromPicker(provider.uri("archive"));authenticate()}
  fun authenticate(){compose.waitUntil(15000){compose.onAllNodesWithText("Archive password").fetchSemanticsNodes().isNotEmpty()};field("Archive password").performTextReplacement(PASSWORD);click("Authenticate and open")}
  fun waitReport(){compose.waitUntil(45000){compose.onAllNodesWithContentDescription("Authenticated evidence PDF report").fetchSemanticsNodes().isNotEmpty()}}
  override fun close(){provider.close();instrumentation.runOnMainSync{compose.activity.lifecycle.removeObserver(lifecycleObserver);owner.close()};accesses.forEach{it.close()};hidden?.lock();key.fill(0);root.deleteRecursively()}
 }
 private fun field(label:String)=compose.onNode(hasSetTextAction() and hasText(label))
 private fun click(text:String){compose.onAllNodesWithText(text).onLast().performScrollTo().performClick()}
 private fun assertEmptyArchivePassword(){compose.waitUntil(15000){compose.onAllNodesWithText("Archive password").fetchSemanticsNodes().isNotEmpty()};assertEquals("",field("Archive password").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)}
 @Test fun importPickerRecreatesAuthorityOnlyAfterResumedOriginalAuthentication()=importFlow(false,false,0)
 @Test fun latePickerAfterReauthenticationCannotReadOrImportItsDocument()=importFlow(false,true,0)
 @Test fun hiddenWithinGracePickerCapturesFreshAuthorityAfterForegroundReconciliation()=importFlow(true,false,30_000)
 @Test fun hiddenExpiredPickerCannotReadDocumentAfterRealForegroundReconciliation()=importFlow(true,false,60_000)
 @Test fun hiddenReauthenticatedPickerCannotReviveOriginalAuthentication()=importFlow(true,true,60_000)
 private fun importFlow(hidden:Boolean,reauthenticate:Boolean,advance:Long){Harness(secret=hidden).use{f->
  f.show();val old=f.accesses.single();click("Import document / image")
  assertEquals("OpenDocument",f.picker.contract);assertFalse(old.current());assertEquals(0,f.provider.reads.get())
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);f.clock.addAndGet(advance)
  if(reauthenticate&&f.hidden==null)f.epoch.incrementAndGet()
  assertEquals("No provider read while stopped",0,f.provider.reads.get())
  if(reauthenticate&&f.hidden!=null){
   // Reconcile the expired background gap through the actual Activity lifecycle first,
   // then authenticate a new identity while resumed before delivering the old result.
   compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
   compose.waitUntil(15000){f.dismissals.get()>0}
   instrumentation.runOnMainSync{check(f.hidden.complete(f.hidden.beginAuthentication(),f.key.copyOf()));f.picker.returnDocument(f.provider.uri("source"))}
  }else {
   instrumentation.runOnMainSync{f.picker.returnDocument(f.provider.uri("source"))}
   instrumentation.waitForIdleSync();assertEquals("No provider read after delivery while stopped",0,f.provider.reads.get())
  }
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  if(reauthenticate||hidden&&advance>=60_000){compose.waitUntil(15000){f.dismissals.get()>0};assertEquals(0,f.provider.reads.get());assertEquals(1,f.accesses.size);assertFalse(f.owner.sameAuthentication())}
  else {
   compose.waitUntil(45000){f.accesses.size==2&&runCatching{f.accesses.last().store.read().evidence().singleOrNull()?.ocr()?.isNotEmpty()==true}.getOrDefault(false)}
   val fresh=f.accesses.last();assertTrue(fresh.current());assertFalse(old.current())
   val record=fresh.store.read().evidence().single();assertEquals("picker179.txt",record.metadata().sourceName());assertEquals(1700000000000L,record.metadata().fileModifiedAt())
   val bytes=fresh.reader().readVerified(record.sourceId()){_,input->input.readBytes()};try{assertArrayEquals(f.provider.file("source").readBytes(),bytes)}finally{bytes.fill(0)}
   assertTrue(f.provider.file("source").isFile);assertTrue(f.provider.deleted.isEmpty())
  }
 }}
 @Test fun encryptedPreparePickerFreshControllerCopiesExactSealedCiphertext() {Harness(seed=true).use{f->
  f.show();val ciphertext=f.encryptedPicker()
  try {
   f.returnFromPicker(f.provider.uri("output"))
   compose.waitUntil(30000){f.provider.writes.get()==1&&f.provider.file("output").length()==ciphertext.size.toLong()&&compose.onAllNodesWithText("Password-encrypted .pgevidence export saved.",substring=true).fetchSemanticsNodes().isNotEmpty()}
   assertEquals(2,f.accesses.size);assertFalse(f.accesses.first().current());assertTrue(f.accesses.last().current())
   assertArrayEquals(ciphertext,f.provider.file("output").readBytes());assertTrue(f.provider.deleted.isEmpty())
   compose.waitUntil(15000){f.stageDirectory.listFiles().orEmpty().isEmpty()}
   assertEquals(1,f.accesses.last().sources.list().size)
  }finally{ciphertext.fill(0)}
 }}
 @Test fun cancelledOutputPickerDiscardsOnlySealedCiphertextAndPreservesOriginals() {Harness(seed=true).use{f->
  f.show();f.encryptedPicker().fill(0);f.returnFromPicker(null)
  compose.waitUntil(15000){f.accesses.size==2&&f.stageDirectory.listFiles().orEmpty().isEmpty()}
  assertEquals(0,f.provider.writes.get());assertTrue(f.provider.deleted.isEmpty());assertEquals(1,f.accesses.last().sources.list().size)
 }}
 @Test fun plaintextOutputNeedsSeparateExplicitConfirmation() {Harness(seed=true).use{f->
  f.show();f.exportDialog();assertEquals(0,f.picker.launches)
  click("Choose explicit plaintext ZIP export…");compose.onNodeWithText("Create unencrypted evidence outside the vault?").assertExists();assertEquals(0,f.picker.launches)
  compose.onAllNodesWithText("Cancel").onLast().performClick();assertEquals(0,f.picker.launches)
  click("Choose explicit plaintext ZIP export…");click("Confirm plaintext export")
  assertEquals("CreateDocument",f.picker.contract);assertEquals("private-gallery-evidence.zip",f.picker.input)
  f.returnFromPicker(f.provider.uri("output"))
  compose.waitUntil(30000){compose.onAllNodesWithText("Explicitly confirmed plaintext ZIP export saved",substring=true).fetchSemanticsNodes().isNotEmpty()}
  assertEquals("PK",f.provider.file("output").inputStream().use{String(it.readNBytes(2))});assertTrue(f.provider.deleted.isEmpty())
 }}
 @Test fun plaintextLatePrimaryTagFailureNeverAcquiresProviderDestination(){Harness(seed=true).use{f->
  f.show();f.exportDialog();click("Choose explicit plaintext ZIP export…");click("Confirm plaintext export")
  val repo=AndroidVaultRepository(f.context,f.key);val item=repo.items().single()
  val payload=File(f.context.filesDir,"vault/payloads/${item.id}.vault")
  assertFalse(ChunkedVaultVideoStore.isChunked(payload))
  java.io.RandomAccessFile(payload,"rw").use{it.seek(it.length()-1);val last=it.readByte();it.seek(it.length()-1);it.writeByte(last.toInt() xor 1)}
  f.returnFromPicker(f.provider.uri("output"))
  compose.waitUntil(45000){compose.onAllNodesWithText("Export could not complete.",substring=true).fetchSemanticsNodes().isNotEmpty()}
  assertEquals(0,f.provider.writes.get());assertEquals(0,f.provider.closes.get());assertFalse(f.provider.file("output").exists())
  assertTrue(payload.isFile);assertTrue(f.provider.deleted.none{it=="source"||it=="archive"})
 }}
 @Test fun cancelBlockedPlaintextSnapshotProviderWriteRevokesWithoutBlockingMain(){Harness(seed=true).use{f->
  f.show();f.exportDialog();click("Choose explicit plaintext ZIP export…");click("Confirm plaintext export")
  f.provider.blockWrites=true;f.returnFromPicker(f.provider.uri("output"))
  assertTrue(f.provider.writeEntered.await(45,TimeUnit.SECONDS))
  val before=android.os.SystemClock.elapsedRealtime();click("Cancel")
  assertTrue("Main cancellation must not wait for snapshot/provider IO",android.os.SystemClock.elapsedRealtime()-before<2000)
  compose.waitUntil(15000){f.provider.cancellations.get()>0&&f.provider.closes.get()>0&&"output" in f.provider.deleted}
  assertFalse(f.owner.sameAuthentication());assertTrue(f.accesses.all{!it.current()})
  compose.waitUntil(15000){File(f.context.noBackupFilesDir,"evidence-open-primary").listFiles().orEmpty().isEmpty()}
  compose.onNodeWithText("Explicitly confirmed plaintext ZIP export saved",substring=true).assertDoesNotExist()
  assertEquals(1,AndroidVaultRepository(f.context,f.key).items().size)
 }}
 @Test fun cancelBlockedProviderWriteClosesAdmissionWithoutBlockingMain()=blockedOutput("Cancel")
 @Test fun lockDuringBlockedProviderWriteRevokesAndRequestsPartialCleanup()=blockedOutput("Lock Vault")
 @Test fun hideDuringBlockedProviderWriteRevokesAndRequestsPartialCleanup()=blockedOutput("Hide Content")
 @Test fun panicDuringBlockedProviderWriteRevokesAndRequestsPartialCleanup()=blockedOutput("Panic Exit")
 private fun blockedOutput(action:String){Harness(seed=true).use{f->
  f.show();f.encryptedPicker().fill(0);f.provider.blockWrites=true;f.returnFromPicker(f.provider.uri("output"))
  assertTrue(f.provider.writeEntered.await(15,TimeUnit.SECONDS))
  if(action!="Cancel")compose.onAllNodesWithContentDescription("Evidence security controls").onLast().performClick()
  val before=android.os.SystemClock.elapsedRealtime();click(action)
  assertTrue("Main cancellation must not wait for provider IO",android.os.SystemClock.elapsedRealtime()-before<2000)
  compose.waitUntil(15000){f.provider.cancellations.get()>0&&f.provider.closes.get()>0&&"output" in f.provider.deleted}
  assertFalse(f.owner.sameAuthentication());assertTrue(f.accesses.all{!it.current()});assertTrue(f.provider.deleted.none{it=="source"||it=="archive"})
  if(action!="Cancel")assertEquals(listOf(action),f.security.toList())
  compose.onNodeWithText("Password-encrypted .pgevidence export saved.",substring=true).assertDoesNotExist()
 }}
 @Test fun permissionReturnUsesFreshControllerAndRequiresNewPreviewWithoutAutoConnecting()=permissionReturn(false)
 @Test fun permissionReturnAfterReauthenticationCannotReopenAiOrStartTransport()=permissionReturn(true)
 private fun permissionReturn(stale:Boolean){Harness(seed=true).use{f->
  f.show();click("Incidents");click("Picker incident179");click("AI analysis");click("Set up Android VPN / isolation permission")
  assertEquals("StartActivityForResult",f.picker.contract);assertFalse(f.accesses.first().current());assertEquals(0,f.routeCreations.get())
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  if(stale)f.epoch.incrementAndGet()
  instrumentation.runOnMainSync{f.picker.returnPermission(true)}
  assertEquals(1,f.accesses.size)
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  if(stale){compose.waitUntil(15000){f.dismissals.get()>0};compose.onNodeWithText("Optional AI analysis").assertDoesNotExist();assertEquals(1,f.accesses.size)}
  else{compose.waitUntil(15000){f.accesses.size==2&&compose.onAllNodesWithText("Prepare exact upload preview").fetchSemanticsNodes().isNotEmpty()};assertTrue(f.accesses.last().current());compose.onNodeWithText("Review before sending").assertDoesNotExist()}
  assertEquals(0,f.routeCreations.get());assertEquals(0,f.provider.reads.get());assertEquals(0,f.provider.writes.get())
 }}
 @Test fun archiveReexportFreshPasswordAndSeparateConfirmationProducePlaintextOnlyAtDestination() {Harness(seed=true).use{f->
  f.show();f.openArchive();f.waitReport();val original=f.provider.file("archive").readBytes()
  try {
   click("Export readable plaintext ZIP…");click("Confirm plaintext re-export");f.returnFromPicker(f.provider.uri("output"))
   compose.onNodeWithContentDescription("Authenticated evidence PDF report").assertDoesNotExist();assertEquals(0,f.provider.writes.get())
   assertEmptyArchivePassword();f.authenticate()
   compose.waitUntil(45000){compose.onAllNodesWithText("Confirm plaintext re-export").fetchSemanticsNodes().isNotEmpty()}
   assertEquals(0,f.provider.writes.get());click("Confirm plaintext re-export")
   compose.waitUntil(30000){compose.onAllNodesWithText("Explicitly confirmed plaintext archive ZIP saved.").fetchSemanticsNodes().isNotEmpty()}
   assertEquals("PK",f.provider.file("output").inputStream().use{String(it.readNBytes(2))});assertArrayEquals(original,f.provider.file("archive").readBytes())
   assertEquals(1,f.provider.writes.get());assertTrue(f.provider.deleted.none{it=="archive"})
  }finally{original.fill(0)}
 }}
 @Test fun lockDuringBorrowedNativeRenderRejectsAndRecyclesUnpublishedBitmap()=borrowedNativeRender("Lock Vault")
 @Test fun hideDuringBorrowedNativeRenderRejectsAndRecyclesUnpublishedBitmap()=borrowedNativeRender("Hide Content")
 @Test fun panicDuringBorrowedNativeRenderRejectsAndRecyclesUnpublishedBitmap()=borrowedNativeRender("Panic Exit")
 private fun borrowedNativeRender(action:String){Harness(seed=true).use{f->
  lateinit var controller:EvidenceController
  instrumentation.runOnMainSync{controller=checkNotNull(f.owner.newController(f.context))}
  compose.waitUntil(15000){controller.state.value.catalog!=null&&!controller.state.value.busy}
  f.provider.file("archive").writeBytes(f.archiveBytes())
  val entered=CountDownLatch(1);val release=CountDownLatch(1);val rendered=java.util.concurrent.atomic.AtomicReference<android.graphics.Bitmap?>(null)
  var visible by mutableStateOf(true)
  try {
   compose.setContent{CompositionLocalProvider(LocalContext provides f.context,LocalEvidenceSecurityActions provides EvidenceSecurityActions(f.owner::lockVault,f.owner::panicExit,f.owner::hideContent)){
    PrivateGalleryTheme(AppTheme.DARK){if(visible)EvidenceArchiveDialog(controller,f.provider.uri("archive"),null,null,
     renderReport={context,bytes,index,current->
      // Run the real proxy-FD/PdfRenderer first, then hold its borrowed return boundary.
      // Retirement must not wipe the borrowed report or publish this late native bitmap.
      val result=VaultPdfPreview.page(context,bytes,index,current);rendered.set(result.first);entered.countDown()
      try{check(release.await(15,TimeUnit.SECONDS));result}catch(failure:Throwable){result.first.recycle();throw failure}
     },onPlaintextRequest={fail("No re-export consent")},onClose={visible=false})}
   }}
   f.authenticate();assertTrue(entered.await(30,TimeUnit.SECONDS));assertFalse(rendered.get()!!.isRecycled)
   compose.onAllNodesWithContentDescription("Evidence security controls").onLast().performClick()
   val before=android.os.SystemClock.elapsedRealtime();click(action)
   assertTrue("Retirement must not wait on borrowed native report",android.os.SystemClock.elapsedRealtime()-before<2000)
   assertFalse(f.owner.sameAuthentication());compose.onNodeWithContentDescription("Authenticated evidence PDF report").assertDoesNotExist()
   release.countDown();compose.waitUntil(15000){rendered.get()?.isRecycled==true}
   compose.waitUntil(15000){File(f.context.noBackupFilesDir,"evidence-open-primary").listFiles().orEmpty().isEmpty()}
   assertTrue(f.provider.file("archive").isFile);assertTrue(f.provider.deleted.isEmpty())
  }finally{release.countDown();instrumentation.runOnMainSync{controller.close()}}
 }}
 @Test fun lockClearsAuthenticatedNativeReportViewAndCipherStage()=activeReport("Lock Vault")
 @Test fun hideClearsAuthenticatedNativeReportViewAndCipherStage()=activeReport("Hide Content")
 @Test fun panicClearsAuthenticatedNativeReportViewAndCipherStage()=activeReport("Panic Exit")
 private fun activeReport(action:String){Harness(seed=true).use{f->
  f.show();f.openArchive();f.waitReport()
  var flags=0
  instrumentation.runOnMainSync{flags=(android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull{it.isShown}?.layoutParams as? android.view.WindowManager.LayoutParams)?.flags?:0}
  assertTrue("Topmost report dialog must use a secure Android window",(flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0)
  compose.onAllNodesWithContentDescription("Evidence security controls").onLast().performClick();click(action)
  compose.waitUntil(15000){!f.owner.sameAuthentication()&&compose.onAllNodesWithContentDescription("Authenticated evidence PDF report").fetchSemanticsNodes().isEmpty()}
  compose.waitUntil(15000){File(f.context.noBackupFilesDir,"evidence-open-primary").listFiles().orEmpty().isEmpty()}
  assertTrue(f.provider.file("archive").isFile);assertTrue(f.provider.deleted.isEmpty())
 }}
 @Test fun archiveReexportRequiresPasswordAgainAndChangedCiphertextNeverPublishesReport() {Harness(seed=true).use{f->
  f.show();f.openArchive();f.waitReport()
  click("Export readable plaintext ZIP…");click("Confirm plaintext re-export")
  assertEquals("CreateDocument",f.picker.contract)
  // Re-encryption of the identical catalogue is still a different ciphertext and is rejected.
  val freshAccess=EvidenceAccess.primary(f.context,f.key,{f.key},{true},{f.key.copyOf()},Any())
  try {val c=freshAccess.store.read();ByteArrayOutputStream().use{output->EvidenceArchive.write(EvidenceExportPlan(c,c.incidents().map{it.id()},emptyList(),EvidenceEgress.digest(c),false),freshAccess.reader(),PASSWORD.toCharArray(),output);f.provider.file("archive").writeBytes(output.toByteArray())}}finally{freshAccess.close()}
  f.returnFromPicker(f.provider.uri("output"))
  compose.onNodeWithContentDescription("Authenticated evidence PDF report").assertDoesNotExist();assertEmptyArchivePassword()
  assertEquals(0,f.provider.writes.get());f.authenticate()
  compose.waitUntil(45000){compose.onAllNodesWithText("No report was published.",substring=true).fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithContentDescription("Authenticated evidence PDF report").assertDoesNotExist();assertEquals(0,f.provider.writes.get());assertTrue(f.provider.file("archive").isFile)
 }}
 @Test fun archiveReexportRejectsSameDocumentAliasBeforeTruncateAndNeverDeletesIncomingArchive()=protectedDestination("tree")
 @Test fun archiveReexportRejectsExactIncomingUriBeforeTruncateAndCleanup()=protectedDestination("same")
 @Test fun archiveReexportFailsClosedWhenProviderIdentityCannotBeEstablished()=protectedDestination("unknown")
 private fun protectedDestination(kind:String){Harness(seed=true).use{f->
  f.show();f.openArchive();f.waitReport();val original=f.provider.file("archive").readBytes()
  try {
   click("Export readable plaintext ZIP…");click("Confirm plaintext re-export")
   val destination=when(kind){"tree"->f.provider.uri("archive-alias");"same"->f.provider.uri("archive");else->Uri.parse("content://evidence179.fixture/opaque/output")}
   f.returnFromPicker(destination);f.authenticate()
   compose.waitUntil(45000){compose.onAllNodesWithText("Confirm plaintext re-export").fetchSemanticsNodes().isNotEmpty()}
   click("Confirm plaintext re-export")
   compose.waitUntil(15000){compose.onAllNodesWithText("Plaintext copy failed.",substring=true).fetchSemanticsNodes().isNotEmpty()}
   assertEquals(0,f.provider.writes.get());assertArrayEquals(original,f.provider.file("archive").readBytes())
   compose.onAllNodesWithContentDescription("Evidence security controls").onLast().performClick();click("Lock Vault")
   compose.waitUntil(15000){!f.owner.sameAuthentication()}
   instrumentation.waitForIdleSync();assertTrue(f.provider.deleted.isEmpty());assertArrayEquals(original,f.provider.file("archive").readBytes())
  }finally{original.fill(0)}
 }}
 @Test fun wrongOriginatingOwnerIsRejectedBeforeArchiveReportPublication() {Harness(seed=true).use{f->
  f.show();val source=f.accesses.last();val c=source.store.read();val foreign=EvidenceCatalog(EvidenceCatalog.Owner.HIDDEN,c.revision(),c.evidence(),c.incidents(),c.audit(),c.analyses(),c.rejectedSuggestions())
  val delegate=source.reader();val reader=object:EvidenceSourceReader{
   override val owner=EvidenceCatalog.Owner.HIDDEN
   override fun current()=delegate.current()
   override fun inspect(sourceId:String)=delegate.inspect(sourceId)
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,java.io.InputStream)->T)=delegate.readVerified(sourceId,consume)
  }
  ByteArrayOutputStream().use{output->EvidenceArchive.write(EvidenceExportPlan(foreign,foreign.incidents().map{it.id()},emptyList(),EvidenceEgress.digest(foreign),false),reader,PASSWORD.toCharArray(),output);f.provider.file("archive").writeBytes(output.toByteArray())}
  click("Open encrypted export");f.returnFromPicker(f.provider.uri("archive"));f.authenticate()
  compose.waitUntil(45000){compose.onAllNodesWithText("No report was published.",substring=true).fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithContentDescription("Authenticated evidence PDF report").assertDoesNotExist();assertEquals(0,f.provider.writes.get());assertTrue(f.provider.file("archive").isFile)
 }}
 @Test fun lockWhileArchiveProviderReadIsActiveCancelsAndPreventsReportPublication()=blockedArchive("Lock Vault")
 @Test fun hideWhileArchiveProviderReadIsActiveCancelsAndPreventsReportPublication()=blockedArchive("Hide Content")
 @Test fun panicWhileArchiveProviderReadIsActiveCancelsAndPreventsReportPublication()=blockedArchive("Panic Exit")
 private fun blockedArchive(action:String){Harness(seed=true).use{f->
  f.show();f.provider.file("archive").writeBytes(f.archiveBytes());f.provider.blockReads=true
  click("Open encrypted export");f.returnFromPicker(f.provider.uri("archive"));f.authenticate()
  assertTrue(f.provider.readEntered.await(15,TimeUnit.SECONDS))
  compose.onAllNodesWithContentDescription("Evidence security controls").onLast().performClick();val before=android.os.SystemClock.elapsedRealtime();click(action)
  assertTrue(android.os.SystemClock.elapsedRealtime()-before<2000)
  compose.waitUntil(15000){f.provider.cancellations.get()>0&&f.provider.closes.get()>0}
  compose.onNodeWithContentDescription("Authenticated evidence PDF report").assertDoesNotExist();assertTrue(f.provider.file("archive").isFile);assertTrue(f.provider.deleted.isEmpty());assertFalse(f.owner.sameAuthentication())
 }}
 companion object{private const val PASSWORD="synthetic179 archive password"}
}
