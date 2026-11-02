package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/** Shared V1 connection presentation; caller retains every transport and session decision. */
@Composable internal fun NovaConnectionPanel(
 tor:Boolean,title:String,message:String,busy:Boolean,action:String?,onAction:()->Unit,
 settingsLabel:String?,onSettings:()->Unit,detail:String?=null,progressDescription:String?=null,
 modifier:Modifier=Modifier,
) {
 var information by remember{mutableStateOf(false)}
 Box(modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
  Column(Modifier.widthIn(max=480.dp).verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
   Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer) {
    Icon(if(tor)Icons.Default.Shield else Icons.Default.VpnLock,null,Modifier.padding(22.dp).size(42.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
   }
   Text(if(tor)"Tor — Beta"else"Browser — Beta",style=MaterialTheme.typography.headlineSmall)
   Text(title,style=MaterialTheme.typography.titleMedium)
   Text(message,color=MaterialTheme.colorScheme.onSurfaceVariant)
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().then(if(progressDescription==null)Modifier else Modifier.semantics{contentDescription=progressDescription}))
   if(action!=null)Button(onClick=onAction){Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(8.dp));Text(action)}
   if(settingsLabel!=null)OutlinedButton(onClick=onSettings){Icon(Icons.Default.Settings,null);Spacer(Modifier.width(8.dp));Text(settingsLabel)}
   if(detail!=null) {
    TextButton(onClick={information=!information}){Text(if(information)"Hide connection details"else"Connection details")}
    if(information)Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
 }
}

@Composable internal fun NovaBrowserConnectionSurface(tor:Boolean,status:String,detail:String,onRetry:()->Unit,onSettings:()->Unit) {
 val failed=status.contains("failed",true)||status=="Disconnected"
 NovaConnectionPanel(tor,if(failed)"Connection unavailable"else"Preparing your private connection",
  if(tor)"Tor only · JavaScript off"else"VPN only · interactive websites",!failed,
  if(failed)"Retry connection"else null,onRetry,"Connection settings",onSettings,"$status\n$detail",
  modifier=Modifier.testTag("nova-browser-connection"))
}

@Composable internal fun NovaBrowserStatus(tor:Boolean,ready:Boolean,detail:String,onDetails:()->Unit) {
 Surface(color=MaterialTheme.colorScheme.surfaceContainerLow) {
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
   Icon(if(ready)Icons.Default.VerifiedUser else Icons.Default.Shield,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary)
   Text(if(tor)"Tor only"else"VPN only",Modifier.weight(1f).padding(8.dp),style=MaterialTheme.typography.labelMedium)
   TextButton(onClick=onDetails){Text(if(ready)"Connected"else"Details")}
  }
 }
}
