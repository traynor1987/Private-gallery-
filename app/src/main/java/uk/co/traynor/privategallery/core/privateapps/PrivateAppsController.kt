package uk.co.traynor.privategallery.core.privateapps

/** No keys or WebViews. Every operation stays bound to the original authenticated browser. */
class PrivateAppsController(
 val owner:PrivateAppStore.Owner,
 private val authorized:()->Boolean,
 private val command:(Int,ByteArray?,String)->PrivateAppCatalog,
 private val navigate:(String)->Unit,
 private val discoverWebsite:((String)->PrivateAppCatalog.App)?=null,
 private val onLaunchApp:((PrivateAppCatalog.App)->Unit)?=null,
) {
 fun isCurrent():Boolean=runCatching{authorized()}.getOrDefault(false)
 private fun execute(action:Int,payload:ByteArray?=null,id:String=""):PrivateAppCatalog {
  try {check(isCurrent());return command(action,payload,id).also{check(isCurrent())}}
  finally{payload?.fill(0)}
 }
 fun discover(url:String):PrivateAppCatalog.App {
  check(isCurrent());require(PrivateAppDiscovery.safe(url,owner==PrivateAppStore.Owner.HIDDEN_TOR))
  return checkNotNull(discoverWebsite){"Discovery unavailable"}.invoke(url).also{check(isCurrent())}
 }
 fun load()=execute(PrivateAppCommands.READ)
 fun save(app:PrivateAppCatalog.App)=execute(PrivateAppCommands.UPSERT,PrivateAppCatalog(owner==PrivateAppStore.Owner.HIDDEN_TOR,listOf(app)).encode())
 fun remove(id:String)=execute(PrivateAppCommands.REMOVE,id=id)
 fun setPinned(id:String,pinned:Boolean)=execute(if(pinned)PrivateAppCommands.PIN else PrivateAppCommands.UNPIN,id=id)
 fun setAddressBar(id:String,visible:Boolean)=execute(if(visible)PrivateAppCommands.SHOW_ADDRESS else PrivateAppCommands.HIDE_ADDRESS,id=id)
 fun move(id:String,delta:Int):PrivateAppCatalog {
  require(delta==-1 || delta==1){"Invalid move"}
  return execute(if(delta==-1)PrivateAppCommands.MOVE_UP else PrivateAppCommands.MOVE_DOWN,id=id)
 }
 /** Resolve the ID again: a stale UI cannot supply an arbitrary old URL or removed entry. Main-thread only. */
 fun launch(id:String) {
  val app=load().apps.single{it.id()==id}
  check(!app.requireAuthentication()){ "Per-open authentication is not available in this checkpoint" }
  check(isCurrent());if(onLaunchApp!=null)onLaunchApp.invoke(app) else navigate(app.startUrl())
 }
}
