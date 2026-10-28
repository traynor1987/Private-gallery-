package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uk.co.traynor.privategallery.core.browser.v2.BrowserFocusMode

/** Actual production permission/save surfaces. Decisions remain in the authenticated owner. */
internal enum class BrowserConsentSurface(val title:String,val message:String,val acceptLabel:String,val cancelLabel:String) {
    CAPABILITY("Website permission","Allow this website to use the requested capability for this session?","Allow this time","Deny"),
    LOCATION("Website location","Allow this website to use your location for this session?","Allow this time","Deny"),
    SAVE_IMAGE("Save image to Vault","Save the image resource currently made available to this Browser session?","Save to Vault","Cancel"),
    SCREENSHOT("Displayed image","This visual has no safe image resource. Save the visible Browser viewport as a screenshot instead?","Screenshot to Vault","Cancel"),
}

@Composable
internal fun BrowserConsentDialog(surface:BrowserConsentSurface,onAccept:()->Unit,onCancel:()->Unit) {
    GalleryAlertDialog(onDismissRequest=onCancel,title={Text(surface.title)},text={Text(surface.message)},
        confirmButton={TextButton(onClick=onAccept,modifier=Modifier.heightIn(min=48.dp)){Text(surface.acceptLabel)}},
        dismissButton={TextButton(onClick=onCancel,modifier=Modifier.heightIn(min=48.dp)){Text(surface.cancelLabel)}})
}

@Composable
internal fun BrowserMessageDialog(value:String,onClose:()->Unit) {
    GalleryAlertDialog(onDismissRequest=onClose,text={Text(value)},
        confirmButton={TextButton(onClick=onClose,modifier=Modifier.heightIn(min=48.dp)){Text("OK")}})
}

@Composable
internal fun BrowserSaveVideoDialog(busy:Boolean,progress:Int?,stage:String,mime:String?,onSave:()->Unit,onCancel:()->Unit) {
    GalleryAlertDialog(onDismissRequest={if(!busy)onCancel()},title={Text("Save video to Vault")},
        text={Text(if(busy)progress?.let {"$stage $it%"} ?: stage else "Save the detected ${mime ?: "video"} from this page as an encrypted Vault item?")},
        confirmButton={if(!busy)TextButton(onClick=onSave,modifier=Modifier.heightIn(min=48.dp)){Text("Save to Vault")}},
        dismissButton={TextButton(onClick=onCancel,modifier=Modifier.heightIn(min=48.dp)){Text("Cancel")}})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrowserFindDialog(value:String,onValueChange:(String)->Unit,onNext:(Boolean)->Unit,onClose:()->Unit) {
    GalleryAlertDialog(scrollBody=false,onDismissRequest=onClose,title={Text("Find in page")},
        text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            TextField(value,onValueChange,modifier=Modifier.fillMaxWidth().testTag("browser-find-query"),singleLine=true,
                label={Text("Find text")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={onNext(true)}))
            FlowRow {TextButton(onClick={onNext(false)},modifier=Modifier.heightIn(min=48.dp)){Text("Previous")};TextButton(onClick={onNext(true)},modifier=Modifier.heightIn(min=48.dp)){Text("Next")}}
        }},confirmButton={TextButton(onClick=onClose,modifier=Modifier.heightIn(min=48.dp)){Text("Close")}})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrowserDiagnosticsDialog(report:String,busy:Boolean,verbose:Boolean,armFailed:Boolean,onArm:()->Unit,onCapture:()->Unit,
    onFocus:(BrowserFocusMode)->Unit,onToggleVerbose:()->Unit,onCopy:()->Unit,onClear:()->Unit,onClose:()->Unit) {
    GalleryAlertDialog(scrollBody=false,onDismissRequest=onClose,title={Text("Browser diagnostics")},
        text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            Text("Clear removes current-session events. The previous-process fatal report is retained.",style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=!busy && verbose,onClick=onArm,modifier=Modifier.heightIn(min=48.dp)){Text("Arm next interaction")}
            TextButton(enabled=!busy && verbose,onClick=onCapture,modifier=Modifier.heightIn(min=48.dp)){Text(if(busy)"Capturing…" else "Capture diagnostic snapshot")}
            if(armFailed)Text("Could not arm this page. Wait for loading to finish and try again.")
            Text("Arm before the failing tap. Capture afterwards, then Copy all. Structure only; no browsing content.",style=MaterialTheme.typography.bodySmall)
            Text("Browser compatibility test",style=MaterialTheme.typography.titleSmall)
            FlowRow {
                TextButton(onClick={onFocus(BrowserFocusMode.CURRENT)},modifier=Modifier.heightIn(min=48.dp)){Text("Current")}
                TextButton(onClick={onFocus(BrowserFocusMode.EXPLICIT_WEBVIEW_FOCUS)},modifier=Modifier.heightIn(min=48.dp)){Text("Explicit WebView focus")}
            }
            TextButton(onClick=onToggleVerbose,modifier=Modifier.heightIn(min=48.dp)){Text(if(verbose)"Verbose diagnostics: on" else "Verbose diagnostics: off")}
            TextButton(enabled=!busy,onClick=onCopy,modifier=Modifier.heightIn(min=48.dp)){Text("Copy all")}
            TextButton(onClick=onClear,modifier=Modifier.heightIn(min=48.dp)){Text("Clear current session")}
            Text(report,style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick=onClose,modifier=Modifier.heightIn(min=48.dp)){Text("Close")}})
}

internal enum class BrowserExternalChoice(val message:String,val acceptLabel:String) {
    APP("This link can be opened by another app.","Open"),
    WEBSITE("No compatible app is available. Open the safe website fallback here instead?","Open website"),
    UNSUPPORTED("This link requires an external app or uses an unsupported link type.","OK"),
}

@Composable
internal fun BrowserExternalNavigationDialog(choice:BrowserExternalChoice,onAccept:()->Unit,onCancel:()->Unit) {
    GalleryAlertDialog(onDismissRequest=onCancel,title={Text("Open in app?")},text={Text(choice.message)},
        confirmButton={TextButton(onClick=onAccept,modifier=Modifier.heightIn(min=48.dp)){Text(choice.acceptLabel)}},
        dismissButton={TextButton(onClick=onCancel,modifier=Modifier.heightIn(min=48.dp)){Text("Cancel")}})
}

@Composable
internal fun BrowserInternalMediaDialog(media:Pair<String,String>,networkAllowed:()->Boolean,onClose:()->Unit,onReady:()->Unit,onFallback:()->Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val item=remember(media){androidx.media3.common.MediaItem.Builder().setUri(media.first).setMimeType(media.second).build()}
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            PrivateVideoPlayer(item,networkAllowed=networkAllowed,onClose=onClose,onReady=onReady,onWebViewFallback=onFallback)
        }
    }
}
