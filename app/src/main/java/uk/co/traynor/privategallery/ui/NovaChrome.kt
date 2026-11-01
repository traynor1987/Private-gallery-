package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.home.HomeLayout

/** Presentation only; callbacks are always supplied by the currently authenticated owner. */
@Composable internal fun NovaAppHeader(
 title:String,space:String?=null,onHome:(()->Unit)?=null,onBack:(()->Unit)?=null,
 onLock:(()->Unit)?=null,onPanic:(()->Unit)?=null,actions:@Composable RowScope.()->Unit={},
) {
 Surface(color=MaterialTheme.colorScheme.surface.copy(alpha=.96f),tonalElevation=0.dp) {
  Row(Modifier.fillMaxWidth().heightIn(min=60.dp).testTag("nova-app-header").padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
   if(onBack!=null)IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}
   if(onHome!=null)IconButton(onClick=onHome){Icon(Icons.Default.Home,"Nova Home")}
   Column(Modifier.weight(1f).padding(horizontal=8.dp,vertical=8.dp)) {
    Text(title,style=if(title.length>16)MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
    if(space!=null)Text(space,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
   }
   actions()
   if(onLock!=null)IconButton(onClick=onLock){Icon(Icons.Default.Lock,"Lock Space")}
   if(onPanic!=null)IconButton(onClick=onPanic){Icon(Icons.Default.PowerSettingsNew,"Panic Exit",tint=MaterialTheme.colorScheme.primary)}
  }
 }
}

internal fun novaAppSymbol(app:HomeLayout.Shortcut):ImageVector=when(app) {
 HomeLayout.Shortcut.GALLERY->Icons.Default.PhotoLibrary
 HomeLayout.Shortcut.VAULT->Icons.Default.Lock
 HomeLayout.Shortcut.BROWSER->Icons.Default.Public
 HomeLayout.Shortcut.PRIVATE_APPS->Icons.Default.Apps
 HomeLayout.Shortcut.EVIDENCE_LOCKER->Icons.Default.FactCheck
 HomeLayout.Shortcut.CAMERA->Icons.Default.CameraAlt
 HomeLayout.Shortcut.COLLECTIONS->Icons.Default.FolderCopy
 HomeLayout.Shortcut.FAVOURITES->Icons.Default.Favorite
 HomeLayout.Shortcut.RECENTLY_DELETED->Icons.Default.DeleteOutline
 HomeLayout.Shortcut.SETTINGS->Icons.Default.Tune
}

@Composable internal fun NovaAppIcon(app:HomeLayout.Shortcut,size:Dp=64.dp) {
 val colors=when(app) {
  HomeLayout.Shortcut.GALLERY->listOf(Color(0xFF4A9DEC),Color(0xFF2459A3))
  HomeLayout.Shortcut.VAULT->listOf(Color(0xFF6584AC),Color(0xFF243956))
  HomeLayout.Shortcut.BROWSER->listOf(Color(0xFF44B3C9),Color(0xFF206880))
  HomeLayout.Shortcut.PRIVATE_APPS->listOf(Color(0xFF788AE2),Color(0xFF404C97))
  HomeLayout.Shortcut.EVIDENCE_LOCKER->listOf(Color(0xFF549F9C),Color(0xFF245653))
  HomeLayout.Shortcut.CAMERA->listOf(Color(0xFF6B839C),Color(0xFF293C52))
  HomeLayout.Shortcut.COLLECTIONS->listOf(Color(0xFF669AD5),Color(0xFF365A88))
  HomeLayout.Shortcut.FAVOURITES->listOf(Color(0xFFC482AD),Color(0xFF805775))
  HomeLayout.Shortcut.RECENTLY_DELETED->listOf(Color(0xFF82909E),Color(0xFF475563))
  HomeLayout.Shortcut.SETTINGS->listOf(Color(0xFF7498BE),Color(0xFF344C69))
 }
 val shape=RoundedCornerShape(size*.29f)
 Box(Modifier.size(size).shadow(3.dp,shape).clip(shape).background(Brush.linearGradient(colors))
  .border(1.dp,Color.White.copy(alpha=.18f),shape),contentAlignment=Alignment.Center) {
  Icon(novaAppSymbol(app),contentDescription=null,tint=Color.White,modifier=Modifier.size(size*.48f))
 }
}

internal class NovaSecurityActions(val home:()->Unit,val lock:()->Unit,val panic:()->Unit,val space:String?)
internal val LocalNovaSecurityActions=androidx.compose.runtime.staticCompositionLocalOf<NovaSecurityActions?>{null}
@Composable internal fun novaSecurityActions():NovaSecurityActions? {
 val provided=LocalNovaSecurityActions.current
 if(provided!=null)return provided
 val activity=androidx.activity.compose.LocalActivity.current as? uk.co.traynor.privategallery.MainActivity ?: return null
 return NovaSecurityActions(activity::returnPrivateHome,activity::lockNovaSpace,activity::panicExit,activity.novaSpaceLabel())
}

/** Full-page app workflow; only short confirmations create separate windows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun NovaWorkflowPage(
 onDismissRequest:()->Unit,title:@Composable ()->Unit,text:@Composable ()->Unit,
 confirmButton:@Composable ()->Unit,dismissButton:@Composable ()->Unit={},
) {
 val security=novaSecurityActions()
 androidx.activity.compose.BackHandler(onBack=onDismissRequest)
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
  Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
   Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
    IconButton(onClick=onDismissRequest){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}
    Box(Modifier.weight(1f).padding(vertical=12.dp)){title()}
    if(security!=null) {
     IconButton(onClick=security.home){Icon(Icons.Default.Home,"Nova Home")}
     IconButton(onClick=security.lock){Icon(Icons.Default.Lock,"Lock Space")}
     IconButton(onClick=security.panic){Icon(Icons.Default.PowerSettingsNew,"Panic Exit",tint=MaterialTheme.colorScheme.primary)}
    }
   }
   Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp)) {text()}
   FlowRow(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.End) {dismissButton();confirmButton()}
  }
 }
}
