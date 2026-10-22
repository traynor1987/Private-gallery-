package uk.co.traynor.privategallery.core.hidden

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import uk.co.traynor.privategallery.core.vault.*

/** Journal precedes insertion, so even death between insert and URI recording is recoverable.
 * Recovery deletes only this app's exact random pending names. Published media is never removed.
 * No source deletion is replayed after restart. */
internal class HiddenPhoneExport(private val context:Context,private val syncOutput:(java.io.FileOutputStream)->Unit={it.fd.sync()}) {
    private val resolver=context.contentResolver
    private val journal=context.getSharedPreferences("hidden-pending-exports-v1",Context.MODE_PRIVATE)
    private fun collection(video:Boolean)=if(video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    fun reconcile() = synchronized(gate) {
        if(Build.VERSION.SDK_INT<29)return@synchronized
        journal.all.forEach { (name,value) ->
            if(!name.matches(Regex("pg-restore-[a-f0-9-]+")) || value !is Boolean)return@forEach
            val base=collection(value)
            val ids=checkNotNull(resolver.query(base,arrayOf(MediaStore.MediaColumns._ID),
                "(${MediaStore.MediaColumns.DISPLAY_NAME}=? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?) AND ${MediaStore.MediaColumns.IS_PENDING}=1 AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=?",
                arrayOf(name,"$name.%",context.applicationContext.packageName),null)).use {cursor ->
                buildList {while(cursor.moveToNext())add(cursor.getLong(0))}
            }
            ids.forEach {id ->check(resolver.delete(android.content.ContentUris.withAppendedId(base,id),null,null)==1) {"Pending export cleanup failed"}}
            check(journal.edit().remove(name).commit()) {"Export journal cleanup failed"}
        }
    }
    fun export(item:VaultItem,source:InputStream,checkActive:()->Unit,publish:((()->Unit)->Unit)):Uri = synchronized(gate) {
        require(Build.VERSION.SDK_INT>=29) {"Safe Phone Gallery restore requires Android 10 or later"}
        VaultEgressPolicy.requireAllowed(item,VaultEgress.RESTORE)
        reconcile();checkActive()
        val video=item.mimeType.startsWith("video/")
        require(video || item.mimeType.startsWith("image/"))
        val name="pg-restore-${UUID.randomUUID()}"
        check(journal.edit().putBoolean(name,video).commit()) {"Cannot record pending export"}
        var uri:Uri?=null
        var published=false
        val buffer=ByteArray(64*1024)
        try {
            uri=checkNotNull(resolver.insert(collection(video),ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,name)
                put(MediaStore.MediaColumns.MIME_TYPE,item.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH,if(video) "Movies/Private Gallery" else "Pictures/Private Gallery")
                put(MediaStore.MediaColumns.IS_PENDING,1)
            })) {"Phone Gallery destination unavailable"}
            val target=uri
            val descriptor=checkNotNull(resolver.openFileDescriptor(target,"w"))
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use {output ->
                while(true) {checkActive();val count=source.read(buffer);if(count<0)break;check(count>0);output.write(buffer,0,count)}
                output.flush();syncOutput(output)
            }
            val digest=MessageDigest.getInstance("SHA-256");var size=0L
            checkNotNull(resolver.openInputStream(target)).use {input ->
                while(true){checkActive();val count=input.read(buffer);if(count<0)break;check(count>0);size+=count;check(size<=item.plaintextSize);digest.update(buffer,0,count)}
            }
            check(size==item.plaintextSize && MessageDigest.isEqual(digest.digest(),item.plaintextSha256)) {"Phone Gallery verification failed"}
            publish {
                checkActive()
                check(resolver.update(target,ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME,item.displayName.replace('/','_').replace('\\','_').take(200))
                    put(MediaStore.MediaColumns.IS_PENDING,0)
                },null,null)==1) {"Phone Gallery publication failed"}
                published=true
            }
            // If journal cleanup fails, later recovery cannot match this now-published entry.
            journal.edit().remove(name).commit()
            target
        } catch(failure:Throwable) {
            if(!published) {
                val removed=uri?.let {runCatching {resolver.delete(it,null,null)==1}.getOrDefault(false)} ?: false
                if(removed)journal.edit().remove(name).commit()
            }
            throw failure
        } finally {buffer.fill(0)}
    }
    companion object {private val gate=Any()}
}
