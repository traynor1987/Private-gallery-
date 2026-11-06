package uk.co.traynor.privategallery.core.backup

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import java.io.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.hidden.*
import uk.co.traynor.privategallery.core.vault.*

internal class BackupFixture62(val root:File, val hidden:Boolean=false, val key:ByteArray=ByteArray(32){42},val primaryGate:Any=Any()):AutoCloseable {
 val base=ApplicationProvider.getApplicationContext<Context>()
 val context=object:ContextWrapper(base){override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"private").apply{mkdirs()};override fun getCacheDir()=File(root,"cache").apply{mkdirs()};override fun getApplicationContext():Context=this}
 val live=AtomicBoolean(true)
 val session=HiddenSession{100L}.also{check(it.complete(it.beginAuthentication(),key.copyOf()))}
 val access=if(hidden)EvidenceAccess.hidden(context,session){live.get()}else EvidenceAccess.primary(context,key,{key},{live.get()},{key.copyOf()},primaryGate)
 val coordinator=BackupCoordinator(context,access)
 val primary=AndroidVaultRepository(context,key)
 val secret=HiddenRepository(context,session)
 fun insert(text:String="original preserved")=if(hidden)secret.importMedia("original.txt","text/plain",ByteArrayInputStream(text.encodeToByteArray()),true)else (primary.importVerified(VaultImportSource("original.txt","text/plain",{ByteArrayInputStream(text.encodeToByteArray())},createDistinctCopy=true)) as ImportResult.Imported).item
 fun items()=if(hidden)secret.items()else primary.items()
 fun read(id:String)=if(hidden)secret.withTransferSource(id,{false}){_,i->i.readBytes()}else primary.withHiddenCopySource(id,{false}){_,i->i.readBytes()}
 fun metadata()=if(hidden)File(context.noBackupFilesDir,"hidden-media-v1/metadata-00000000-0000-0000-0000-000000000000.enc")else File(context.filesDir,"vault/vault-index.enc")
 override fun close(){coordinator.close();access.close();session.lock();key.fill(0)}
 companion object {fun temporary(name:String)=File(ApplicationProvider.getApplicationContext<Context>().cacheDir,"$name-${UUID.randomUUID()}").apply{mkdirs()}}
}
internal object BackupDocuments62 {
 private val context get()=ApplicationProvider.getApplicationContext<Context>()
 private val authority get()=BackupDocumentsFixtureProvider.AUTHORITY
 private val flags=android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
 @Synchronized fun persistedTree():Uri {
  context.contentResolver.persistedUriPermissions.firstOrNull{
   it.uri.authority==authority && android.provider.DocumentsContract.isTreeUri(it.uri) && it.isReadPermission && it.isWritePermission
  }?.let{return it.uri}
  val result=java.util.concurrent.ArrayBlockingQueue<Uri>(1)
  androidx.test.core.app.ActivityScenario.launch(androidx.activity.ComponentActivity::class.java).use{scenario->
   scenario.onActivity{activity->
    val launcher=activity.activityResultRegistry.register("backup-tree62",androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()){uri->
     checkNotNull(uri){"Synthetic document tree selection cancelled"}
     context.contentResolver.takePersistableUriPermission(uri,flags)
     result.add(uri)
    }
    launcher.launch(null)
   }
   BackupDocumentsUi62.folder()
   val uri=checkNotNull(result.poll(15,java.util.concurrent.TimeUnit.SECONDS)){"No document tree result"}
   check(context.contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission&&it.isWritePermission})
   return uri
  }
 }
 fun requirePersistedAccess(uri:Uri){
  check(context.contentResolver.persistedUriPermissions.any{
   it.uri.authority==authority && it.isReadPermission && it.isWritePermission &&
    android.provider.DocumentsContract.isTreeUri(it.uri) &&
    android.provider.DocumentsContract.getTreeDocumentId(it.uri)==android.provider.DocumentsContract.getTreeDocumentId(uri)
  }){"Missing persisted read/write tree grant for $uri"}
 }
 fun call(method:String,arg:String?=null,extras:Bundle?=null):Bundle {
  // Acquire access through the real system picker before connecting to the protected provider.
  persistedTree()
  return checkNotNull(context.contentResolver.call(authority,"fixture-$method",arg,extras))
 }
 fun create(mode:String="normal"):Uri {
  val tree=persistedTree()
  val parent=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,android.provider.DocumentsContract.getTreeDocumentId(tree))
  val uri=checkNotNull(android.provider.DocumentsContract.createDocument(context.contentResolver,parent,"application/octet-stream","Synthetic backup"))
  requirePersistedAccess(uri)
  mode(uri,mode)
  return uri
 }
 fun mode(uri:Uri,value:String){call("mode",uri.lastPathSegment,Bundle().apply{putString("mode",value)})}
 fun delete(uri:Uri){call("delete",uri.lastPathSegment)}
 fun stats()=call("stats")
}
internal class BackupFile62(val file:File):BackupProvider {override val label="Synthetic local ciphertext";override fun read()=FileInputStream(file);override fun write()=FileOutputStream(file)}
