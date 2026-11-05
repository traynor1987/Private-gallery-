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
  matching.forEach{pool.requireFriends(it,true)}
  return matching.maxByOrNull{it.touched}
 }

}

@Composable internal fun ChatLiteScreen(chat:ChatLiteData,pool:PreviewBrowserModel,profile:String,onHome:()->Unit,onNormal:()->Unit,onInspect:()->Unit){
 val context=LocalContext.current
 DisposableEffect(pool,profile,pool.tabs.size){chat.guard(pool,profile);onDispose{}}
 var manage by rememberSaveable{mutableStateOf(false)};var diagnostics by rememberSaveable{mutableStateOf(false)};var confirmNormal by remember{mutableStateOf(false)}
 var phone by remember{mutableStateOf("")};var alias by remember{mutableStateOf("")};var token by remember{mutableIntStateOf(0)}
 androidx.activity.compose.BackHandler(manage||diagnostics){if(diagnostics)diagnostics=false else manage=false}
 DisposableEffect(context){val window=(context as? android.app.Activity)?.window;window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);onDispose{window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}}
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("nova-chat-lite")){
  PreviewHeader(if(manage)"Manage Friends"else "Nova Chat Lite","Friends Mode · experimental launcher prototype",{if(manage)manage=false else onHome()})
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Text("Your chosen conversations. A quieter starting point.",style=MaterialTheme.typography.titleLarge)
   Text("Preview approvals are stored locally and are not production encrypted. No messages are imported. This build cannot safely display filtered chats; opening stays covered until an adapter is verified.")
   Surface(Modifier.fillMaxWidth().testTag("chat-compatibility-cover"),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.secondaryContainer){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    Text(if(chat.state==ChatCompatibility.CHECKING)"Checking compatibility…"else "⚠ Nova Chat needs an update",style=MaterialTheme.typography.titleMedium)
    Text(chat.state.name,Modifier.testTag("chat-state"));Text(chat.reason)
    Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick=onInspect){Text("Retry")};TextButton(onClick=onInspect){Text("Check compatibility")};TextButton(onClick={diagnostics=!diagnostics}){Text("Open diagnostics")}}
    if(diagnostics)Text(chat.facts,Modifier.testTag("chat-diagnostics"),style=MaterialTheme.typography.bodySmall)
   }}
   Button(onClick=onInspect,modifier=Modifier.fillMaxWidth().testTag("open-chat-inspector")){Text("Compatibility Inspector")}
   if(!manage)Button(onClick={manage=true},modifier=Modifier.fillMaxWidth().testTag("manage-chat-friends")){Text("Manage Friends")}
   if(manage){
    OutlinedTextField(alias,{alias=it.take(60)},Modifier.fillMaxWidth().testTag("chat-alias"),label={Text("Local nickname")},singleLine=true)
    OutlinedTextField(phone,{phone=it.take(50)},Modifier.fillMaxWidth().testTag("chat-phone"),label={Text("International phone number (+country code)")},singleLine=true)
    Button(onClick={chat.clearError();if(chat.approve(profile,phone,alias)){phone="";alias=""}},modifier=Modifier.testTag("approve-chat-friend")){Text("Approve number")}
    Text("Phone numbers are explicit identities supplied by you, not verified WhatsApp conversation IDs. Display names are never used for matching. Groups are not supported by this prototype.",style=MaterialTheme.typography.bodySmall)
   }
   chat.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   val friends=chat.friends(profile)
   if(friends.isEmpty())Text("No conversations approved. Unknown contacts are never added automatically.")
   friends.forEach{friend->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
    Text(friend.alias,style=MaterialTheme.typography.titleMedium)
    if(manage){Text(friend.phone);TextButton(onClick={chat.remove(profile,friend.phone)}){Text("Remove approval")}}
    else OutlinedButton(onClick=onInspect,modifier=Modifier.testTag("chat-approved-action")){Text("Check opening compatibility")}
   }}}
   Row{Text("Enter Friends Mode with Sanctuary",Modifier.weight(1f));Switch(chat.sanctuary(profile),{chat.sanctuary(profile,it)},modifier=Modifier.testTag("chat-sanctuary"))}
   Text("Visual wellbeing feature only; not an encryption or access-control boundary. No absolute concealment claim. No sending, read acknowledgements, deletion or contact blocking is automated.",style=MaterialTheme.typography.bodySmall)
   Text("Adapter updates require a signed NovaOS Preview APK. No remote JavaScript or selector downloads.",style=MaterialTheme.typography.bodySmall)
   OutlinedButton(onClick={confirmNormal=true},modifier=Modifier.fillMaxWidth().testTag("chat-normal-whatsapp")){Text("Open normal WhatsApp (unfiltered)…")}
   TextButton(onClick=onHome){Text("Return Home")}
  }
 }
 if(confirmNormal)AlertDialog(onDismissRequest={confirmNormal=false},title={Text("Leave Friends Mode?")},text={Text("Normal WhatsApp can show every conversation, preview and unread badge. Your approvals stay saved. Continue only if you want the unfiltered interface.")},confirmButton={TextButton(onClick={confirmNormal=false;onNormal()}){Text("Open unfiltered WhatsApp")}},dismissButton={TextButton(onClick={confirmNormal=false}){Text("Stay in Friends Mode")}})
}
