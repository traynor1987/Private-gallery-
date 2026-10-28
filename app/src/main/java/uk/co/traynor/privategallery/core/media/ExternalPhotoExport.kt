package uk.co.traynor.privategallery.core.media

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import uk.co.traynor.privategallery.core.hidden.HiddenPhoneExport
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultItemState

/** Explicit public fallback only. Reuses verified pending-row publication and crash cleanup. */
internal object ExternalPhotoExport {
 fun toGallery(context:Context,file:File,allowed:()->Boolean,publish:((()->Unit)->Unit),beforePublish:(Uri)->Unit={}):Uri {
  fun active(){check(allowed()) {"Originating vault locked"}}
  active()
  require(file.canonicalFile.parentFile==File(context.cacheDir,"external-edit").canonicalFile)
  check(ExternalEditLease.allowed(file,System.currentTimeMillis())) {"Temporary copy expired"}
  val digest=MessageDigest.getInstance("SHA-256")
  val buffer=ByteArray(64*1024)
  try {file.inputStream().use {input ->while(true){active();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}}finally{buffer.fill(0)}
  val item=VaultItem(UUID.randomUUID().toString(),"image/png","Photo-edit-${UUID.randomUUID()}.png",System.currentTimeMillis(),file.length(),digest.digest(),byteArrayOf(),VaultItemState.COMPLETE)
  return file.inputStream().use {input ->HiddenPhoneExport(context).export(item,input,::active,{commit ->publish {active();commit()}},beforePublish)}
 }
 fun reconcile(context:Context)=HiddenPhoneExport(context).reconcile()
}
