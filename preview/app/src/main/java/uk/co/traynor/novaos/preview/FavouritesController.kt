package uk.co.traynor.novaos.preview
import android.app.Application
import android.view.View
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import org.json.JSONObject
import org.json.JSONTokener

/** Owns bounded state verification for a retained WebView; never owns authentication or messages. */
class FavouritesController(private val app:Application,private val pool:PreviewBrowserModel,private val scope:CoroutineScope){
 private val jobs=mutableMapOf<String,Job>();private val epochs=mutableMapOf<String,Int>()
 private val script by lazy{app.assets.open("chat_favourites_v10.js").bufferedReader().use{it.readText()}}
 fun invalidate(tab:PreviewTab){freeze(tab);epochs[tab.id]=(epochs[tab.id]?:0)+1;jobs.remove(tab.id)?.cancel();tab.favouritesVerified=false;tab.favouritesState="CHECKING";tab.favouritesReason="Checking the native Favourites filter.";hide(tab)}
 private fun freeze(tab:PreviewTab){if(ChatInspectorRules.origin(tab.url)){val token=JSONObject.quote(tab.favouritesToken);runCatching{tab.view.evaluateJavascript("if(window.__novaFav10?.token===$token)window.__novaFav10.freeze();",null)}}}
 private fun hide(tab:PreviewTab){if(pool.friendsRequested(tab)){tab.view.visibility=View.INVISIBLE;tab.view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}}
 fun stop(tab:PreviewTab,normal:Boolean){invalidate(tab);if(normal&&ChatInspectorRules.origin(tab.url)){val token=JSONObject.quote(tab.favouritesToken);runCatching{tab.view.evaluateJavascript("if(window.__novaFav10?.token===$token)window.__novaFav10.stop();",null)}}}
 fun ensure(tab:PreviewTab,recheck:Boolean=false){
  if(!pool.friendsRequested(tab)||!pool.foreground||tab.profile!=pool.currentProfile)return
  if(recheck)invalidate(tab)
  if(jobs[tab.id]?.isActive==true)return
  val epoch=epochs[tab.id]?:0;val revision=tab.navigationRevision
  jobs[tab.id]=scope.launch{
   try{
   var deadline=android.os.SystemClock.uptimeMillis()+30000
   while(isActive&&tab in pool.tabs&&pool.friendsRequested(tab)&&pool.foreground&&tab.profile==pool.currentProfile&&(epochs[tab.id]?:0)==epoch&&tab.navigationRevision==revision){
    if(tab.progress<100){if(android.os.SystemClock.uptimeMillis()>deadline){fail(tab,"COMPATIBILITY_ERROR","The page did not finish loading. Retry when your connection is ready.");break};delay(200);continue}
    if(!ChatInspectorRules.origin(tab.url)){fail(tab,"COMPATIBILITY_ERROR","The retained page is outside the supported WhatsApp Web origin.");break}
    val raw=withTimeoutOrNull(3000){suspendCancellableCoroutine<String?>{c->tab.view.evaluateJavascript(script.replace("__NOVA_TOKEN__",JSONObject.quote(tab.favouritesToken))){if(c.isActive)c.resume(it)}}}
    if(!isActive||tab !in pool.tabs||!pool.friendsRequested(tab)||tab.profile!=pool.currentProfile||(epochs[tab.id]?:0)!=epoch||tab.navigationRevision!=revision)break
    val result=runCatching{require(raw!=null&&raw.length<1600);val decoded=JSONTokener(raw).nextValue() as String;require(decoded.length<800);val o=JSONObject(decoded);require(o.length()==2&&o.has("state")&&o.has("reason"));val state=o.getString("state");val reason=o.getString("reason");require(FavouritesRules.state(state));require(reason in reasons);state to reason}.getOrNull()
    if(result==null){fail(tab,"COMPATIBILITY_ERROR","The filter check returned an unsupported result.");break}
    val (state,reason)=result
    tab.favouritesState=state;tab.favouritesReason=description(reason)
    val active=FavouritesRules.reveal(state,reason,ChatInspectorRules.origin(tab.url))
    tab.favouritesVerified=active
    if(active){deadline=android.os.SystemClock.uptimeMillis()+30000;tab.view.visibility=View.VISIBLE}else hide(tab)
    if(state=="LOGIN_REQUIRED"||state=="COMPATIBILITY_ERROR"){freeze(tab);break}
    if(!active&&android.os.SystemClock.uptimeMillis()>deadline){fail(tab,if(state=="FILTER_UNAVAILABLE")state else "COMPATIBILITY_ERROR",if(state=="FILTER_UNAVAILABLE")description(reason)else "Favourites activation was not confirmed before the checking deadline.");break}
    delay(750)
   }
   }catch(e:CancellationException){throw e}catch(e:Exception){if(tab in pool.tabs&&pool.friendsRequested(tab)&&(epochs[tab.id]?:0)==epoch)fail(tab,"COMPATIBILITY_ERROR","Native filter verification could not complete. The page remains concealed.")}
  }
 }
 fun profileChanged(){pool.tabs.filter{it.profile!=pool.currentProfile&&pool.friendsRequested(it)}.forEach{invalidate(it)}}
 fun maintain(){pool.tabs.filter{pool.friendsRequested(it)}.forEach{if(it.profile!=pool.currentProfile){if(it.favouritesVerified||jobs.containsKey(it.id))invalidate(it)}else if(it.favouritesState in listOf("CHECKING","FAVOURITES_ACTIVE"))ensure(it)}}
 private fun fail(tab:PreviewTab,state:String,reason:String){tab.favouritesVerified=false;tab.favouritesState=state;tab.favouritesReason=reason;freeze(tab);hide(tab)}
 companion object{
  private val reasons=setOf("UNEXPECTED_NAVIGATION","WAITING_INTERFACE","LOGIN_INTERFACE","FILTER_STRUCTURE_UNRECOGNISED","SELECTED_STATE_UNAVAILABLE","SELECTED","ACTIVATION_PENDING","CONFLICTING_SELECTION","ACTIVATION_NOT_CONFIRMED","DOCUMENT_RELOADING")
  private fun description(reason:String)=when(reason){"SELECTED"->"Native Favourites selection confirmed. Other chats remain accessible through WhatsApp navigation.";"LOGIN_INTERFACE"->"WhatsApp's login interface was detected. Your linked session has not been changed.";"FILTER_STRUCTURE_UNRECOGNISED"->"The conversation container exists, but a unique supported Favourites filter group was not found.";"SELECTED_STATE_UNAVAILABLE"->"The filter does not expose a supported selected-state indicator.";"ACTIVATION_NOT_CONFIRMED"->"The Favourites control was selected, but activation was not confirmed.";"CONFLICTING_SELECTION"->"The interface reports conflicting filter selections.";"UNEXPECTED_NAVIGATION"->"The page left the supported WhatsApp origin.";"DOCUMENT_RELOADING"->"WhatsApp is reloading; checking Favourites again.";"ACTIVATION_PENDING"->"Waiting for WhatsApp to confirm Favourites.";else->"Waiting for the authenticated conversation interface."}
 }
}
