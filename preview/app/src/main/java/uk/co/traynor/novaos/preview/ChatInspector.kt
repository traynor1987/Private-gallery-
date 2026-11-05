package uk.co.traynor.novaos.preview

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import org.json.JSONObject

/** Bounded local-only inspector. No bridge, networking, page text or executable updates. */
class ChatInspector(context:Context){
 private val app=context.applicationContext
 private val prefs=app.getSharedPreferences("nova_chat_inspector_v01",0)
 private var job:Job?=null
 private var generation=0
 var status by mutableStateOf("IDLE");private set
 var issue by mutableStateOf("Inspection is off. Start only when you choose.");private set
 var revision by mutableIntStateOf(0);private set
 var samplesTaken by mutableIntStateOf(0);private set
 fun target(pool:PreviewBrowserModel,profile:String)=pool.active?.takeIf{it.profile==profile&&ChatInspectorRules.origin(it.url)}?:pool.tabs.filter{it.profile==profile&&ChatInspectorRules.origin(it.url)}.maxByOrNull{it.touched}
 fun report(profile:String):String?{revision;val raw=prefs.getString("report:$profile",null)?:return null;return runCatching{ChatInspectorRules.validateReport(raw)}.getOrNull()}
 fun stop(){generation++;job?.cancel();job=null;if(status=="RUNNING"){status="STOPPED";issue="Inspection stopped. No further samples will be scheduled; in-flight results are discarded. Any report shown is the last completed report."}}
 fun delete(profile:String){stop();if(prefs.edit().remove("report:$profile").commit()){revision++;status="IDLE";issue="Local report deleted. Copies you chose to share or copy are not deleted."}else{status="BROKEN";issue="The local report could not be deleted."}}
 fun start(scope:CoroutineScope,pool:PreviewBrowserModel,profile:String,explicitAction:Boolean){
  if(!explicitAction)return
  stop();val tab=target(pool,profile)
  if(!ChatInspectorRules.safeToInspect(explicitAction,tab!=null)){status="BROKEN";issue="No retained page at the verified HTTPS WhatsApp Web origin is available. Open your existing WhatsApp app first.";return}
  tab!!;val sourceRevision=tab.navigationRevision;val token=++generation;status="RUNNING";issue="Taking three bounded structural samples locally. No chats are opened or scrolled.";samplesTaken=0
  job=scope.launch{
   try{
    val script=app.assets.open("chat_inspector_v01.js").bufferedReader().use{it.readText()}
    val samples=mutableListOf<JSONObject>()
    repeat(3){index->
     if(index>0)delay(1000)
     if(token!=generation)return@launch
     if(tab !in pool.tabs||pool.currentProfile!=profile||!pool.foreground||tab.navigationRevision!=sourceRevision||!ChatInspectorRules.origin(tab.url)){status="BROKEN";issue="The page, profile or foreground state changed during inspection. Retry explicitly after returning.";return@launch}
     val result=withTimeoutOrNull(3000){suspendCancellableCoroutine<String?>{c->tab.view.evaluateJavascript(script){raw->if(c.isActive)c.resume(raw)}}}
     if(token!=generation)return@launch
     if(tab !in pool.tabs||pool.currentProfile!=profile||tab.navigationRevision!=sourceRevision||!ChatInspectorRules.origin(tab.url)){status="BROKEN";issue="Navigation changed during inspection. No new report was saved.";return@launch}
     val sample=runCatching{require(result!=null&&result.length<=1500);val decoded=org.json.JSONTokener(result).nextValue() as String;ChatInspectorRules.sample(decoded)}.getOrNull()
     if(sample==null||!sample.getBoolean("origin")){status="BROKEN";issue="The page returned an invalid structural sample or failed its origin check. No page strings were retained.";return@launch}
     samples+=sample;samplesTaken=samples.size
    }
    val consistent=samples.all{it.getBoolean("consistent")==samples[0].getBoolean("consistent")&&it.getBoolean("container")==samples[0].getBoolean("container")&&it.getInt("rows")==samples[0].getInt("rows")&&it.getInt("shape")==samples[0].getInt("shape")}
    val safe=ChatInspectorRules.report(samples.last(),consistent)
    if(token!=generation)return@launch
    if(!prefs.edit().putString("report:$profile",safe).commit()){status="BROKEN";issue="The sanitised report could not be saved locally.";return@launch}
    revision++;status="COMPLETE";issue=if(!samples.last().getBoolean("container"))"A conversation container was not detected by this inspector version."else if(samples.last().getInt("rows")==0)"The container was detected but recognised conversation rows were not."else "Conversation structure was detected. Stable non-personal identity and complete filtering remain unverified."
   }catch(e:CancellationException){throw e}catch(e:Exception){if(token==generation){status="BROKEN";issue="The bounded inspection could not complete. No new report was saved."}}
  }
 }
}

