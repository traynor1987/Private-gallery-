package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Presentation only; caller owns secret lifetime and cryptographic authentication. Never saved state. */
@Composable
internal fun PasscodeKeypad(
 pin:String,onPinChange:(String)->Unit,onSubmit:()->Unit,
 expectedLength:Int?=null,enabled:Boolean=true,message:String?=null,
 appTitle:String="Private Gallery",onDiscreetEntry:(()->Unit)?=null,
 action:String="Unlock",secondary:(@Composable ()->Unit)?=null,
) {
 val feedback=LocalHapticFeedback.current
 val currentSubmit by rememberUpdatedState(onSubmit)
 LaunchedEffect(pin,expectedLength) {if(enabled&&expectedLength!=null&&pin.length==expectedLength)currentSubmit()}
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
  BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
   val wide=maxWidth>maxHeight
   val heading:@Composable ()->Unit={
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
     Text(appTitle,Modifier.then(if(onDiscreetEntry!=null)Modifier.pointerInput(onDiscreetEntry){detectTapGestures(onLongPress={onDiscreetEntry()})} else Modifier),style=MaterialTheme.typography.titleMedium)
     Icon(Icons.Default.Lock,null,Modifier.size(28.dp))
     Text("Enter passcode",fontSize=24.sp)
     Text("•".repeat(pin.length).ifEmpty {" "},Modifier.testTag("passcode-indicator").semantics {password();stateDescription="${pin.length} digits entered"},fontSize=28.sp,letterSpacing=8.sp)
     message?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
    }
   }
   val keys:@Composable ()->Unit={
    Column(Modifier.widthIn(max=320.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
     listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf("","0","delete")).forEach {row->
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {row.forEach {digit->
       Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
        if(digit.isNotEmpty()) FilledTonalButton(enabled=enabled,onClick={
         feedback.performHapticFeedback(HapticFeedbackType.LongPress)
         if(digit=="delete")onPinChange(pin.dropLast(1)) else if(expectedLength==null||pin.length<expectedLength)onPinChange(pin+digit)
        },modifier=Modifier.fillMaxWidth().height(52.dp),contentPadding=PaddingValues(0.dp),
         colors=ButtonDefaults.filledTonalButtonColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHighest,contentColor=MaterialTheme.colorScheme.onSurface)) {
         if(digit=="delete")Icon(Icons.Default.Backspace,"Delete last digit") else Text(digit,fontSize=26.sp)
        }
       }
      }}
     }
     if(expectedLength==null)Button(onClick=onSubmit,enabled=enabled&&pin.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(action)}
     secondary?.invoke()
    }
   }
   if(wide)Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
    Box(Modifier.weight(1f).verticalScroll(rememberScrollState()),contentAlignment=Alignment.Center){heading()}
    Box(Modifier.widthIn(max=320.dp).weight(1f).verticalScroll(rememberScrollState()),contentAlignment=Alignment.Center){keys()}
   } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
    heading();Spacer(Modifier.height(24.dp));keys()
   }
  }
 }
}
