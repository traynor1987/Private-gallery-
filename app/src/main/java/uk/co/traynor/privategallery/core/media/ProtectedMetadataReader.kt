@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.media

import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.datasource.DataSpec
import uk.co.traynor.privategallery.core.vault.ProtectedVideoSession

/** Read-only random access through the existing revocable encrypted video session. No plaintext file. */
internal object ProtectedMetadataReader {
 fun read(session:ProtectedVideoSession,size:Long,allowed:()->Boolean):List<Pair<String,String>> {
  val source=object:MediaDataSource() {
   private val data=session.sourceFactory.createDataSource()
   private var next=-1L
   @Synchronized override fun readAt(position:Long,buffer:ByteArray,offset:Int,count:Int):Int {
    if(!allowed())throw java.io.IOException("Vault closed")
    require(position>=0 && offset>=0 && count>=0 && offset<=buffer.size-count)
    if(count==0)return 0
    if(position>=size)return -1
    if(next!=position){data.close();data.open(DataSpec.Builder().setUri(Uri.parse("vault://metadata")).setPosition(position).build());next=position}
    val n=data.read(buffer,offset,minOf(count.toLong(),size-position).toInt())
    if(n>0)next+=n
    return n
   }
   override fun getSize()=size
   @Synchronized override fun close(){data.close()}
  }
  val retriever=MediaMetadataRetriever()
  return try {retriever.setDataSource(source);fields(retriever).also {check(allowed()) {"Vault closed"}}}finally{retriever.release();source.close();session.close()}
 }
 fun fields(reader:MediaMetadataRetriever):List<Pair<String,String>> = buildList {
  val width=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
  val height=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
  if(width!=null&&height!=null)add("Resolution" to "$width × $height")
  reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {add("Duration" to "${it/1000.0} seconds")}
  reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.let {add("Frame rate" to "$it fps")}
  reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let {add("GPS location" to it)}
  reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let {add("Container date" to it)}
 }
}
