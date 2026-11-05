package uk.co.traynor.novaos.preview
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.json.JSONArray

data class SearchHit(val id:String,val title:String,val category:String,val detail:String,val target:String)
class NovaSearchHistory(context:Context){
 private val prefs=context.getSharedPreferences("nova_local_search_v1",0)
 var revision by mutableIntStateOf(0);private set
 fun history(profile:String):List<String>{revision;return runCatching{val a=JSONArray(prefs.getString(profile,"[]"));(0 until minOf(a.length(),20)).map{a.getString(it).take(80)}}.getOrDefault(emptyList())}
 fun add(profile:String,query:String){val value=query.trim().take(80);if(value.isBlank())return;if(prefs.edit().putString(profile,JSONArray((listOf(value)+history(profile).filterNot{it==value}).take(20)).toString()).commit())revision++}
 fun clear(profile:String){if(prefs.edit().remove(profile).commit())revision++}
}
internal fun searchIndex(data:PreviewData,widgets:WidgetData,pool:PreviewBrowserModel):List<SearchHit>{
 val apps=data.allApps().map{SearchHit("app:"+it.id,it.name,"Apps",if(it.website!=null)"Installed web app"else "Nova application",it.id)}
 val folders=data.profile.folders.map{SearchHit(it.id,it.name,"Folders","Home folder",it.id)}
 val tools=listOf(SearchHit("breathing","Breathing","Mind","Guided breathing · no scoring","mind:breathing"),SearchHit("grounding","Grounding","Mind","5–4–3–2–1 or without counting","mind:grounding"),SearchHit("sounds","Calm sounds","Mind","Offline native sound textures","mind:sounds"))
 val settings=listOf(SearchHit("wallpaper","Wallpaper","Settings","Choose wallpaper","wallpaper"),SearchHit("navigation","Navigation & Edge Panel","Settings","Buttons, gestures and Edge","shell-settings"),SearchHit("backup","Home backup & restore","Settings","Layout metadata only","backup"),SearchHit("lock","Preview lock screen","Settings","Optional demonstration lock","lock-settings"),SearchHit("widgets","Widget Centre","Widgets","Add and manage Home widgets","widgets"))
 val placed=widgets.slots(data.state.current).flatMap{s->s.items.map{item->SearchHit("placed:"+item.id,widgetSpecs.firstOrNull{it.type==item.type}?.name?:"Unavailable widget","Widgets","Home page ${s.page+1}","widget:"+s.id)}}
 val games=data.catalogue.filter{it.category=="Games"&&data.installed(it)==null}.map{SearchHit("store:"+it.id,it.name,"Games",it.classification,"store:"+it.id)}
 val recents=pool.recents(data.state.current).map{SearchHit("recent:"+it.id,it.app.name,"Recent","Retained app","recent-tab:"+it.id)}
 val internal=data.shell.internalRecents.filter{it.first==data.state.current}.mapNotNull{(_,id)->data.app(id)?.let{SearchHit("recent-internal:"+id,it.name,"Recent","Nova application","resume-internal:"+id)}}
 val availableWidgets=widgetSpecs.map{SearchHit("widget-type:"+it.type,it.name,"Widgets",it.description,"widgets")}
 return apps+folders+tools+settings+placed+availableWidgets+games+recents+internal
}
@Composable internal fun NovaSearch(data:PreviewData,widgets:WidgetData,pool:PreviewBrowserModel,history:NovaSearchHistory,onBack:()->Unit,onResult:(String)->Unit,onWeb:(String)->Unit){
 var query by rememberSaveable{mutableStateOf("")};var category by rememberSaveable{mutableStateOf("All")}
 val index=searchIndex(data,widgets,pool);val hits=index.filter{(category=="All"||category==it.category)&&(it.title.contains(query,true)||it.detail.contains(query,true))}
 val choose:(SearchHit)->Unit={history.add(data.state.current,query);onResult(it.target)}
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("nova-search")){
  PreviewHeader("Nova Search","Local apps, tools and Home · nothing uploaded",onBack)
  OutlinedTextField(query,{query=it.take(80)},Modifier.fillMaxWidth().padding(horizontal=20.dp).testTag("nova-search-input"),singleLine=true,label={Text("Search NovaOS")},leadingIcon={Icon(Icons.Default.Search,null)},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={hits.firstOrNull()?.let(choose)}))
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=20.dp)){listOf("All","Apps","Folders","Widgets","Settings","Games","Recent","Mind").forEach{c->FilterChip(category==c,{category=c},label={Text(c)})}}
  LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp)){
   if(query.isBlank()){
    item{Row{Text("Recent searches",Modifier.weight(1f));TextButton(onClick={history.clear(data.state.current)}){Text("Clear history")}}}
    items(history.history(data.state.current),key={"history:$it"}){q->TextButton(onClick={query=q}){Icon(Icons.Default.History,null);Text(q,Modifier.padding(start=10.dp))}}
   }
   if(hits.isEmpty())item{Text("No local matches. Try an app or tool name.",Modifier.padding(vertical=24.dp))}
   items(hits,key={it.id}){hit->ListItem(headlineContent={Text(hit.title)},supportingContent={Text("${hit.category} · ${hit.detail}")},modifier=Modifier.testTag("nova-search-result-"+hit.id).clickable{choose(hit)})}
   if(query.isNotBlank())item{OutlinedButton(onClick={onWeb(query)},Modifier.fillMaxWidth().padding(top=20.dp)){Text("Search the web explicitly…")};Text("This opens Browser V1 with your search terms. Local search never sends them automatically.",style=MaterialTheme.typography.bodySmall)}
  }
 }
}
