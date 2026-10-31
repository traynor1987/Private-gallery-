package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.SecureFlagPolicy
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog

/** Presentation only: UUID callbacks never carry transport, credentials or arbitrary URLs. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PrivateAppsDrawerTile(
    app:PrivateAppCatalog.App, index:Int, total:Int, enabled:Boolean,
    reordering:Boolean, selected:Boolean, menuExpanded:Boolean,
    onTap:()->Unit, onActions:()->Unit, onDismissMenu:()->Unit, onAction:(String)->Unit,
) {
    Box(Modifier.fillMaxWidth().testTag("private-app-tile-${app.id()}")
        .semantics {stateDescription=(if(selected)"Selected · " else "")+(if(app.pinned())"Pinned · " else "")+"App ${index+1} of $total"}
        .combinedClickable(enabled=enabled,role=Role.Button,
            onClickLabel=if(reordering)"Select app or new position" else "Open ${app.name()}",onClick=onTap,
            onLongClickLabel="App actions",onLongClick=if(reordering)null else onActions)) {
        Column(Modifier.fillMaxWidth().padding(top=20.dp,bottom=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Surface(shape=RoundedCornerShape(18.dp),color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                border=if(selected)androidx.compose.foundation.BorderStroke(2.dp,MaterialTheme.colorScheme.primary)else null) {
                Box(Modifier.size(64.dp),contentAlignment=Alignment.Center) {
                    if(app.icon().isBlank())Text(String(Character.toChars(app.name().codePointAt(0))).uppercase(),style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.onSecondaryContainer)
                    else PrivateAppIcon(app.icon(),app.name(),52)
                    if(app.pinned())Icon(Icons.Default.PushPin,null,Modifier.align(Alignment.TopStart).padding(3.dp).size(14.dp),tint=MaterialTheme.colorScheme.primary)
                }
            }
            Text(app.name(),Modifier.fillMaxWidth().padding(top=8.dp).heightIn(min=36.dp),
                textAlign=TextAlign.Center,style=MaterialTheme.typography.labelLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        IconButton(onClick=onActions,enabled=enabled && !reordering,modifier=Modifier.align(Alignment.TopEnd).size(48.dp)) {
            Icon(Icons.Default.MoreVert,"App actions for ${app.name()}",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded=menuExpanded,onDismissRequest=onDismissMenu,
            properties=PopupProperties(focusable=true,securePolicy=SecureFlagPolicy.SecureOn)) {
            for(label in listOf("Edit",if(app.pinned())"Unpin"else"Pin","Reorder","App Settings","Remove")) {
                DropdownMenuItem(text={Text(label)},onClick={onAction(label)},enabled=enabled)
            }
        }
    }
}

/** Shared production layout: actual constraints, not the size of a parent outside its Dialog. */
@Composable
internal fun PrivateAppsDrawerGrid(
    apps:List<PrivateAppCatalog.App>,visibleApps:List<PrivateAppCatalog.App>,enabled:Boolean,
    reordering:Boolean,selectedId:String?,menuId:String?,
    onTap:(PrivateAppCatalog.App,Int)->Unit,onActions:(String)->Unit,onDismissMenu:()->Unit,
    onAction:(PrivateAppCatalog.App,String)->Unit,modifier:Modifier=Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns=((maxWidth.value+8f)/(72f*LocalDensity.current.fontScale.coerceAtLeast(1f)+8f)).toInt().coerceIn(1,8)
        LazyVerticalGrid(columns=GridCells.Fixed(columns),modifier=Modifier.fillMaxSize().testTag("private-app-grid"),
            horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            items(visibleApps,key={it.id()}) {app ->
                val index=apps.indexOfFirst{it.id()==app.id()}
                PrivateAppsDrawerTile(app,index,apps.size,enabled,reordering,selectedId==app.id(),menuId==app.id(),
                    onTap={onTap(app,index)},onActions={onActions(app.id())},onDismissMenu=onDismissMenu,
                    onAction={onAction(app,it)})
            }
        }
    }
}