@Composable internal fun ChatInspectorScreen(inspector:ChatInspector,pool:PreviewBrowserModel,profile:String,onBack:()->Unit,onHome:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope();var preview by rememberSaveable{mutableStateOf(false)};var copied by remember{mutableStateOf(false)}
 val report=inspector.report(profile)
 DisposableEffect(inspector,context){val window=(context as? android.app.Activity)?.window;window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);val owner=context as? androidx.lifecycle.LifecycleOwner;val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_PAUSE)inspector.stop()};owner?.lifecycle?.addObserver(observer);onDispose{inspector.stop();owner?.lifecycle?.removeObserver(observer);window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}}
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("chat-inspector")){
  PreviewHeader("Compatibility Inspector","Nova Chat · local experimental tool · v0.1",onBack)
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Text("Only you start inspection",style=MaterialTheme.typography.headlineSmall)
   Text("Three read-only samples check the retained web.whatsapp.com page. No names, messages, phone numbers, raw IDs, images, cookies or tokens are collected. No screenshots, scrolling, account actions or uploads. The report contains only fixed capability values.")
   Text(if(inspector.status=="RUNNING")"Inspecting locally…"else "⚠ Nova Chat compatibility needs attention",style=MaterialTheme.typography.titleMedium)
   Text(inspector.status,Modifier.testTag("inspector-status"));Text(inspector.issue)
   Button(onClick={preview=false;copied=false;inspector.start(scope,pool,profile,true)},enabled=inspector.status!="RUNNING"&&inspector.target(pool,profile)!=null,modifier=Modifier.fillMaxWidth().testTag("start-chat-inspection")){Text(if(inspector.status=="BROKEN"||inspector.status=="COMPLETE")"Retry Inspection"else "Start Inspection")}
   OutlinedButton(onClick={inspector.stop()},enabled=inspector.status=="RUNNING",modifier=Modifier.fillMaxWidth().testTag("stop-chat-inspection")){Text("Stop Inspection")}
   if(inspector.target(pool,profile)==null)Text("Open your existing authenticated WhatsApp Web app first. No new linked device or login is created here.")
   OutlinedButton(onClick={preview=true},enabled=report!=null&&inspector.status!="RUNNING",modifier=Modifier.fillMaxWidth().testTag("view-chat-report")){Text("View Report")}
   if(preview&&report!=null){Text("Entire sanitised report — last completed inspection",style=MaterialTheme.typography.titleMedium);Text(report,Modifier.fillMaxWidth().testTag("sanitised-chat-report"),style=MaterialTheme.typography.bodySmall)}
   Button(onClick={val safe=inspector.report(profile);if(preview&&safe!=null&&inspector.status!="RUNNING"){val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Nova Chat Sanitised Report",safe));copied=true}},enabled=preview&&report!=null&&inspector.status!="RUNNING",modifier=Modifier.fillMaxWidth().testTag("copy-chat-report")){Text("Copy Sanitised Report")}
   if(copied)Text("Copied only the sanitised report. Sharing it is your choice.")
   TextButton(onClick={inspector.delete(profile);preview=false;copied=false},enabled=report!=null,modifier=Modifier.testTag("delete-chat-report")){Text("Delete Report")}
   Text("Row detection does not prove reliable Friends Mode filtering. Stable identity, filtering attachment and closed shadow roots remain unknown. Virtualisation is a geometry hint only. The across-sample signature covers bounded tag categories, child-count buckets and grid-cell presence; it is not a complete DOM equivalence check. Scrolling and single-page navigation are not observed because this tool never scrolls or opens chats. Inspection stops on leaving this page or backgrounding; it never resumes automatically.",style=MaterialTheme.typography.bodySmall)
   TextButton(onClick=onHome){Text("Return Home")}
  }
 }
}
