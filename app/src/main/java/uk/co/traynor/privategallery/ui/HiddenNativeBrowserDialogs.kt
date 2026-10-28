package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** UI only: the authenticated owner decides whether management can return to Hidden Vault. */
@Composable internal fun HiddenDownloadsDialog(status:String,onManage:()->Unit,onClose:()->Unit) {
    GalleryAlertDialog(onDismissRequest=onClose,title={Text("Hidden downloads")},text={
        Column {if(status.isNotBlank())Text(status)
            Text("Supported GET image/video downloads are saved encrypted in Hidden Vault. Return to Hidden Vault to manage saved media. Returning closes all browser tabs and cancels transfers still in progress.")}
    },confirmButton={TextButton(onClick=onManage){Text("Manage in Hidden Vault")}},dismissButton={TextButton(onClick=onClose){Text("Keep browsing")}})
}
/** Find presentation only; WebView matching and lifecycle stay with the isolated owner. */
@Composable internal fun HiddenFindBar(query:String,result:String,onChange:(String)->Unit,onNext:(Boolean)->Unit,onClose:()->Unit) {
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(query,onChange,Modifier.fillMaxWidth(),singleLine=true,label={Text("Find in page")},
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}))
        GalleryBrowserToolbar {
            TextButton(onClick={onNext(false)}){Text("Previous")}
            TextButton(onClick={onNext(true)}){Text("Next")}
            TextButton(onClick=onClose){Text("Close find")}
        }
        Text(result,Modifier.heightIn(max=72.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)
    }
}

/** Production native-child modal content; transport and authority checks remain in its owner. */
@Composable internal fun HiddenNativeAuthContent(realm:String,user:String,password:String,onUser:(String)->Unit,onPassword:(String)->Unit) {
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    Column {Text(realm);OutlinedTextField(user,{onUser(it.take(4096))},Modifier.fillMaxWidth(),label={Text("Username")},singleLine=true,
            keyboardOptions=KeyboardOptions(autoCorrect=false,imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Next)}))
        OutlinedTextField(password,{onPassword(it.take(4096))},Modifier.fillMaxWidth(),label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),
            keyboardOptions=KeyboardOptions(autoCorrect=false,keyboardType=KeyboardType.Password,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}))}
}
@Composable internal fun HiddenNativeWebsiteContent(origin:String,message:String,prompt:Boolean,response:String,onResponse:(String)->Unit) {
    Column {Text(origin,style=MaterialTheme.typography.labelMedium);Text(message)
        if(prompt)OutlinedTextField(response,{onResponse(it.take(8000))},Modifier.fillMaxWidth(),label={Text("Response")})}
}
@Composable internal fun HiddenNativeResubmitContent() {Text("Reloading this page can repeat a previous action, upload or purchase.")}
@Composable internal fun HiddenNativeUploadConsentContent(host:String?,count:Int) {Text("Allow the page at $host to access $count selected item(s)? It can keep this media and send it to other destinations. This is an explicit copy outside your vault.")}
@Composable internal fun HiddenNativePickerIntro(multiple:Boolean,restricted:Int,types:Int,loading:Boolean,loaded:Int,total:Int,error:String?,empty:Boolean) {
    Column {
        Text("Vault-only items cannot be uploaded. Only this authenticated Hidden Vault is shown.")
        Text(if(multiple)"Select up to four compatible items, then review the upload." else "Select one compatible item. Nothing is sent before confirmation.")
        Text("$restricted Vault-only restricted item(s) cannot be uploaded. $types other item(s) do not match this website's accepted file types.")
        if(loading){Text("Loading Hidden media… $loaded of $total");LinearProgressIndicator(Modifier.fillMaxWidth())}
        error?.let {Text(it)}
        if(!loading && error==null && empty)Text("No eligible Hidden media matches this website's accepted file types.")
    }
}
@Composable internal fun HiddenNativeLibraryContent(urls:List<String>,bookmarks:Boolean,select:(String)->Unit,remove:(String)->Unit) {
    LazyColumn(Modifier.heightIn(max=420.dp)) {
        if(urls.isEmpty())item {Text("No saved pages in this mode.")}
        itemsIndexed(urls) {index,url ->Row {
        TextButton(modifier=Modifier.weight(1f),onClick={select(url)}){Text(url,maxLines=3,overflow=TextOverflow.Ellipsis)}
        if(bookmarks)TextButton(onClick={remove(url)},modifier=Modifier.semantics {contentDescription="Remove bookmark ${index+1}"}){Text("Remove")}
    }}}
}

