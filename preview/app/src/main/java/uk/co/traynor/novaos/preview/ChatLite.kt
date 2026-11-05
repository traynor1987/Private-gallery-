package uk.co.traynor.novaos.preview

import android.content.Context
import android.view.View
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject

enum class ChatCompatibility { CHECKING, WORKING, DEGRADED, BROKEN, UPDATE_AVAILABLE }
data class ChatFriend(val phone:String,val alias:String)
/** Preview-local manual approvals only. No message/token field or export surface. */
class ChatLiteData(context:Context){
 private val prefs=context.applicationContext.getSharedPreferences("nova_chat_lite_v1",0)
 var revision by mutableIntStateOf(0);private set
 var error by mutableStateOf<String?>(null);private set
 var state by mutableStateOf(ChatCompatibility.DEGRADED);private set
 var reason by mutableStateOf("No verified filtering adapter is installed. Use the optional Compatibility Inspector to investigate.");private set
 var facts by mutableStateOf("Adapter ${ChatRules.ADAPTER_VERSION}; no remote executable updates.");private set
 private var generation=0
 private var damaged=false
 fun friends(profile:String):List<ChatFriend>{revision;damaged=false;return runCatching{val raw=prefs.getString("friends:$profile","[]")!!;require(raw.length<=20000);val a=JSONArray(raw);require(a.length()<=64);(0 until a.length()).map{val o=a.getJSONObject(it);ChatFriend(ChatRules.phone(o.getString("phone")),o.getString("alias").take(60))}.also{list->require(list.map{it.phone}.distinct().size==list.size)}}.getOrElse{damaged=true;error="Approvals could not be read. Existing data is preserved.";emptyList()}}
 fun approve(profile:String,phone:String,alias:String):Boolean=runCatching{
  require(profile in listOf("primary","hidden"));val id=ChatRules.phone(phone);require(alias.trim().length in 1..60){"Enter a local nickname (1–60 characters)."}
  val old=friends(profile);require(!damaged){"Existing approval data needs recovery before editing."};require(old.none{it.phone==id}){"That number is already approved."};require(old.size<64){"This preview supports up to 64 approvals."};save(profile,old+ChatFriend(id,alias.trim()))
 }.getOrElse{error=it.message?:"Could not save approval.";false}
 fun remove(profile:String,phone:String){val old=friends(profile);if(!damaged)save(profile,old.filterNot{it.phone==phone})}
 private fun save(profile:String,list:List<ChatFriend>):Boolean{val a=JSONArray();list.forEach{a.put(JSONObject().put("phone",it.phone).put("alias",it.alias))};val ok=prefs.edit().putString("friends:$profile",a.toString()).commit();if(ok){revision++;error=null}else error="Could not save approvals.";return ok}
 fun clearError(){error=null}
 fun sanctuary(profile:String):Boolean{revision;return prefs.getBoolean("sanctuary:$profile",false)}
 fun sanctuary(profile:String,value:Boolean){if(prefs.edit().putBoolean("sanctuary:$profile",value).commit())revision++ else error="Could not save Sanctuary preference."}
 fun isWhatsApp(tab:PreviewTab)=runCatching{Uri.parse(tab.url).host=="web.whatsapp.com"||Uri.parse(tab.app.url).host=="web.whatsapp.com"}.getOrDefault(false)
 fun guard(pool:PreviewBrowserModel,profile:String):PreviewTab?{
  val matching=pool.tabs.filter{it.profile==profile&&isWhatsApp(it)}
  val selected=pool.active?.takeIf{it in matching}?:matching.maxByOrNull{it.touched}
  selected?.let{pool.requireFriends(it,true)}
  return selected
 }

}

@Composable internal fun ChatLiteScreen(chat:ChatLiteData,pool:PreviewBrowserModel,profile:String,onHome:()->Unit,onNormal:()->Unit,onInspect:()->Unit){
 var confirm by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("chat-settings")){
  PreviewHeader("Nova Chat Lite 1.0","Native Favourites · experimental",onHome)
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   Text("Use WhatsApp's native Favourites",style=MaterialTheme.typography.titleLarge)
   Text("Choose your friends in WhatsApp's own Favourites list. Nova Chat restores that view after reloads and verifies its selected state before revealing the page. Existing manual approval notes remain stored locally but do not control WhatsApp's Favourites.")
   Row{Text("Open Nova Chat in Friends Mode when entering Sanctuary",Modifier.weight(1f));Switch(chat.sanctuary(profile),{chat.sanctuary(profile,it)},modifier=Modifier.testTag("chat-sanctuary"))}
   Button(onClick=onInspect,modifier=Modifier.fillMaxWidth().testTag("open-chat-inspector")){Text("Compatibility Inspector")}
   Text("Favourites is a convenience filter, not isolation or encryption. Other conversations may remain accessible through search, filter switching, notifications and other WhatsApp navigation. No message bodies or contacts are collected. No sending, deletion, archiving, blocking or read acknowledgements are automated.")
   Text("Only a unique supported filter group with explicit selected-state indicators can be revealed. Unrecognised WhatsApp versions remain covered. Normal browsing is available by explicit confirmation.")
   OutlinedButton(onClick={confirm=true},modifier=Modifier.fillMaxWidth().testTag("chat-normal-whatsapp")){Text("Open Normal WhatsApp…")}
  }
 }
 if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Leave Friends Mode?")},text={Text("Normal WhatsApp can expose every conversation. Your session is preserved.")},confirmButton={TextButton(onClick={confirm=false;onNormal()}){Text("Open unfiltered WhatsApp")}},dismissButton={TextButton(onClick={confirm=false}){Text("Stay in Friends Mode")}})
}
