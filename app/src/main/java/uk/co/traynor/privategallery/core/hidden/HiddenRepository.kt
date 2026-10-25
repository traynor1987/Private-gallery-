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
    private val appContext = context.applicationContext
    private val resolver = context.contentResolver
    private val root = File(context.noBackupFilesDir,"hidden-media-v1")
    private val records = HiddenRecordStore(root, directorySync)

    fun items(): List<VaultItem> = operation { op -> VaultPresentationStore(root,"hidden",op.key()).decorate(snapshot(op).items.filter {it.state==VaultItemState.COMPLETE}).sortedByDescending {it.addedAtEpochMillis ?: it.importedAtEpochMillis} }
    fun presentationSort():String? = operation {op -> VaultPresentationStore(root,"hidden",op.key()).sort()}
    fun presentationSort(value:String) = operation {op -> VaultPresentationStore(root,"hidden",op.key(),{commit->session.commit(op){commit()}}).sort(value)}
    fun storageSummary() = operation {op ->
        uk.co.traynor.privategallery.core.media.VaultStorageStats.calculate(root,snapshot(op).items,File(appContext.noBackupFilesDir,"camera-hidden-v1")) {records.file("media",it.id)}
    }
    fun adjustMediaDate(id:String,timestamp:Long?,zone:String?) = operation {op ->
        completeItem(id,op)
        VaultPresentationStore(root,"hidden",op.key(),{commit->session.commit(op){commit()}}).adjustDate(id,timestamp,zone)
    }
    fun collections(): List<VaultCollection> = operation {op -> snapshot(op).collections}
    fun recentlyDeleted(): List<VaultItem> = operation {op -> snapshot(op).items.filter {it.state==VaultItemState.TRASHED}}
    fun favouriteCollection(): VaultCollection? = operation {op -> val s=snapshot(op);s.collections.firstOrNull {it.id==s.favouriteCollectionId}}
    fun itemsInCollection(id:String):List<VaultItem> = operation {op -> VaultPresentationStore(root,"hidden",op.key()).decorate(VaultCollectionsState(snapshot(op)).itemsIn(id))}
    fun createCollection(name:String):VaultCollection = mutate {state -> val next=state.create(name.take(100));next to next.collections.last()}
    fun renameCollection(id:String,name:String) = mutate {state -> state.rename(id,name.take(100)) to Unit}
    fun deleteCollection(id:String) = mutate {state -> state.deleteCollection(id) to Unit}
    fun setFavouriteCollection(id:String) = mutate {state -> state.setFavourite(id) to Unit}
    fun addItemsToCollection(id:String,items:List<String>) = mutate {state -> state.addItems(id,items) to Unit}
    fun removeItemsFromCollection(id:String,items:List<String>) = mutate {state -> items.fold(state){s,item -> s.removeItem(id,item)} to Unit}

    fun findTransfer(reference:String):VaultItem? = operation {op -> snapshot(op).items.singleOrNull {it.sourceUri==reference}}

    fun import(uri:Uri):VaultItem = operation {op ->
        checkCurrent(op)
        require(uri.scheme=="content") {"Only selected Android media is supported"}
        val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor ->
            if(cursor.moveToFirst()) cursor.getString(0) else null
        }?.take(200) ?: "Imported media"
        val mime=resolver.getType(uri) ?: ""
        val captured=uk.co.traynor.privategallery.core.gallery.MediaCaptureDate.read(appContext,uri,mime)
        checkNotNull(resolver.openInputStream(uri)).use {importMedia(name,mime,it,capturedAtEpochMillis=captured)}
    }
    /** Does not retain source URIs or request deletion. At most one bounded import executes at a time. */
    fun importMedia(name:String,mime:String,input:InputStream,createDistinctCopy:Boolean=false,
        origin:MediaOrigin=MediaOrigin.IMPORTED,sourceReference:String?=null,vaultOnly:Boolean=false,
        validateDownloadedMedia:((MediaDataSource,Long)->Unit)?=null,capturedAtEpochMillis:Long?=null):VaultItem = operation {op ->
        require(mime.startsWith("image/") || mime.startsWith("video/")) {"Unsupported media type"}
        val current=snapshot(op)
        if (!records.file("metadata",METADATA_ID).exists()) save(current,op)
        check(current.items.size<10_000) {"Hidden item limit reached"}
        var thumb:ByteArray?=null
        val staged=mutableListOf<File>()
        val id=UUID.randomUUID().toString()
        try {
            val media=HiddenChunkStore.stage(root,id,input,op.key()) { checkCurrent(op) }
            staged+=media.file
            val item=VaultItem(id,mime,name.take(200),capturedAtEpochMillis ?: System.currentTimeMillis(),media.size,media.hash,media.nonce,VaultItemState.COMPLETE,sourceUri=sourceReference,origin=origin,vaultOnly=vaultOnly)
            // Validate authenticated encrypted staging before either deduplication or publication.
            if(validateDownloadedMedia!=null) chunkReader(item,media.file,op).use {reader ->
                val source=object:MediaDataSource() {
                    override fun getSize():Long {checkCurrent(op);return reader.size}
                    override fun close()=Unit
                    override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
                        checkCurrent(op)
                        return try {reader.readAt(position,buffer,offset,size).also {checkCurrent(op)}}
                        catch(failure:Throwable){buffer.fill(0,offset,offset+size);throw failure}
                    }
                }
                validateDownloadedMedia(source,media.size)
                checkCurrent(op)
            }
            val duplicate=current.items.firstOrNull {it.state==VaultItemState.COMPLETE && MessageDigest.isEqual(it.plaintextSha256,media.hash)}
            if(duplicate!=null && !createDistinctCopy) return@operation duplicate
            thumb=chunkReader(item,media.file,op).use { thumbnail(it,mime,512) }
            staged+=records.stage("thumbnail",id,thumb,op.key())
            checkCurrent(op)
            publishIfAllowed { session.commit(op) {
                records.promote(staged[0],"media",id)
                records.promote(staged[1],"thumbnail",id)
            } }
            records.syncPromotions()
            VaultPresentationStore(root,"hidden",op.key(),{commit->session.commit(op){commit()}}).record(item.id,System.currentTimeMillis(),capturedAtEpochMillis,current.items.mapTo(hashSetOf()){it.id})
            save(current.copy(items=current.items+item),op)
            VaultPresentationStore(root,"hidden",op.key()).decorate(listOf(item)).single()
        } finally { thumb?.fill(0);staged.forEach {it.delete()} }
    }
    fun importEditedCopy(parentId:String,bytes:ByteArray,ai:uk.co.traynor.privategallery.core.editor.AiEditProvenance?):VaultItem = operation {op ->
        val parent=VaultPresentationStore(root,"hidden",op.key()).decorate(listOf(completeItem(parentId,op))).single()
        require(parent.mimeType.startsWith("image/"))
        require(bytes.isNotEmpty() && bytes.size<=uk.co.traynor.privategallery.core.editor.PhotoRenderer.MAX_SOURCE_BYTES)
        val origin=when {
            ai?.processing==uk.co.traynor.privategallery.core.editor.AiProcessing.ON_DEVICE -> MediaOrigin.LOCAL_AI_EDIT
            ai!=null -> MediaOrigin.REMOTE_AI_EDIT
            parent.origin in setOf(MediaOrigin.REMOTE_AI_EDIT,MediaOrigin.LOCAL_AI_EDIT) -> parent.origin
            else -> MediaOrigin.LOCAL_EDIT
        }
        importMedia("edited-photo.png","image/png",java.io.ByteArrayInputStream(bytes),true,origin,
            "editedFrom:$parentId"+(ai?.let {"|ai:${it.providerId}|model:${it.modelId.orEmpty()}"} ?: ""),true,capturedAtEpochMillis=parent.capturedAtEpochMillis)
    }
    fun importGeneratedImage(bytes:ByteArray,modelId:String):VaultItem {
        require(bytes.isNotEmpty() && bytes.size<=32*1024*1024 && modelId.length in 1..160)
        return importMedia("generated-image.png","image/png",java.io.ByteArrayInputStream(bytes),true,
            MediaOrigin.REMOTE_AI_GENERATED,"ai:replicate|model:$modelId",true)
    }
    /** Full-resolution compressed source, bounded to the same editor limit as Primary. */
    fun readForEditing(id:String,cancelled:()->Boolean):ByteArray = operation {op ->
        val item=completeItem(id,op)
        require(item.mimeType.startsWith("image/") && item.plaintextSize<=uk.co.traynor.privategallery.core.editor.PhotoRenderer.MAX_SOURCE_BYTES)
        check(!cancelled()) {"Editing cancelled"}
        val plain=if(chunked(item)) chunkReader(item,records.file("media",id),op).use {
            readBounded(it.stream(),uk.co.traynor.privategallery.core.editor.PhotoRenderer.MAX_SOURCE_BYTES) {check(!cancelled());checkCurrent(op)}
        } else read(id)
        try {
            check(!cancelled());checkCurrent(op)
            check(plain.size.toLong()==item.plaintextSize && MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(plain),item.plaintextSha256))
            plain
        } catch(failure:Throwable) {plain.fill(0);throw failure}
    }
    private fun completeItem(id:String,op:HiddenSession.Operation)=snapshot(op).items.singleOrNull {it.id==id && it.state==VaultItemState.COMPLETE} ?: error("Hidden media unavailable")
    private fun chunked(item:VaultItem)=item.payloadNonce.any {it.toInt()!=0}
    private fun chunkReader(item:VaultItem,file:File,op:HiddenSession.Operation)=HiddenChunkStore.Reader(file,item.id,item.plaintextSize,item.plaintextSha256,item.payloadNonce,op.key()) {checkCurrent(op)}
    fun read(id:String):ByteArray = operation {op ->
        val item=completeItem(id,op)
        check(item.plaintextSize<=HiddenRecordStore.MAX_MEDIA_BYTES) {"Use streaming access for large Hidden media"}
        val plain=if(chunked(item))chunkReader(item,records.file("media",id),op).use { readBounded(it.stream(),HiddenRecordStore.MAX_MEDIA_BYTES) } else records.read("media",id,op.key())
        try {
            check(plain.size.toLong()==item.plaintextSize && MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(plain),item.plaintextSha256)) {"Hidden media verification failed"}
            checkCurrent(op);plain
        } catch(failure:Throwable) {plain.fill(0);throw failure}
    }
    /** Bounded presentation only; originals remain encrypted at full resolution. */
    fun readForDisplay(id:String):ByteArray = operation {op ->
        val item=completeItem(id,op)
        if(!chunked(item) || item.plaintextSize<=HiddenRecordStore.MAX_MEDIA_BYTES) return@operation read(id)
        require(item.mimeType.startsWith("image/"))
        chunkReader(item,records.file("media",id),op).use { thumbnail(it,item.mimeType,2048) }
    }
    fun verify(id:String) = operation {op ->
        val item=completeItem(id,op)
        if(chunked(item)) chunkReader(item,records.file("media",id),op).use {it.verify()}
        else read(id).fill(0)
    }
    /** Each read owns and retires its own key and plaintext; no key/cache lives in a playback session. */
    fun readRange(id:String,position:Long,target:ByteArray,offset:Int,length:Int):Int = try { operation {op ->
        val item=completeItem(id,op)
        try {
            if(chunked(item)) chunkReader(item,records.file("media",id),op).use {it.readAt(position,target,offset,length)}
            else {val bytes=read(id);try {if(position>=bytes.size) -1 else minOf(length,bytes.size-position.toInt()).also {bytes.copyInto(target,offset,position.toInt(),position.toInt()+it)}}finally {bytes.fill(0)}}
        } catch(failure:Throwable) {target.fill(0,offset,offset+length);throw failure}
    }
    } catch(failure:Throwable) {target.fill(0,offset,offset+length);throw failure}
    fun mediaSize(id:String):Long=operation {op ->completeItem(id,op).plaintextSize}
    fun preview(id:String):Bitmap = operation {op ->
        check(snapshot(op).items.any {it.id==id && it.state==VaultItemState.COMPLETE}) {"Hidden media unavailable"}
        val bytes=records.read("thumbnail",id,op.key())
        try {
            val bitmap=checkNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size)) {"Hidden preview unavailable"}
            try {checkCurrent(op);bitmap} catch(failure:Throwable){bitmap.recycle();throw failure}
        } finally {bytes.fill(0)}
    }
    /** Scoped original stream; EOF authenticates the complete length and digest before publication. */
    internal fun <T> withTransferSource(id:String, cancelled:()->Boolean, consume:(VaultItem,InputStream)->T):T = operation {op ->
        val item=VaultPresentationStore(root,"hidden",op.key()).decorate(listOf(completeItem(id,op))).single()
        fun readSource(input:InputStream):T {
            var size=0L;var verified=false
            val digest=MessageDigest.getInstance("SHA-256")
            val source=object:InputStream() {
                override fun read():Int {val b=ByteArray(1);return try {if(read(b,0,1)<0)-1 else b[0].toInt() and 255} finally {b.fill(0)}}
                override fun read(b:ByteArray,off:Int,len:Int):Int {
                    checkCurrent(op);check(!cancelled()) {"Transfer cancelled"}
                    if(len==0)return 0
                    val count=input.read(b,off,len)
                    if(count>0){size+=count;check(size<=item.plaintextSize);digest.update(b,off,count)}
                    if(count<0 && !verified){check(size==item.plaintextSize && MessageDigest.isEqual(digest.digest(),item.plaintextSha256));verified=true}
                    checkCurrent(op);check(!cancelled());return count
                }
            }
            return consume(item,source).also {check(verified) {"Hidden source was not fully verified"}}
        }
        if(chunked(item)) chunkReader(item,records.file("media",id),op).use {reader ->reader.stream().use {readSource(it)}}
        else {val bytes=read(id);try {java.io.ByteArrayInputStream(bytes).use {readSource(it)}} finally {bytes.fill(0)}}
    }
    /** Recoverable removal, only for the exact authenticated source that was transferred. */
    internal fun trashVerifiedSource(expected:VaultItem) = operation {op ->
        val current=completeItem(expected.id,op)
        check(current.plaintextSize==expected.plaintextSize && MessageDigest.isEqual(current.plaintextSha256,expected.plaintextSha256)
            && MessageDigest.isEqual(current.payloadNonce,expected.payloadNonce)) {"Hidden source changed"}
        trash(listOf(current.id))
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
    private fun encode(s:VaultIndexSnapshot):JSONObject = JSONObject().put("domain","hidden").put("version",2)
        .put("items",JSONArray(s.items.map {i -> JSONObject().put("id",i.id).put("mime",i.mimeType).put("name",i.displayName).put("time",i.importedAtEpochMillis).put("size",i.plaintextSize).put("hash",android.util.Base64.encodeToString(i.plaintextSha256,android.util.Base64.NO_WRAP)).put("nonce",android.util.Base64.encodeToString(i.payloadNonce,android.util.Base64.NO_WRAP)).put("origin",i.origin.name).put("vaultOnly",i.vaultOnly).put("source",i.sourceUri ?: "").put("trash",i.state==VaultItemState.TRASHED).put("deleted",i.deletedAtEpochMillis ?: 0)}))
        .put("collections",JSONArray(s.collections.map {c -> JSONObject().put("id",c.id).put("name",c.name).put("time",c.createdAtEpochMillis)}))
        .put("memberships",JSONArray(s.memberships.map {m -> JSONObject().put("collection",m.collectionId).put("item",m.vaultItemId).put("time",m.addedAtEpochMillis)}))
        .put("favourite",s.favouriteCollectionId ?: "")
    private fun decode(json:JSONObject):VaultIndexSnapshot {
        check(json.getString("domain")=="hidden" && json.getInt("version") in 1..2) {"Unsupported Hidden metadata"}
        val a=json.getJSONArray("items");check(a.length()<=10_000)
        val items=List(a.length()){n -> val i=a.getJSONObject(n);val id=i.getString("id");records.file("media",id)
            val hash=android.util.Base64.decode(i.getString("hash"),android.util.Base64.NO_WRAP);check(hash.size==32)
            val nonce=if(i.has("nonce")) android.util.Base64.decode(i.getString("nonce"),android.util.Base64.NO_WRAP) else ByteArray(12);check(nonce.size==12)
            val size=i.getLong("size");check(size in 1..(if(nonce.any {it.toInt()!=0}) HiddenChunkStore.MAX_BYTES else HiddenRecordStore.MAX_MEDIA_BYTES.toLong()))
            VaultItem(id,i.getString("mime"),i.getString("name"),i.getLong("time"),size,hash,nonce,if(i.getBoolean("trash")) VaultItemState.TRASHED else VaultItemState.COMPLETE,sourceUri=i.optString("source").takeIf {it.isNotEmpty()},origin=MediaOrigin.valueOf(i.optString("origin",MediaOrigin.IMPORTED.name)),vaultOnly=i.optBoolean("vaultOnly",false),deletedAtEpochMillis=i.getLong("deleted").takeIf {it>0})}
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
        private fun readBounded(input:InputStream,max:Int,checkAllowed:()->Unit={}):ByteArray {
            val output=WipingBuffer();val buffer=ByteArray(64*1024)
            try {while(true){checkAllowed();val count=input.read(buffer);if(count<0)break;check(count>0) {"Import stalled"};check(output.size().toLong()+count<=max) {"Bounded Hidden read limit exceeded"};output.write(buffer,0,count)};return output.toByteArray().also {check(it.isNotEmpty())}}
            finally {buffer.fill(0);output.wipe()}
        }
        private fun thumbnail(reader:HiddenChunkStore.Reader,mime:String,maxDimension:Int):ByteArray {
            val bitmap=if(mime.startsWith("image/")) {
                val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                reader.stream().use {BitmapFactory.decodeStream(it,null,bounds)}
                check(bounds.outWidth>0 && bounds.outHeight>0)
                var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxDimension)sample*=2
                reader.stream().use {checkNotNull(BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample}))}
            } else MediaMetadataRetriever().let {retriever ->
                try {retriever.setDataSource(object:MediaDataSource(){override fun getSize()=reader.size;override fun close()=Unit
                    override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int)=reader.readAt(position,buffer,offset,size)})
                    if(android.os.Build.VERSION.SDK_INT>=27) checkNotNull(retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,maxDimension,maxDimension))
                    else { // API26 has no bounded frame extraction. Keep playback; use a bounded placeholder.
                        checkNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
                        Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
                    }
                } finally {retriever.release()}
            }
            val oriented=try {if(mime.startsWith("image/")) {
                val orientation=try {reader.stream().use {android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}}
                    catch(_:IOException){reader.verify();1} // Valid image formats without EXIF retain normal orientation; corruption still fails.
                val matrix=android.graphics.Matrix()
                when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)}
                try {Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)}catch(failure:Throwable){bitmap.recycle();throw failure}
            }else bitmap} catch(failure:Throwable){bitmap.recycle();throw failure}
            if(oriented!==bitmap)bitmap.recycle()
            return try {ByteArrayOutputStream().use {check(oriented.compress(if(maxDimension>512 && oriented.hasAlpha())Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,90,it));it.toByteArray()}} finally {oriented.recycle()}
        }
    }
}
