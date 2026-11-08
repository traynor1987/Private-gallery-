package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Stateless shortcuts only: opening AI selects sources and never prepares or sends a payload. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun EvidenceDashboardOverview(
 evidenceCount:Int,incidentCount:Int,busy:Boolean,query:String,onQuery:(String)->Unit,
 onImport:()->Unit,onExisting:()->Unit,onLibrary:()->Unit,onTimeline:()->Unit,onAi:()->Unit,
 onNewIncident:()->Unit,onArchive:()->Unit,onRefresh:()->Unit,
 onConnect:(()->Unit)?=null,onPhotos:(()->Unit)?=null,
) {
 Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
  Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),modifier=Modifier.fillMaxWidth()) {
   Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Text("Your evidence, organised",style=MaterialTheme.typography.headlineSmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     EvidenceDashboardCount(evidenceCount,"Evidence records")
     EvidenceDashboardCount(incidentCount,"Active incidents")
    }
    Button(enabled=!busy,onClick=onConnect?:onImport,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
     Icon(Icons.Default.Add,contentDescription=null);Spacer(Modifier.width(8.dp));Text(if(onConnect==null)"Import Evidence" else "Connect Vault collection")
    }
   }
  }
  OutlinedTextField(value=query,onValueChange=onQuery,label={Text("Search evidence")},
   leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth().testTag("evidence-dashboard-search"))
  FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   EvidenceDashboardAction("Browse library",Icons.Default.Folder,onLibrary)
   EvidenceDashboardAction("View timeline",Icons.Default.DateRange,onTimeline)
   EvidenceDashboardAction("AI analysis",Icons.Default.AutoAwesome,onAi)
  }
  FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
   if(onConnect!=null)TextButton(enabled=!busy,onClick=onImport){Text("Import Evidence")}
   onPhotos?.let{TextButton(enabled=!busy,onClick=it){Text("Pick screenshots")}}
   TextButton(enabled=!busy,onClick=onExisting){Text("Add existing evidence")}
   TextButton(enabled=!busy,onClick=onNewIncident){Text("New incident")}
   TextButton(enabled=!busy,onClick=onArchive){Text("Open encrypted export")}
   TextButton(enabled=!busy,onClick=onRefresh){Text("Refresh originals")}
  }
 }
}

@Composable private fun EvidenceDashboardCount(value:Int,label:String) {
 Column {Text(value.toString(),style=MaterialTheme.typography.headlineMedium);Text(label,style=MaterialTheme.typography.labelLarge)}
}

@Composable private fun EvidenceDashboardAction(label:String,icon:ImageVector,onClick:()->Unit) {
 OutlinedButton(onClick=onClick,modifier=Modifier.heightIn(min=48.dp)) {
  Icon(icon,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(label)
 }
}

@Composable internal fun EvidenceWorkflowHeading(title:String,subtitle:String) {
 Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
   Text(title,style=MaterialTheme.typography.titleLarge)
   Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}
