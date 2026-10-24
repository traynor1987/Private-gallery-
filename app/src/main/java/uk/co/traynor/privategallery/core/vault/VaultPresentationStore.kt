package uk.co.traynor.privategallery.core.vault

import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONObject
import uk.co.traynor.privategallery.core.crypto.VaultCipher
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader

/** Additive authenticated metadata only. Existing payloads and index formats are never rewritten. */
internal class VaultPresentationStore(root:File,domain:String,private val key:ByteArray,private val publish:((()->Unit)->Unit)={it()}) {
 private val file=AtomicFile(File(root,"presentation-v1.enc"))
 private val aad="private-gallery/presentation/v1/$domain".toByteArray(Charsets.UTF_8)
 init {require(domain=="primary"||domain=="hidden")}
 private fun load():JSONObject {
  if(!file.baseFile.exists())return JSONObject().put("version",1).put("dates",JSONObject())
  val size=file.baseFile.length();check(size in 28..2_097_152){"Invalid presentation metadata"}
  val sealed=file.openRead().use {it.readBytes()};val output=ByteArrayOutputStream()
  return try {
   VaultCipher.decrypt(ByteArrayInputStream(sealed,12,sealed.size-12),output,key,aad,EncryptionHeader(sealed.copyOf(12)))
   val plain=output.toByteArray()
   try {JSONObject(plain.toString(Charsets.UTF_8)).also {check(it.getInt("version")==1);check(it.getJSONObject("dates").length()<=20_000)}} finally {plain.fill(0)}
  } finally {sealed.fill(0);output.reset()}
 }
 private fun save(json:JSONObject) {
  file.baseFile.parentFile!!.let {check(it.isDirectory||it.mkdirs())}
  val plain=json.toString().toByteArray(Charsets.UTF_8);val output=ByteArrayOutputStream()
  try {
   val header=VaultCipher.encrypt(ByteArrayInputStream(plain),output,key,aad)
   val staged=File(file.baseFile.parentFile,".presentation-${java.util.UUID.randomUUID()}")
   try {
    java.io.FileOutputStream(staged).use {stream->stream.write(header.nonce);stream.write(output.toByteArray());stream.fd.sync()}
    publish {java.nio.file.Files.move(staged.toPath(),file.baseFile.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)}
    java.nio.channels.FileChannel.open(file.baseFile.parentFile!!.toPath(),java.nio.file.StandardOpenOption.READ).use {it.force(true)}
   } finally {staged.delete()}
  } finally {plain.fill(0);output.reset()}
 }
 fun record(id:String,added:Long,captured:Long?) {
  require(uk.co.traynor.privategallery.core.ui.MediaOrder.valid(added)>0);val json=load();val dates=json.getJSONObject("dates");check(dates.length()<20_000||dates.has(id))
  dates.put(id,JSONObject().put("added",added).put("captured",captured?.takeIf {uk.co.traynor.privategallery.core.ui.MediaOrder.valid(it)>0} ?: 0));save(json)
 }
 fun decorate(items:List<VaultItem>):List<VaultItem> {
  val dates=runCatching {load().getJSONObject("dates")}.getOrElse {return items}
  return items.map {item->val data=dates.optJSONObject(item.id)
   item.copy(addedAtEpochMillis=data?.optLong("added")?.takeIf {uk.co.traynor.privategallery.core.ui.MediaOrder.valid(it)>0},
    capturedAtEpochMillis=data?.optLong("captured")?.takeIf {uk.co.traynor.privategallery.core.ui.MediaOrder.valid(it)>0} ?: legacyCapture(item))
  }
 }
 fun sort():String?=runCatching {load().optString("sort").takeIf {it.isNotBlank()}}.getOrNull()
 fun sort(value:String){val json=load();json.put("sort",value);save(json)}
 companion object {
  fun legacyCapture(item:VaultItem):Long?=item.sourceUri?.takeIf {it.startsWith("capture:v1|")}?.split('|')?.getOrNull(2)?.toLongOrNull()?.takeIf {uk.co.traynor.privategallery.core.ui.MediaOrder.valid(it)>0}
 }
}
