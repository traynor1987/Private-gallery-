package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Presentation only: callbacks retain each browser's separate session and authority. */
@Composable internal fun BrowserAddressBar(address:String,onAddress:(String)->Unit,enabled:Boolean,loading:Boolean,
    submit:()->Unit,reloadOrStop:()->Unit,onFocus:(Boolean)->Unit) {
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        TextField(address,onAddress,Modifier.weight(1f).onFocusChanged {onFocus(it.isFocused)}.semantics {testTag="browser-v2-address"},
            singleLine=true,enabled=enabled,shape=RoundedCornerShape(16.dp),
            colors=TextFieldDefaults.colors(focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent),
            leadingIcon={Icon(Icons.Default.Language,"Web address")},trailingIcon={IconButton(enabled=enabled,onClick={focus.clearFocus();keyboard?.hide();submit()}){Icon(Icons.AutoMirrored.Filled.ArrowForward,"Go")}},placeholder={Text("Search or enter address")},
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Go),keyboardActions=KeyboardActions(onGo={focus.clearFocus();keyboard?.hide();submit()}))
        IconButton(enabled=enabled,modifier=Modifier.semantics {testTag="browser-v2-reload"},onClick=reloadOrStop) {Icon(if(loading)Icons.Default.Close else Icons.Default.Refresh,if(loading)"Stop" else "Reload")}
    }
}
@Composable internal fun BrowserNavigationToolbar(canBack:Boolean,canForward:Boolean,tabs:Int,back:()->Unit,forward:()->Unit,bookmarks:()->Unit,showTabs:()->Unit,settings:()->Unit,more:()->Unit) {
    GalleryBrowserToolbar(Modifier.semantics {testTag="browser-v2-toolbar"}) {
        IconButton(enabled=canBack,onClick=back,modifier=Modifier.size(48.dp)){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}
        IconButton(enabled=canForward,onClick=forward,modifier=Modifier.size(48.dp)){Icon(Icons.AutoMirrored.Filled.ArrowForward,"Forward")}
        IconButton(onClick=bookmarks,modifier=Modifier.size(48.dp)){Icon(Icons.Default.StarOutline,"Bookmarks")}
        IconButton(onClick=showTabs,modifier=Modifier.size(48.dp).semantics {testTag="browser-v2-tabs"}){Box(contentAlignment=Alignment.Center){Icon(Icons.Default.CropSquare,"Tabs",Modifier.size(32.dp));Text(tabs.toString(),style=MaterialTheme.typography.labelSmall)}}
        IconButton(onClick=settings,modifier=Modifier.size(48.dp)){Icon(Icons.Default.Settings,"Browser settings")}
        IconButton(onClick=more,modifier=Modifier.size(48.dp)){Icon(Icons.Default.Menu,"More")}
    }
}
@Composable internal fun BrowserPageError(detail:String,retry:()->Unit,settings:()->Unit) {
    Surface(Modifier.fillMaxSize().semantics {testTag="browser-page-error"}) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Text("Page unavailable",style=MaterialTheme.typography.titleLarge);Text(detail)
            TextButton(onClick=retry){Text("Retry page")};TextButton(onClick=settings){Text("Browser settings")}
        }
    }
}
