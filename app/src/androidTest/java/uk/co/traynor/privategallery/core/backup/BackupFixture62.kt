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
 fun call(method:String,arg:String?=null,extras:Bundle?=null):Bundle=checkNotNull(context.contentResolver.call(authority,"fixture-$method",arg,extras))
 fun create(mode:String="normal"):Uri=checkNotNull(call("create",mode).getParcelable("uri",Uri::class.java))
 fun mode(uri:Uri,value:String){call("mode",uri.lastPathSegment,Bundle().apply{putString("mode",value)})}
 fun delete(uri:Uri){call("delete",uri.lastPathSegment)}
 fun stats()=call("stats")
}
internal class BackupFile62(val file:File):BackupProvider {override val label="Synthetic local ciphertext";override fun read()=FileInputStream(file);override fun write()=FileOutputStream(file)}
