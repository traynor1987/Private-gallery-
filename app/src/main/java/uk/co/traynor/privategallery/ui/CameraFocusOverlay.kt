package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** A target marker, never an assertion that hardware focus succeeded. */
@Composable
internal fun CameraFocusOverlay(point: Offset?, message: String, onDismiss: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(point,message) {
        if(point!=null&&message.isNotEmpty()) {delay(3000);dismiss()}
    }
    if(point!=null&&message.isNotEmpty())BoxWithConstraints(Modifier.fillMaxSize().testTag("camera-focus-overlay")) {
        Box(Modifier.offset(x=maxWidth*point.x-24.dp,y=maxHeight*point.y-24.dp)
            .size(48.dp).border(2.dp,Color.White,CircleShape).testTag("camera-focus-target")
            .semantics {contentDescription="Requested focus position"})
        Box(Modifier.fillMaxSize().padding(12.dp),contentAlignment=Alignment.TopCenter) {
            Surface(color=Color.Black.copy(alpha=0.75f),shape=GalleryTokens.RowShape) {
                Text(message,Modifier.padding(8.dp).semantics {liveRegion=LiveRegionMode.Polite},
                    color=Color.White,style=MaterialTheme.typography.labelMedium)
            }
        }
    }
}