@Composable internal fun HiddenNativeSettingsContent(tor:Boolean,indicator:String,saveHistory:Boolean,desktop:Boolean,bookmark:()->Unit,bookmarks:()->Unit,history:()->Unit,setHistory:(Boolean)->Unit,clearHistory:()->Unit,toggleDesktop:()->Unit,download:()->Unit,clearClose:()->Unit,find:()->Unit={},downloads:()->Unit={},searchEngine:uk.co.traynor.privategallery.core.browser.BrowserSearchEngine=uk.co.traynor.privategallery.core.browser.BrowserSearchEngine.DUCKDUCKGO,setSearchEngine:(uk.co.traynor.privategallery.core.browser.BrowserSearchEngine)->Unit={}) {
    Column {
            Text(if(tor)"Tor Only uses isolated native forms and uploads with JavaScript off. DNS and bypass traffic are blocked by the local isolation boundary. This is not Tor Browser." else "JavaScript, forms and POST run through the owned VPN. Closing this window ends all tabs and session data.")
            TextButton(onClick=find){Text("Find in page")}
            TextButton(onClick=downloads){Text("Downloads")}
            Text("Search provider")
            uk.co.traynor.privategallery.core.browser.BrowserSearchEngine.entries.forEach {engine ->
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {RadioButton(selected=engine==searchEngine,onClick={setSearchEngine(engine)});Text(engine.label)}
            }
            TextButton(onClick={bookmark()}){Text("Bookmark page")}
            TextButton(onClick={bookmarks()}){Text("Bookmarks")}
            TextButton(onClick={history()}){Text("History")}
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Switch(modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics {contentDescription="Save Hidden $indicator history"},checked=saveHistory,onCheckedChange={setHistory(it)});Text("Save Hidden $indicator history")}
            TextButton(onClick={clearHistory()}){Text("Clear Hidden history")}
            Text("Bookmarks and optional history stay encrypted in Hidden Vault. Clearing browsing data retains bookmarks.")
            TextButton(onClick={toggleDesktop()}){Text(if(desktop)"Desktop layout on" else "Desktop layout off")}
            TextButton(onClick={download()}){Text("Save GET media to Hidden Vault")}
            TextButton(onClick={clearClose()}){Text("Clear browsing data and close")}
    }
}

internal data class HiddenNativeUploadItem(val id:String,val name:String,val mime:String)

@Composable internal fun HiddenNativeUploadPickerGrid(
    items:List<HiddenNativeUploadItem>,loading:Boolean,multiple:Boolean,restricted:Int,types:Int,total:Int,error:String?,selected:Set<String>,
    preview:(HiddenNativeUploadItem,(Result<android.graphics.Bitmap>)->Unit)->Unit,choose:(HiddenNativeUploadItem)->Unit,
) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns=androidx.compose.foundation.lazy.grid.GridCells.Adaptive(88.dp),modifier=Modifier.heightIn(max=360.dp),
        horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item(span={androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan)}) {
            HiddenNativePickerIntro(multiple,restricted,types,loading,items.size,total,error,items.isEmpty())
        }
        items(if(loading)0 else items.size,key={items[it].id}) {index ->
            val item=items[index]
            GalleryMediaSelectionTile(item.id,item.name,item.mime.startsWith("video/"),item.id in selected,!loading,{callback ->preview(item,callback)}){choose(item)}
        }
    }
}
