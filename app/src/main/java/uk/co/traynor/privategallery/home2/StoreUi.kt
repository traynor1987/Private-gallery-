package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.ui.navigationHaptic

@Composable internal fun PreviewHeader(title:String,subtitle:String,onBack:()->Unit,actions:@Composable RowScope.()->Unit={}){
 val tap=navigationHaptic()
 Row(Modifier.fillMaxWidth().padding(start=4.dp,end=14.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={tap();onBack()}){Icon(Icons.Default.ArrowBack,"Back to Home")};Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.headlineSmall);Text(subtitle,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)};actions()}
}
@Composable internal fun StoreScreen(data:PreviewData,onBack:()->Unit,onDetail:(StoreEntry)->Unit,onCustom:()->Unit,onVisualFoundation:(()->Unit)?=null){
 var query by remember{mutableStateOf("")};var category by remember{mutableStateOf("All")};var mine by remember{mutableStateOf(false)}
 val entries=(if(mine)data.profile.installed else data.catalogue).filter{(category=="All"||it.category==category)&&(it.name.contains(query,true)||it.description.contains(query,true)||it.publisher.contains(query,true))}
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("nova-store")){
  PreviewHeader("Nova Store",data.profileLabel,onBack){IconButton(onClick=onCustom){Icon(Icons.Default.Add,"Install custom website")}}
  LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   if(onVisualFoundation!=null)item{VisualCatalogueEntry("store-visual-foundation",onVisualFoundation)}
   item{Surface(shape=RoundedCornerShape(30.dp),color=Color(0xFF303F65),contentColor=Color.White){Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF303F65),Color(0xFF455568),Color(0xFF4B3B70)))).padding(24.dp)){
    Text("YOUR WEB. YOUR SPACE.",style=MaterialTheme.typography.labelSmall)
    Text("A little more\npossibility.",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Light,modifier=Modifier.padding(vertical=12.dp))
    Text("${data.catalogue.size} official destinations. Add your favourites to Nova Home.",style=MaterialTheme.typography.bodyMedium)
    TextButton(onClick={onDetail(data.catalogue.first{it.id=="photopea"})}){Text("Discover Photopea  →",color=Color(0xFFB9F6E3))}
   }}}
   item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){FilterChip(selected=!mine,onClick={mine=false;category="All"},label={Text("Explore")},modifier=Modifier.testTag("store-explore"));FilterChip(selected=mine,onClick={mine=true;category="All"},label={Text("My Apps (${data.profile.installed.size})")},modifier=Modifier.testTag("store-my-apps"))}}
   item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("store-search"),placeholder={Text(if(mine)"Search installed apps"else "Find something useful")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(22.dp))}
   item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){(listOf("All")+data.catalogue.map{it.category}.distinct().sorted()).forEach{value->FilterChip(selected=category==value,onClick={category=value},label={Text(value)})}}}
   item{Row(verticalAlignment=Alignment.CenterVertically){Text(if(mine)"Your installed apps"else if(query.isBlank()&&category=="All")"Curated for you"else "Results",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);Text("${entries.size}",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
   if(entries.isEmpty())item{Column(Modifier.fillMaxWidth().padding(vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.SearchOff,null,Modifier.size(40.dp));Text(if(mine)"No installed apps match this search."else "No destinations match this search.",Modifier.padding(top=12.dp));TextButton(onClick={query="";category="All";mine=false}){Text("Explore the catalogue")}}}
   items(entries,key={it.id}){entry->
    Surface(onClick={onDetail(entry)},shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainer){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
     AppIcon(LaunchApp(entry.id,entry.name,"website",entry),54.dp)
     Column(Modifier.weight(1f)){Text(entry.name,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(entry.category,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary);Text(entry.description,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)}
     Icon(if(data.installed(entry)!=null)Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,if(data.installed(entry)!=null)"Installed"else "View app details",tint=MaterialTheme.colorScheme.primary)
    }}
   }
   item{Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.5f)){Text("Installations use the existing encrypted Private Apps catalogue. Websites open in the existing Primary browser container and keep its network and login policies. Catalogue classifications do not promise PWA or login compatibility.",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)}}
   item{TextButton(onClick=onCustom,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Text("Add your own HTTPS website",Modifier.padding(start=8.dp))}}
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AppDetails(data:PreviewData,entry:StoreEntry,onDismiss:()->Unit,onOpen:(StoreEntry)->Unit,onUninstall:(String)->Unit,onAddHome:(String)->Unit,onPin:(String)->Unit){
 var confirm by remember(entry.id){mutableStateOf(false)};var pinned by remember{mutableStateOf(false)}
 val installed=data.installed(entry)
 PanicHomeModalBottomSheet(onDismissRequest=onDismiss){
  Column(Modifier.fillMaxWidth().fillMaxHeight(.91f).verticalScroll(rememberScrollState()).padding(24.dp)){
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){AppIcon(LaunchApp(entry.id,entry.name,"website",entry),74.dp);Column(Modifier.weight(1f)){Text(entry.name,style=MaterialTheme.typography.headlineMedium);Text(entry.publisher,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(entry.category,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)};IconButton(onClick=onDismiss){Icon(Icons.Default.Close,"Close app details")}}
   Spacer(Modifier.height(22.dp))
   if(installed==null)Button(enabled=entry.classification!="Unsupported",onClick={confirm=true},modifier=Modifier.fillMaxWidth().testTag("install-app")){Icon(Icons.Default.Add,null);Text("Install in ${data.profileLabel}",Modifier.padding(start=8.dp))}
   else {Button(onClick={onOpen(installed)},Modifier.fillMaxWidth().testTag("open-installed")){Icon(Icons.Default.OpenInNew,null);Text("Open app",Modifier.padding(start=8.dp))};Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton(onClick={onAddHome(installed.id)},Modifier.weight(1f)){Text("Add to Home")};OutlinedButton(onClick={onPin(installed.id);pinned=true},Modifier.weight(1f)){Text(if(pinned)"Pinned ✓"else "Pin to Dock")}};TextButton(onClick={onUninstall(installed.id)}){Text("Uninstall")}}
   Text(entry.description,Modifier.padding(vertical=20.dp),style=MaterialTheme.typography.bodyLarge)
   Text("${entry.classification} · existing Private Apps container",style=MaterialTheme.typography.titleSmall)
   Text("Website capability classification. Browser games are identified as Web Apps from their official destinations; PWA installability and offline operation have not been verified. The container supports ordinary web navigation; individual websites may impose embedded-browser or device restrictions.",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall)
   HorizontalDivider(Modifier.padding(vertical=18.dp))
   Text("Official website",style=MaterialTheme.typography.labelLarge);Text(entry.url,Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodyMedium)
   Text("Compatibility",Modifier.padding(top=18.dp),style=MaterialTheme.typography.labelLarge);Text(entry.limitations.ifBlank{"Custom destination. Not reviewed by the Nova catalogue. Compatibility is unknown."},Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodySmall)
   Text("Privacy",Modifier.padding(top=18.dp),style=MaterialTheme.typography.labelLarge);Text("Website storage and sessions follow existing Primary Browser V1 policy. Websites retain their own privacy policies and may restrict embedded authentication. Hidden networking is disabled in this release.",Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodySmall)
   Text("Destination verification · ${entry.date.ifBlank{"custom / unverified"}}",Modifier.padding(top=18.dp),style=MaterialTheme.typography.labelSmall)
   if(entry.source.isNotEmpty())Text(entry.source,style=MaterialTheme.typography.bodySmall)
  }
 }
 if(confirm)PanicHomeAlertDialog(onDismissRequest={confirm=false},title={Text("Install ${entry.name}?")},text={Text("Add ${entry.url} to ${data.profileLabel}. This creates an encrypted Private Apps entry; it does not install an Android package or open the website yet.")},confirmButton={TextButton(onClick={data.install(entry);confirm=false},modifier=Modifier.testTag("confirm-install")){Text("Install")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
}

@Composable internal fun CustomWebsiteDialog(onDismiss:()->Unit,onInstall:(StoreEntry)->Unit){
 var name by remember{mutableStateOf("")};var url by remember{mutableStateOf("https://")};var error by remember{mutableStateOf<String?>(null)}
 PanicHomeAlertDialog(onDismissRequest=onDismiss,title={Text("Add a website")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text("Custom Website Shortcut. PWA metadata is not inspected. You choose whether to trust this destination.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(name,{name=it.take(40)},label={Text("App name")},singleLine=true)
  OutlinedTextField(url,{url=it.take(2048)},label={Text("HTTPS website")},singleLine=true)
  error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
 }},confirmButton={TextButton(onClick={runCatching{val valid=PreviewRules.https(url.trim());require(name.isNotBlank()){ "Enter an app name" };onInstall(StoreEntry("custom",name.trim(),valid,"Custom website","Custom","Your custom website.",limitations="Unverified custom website. Browser and sign-in compatibility may vary."))}.onFailure{error=it.message}}){Text("Install shortcut")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable internal fun CreateFolderDialog(data:PreviewData,page:Int,onDismiss:()->Unit){
 var name by remember{mutableStateOf("")};var apps by remember{mutableStateOf(setOf<String>())}
 PanicHomeAlertDialog(onDismissRequest=onDismiss,title={Text("Create folder")},text={Column{
  OutlinedTextField(name,{name=it.take(32)},label={Text("Folder name")},singleLine=true)
  Text("Select at least two apps",Modifier.padding(top=10.dp),style=MaterialTheme.typography.labelMedium)
  Column(Modifier.heightIn(max=280.dp).verticalScroll(rememberScrollState())){data.allApps().forEach{app->Row(Modifier.fillMaxWidth().clickable{apps=if(app.id in apps)apps-app.id else apps+app.id},verticalAlignment=Alignment.CenterVertically){Checkbox(app.id in apps,{apps=if(it)apps+app.id else apps-app.id});Text(app.name)}}}
 }},confirmButton={TextButton(enabled=name.isNotBlank()&&apps.size>=2,onClick={data.createFolder(name,apps.toList(),page);onDismiss()}){Text("Create")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
