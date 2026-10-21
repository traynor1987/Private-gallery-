package uk.co.traynor.privategallery.core.hidden

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.vault.*

/** All persistence is Hidden-only and authenticated by an actual HiddenSession, never a UI flag. */
internal class HiddenRepository(context: Context, private val session: HiddenSession,
    directorySync: (File) -> Unit = ::syncDirectory,
    private val publishIfAllowed: ((() -> Unit) -> Unit) = { it() },
) {
    private val authority = session.authority()
    private val resolver = context.contentResolver
    private val root = File(context.noBackupFilesDir,"hidden-media-v1")
    private val records = HiddenRecordStore(root, directorySync)

    fun items(): List<VaultItem> = operation { op -> snapshot(op).items.filter {it.state==VaultItemState.COMPLETE}.sortedByDescending {it.importedAtEpochMillis} }
    fun collections(): List<VaultCollection> = operation {op -> snapshot(op).collections}
    fun recentlyDeleted(): List<VaultItem> = operation {op -> snapshot(op).items.filter {it.state==VaultItemState.TRASHED}}
    fun favouriteCollection(): VaultCollection? = operation {op -> val s=snapshot(op);s.collections.firstOrNull {it.id==s.favouriteCollectionId}}
    fun itemsInCollection(id:String):List<VaultItem> = operation {op -> VaultCollectionsState(snapshot(op)).itemsIn(id)}
    fun createCollection(name:String):VaultCollection = mutate {state -> val next=state.create(name.take(100));next to next.collections.last()}
    fun renameCollection(id:String,name:String) = mutate {state -> state.rename(id,name.take(100)) to Unit}
    fun deleteCollection(id:String) = mutate {state -> state.deleteCollection(id) to Unit}
    fun setFavouriteCollection(id:String) = mutate {state -> state.setFavourite(id) to Unit}
    fun addItemsToCollection(id:String,items:List<String>) = mutate {state -> state.addItems(id,items) to Unit}
    fun removeItemsFromCollection(id:String,items:List<String>) = mutate {state -> items.fold(state){s,item -> s.removeItem(id,item)} to Unit}

    fun import(uri:Uri):VaultItem = operation {op ->
        checkCurrent(op)
        require(uri.scheme=="content") {"Only selected Android media is supported"}
        val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor ->
            if(cursor.moveToFirst()) cursor.getString(0) else null
        }?.take(200) ?: "Imported media"
        checkNotNull(resolver.openInputStream(uri)).use {importMedia(name,resolver.getType(uri) ?: "",it)}
    }
    /** Does not retain source URIs or request deletion. At most one bounded import executes at a time. */
    fun importMedia(name:String,mime:String,input:InputStream):VaultItem = operation {op ->
        require(mime.startsWith("image/") || mime.startsWith("video/")) {"Unsupported media type"}
        val current=snapshot(op)
        if (!records.file("metadata",METADATA_ID).exists()) save(current,op)
        check(current.items.size<10_000) {"Hidden item limit reached"}
        val bytes=readBounded(input,HiddenRecordStore.MAX_MEDIA_BYTES)
        var thumb:ByteArray?=null
        val staged=mutableListOf<File>()
        val id=UUID.randomUUID().toString()
        try {
            val digest=MessageDigest.getInstance("SHA-256").digest(bytes)
            val duplicate=current.items.firstOrNull {it.state==VaultItemState.COMPLETE && MessageDigest.isEqual(it.plaintextSha256,digest)}
            if(duplicate!=null) return@operation duplicate
            thumb=thumbnail(bytes,mime)
            staged+=records.stage("media",id,bytes,op.key())
            checkCurrent(op)
            staged+=records.stage("thumbnail",id,thumb,op.key())
            checkCurrent(op)
            val item=VaultItem(id,mime,name.take(200),System.currentTimeMillis(),bytes.size.toLong(),digest,ByteArray(12),VaultItemState.COMPLETE)
            // Payloads are immutable. The authenticated metadata record is the final commit point.
            publishIfAllowed { session.commit(op) {
                records.promote(staged[0],"media",id)
                records.promote(staged[1],"thumbnail",id)
            } }
            records.syncPromotions() // Payload durability precedes the metadata commit. No session monitor held.
            save(current.copy(items=current.items+item),op)
            item
        } finally {
            bytes.fill(0);thumb?.fill(0);staged.forEach {it.delete()}
            // A sync error after metadata rename has an uncertain commit outcome.
            // Retain promoted ciphertext; authenticated reconcile can remove only proven orphans.
        }
    }
    fun read(id:String):ByteArray = operation {op ->
        val item=snapshot(op).items.singleOrNull {it.id==id && it.state==VaultItemState.COMPLETE} ?: error("Hidden media unavailable")
        val plain=records.read("media",id,op.key())
        try {
            check(plain.size.toLong()==item.plaintextSize && MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(plain),item.plaintextSha256)) {"Hidden media verification failed"}
            checkCurrent(op)
            plain
        } catch(failure:Throwable) {plain.fill(0);throw failure}
    }
    fun preview(id:String):Bitmap = operation {op ->
        check(snapshot(op).items.any {it.id==id && it.state==VaultItemState.COMPLETE}) {"Hidden media unavailable"}
        val bytes=records.read("thumbnail",id,op.key())
        try {
            val bitmap=checkNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size)) {"Hidden preview unavailable"}
            try {checkCurrent(op);bitmap} catch(failure:Throwable){bitmap.recycle();throw failure}
        } finally {bytes.fill(0)}
    }
    fun trash(ids:List<String>) = operation {op ->
        val s=snapshot(op)
        save(s.copy(items=s.items.map {if(it.id in ids && it.state==VaultItemState.COMPLETE) it.copy(state=VaultItemState.TRASHED,deletedAtEpochMillis=System.currentTimeMillis()) else it}),op)
    }
    fun restore(id:String) = operation {op ->
        val s=snapshot(op);check(s.items.any {it.id==id && it.state==VaultItemState.TRASHED})
        save(s.copy(items=s.items.map {if(it.id==id) it.copy(state=VaultItemState.COMPLETE,deletedAtEpochMillis=null) else it}),op)
    }
    fun deletePermanently(id:String) = operation {op ->
        val s=snapshot(op);check(s.items.any {it.id==id && it.state==VaultItemState.TRASHED})
        // Commit logical deletion first. A failed file deletion leaves ciphertext, never a dangling record.
        save(VaultCollectionsState(s).removeVaultItem(id).asSnapshot(),op)
        session.commit(op) {
            val media=records.file("media",id);val thumbnail=records.file("thumbnail",id)
            check((!media.exists() || media.delete()) && (!thumbnail.exists() || thumbnail.delete())) {"Encrypted cleanup pending"}
        }
    }
    fun reconcile() = operation {op ->
        val s=snapshot(op) // Never clean files if authenticated metadata is corrupt.
        records.clearStaging()
        s.items.forEach {check(records.file("media",it.id).isFile && records.file("thumbnail",it.id).isFile) {"Hidden media needs recovery; records retained"}}
        val expected=s.items.flatMap {listOf(records.file("media",it.id).name,records.file("thumbnail",it.id).name)}.toSet()+records.file("metadata",METADATA_ID).name
        root.listFiles()?.filter {it.name.endsWith(".enc") && it.name !in expected}?.forEach {orphan -> session.commit(op) {check(orphan.delete()) {"Encrypted cleanup pending"}}}
        s.items.filter {it.state==VaultItemState.TRASHED && RecentlyDeletedPolicy.expired(it.deletedAtEpochMillis,System.currentTimeMillis())}
            .forEach {deletePermanently(it.id)}
    }
    private fun <T> mutate(change:(VaultCollectionsState)->Pair<VaultCollectionsState,T>):T = operation {op ->
        val (next,result)=change(VaultCollectionsState(snapshot(op)));save(next.asSnapshot(),op);result
    }
    private fun <T> operation(block:(HiddenSession.Operation)->T):T = synchronized(gate) {
        session.beginOperation(authority).use {op ->
            checkCurrent(op)
            val result=block(op)
            try {checkCurrent(op);result} catch(failure:Throwable) {
                if(result is ByteArray) result.fill(0)
                if(result is Bitmap) result.recycle()
                throw failure
            }
        }
    }
    private fun checkCurrent(op:HiddenSession.Operation) {check(session.isCurrent(op)) {"Hidden locked"}}
    private fun snapshot(op:HiddenSession.Operation):VaultIndexSnapshot {
        val metadata=records.file("metadata",METADATA_ID)
        if(!metadata.exists()) {
            if(root.exists()) {
                val files=checkNotNull(root.listFiles()) {"Hidden storage could not be read"}
                check(files.none {it.name.endsWith(".enc")}) {"Hidden metadata missing; encrypted records retained"}
            }
            return VaultIndexSnapshot(emptyList())
        }
        val bytes=records.read("metadata",METADATA_ID,op.key())
        try {checkCurrent(op);return decode(JSONObject(bytes.toString(Charsets.UTF_8)))} finally {bytes.fill(0)}
    }
    private fun save(snapshot:VaultIndexSnapshot,op:HiddenSession.Operation) {
        check(snapshot.items.size<=10_000 && snapshot.collections.size<=1000 && snapshot.memberships.size<=100_000) {"Hidden metadata limit reached"}
        val encoded=encode(snapshot)
        decode(encoded) // Same invariants before commit as after restart.
        val bytes=encoded.toString().toByteArray(Charsets.UTF_8)
        var staged:File?=null
        try {val prepared=records.stage("metadata",METADATA_ID,bytes,op.key());staged=prepared;publishIfAllowed { session.commit(op) {records.promote(prepared,"metadata",METADATA_ID)} };records.syncPromotions()}
        finally {bytes.fill(0);staged?.delete()}
    }
    private fun encode(s:VaultIndexSnapshot):JSONObject = JSONObject().put("domain","hidden").put("version",1)
        .put("items",JSONArray(s.items.map {i -> JSONObject().put("id",i.id).put("mime",i.mimeType).put("name",i.displayName).put("time",i.importedAtEpochMillis).put("size",i.plaintextSize).put("hash",android.util.Base64.encodeToString(i.plaintextSha256,android.util.Base64.NO_WRAP)).put("trash",i.state==VaultItemState.TRASHED).put("deleted",i.deletedAtEpochMillis ?: 0)}))
        .put("collections",JSONArray(s.collections.map {c -> JSONObject().put("id",c.id).put("name",c.name).put("time",c.createdAtEpochMillis)}))
        .put("memberships",JSONArray(s.memberships.map {m -> JSONObject().put("collection",m.collectionId).put("item",m.vaultItemId).put("time",m.addedAtEpochMillis)}))
        .put("favourite",s.favouriteCollectionId ?: "")
    private fun decode(json:JSONObject):VaultIndexSnapshot {
        check(json.getString("domain")=="hidden" && json.getInt("version")==1) {"Unsupported Hidden metadata"}
        val a=json.getJSONArray("items");check(a.length()<=10_000)
        val items=List(a.length()){n -> val i=a.getJSONObject(n);val id=i.getString("id");records.file("media",id)
            val hash=android.util.Base64.decode(i.getString("hash"),android.util.Base64.NO_WRAP);check(hash.size==32)
            val size=i.getLong("size");check(size in 1..HiddenRecordStore.MAX_MEDIA_BYTES.toLong())
            VaultItem(id,i.getString("mime"),i.getString("name"),i.getLong("time"),size,hash,ByteArray(12),if(i.getBoolean("trash")) VaultItemState.TRASHED else VaultItemState.COMPLETE,deletedAtEpochMillis=i.getLong("deleted").takeIf {it>0})}
        val c=json.getJSONArray("collections");check(c.length()<=1000)
        val collections=List(c.length()){n -> c.getJSONObject(n).let {VaultCollection(it.getString("id"),it.getString("name"),it.getLong("time"))}}
        val m=json.getJSONArray("memberships");check(m.length()<=100_000)
        val memberships=List(m.length()){n -> m.getJSONObject(n).let {VaultCollectionMembership(it.getString("collection"),it.getString("item"),it.getLong("time"))}}
        val favourite=json.getString("favourite").takeIf {it.isNotEmpty()}
        check(items.all {(it.state==VaultItemState.TRASHED)==(it.deletedAtEpochMillis!=null)}) {"Invalid Hidden deletion metadata"}
        check(items.map {it.id}.distinct().size==items.size && collections.map {it.id}.distinct().size==collections.size)
        check(memberships.all {member -> collections.any {it.id==member.collectionId} && items.any {it.id==member.vaultItemId}})
        check(favourite==null || collections.any {it.id==favourite})
        return VaultIndexSnapshot(items,collections,memberships,favouriteCollectionId=favourite)
    }
    companion object {
        private val gate=Any()
        private const val METADATA_ID="00000000-0000-0000-0000-000000000000"
        private fun syncDirectory(directory:File) {
            val fd=android.system.Os.open(directory.path,android.system.OsConstants.O_RDONLY,0)
            try {android.system.Os.fsync(fd)} finally {android.system.Os.close(fd)}
        }
        private class WipingBuffer:ByteArrayOutputStream() {fun wipe(){buf.fill(0);reset()}}
        private fun readBounded(input:InputStream,max:Int):ByteArray {
            val output=WipingBuffer();val buffer=ByteArray(64*1024)
            try {while(true){val count=input.read(buffer);if(count<0)break;check(count>0) {"Import stalled"};check(output.size().toLong()+count<=max) {"Hidden import limit is 16 MiB"};output.write(buffer,0,count)};return output.toByteArray().also {check(it.isNotEmpty())}}
            finally {buffer.fill(0);output.wipe()}
        }
        private fun thumbnail(bytes:ByteArray,mime:String):ByteArray {
            val bitmap=if(mime.startsWith("image/")) {
                val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                check(bounds.outWidth>0 && bounds.outHeight>0)
                var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>512)sample*=2
                checkNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample}))
            } else MediaMetadataRetriever().let {reader ->
                try {reader.setDataSource(object:MediaDataSource(){override fun getSize()=bytes.size.toLong();override fun close()=Unit
                    override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {if(position<0 || position>=bytes.size)return -1;val count=minOf(size,bytes.size-position.toInt());bytes.copyInto(buffer,offset,position.toInt(),position.toInt()+count);return count}})
                    if(android.os.Build.VERSION.SDK_INT>=27) checkNotNull(reader.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,512,512))
                    else {
                        val frame=checkNotNull(reader.getFrameAtTime(0))
                        val scale=minOf(1f,512f/maxOf(frame.width,frame.height))
                        val scaled=Bitmap.createScaledBitmap(frame,maxOf(1,(frame.width*scale).toInt()),maxOf(1,(frame.height*scale).toInt()),true)
                        if(scaled!==frame) frame.recycle()
                        scaled
                    }
                } finally {reader.release()}
            }
            return try {ByteArrayOutputStream().use {check(bitmap.compress(Bitmap.CompressFormat.JPEG,80,it));it.toByteArray()}} finally {bitmap.recycle()}
        }
    }
}
