package uk.co.traynor.privategallery.core.backup

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.provider.OpenableColumns
import java.io.*
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess

/** Encryption is above the transport boundary; future transports cannot receive plaintext. */
internal interface BackupProvider {
 val label:String
 fun read():InputStream
 fun write():OutputStream
}
internal data class BackupProviderOption(val label:String,val enabled:Boolean)
internal val backupProviders=listOf(BackupProviderOption("Local document (SAF)",true),BackupProviderOption("WebDAV · future",false),BackupProviderOption("S3 · future",false),BackupProviderOption("Self-hosted server · future",false))

internal class SafBackupProvider(private val context:Context,private val uri:Uri,private val access:EvidenceAccess):BackupProvider {
 init {require(uri.scheme=="content"){"Select a document using Android's picker"}}
 override val label:String get()=runCatching{context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0).take(160)else null}}.getOrNull()?:"Selected local document"
 override fun read():InputStream {
  // A DocumentsProvider typed-read fallback can discard CancellationSignal. Backups
  // are binary documents: use the cancellable untyped descriptor API directly.
  access.checkCurrent();val signal=CancellationSignal();val cancel=access.onRetire{signal.cancel()}
  var descriptor:android.os.ParcelFileDescriptor?=null
  try {
   val opened=checkNotNull(context.contentResolver.openFileDescriptor(uri,"r",signal));descriptor=opened
   val stream=android.os.ParcelFileDescriptor.AutoCloseInputStream(opened)
   val retire=access.onRetire{runCatching{stream.close()}}
   access.checkCurrent()
   return object:FilterInputStream(stream){
    override fun read():Int{access.checkCurrent();return super.read().also{access.checkCurrent()}}
    override fun read(bytes:ByteArray,offset:Int,length:Int):Int{access.checkCurrent();return super.read(bytes,offset,length).also{access.checkCurrent()}}
    override fun close(){try{super.close()}finally{retire.close();cancel.close()}}
   }
  }catch(failure:Throwable){cancel.close();signal.cancel();descriptor?.close();throw failure}
 }
 override fun write():OutputStream {
  access.checkCurrent();val signal=CancellationSignal();val cancel=access.onRetire{signal.cancel()}
  var descriptor:android.os.ParcelFileDescriptor?=null
  try {
   val opened=checkNotNull(context.contentResolver.openFileDescriptor(uri,"wt",signal));descriptor=opened
   val stream=android.os.ParcelFileDescriptor.AutoCloseOutputStream(opened)
   val retire=access.onRetire{runCatching{stream.close()}}
   return object:FilterOutputStream(stream){
    override fun write(value:Int){access.checkCurrent();out.write(value);access.checkCurrent()}
    override fun write(bytes:ByteArray,offset:Int,length:Int){access.checkCurrent();out.write(bytes,offset,length);access.checkCurrent()}
    override fun close(){try{super.close()}finally{retire.close();cancel.close()}}
   }
  }catch(failure:Throwable){cancel.close();signal.cancel();descriptor?.close();throw failure}
 }
}
