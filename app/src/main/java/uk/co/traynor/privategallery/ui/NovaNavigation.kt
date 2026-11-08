package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** App-owned controls. The viewport owner preserves Android/IME insets exactly once. */
@Composable
internal fun NovaNavigationRow(
 onRecents:()->Unit,onHome:()->Unit,onBack:()->Unit,
 modifier:Modifier=Modifier,reverse:Boolean=false,onOverflow:(()->Unit)?=null,navigationEnabled:Boolean=true,
) {
 val tap = navigationHaptic()
 Surface(modifier.fillMaxWidth(),tonalElevation=4.dp) {
  Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("nova-navigation"),
   verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
   val order=if(reverse && onOverflow==null)listOf("back","home","recents")else listOf("recents","home","back")
   order.forEach {action ->
    IconButton(enabled=navigationEnabled,onClick={
     when(action) {
      "recents" -> onRecents()
      "home" -> { tap(); onHome() }
      else -> { tap(); onBack() }
     }
    },modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("nova-$action")) {
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

private fun navigationPreferences(context: Context) =
 context.applicationContext.getSharedPreferences("nova-navigation", Context.MODE_PRIVATE)

@Composable internal fun navigationHaptic(): () -> Unit {
 val view = LocalView.current
 val settings = navigationPreferences(LocalContext.current)
 return {
  if (settings.getBoolean("haptics", true)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
 }
}

@Composable internal fun NovaNavigationHapticsSetting() {
 val settings = navigationPreferences(LocalContext.current)
 var enabled by remember(settings) { mutableStateOf(settings.getBoolean("haptics", true)) }
 ListItem(
  headlineContent={Text("Navigation haptics")},
  supportingContent={Text("Light feedback for NovaOS Home and Back, following Android’s touch feedback setting.")},
  trailingContent={Switch(enabled,{ value ->
   enabled = value
   settings.edit().putBoolean("haptics",value).apply()
  },modifier=Modifier.testTag("nova-navigation-haptics"))},
 )
}

/** Uses the same authenticated Home owner and the same Back dispatcher as system Back. */
@Composable
internal fun NovaNativeNavigation(onBack:(()->Unit)?=null) {
 val activity=androidx.activity.compose.LocalActivity.current as? uk.co.traynor.privategallery.MainActivity
 val security=novaSecurityActions() ?: return
 NovaNavigationRow(onRecents={activity?.openNovaRecents() ?: security.home()},onHome=security.home,
  onBack=onBack ?: {activity?.onBackPressedDispatcher?.onBackPressed();Unit})
}
