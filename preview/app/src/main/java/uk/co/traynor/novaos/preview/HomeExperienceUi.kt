package uk.co.traynor.novaos.preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable internal fun HomeBackupScreen(data:PreviewData,widgets:WidgetData,backup:HomeBackup,onBack:()->Unit,onExport:()->Unit,onImport:()->Unit){
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("home-backup")){
  PreviewHeader("Home backup & restore",data.profileLabel+" · layout metadata only",onBack)
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
   Text("Keep your Home, your way.",style=MaterialTheme.typography.headlineMedium)
   Text("Export pages, shortcut identifiers, folders, Dock, wallpaper settings, widgets and stacks. Existing installed apps remain installed when restoring.")
   Text("Backups are not encrypted. They exclude browser storage, login tokens, journal/mood entries, Quick Note text and search history. Custom website URLs are never exported; their identifiers can resolve only if the app is already installed. Wallpaper image files stay on this device; missing images use the chosen Nova wallpaper.",style=MaterialTheme.typography.bodySmall)
   Button(onClick=onExport,modifier=Modifier.fillMaxWidth().testTag("export-home")){Icon(Icons.Default.FileUpload,null);Text("Export layout",Modifier.padding(start=10.dp))}
   OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth().testTag("import-home")){Icon(Icons.Default.FileDownload,null);Text("Choose backup to restore",Modifier.padding(start=10.dp))}
   backup.pending?.let{p->
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){
     Text("Review before restoring",style=MaterialTheme.typography.titleLarge)
     val l=p.getJSONObject("layout");Text("${l.getJSONArray("pages").length()} Home pages · ${l.getJSONArray("folders").length()} folders · ${p.getJSONArray("widgets").length()} widget positions\n${l.getJSONArray("installedIds").length()} installed-app identifiers\nTarget: ${data.profileLabel}",Modifier.padding(vertical=16.dp))
     Text("Your current Home layout will be replaced after confirmation. Private data and existing installed apps remain. Notes inside restored widgets are blank.",style=MaterialTheme.typography.bodySmall)
     Row{Button(onClick={backup.restore(data,widgets)},modifier=Modifier.testTag("confirm-home-restore")){Text("Confirm Restore")};TextButton(onClick={backup.cancel()}){Text("Cancel")}}
    }}
   }
   backup.message?.let{Text(it)};backup.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  }
 }
}
@Composable internal fun LockSettings(widgets:WidgetData,onBack:()->Unit,onLock:()->Unit){
 Column(Modifier.fillMaxSize().safeDrawingPadding()){
  PreviewHeader("Nova preview lock","A demonstration, not Android device security",onBack)
  Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
   Text("A quieter entrance to your space.",style=MaterialTheme.typography.headlineMedium)
   Text("This optional internal screen hides the NovaOS interface. It does not encrypt data, authenticate a Space, or replace Android’s lock screen.")
   Row(verticalAlignment=Alignment.CenterVertically){Text("Enable demonstration lock",Modifier.weight(1f));Switch(widgets.demoLock,{widgets.demoLock(it)},modifier=Modifier.testTag("enable-demo-lock"))}
   Button(onClick=onLock,enabled=widgets.demoLock,modifier=Modifier.fillMaxWidth()){Text("Show preview lock")}
   Row(verticalAlignment=Alignment.CenterVertically){Text("Reduce motion",Modifier.weight(1f));Switch(widgets.reduced,{widgets.reduced(it)})}
  }
 }
}
/** Future production authentication must replace this interface; no PIN/biometric material exists here. */
interface NovaUnlockGate { fun unlockDemonstration():Boolean }
@Composable internal fun DemoLockScreen(wallpaper:Wallpaper,filesDir:File,gate:NovaUnlockGate,onUnlock:()->Unit,onDisable:()->Unit){
 var now by remember{mutableStateOf(LocalDateTime.now())};LaunchedEffect(Unit){while(true){now=LocalDateTime.now();delay(1000)}}
 Box(Modifier.fillMaxSize().testTag("nova-demo-lock")){
  WallpaperSurface(wallpaper,filesDir,Modifier.fillMaxSize())
  Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
   Icon(Icons.Default.Lock,null,Modifier.size(32.dp),tint=Color.White);Text("NOVAOS",Modifier.padding(top=24.dp),color=Color.White,letterSpacing=4.sp)
   Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=64.sp,color=Color.White)
   Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),color=Color.White)
   Text("Demonstration lock · no authentication",Modifier.padding(vertical=30.dp),color=Color.White,style=MaterialTheme.typography.bodySmall)
   Button(onClick={if(gate.unlockDemonstration())onUnlock()},modifier=Modifier.testTag("unlock-demo")){Text("Enter NovaOS")}
   TextButton(onClick=onDisable){Text("Disable preview lock",color=Color.White)}
  }
 }
}
