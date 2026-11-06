package uk.co.traynor.privategallery.core.backup

import android.content.Context
import java.io.File
import java.net.URI
import java.util.UUID
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.privateapps.*

/** Definitions only. Never touches a browser profile, session, cookie, route or network connection. */
internal class BackupApps(private val context:Context,private val access:EvidenceAccess){
 private fun <T> store(action:(PrivateAppStore)->T):T=access.leases.acquire().use{lease->
  access.checkCurrent()
  val store=if(access.owner==EvidenceCatalog.Owner.PRIMARY)
   PrimaryPrivateAppStore(File(context.filesDir,"private-apps"),lease.key(),{access.current()&&lease.current()},{commit->lease.commit{commit()}})
  else {
   val session=checkNotNull(access.hiddenSession)
   HiddenPrivateAppStore(context.noBackupFilesDir,session,session.authority(),false,{access.current()&&lease.current()},{commit->lease.commit{commit.run()}})
  }
  action(store).also{access.checkCurrent()}
 }
 private fun address(value:String):String {
  if(value.isEmpty())return ""
  val uri=URI(value);require(uri.userInfo==null)
  return "${uri.scheme}://${uri.rawAuthority}${uri.rawPath.orEmpty()}"
 }
 fun snapshot():ByteArray=store{source->PrivateAppCatalog(false,source.read().apps.map{app->
  PrivateAppCatalog.App(app.id(),app.name(),address(app.startUrl()),address(app.scope()),app.display(),app.icon(),app.pinned(),app.addressBar(),app.requireAuthentication(),app.appStyle(),"")
 }).encode()}
 fun validate(bytes:ByteArray)=PrivateAppCatalog.decode(bytes,false).also{catalog->catalog.apps.forEach{require(address(it.startUrl())==it.startUrl()&&address(it.scope())==it.scope())}}
 fun homeBinding(bytes:ByteArray):Pair<Map<String,String>,org.json.JSONArray> {
  val incoming=validate(bytes)
  return store{source->
   val current=source.read().apps
   val ids=incoming.apps.mapNotNull{old->current.firstOrNull{it.startUrl()==old.startUrl()}?.let{"web:${old.id()}" to "web:${it.id()}"}}.toMap()
   val apps=org.json.JSONArray(current.map{app->org.json.JSONObject().put("id","web:${app.id()}").put("name",app.name()).put("url",app.startUrl()).put("classification","Website Shortcut")})
   ids to apps
  }
 }
 fun restore(bytes:ByteArray):Int {
  val incoming=validate(bytes);var added=0
  store{destination->destination.update{old->
   var next=old
   incoming.apps.forEach{app->if(next.apps.none{it.startUrl()==app.startUrl()}){
    next=next.upsert(PrivateAppCatalog.App(UUID.randomUUID().toString(),app.name(),app.startUrl(),app.scope(),app.display(),app.icon(),app.pinned(),app.addressBar(),app.requireAuthentication(),app.appStyle(),""));added++
   }}
   next
  }}
  return added
 }
}
