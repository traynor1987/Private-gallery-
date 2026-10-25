package uk.co.traynor.privategallery.core.media

import android.content.Context
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.vault.*

/** Caller owns/wipes bytes and keys. Origin is explicit; there is no fallback between domains. */
internal object ExternalEditImport {
 fun primary(context:Context,key:ByteArray,parent:String,bytes:ByteArray,replace:Boolean,
             allowed:()->Boolean,publish:((()->Unit)->Unit)):VaultItem {
  check(allowed());val r=AndroidVaultRepository(context,key);val original=r.items().single {it.id==parent}
  val copy=r.importAiEditedCopy(original.id,bytes,null,true,publishIfAllowed=publish){!allowed()}
  r.verifyTransferDestination(copy){!allowed()};check(allowed())
  if(replace)r.replaceVerifiedEditedSource(original,copy,publish)
  return copy
 }
 fun hidden(context:Context,session:HiddenSession,parent:String,bytes:ByteArray,replace:Boolean,allowed:()->Boolean):VaultItem {
  check(allowed());val r=HiddenRepository(context,session,publishIfAllowed={commit ->check(allowed());commit()})
  val original=r.items().single {it.id==parent};val copy=r.importEditedCopy(original.id,bytes,null)
  r.verify(copy.id);check(allowed());if(replace)r.replaceVerifiedEditedSource(original,copy)
  return copy
 }
}
