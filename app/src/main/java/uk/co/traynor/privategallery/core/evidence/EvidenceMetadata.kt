package uk.co.traynor.privategallery.core.evidence

import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import uk.co.traynor.privategallery.core.vault.VaultDocumentPolicy
import uk.co.traynor.privategallery.core.vault.VaultItem

/** Metadata claims are source-labelled. Missing filesystem times are not replaced by vault import dates. */
internal object EvidenceMetadata {
 fun supported(mime:String)=mime.lowercase() in setOf("image/jpeg","image/png","image/webp","image/gif","image/bmp","image/heic","image/heif","image/avif") || VaultDocumentPolicy.isDocument(mime.lowercase())
 fun fromItem(item:VaultItem):EvidenceCatalog.Metadata = EvidenceCatalog.Metadata(item.displayName,0,0,
  item.addedAtEpochMillis ?: item.importedAtEpochMillis,item.capturedAtEpochMillis ?: 0,0,0,
  mapOf("sourceNameProvenance" to "Existing encrypted vault catalogue name; acquisition filename may differ",
   "dateAddedProvenance" to "Vault catalogue/app clock, not original filesystem creation",
   "captureTimeProvenance" to if(item.capturedAtEpochMillis!=null)"Existing vault capture claim/app clock; not independently verified" else "Unavailable",
   "filesystemCreationTime" to "Unavailable","vaultImportedAtMillis" to item.importedAtEpochMillis.toString()),item.vaultOnly)
 /** Retain only bounded acquisition claims from this exact verified imported original, never a URI. */
 fun acquisition(verified:EvidenceOriginal,acquired:EvidenceOriginal):EvidenceCatalog.Metadata {
  EvidenceAccess.requireSameOriginal(acquired.item,verified.item)
  val base=verified.metadata;val claims=acquired.metadata
  val labels=claims.embedded().filterKeys{it=="sourceNameProvenance"||it=="fileModifiedProvenance"}.mapValues{it.value.take(200)}
  return EvidenceCatalog.Metadata(claims.sourceName().take(200),0,claims.fileModifiedAt().coerceAtLeast(0),
   base.dateAddedAt(),base.captureAt(),base.width(),base.height(),base.embedded()+labels,base.vaultOnly())
 }
 fun extract(item:VaultItem,bytes:ByteArray,base:EvidenceCatalog.Metadata=fromItem(item)):EvidenceCatalog.Metadata {
  val embedded=base.embedded().toMutableMap();var width=base.width();var height=base.height()
  if(item.mimeType.startsWith("image/")) {
   val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
   runCatching{BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)}
   if(options.outWidth>0&&options.outHeight>0){width=options.outWidth;height=options.outHeight;embedded["dimensionsProvenance"]="Encoded image dimensions"}
   runCatching {
    ExifInterface(ByteArrayInputStream(bytes)).let{exif ->
     for(tag in listOf("DateTimeOriginal","DateTimeDigitized","DateTime","OffsetTimeOriginal","SubSecTimeOriginal","Orientation","Make","Model","Software","ImageDescription","GPSLatitude","GPSLatitudeRef","GPSLongitude","GPSLongitudeRef")) {
      exif.getAttribute(tag)?.takeIf{it.isNotBlank()}?.let{embedded["embedded.$tag"]=it.take(512)}
     }
    }
   }.onFailure{embedded["embeddedMetadataStatus"]="Unavailable or unsupported; no timestamp inferred"}
   embedded["embeddedTimeProvenance"]="Embedded claims only; no implicit timezone/date conversion"
  } else embedded["documentMetadataStatus"]="Inert original; readable text/page extraction recorded separately"
  return EvidenceCatalog.Metadata(base.sourceName(),base.fileCreatedAt(),base.fileModifiedAt(),base.dateAddedAt(),base.captureAt(),width,height,embedded,base.vaultOnly())
 }
}
