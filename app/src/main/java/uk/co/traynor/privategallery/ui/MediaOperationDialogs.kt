package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.vault.VaultCollection

/** Production modal content only; callers retain operation, permission and lifecycle authority. */
@Composable
private fun ModalAction(label:String,enabled:Boolean=true,action:()->Unit) {
    TextButton(modifier=Modifier.heightIn(min=48.dp).widthIn(min=48.dp),enabled=enabled,onClick=action) {Text(label)}
}
@Composable
internal fun PublicCopyExportDialog(openExisting:Boolean,onDismiss:()->Unit,onConfirm:()->Unit) {
    GalleryAlertDialog(modifier=Modifier.testTag("media-operation-dialog"),onDismissRequest=onDismiss,title={Text(if(openExisting)"Open exported photo?" else "Export a public copy?")},
        text={Text("The copy will be visible in phone Gallery. Edit and save it there, then return and import the edited photo. Public copies are removed only with your confirmation after encrypted import. Editor caches or cloud copies are not covered.")},
        confirmButton={ModalAction(if(openExisting)"Open Gallery" else "Export copy",action=onConfirm)},dismissButton={ModalAction("Cancel",action=onDismiss)})
}
@Composable
internal fun VaultTrashConfirmationDialog(onDismiss:()->Unit,onConfirm:()->Unit) {
    GalleryAlertDialog(modifier=Modifier.testTag("media-operation-dialog"),onDismissRequest=onDismiss,title={Text("Move to Recently Deleted?")},
        text={Text("The encrypted Vault copy can be restored for 30 days, then it is deleted permanently.")},
        confirmButton={ModalAction("Move",action=onConfirm)},dismissButton={ModalAction("Cancel",action=onDismiss)})
}
@Composable
internal fun VideoOpenErrorDialog(error:String,onRetry:()->Unit,onClose:()->Unit) {
    GalleryAlertDialog(modifier=Modifier.testTag("media-operation-dialog"),onDismissRequest=onClose,title={Text("Video could not be opened")},text={Text(error)},
        confirmButton={ModalAction("Retry",action=onRetry)},dismissButton={ModalAction("Close",action=onClose)})
}
@Composable
internal fun HiddenCollectionChoiceDialog(collections:List<VaultCollection>,onChoose:(String)->Unit,onDismiss:()->Unit) {
    GalleryAlertDialog(modifier=Modifier.testTag("media-operation-dialog"),scrollBody=false,onDismissRequest=onDismiss,title={Text("Add to collection")},text={
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp)) {
            if(collections.isEmpty())item {Text("Create a collection in Hidden first.")}
            items(collections,key={it.id}) {collection ->
                TextButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),onClick={onChoose(collection.id)}) {
                    Text(collection.name,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    },confirmButton={ModalAction("Close",action=onDismiss)})
}
@Composable
internal fun PromptDiagnosticDialog(original:String,effective:String,onClose:()->Unit) {
    GalleryAlertDialog(modifier=Modifier.testTag("media-operation-dialog"),scrollBody=false,onDismissRequest=onClose,title={Text("Prompt diagnostic")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
            Text("Original prompt");Text(original);Spacer(Modifier.height(12.dp));Text("Effective prompt");Text(effective)
        }
    },confirmButton={ModalAction("Close",action=onClose)})
}
@Composable
internal fun PredictionDiagnosticDialog(details:String,onClose:()->Unit) {
    GalleryAlertDialog(modifier=Modifier.testTag("media-operation-dialog"),onDismissRequest=onClose,title={Text("Replicate prediction")},text={Text(details)},confirmButton={ModalAction("Close",action=onClose)})
}
@Composable
internal fun AiOperationProgressDialog(provider:String,status:String,elapsed:String,location:String,onCancel:()->Unit) {
    GalleryAlertDialog(modifier=Modifier.testTag("media-operation-dialog"),scrollBody=false,onDismissRequest={},title={Text("Creating your edit")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(provider);LinearProgressIndicator(Modifier.fillMaxWidth());Text(status)
            Text(elapsed,style=MaterialTheme.typography.bodySmall);Text(location,style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={ModalAction("Cancel",action=onCancel)})
}
@Composable
internal fun AiSaveProgressDialog(stage:String,saved:Boolean,onCancel:()->Unit) {
    GalleryAlertDialog(modifier=Modifier.testTag("media-operation-dialog"),onDismissRequest={},title={Text(if(saved)stage else "Saving encrypted copy")},text={
        if(saved)Text("Your encrypted copy is saved.") else Column {Text(stage);LinearProgressIndicator(Modifier.fillMaxWidth())}
    },confirmButton={if(!saved)ModalAction("Cancel",action=onCancel)})
}
@Composable
internal fun RecordingDetailsContentDialog(details:String,integrity:String,verifying:Boolean,onVerify:()->Unit,onClose:()->Unit) {
    GalleryAlertDialog(modifier=Modifier.testTag("media-operation-dialog"),scrollBody=false,onDismissRequest=onClose,title={Text("Recording details")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            Text(details,style=MaterialTheme.typography.bodySmall);Text(integrity,style=MaterialTheme.typography.labelMedium)
            ModalAction(if(verifying)"Verifying…" else "Verify integrity",!verifying,onVerify)
            Spacer(Modifier.height(16.dp))
            Text("Hashes detect changes when checked against the original. They do not prove who recorded the media or legal admissibility. Edited copies are separate items.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={ModalAction("Done",action=onClose)})
}
@Composable
internal fun VideoPlaybackSpeedMenu(speed:Float,onSelect:(Float)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {
        TextButton(modifier=Modifier.heightIn(min=48.dp).semantics {contentDescription="Playback speed";stateDescription="${speed}×"},onClick={expanded=true}) {Text("${speed}×",color=androidx.compose.ui.graphics.Color.White)}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            listOf(.5f,1f,1.5f,2f).forEach {value ->
                DropdownMenuItem(modifier=Modifier.heightIn(min=48.dp),text={Text("${value}×")},
                    leadingIcon={RadioButton(selected=speed==value,onClick=null)},onClick={onSelect(value);expanded=false})
            }
        }
    }
}
