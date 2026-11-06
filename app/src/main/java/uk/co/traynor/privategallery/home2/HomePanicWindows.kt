@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
package uk.co.traynor.privategallery.home2

import android.view.ViewParent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.ui.playerActivity

/** Dialogs own Window callbacks; they do not pass their gestures through Activity.dispatchTouchEvent. */
@Composable
private fun OwnedHomePanicWindow() {
    val activity=LocalContext.current.playerActivity() as? MainActivity
    val view=LocalView.current
    fun ownedWindow():android.view.Window? {
        var parent:ViewParent?=view.parent
        var found:android.view.Window?=null
        while(parent!=null && found==null) {
            if(parent is DialogWindowProvider)found=parent.window
            parent=parent.parent
        }
        return found
    }
    DisposableEffect(activity,view) {
        var binding:AutoCloseable?=null
        fun bind() {if(binding==null && activity!=null)ownedWindow()?.let {binding=activity.bindOwnedPanicWindow(it)}}
        val listener=object:android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(attached:android.view.View){bind()}
            override fun onViewDetachedFromWindow(detached:android.view.View){binding?.close();binding=null}
        }
        view.addOnAttachStateChangeListener(listener);bind()
        onDispose {view.removeOnAttachStateChangeListener(listener);binding?.close()}
    }
}

@Composable
internal fun PanicHomeDialog(onDismissRequest:()->Unit,properties:DialogProperties=DialogProperties(),content:@Composable ()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest,properties){OwnedHomePanicWindow();content()}
}
@Composable
internal fun PanicHomeModalBottomSheet(onDismissRequest:()->Unit,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest=onDismissRequest,modifier=modifier){OwnedHomePanicWindow();content()}
}
@Composable
internal fun PanicHomeAlertDialog(onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,modifier:Modifier=Modifier,
    dismissButton:(@Composable ()->Unit)?=null,title:(@Composable ()->Unit)?=null,text:(@Composable ()->Unit)?=null) {
    androidx.compose.material3.AlertDialog(onDismissRequest=onDismissRequest,
        confirmButton={OwnedHomePanicWindow();confirmButton()},modifier=modifier,dismissButton=dismissButton,title=title,text=text)
}

private class PopupPanicResult {var consume=false}
/** Popups have no public Window callback. Observe the original native event once, then consume
 * its Compose changes only when the SAME Activity gate has triggered/consumed that event. */
private fun Modifier.popupPanicInput():Modifier=composed {
    val activity=LocalContext.current.playerActivity() as? MainActivity
    val result=remember(activity){PopupPanicResult()}
    val watcher=remember(activity,result){{event:android.view.MotionEvent->
        result.consume=activity?.dispatchOwnedPanicTouch(event){false}==true
    }}
    if(activity==null)this else this.motionEventSpy(watcher).pointerInput(activity,result) {
        awaitPointerEventScope {while(true) {
            val event=awaitPointerEvent(PointerEventPass.Initial)
            if(result.consume)event.changes.forEach {it.consume()}
        }}
    }
}
@Composable
internal fun PanicHomeDropdownMenu(expanded:Boolean,onDismissRequest:()->Unit,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    androidx.compose.material3.DropdownMenu(expanded,onDismissRequest,modifier.popupPanicInput(),content=content)
}
