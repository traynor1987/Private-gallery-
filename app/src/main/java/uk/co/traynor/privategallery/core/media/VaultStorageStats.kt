package uk.co.traynor.privategallery.core.media
import java.io.File
import uk.co.traynor.privategallery.core.vault.VaultItem

data class VaultStorageSummary(
 val totalBytes:Long,val photoBytes:Long,val videoBytes:Long,val thumbnailBytes:Long,val temporaryBytes:Long,val otherBytes:Long,
 val photoCount:Int,val videoCount:Int,val largest:List<VaultItem>,val duplicates:List<List<VaultItem>>,
)
/** Receives exactly one authenticated repository root. Never traverses the app data directory. */
object VaultStorageStats {
 fun calculate(root:File,items:List<VaultItem>,payload:(VaultItem)->File):VaultStorageSummary {
  val files=root.walkTopDown().filter {it.isFile}.toList()
  val photoFiles=items.filter {it.mimeType.startsWith("image/")}.map(payload).toSet()
  val videoFiles=items.filter {it.mimeType.startsWith("video/")}.map(payload).toSet()-photoFiles
  val thumbFiles=files.filter {it.name.startsWith("thumbnail-")||it.parentFile==File(root,"previews")}.toSet()-photoFiles-videoFiles
  val temporary=files.filter {it.name.startsWith("staged-")||it.parentFile?.name=="staging"||it.name.endsWith(".new")}.toSet()-photoFiles-videoFiles-thumbFiles
  val photos=photoFiles.sumOf {if(it.isFile)it.length() else 0L};val videos=videoFiles.sumOf {if(it.isFile)it.length() else 0L}
  val thumbs=thumbFiles.sumOf {it.length()};val temp=temporary.sumOf {it.length()};val total=files.sumOf {it.length()}
  val ordered=items.sortedWith(compareByDescending<VaultItem>{it.plaintextSize}.thenBy {it.id})
  val duplicates=items.filter {it.plaintextSha256.size==32&&it.plaintextSize>=0}.groupBy {it.plaintextSize to it.plaintextSha256.joinToString(""){b->"%02x".format(b)}}.values.filter {it.size>1}.map {it.sortedBy {v->v.id}}
  return VaultStorageSummary(total,photos,videos,thumbs,temp,(total-photos-videos-thumbs-temp).coerceAtLeast(0),items.count {it.mimeType.startsWith("image/")},items.count {it.mimeType.startsWith("video/")},ordered.take(10),duplicates)
 }
}
