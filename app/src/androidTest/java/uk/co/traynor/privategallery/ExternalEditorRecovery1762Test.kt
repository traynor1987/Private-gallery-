package uk.co.traynor.privategallery

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.media.ExternalEditRecoveryStore

/** Real Activity recreation and new disk-store instances; synthetic keys, no owner credentials. */
class ExternalEditorRecovery1762Test {
 private fun state(a:MainActivity)=ViewModelProvider(a)[ProtectedSessionState::class.java]
 private fun invoke(a:MainActivity,name:String,vararg args:Any?):Any? = MainActivity::class.java.declaredMethods.single {it.name==name}.apply {isAccessible=true}.invoke(a,*args)
 private fun ticket(a:MainActivity)=invoke(a,"getExternalTicket")
 @Test fun recreationLocksThenFreshOriginalHiddenAuthenticationRecoversCancelledReceipt() {
  var id=""
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->
   scenario.onActivity {a ->
    val s=state(a);assertTrue(s.hidden.session.complete(s.hidden.session.beginAuthentication(),ByteArray(32){23}));s.hidden.select()
    a.openExternalEdit("recovery-original",s.hidden.session)
    val t=checkNotNull(ticket(a));id=t.javaClass.getDeclaredMethod("getRecoveryId").apply {isAccessible=true}.invoke(t) as String
    invoke(a,"persistExternalReceipt",t,android.net.Uri.parse("content://media/external/images/media/999999"))
    invoke(a,"clearExternalEdit")
    assertTrue(ExternalEditRecoveryStore(a.noBackupFilesDir,"hidden").ids().contains(id))
    a.openExternalEdit("recovery-original",s.hidden.session)
    val reopened=checkNotNull(ticket(a))
    assertEquals("Cancel and reopen must reuse the durable cleanup receipt",id,reopened.javaClass.getDeclaredMethod("getRecoveryId").apply {isAccessible=true}.invoke(reopened))
    assertEquals("content://media/external/images/media/999999",reopened.javaClass.getDeclaredMethod("getExportDestination").apply {isAccessible=true}.invoke(reopened).toString())
    assertEquals(1,ExternalEditRecoveryStore(a.noBackupFilesDir,"hidden").ids().count {it==id})
    invoke(a,"clearExternalEdit")
   }
   scenario.recreate()
   scenario.onActivity {a ->
    val s=state(a);assertFalse(s.hidden.session.isUnlocked);assertFalse(s.session.isUnlocked);assertNull(ticket(a))
    s.key=ByteArray(32){25};s.session.unlock();invoke(a,"recoverExternalReceipt")
    assertNull("Primary cannot recover Hidden receipt",ticket(a));s.key?.fill(0);s.key=null;s.session.lock()
    assertTrue(s.hidden.session.complete(s.hidden.session.beginAuthentication(),ByteArray(32){23}));s.hidden.select()
    invoke(a,"recoverExternalReceipt")
    val recovered=checkNotNull(ticket(a));assertEquals("recovery-original",recovered.javaClass.getDeclaredMethod("getOriginal").apply {isAccessible=true}.invoke(recovered))
    ExternalEditRecoveryStore(a.noBackupFilesDir,"hidden").acknowledge(id)
    invoke(a,"clearExternalEdit");s.hidden.leave()
   }
  }
 }
 @Test fun repeatedGalleryFallbackReusesPublishedCopyAndNeverPreparesAnotherExport() {
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {a ->
   val s=state(a);assertTrue(s.hidden.session.complete(s.hidden.session.beginAuthentication(),ByteArray(32){23}));s.hidden.select()
   a.openExternalEdit("repeat-export-origin",s.hidden.session);val t=checkNotNull(ticket(a))
   val receipt=t.javaClass.getDeclaredMethod("getRecoveryId").apply {isAccessible=true}.invoke(t) as String
   val bytes=png();val file=uk.co.traynor.privategallery.core.media.ExternalEditLease.create(java.io.File(a.cacheDir,"external-edit"),bytes,System.currentTimeMillis())
   var uri:android.net.Uri?=null
   try {
    uri=uk.co.traynor.privategallery.core.media.ExternalPhotoExport.toGallery(a,file,{true},{it()}) {published ->invoke(a,"persistExternalReceipt",t,published)}
    t.javaClass.getDeclaredMethod("setExportDestination",android.net.Uri::class.java).apply {isAccessible=true}.invoke(t,uri)
    // No prepared file or readable source exists in the ticket. Re-export would start preparation.
    repeat(2) {invoke(a,"exportExternalGallery",t)}
    assertEquals(false,t.javaClass.getDeclaredMethod("getWorking").apply {isAccessible=true}.invoke(t))
    assertEquals(uri,t.javaClass.getDeclaredMethod("getExportDestination").apply {isAccessible=true}.invoke(t))
    assertEquals(uri.toString(),ExternalEditRecoveryStore(a.noBackupFilesDir,"hidden").read(receipt,ByteArray(32){23}).exportUri)
   } finally {
    uri?.let {a.contentResolver.delete(it,null,null)};file.delete();bytes.fill(0)
    ExternalEditRecoveryStore(a.noBackupFilesDir,"hidden").acknowledge(receipt);invoke(a,"clearExternalEdit");s.hidden.leave()
   }
  }}
 }
 private fun png():ByteArray {
  val bitmap=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888)
  return try {java.io.ByteArrayOutputStream().use {out ->check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));out.toByteArray()}}finally{bitmap.recycle()}
 }
 @Test fun committedHiddenImportWithoutReceiptUpdateRecoversOnceAndReportsRetainedOriginal()=committedImport(false)
 @Test fun committedHiddenReplacementWithoutReceiptUpdateRecoversOnceAndReportsTrashedOriginal()=committedImport(true)
 private fun committedImport(replace:Boolean) {
  var receipt="";var original="";var imported="";val bytes=png()
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->
   try {
    scenario.onActivity {a ->
     val s=state(a);assertTrue(s.hidden.session.complete(s.hidden.session.beginAuthentication(),ByteArray(32){23}));s.hidden.select()
     val repo=uk.co.traynor.privategallery.core.hidden.HiddenRepository(a,s.hidden.session)
     original=repo.importMedia("recovery.png","image/png",java.io.ByteArrayInputStream(bytes),true).id
     a.openExternalEdit(original,s.hidden.session);val t=checkNotNull(ticket(a))
     receipt=t.javaClass.getDeclaredMethod("getRecoveryId").apply {isAccessible=true}.invoke(t) as String
     invoke(a,"persistExternalReceipt",t,null)
     // Stop at the exact production boundary: encrypted index committed; Activity receipt is still unsaved.
     imported=uk.co.traynor.privategallery.core.media.ExternalEditImport.hidden(a,s.hidden.session,original,bytes,replace,{s.hidden.session.isUnlocked},"external-edit:v1|$receipt|$replace").id
     assertFalse(ExternalEditRecoveryStore(a.noBackupFilesDir,"hidden").read(receipt,ByteArray(32){23}).saved)
    }
    scenario.recreate()
    scenario.onActivity {a ->
     val s=state(a);assertTrue(s.hidden.session.complete(s.hidden.session.beginAuthentication(),ByteArray(32){23}));s.hidden.select()
     invoke(a,"recoverExternalReceipt");val t=checkNotNull(ticket(a))
     assertEquals(true,t.javaClass.getDeclaredMethod("getSaved").apply {isAccessible=true}.invoke(t))
     assertEquals(imported,t.javaClass.getDeclaredMethod("getImportedId").apply {isAccessible=true}.invoke(t))
     val repo=uk.co.traynor.privategallery.core.hidden.HiddenRepository(a,s.hidden.session)
     assertEquals(1,repo.items().count {it.sourceUri=="external-edit:v1|$receipt|$replace"})
     assertEquals(replace,repo.recentlyDeleted().any {it.id==original})
     repo.verify(imported)
     val retry=uk.co.traynor.privategallery.core.media.ExternalEditImport.hidden(a,s.hidden.session,original,bytes,!replace,{s.hidden.session.isUnlocked},"external-edit:v1|$receipt|${!replace}")
     assertEquals("Retry reuses verified marker even if selection changes",imported,retry.id)
     assertEquals(replace,repo.recentlyDeleted().any {it.id==original})
    }
   } finally {
    scenario.onActivity {a ->
     val s=state(a)
     if(s.hidden.session.isUnlocked) {
      val repo=uk.co.traynor.privategallery.core.hidden.HiddenRepository(a,s.hidden.session)
      val ids=listOf(original,imported).filter {it.isNotEmpty()};repo.trash(ids)
      ids.forEach {id ->if(repo.recentlyDeleted().any {it.id==id})repo.deletePermanently(id)}
     }
     if(receipt.isNotEmpty())ExternalEditRecoveryStore(a.noBackupFilesDir,"hidden").acknowledge(receipt)
     invoke(a,"clearExternalEdit");s.hidden.leave()
    }
   }
  }
  bytes.fill(0)
 }

}
