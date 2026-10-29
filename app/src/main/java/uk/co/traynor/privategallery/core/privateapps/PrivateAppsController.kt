package uk.co.traynor.privategallery.core.privateapps

/** No keys or WebViews. Every operation stays bound to the original authenticated browser. */
class PrivateAppsController(
 val owner:PrivateAppStore.Owner,
 private val authorized:()->Boolean,
 private val command:(Int,ByteArray?,String)->PrivateAppCatalog,
 private val navigate:(String)->Unit,
) {
 fun isCurrent():Boolean=runCatching{authorized()}.getOrDefault(false)
 private fun execute(action:Int,payload:ByteArray?=null,id:String=""):PrivateAppCatalog {
  try {check(isCurrent());return command(action,payload,id).also{check(isCurrent())}}
  finally{payload?.fill(0)}
 }
 fun load()=execute(0)
 fun save(app:PrivateAppCatalog.App)=execute(1,PrivateAppCatalog(owner==PrivateAppStore.Owner.HIDDEN_TOR,listOf(app)).encode())
 fun remove(id:String)=execute(2,id=id)
 /** Resolve the ID again: a stale UI cannot supply an arbitrary old URL or removed entry. Main-thread only. */
 fun launch(id:String) {
  val app=load().apps.single{it.id()==id}
  check(!app.requireAuthentication()){ "Per-open authentication is not available in this checkpoint" }
  check(isCurrent());navigate(app.startUrl())
 }
}
