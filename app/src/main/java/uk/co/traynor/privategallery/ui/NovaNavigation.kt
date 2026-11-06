package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** App-owned controls. The viewport owner preserves Android/IME insets exactly once. */
@Composable
internal fun NovaNavigationRow(
 onRecents:()->Unit,onHome:()->Unit,onBack:()->Unit,
 modifier:Modifier=Modifier,reverse:Boolean=false,onOverflow:(()->Unit)?=null,
) {
 Surface(modifier.fillMaxWidth(),tonalElevation=4.dp) {
  Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("nova-navigation"),
   verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
   val order=if(reverse && onOverflow==null)listOf("back","home","recents")else listOf("recents","home","back")
   order.forEach {action ->
    IconButton(onClick=when(action){"recents"->onRecents;"home"->onHome;else->onBack},
     modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("nova-$action")) {
     Icon(when(action){"recents"->Icons.Default.ViewWeek;"home"->Icons.Default.CropSquare;else->Icons.Default.ChevronLeft},
      action.replaceFirstChar{it.uppercase()},Modifier.size(23.dp))
    }
   }
   if(onOverflow!=null)IconButton(onClick=onOverflow,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("web-app-menu")) {
    Icon(Icons.Default.MoreVert,"App menu")
   }
  }
 }
}

/** Uses the same authenticated Home owner and the same Back dispatcher as system Back. */
@Composable
internal fun NovaNativeNavigation(onBack:(()->Unit)?=null) {
 val activity=androidx.activity.compose.LocalActivity.current as? uk.co.traynor.privategallery.MainActivity
 val security=novaSecurityActions() ?: return
 NovaNavigationRow(onRecents={activity?.openNovaRecents() ?: security.home()},onHome=security.home,
  onBack=onBack ?: {activity?.onBackPressedDispatcher?.onBackPressed();Unit})
}
