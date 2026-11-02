package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

/** Own the down event, but accept capture only after an unconsumed single-pointer tap. */
@Composable
internal fun Modifier.cameraConcealedTapInput(enabled:Boolean,onCapture:()->Unit):Modifier {
    val capture=rememberUpdatedState(onCapture)
    return pointerInput(enabled) {
        if(enabled)awaitEachGesture {
            val down=awaitFirstDown()
            down.consume()
            val start=down.position
            var tap=true
            while(true) {
                val event=awaitPointerEvent()
                val change=event.changes.firstOrNull {it.id==down.id} ?: break
                if(change.isConsumed||event.changes.count {it.pressed}>1||
                    (change.position-start).getDistance()>viewConfiguration.touchSlop)tap=false
                if(change.changedToUpIgnoreConsumed()) {
                    if(tap){change.consume();capture.value()}
                    break
                }
                if(!change.pressed)break
            }
        }
    }
}
