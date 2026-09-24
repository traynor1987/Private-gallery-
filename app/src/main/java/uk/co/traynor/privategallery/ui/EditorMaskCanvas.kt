package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.ui.CropEditorGeometry

/** Fit-only canvas. Normalized mask coordinates map to the outgoing image, never screen pixels. */
@Composable
internal fun MaskCanvas(bitmap: Bitmap, strokes: List<MaskStroke>, brush: Float, onStroke: (MaskStroke) -> Unit) {
    var drawing by remember { mutableStateOf<List<MaskPoint>>(emptyList()) }
    val latestStroke by rememberUpdatedState(onStroke)
    Box(Modifier.fillMaxSize()) {
        Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize().pointerInput(bitmap, brush) {
            detectDragGestures(onDragStart = { p -> drawing = listOfNotNull(MaskGeometry.point(p.x,p.y,size.width.toFloat(),size.height.toFloat(),bitmap.width.toFloat()/bitmap.height)) },
                onDragCancel = { drawing = emptyList() }, onDragEnd = { if (drawing.isNotEmpty()) latestStroke(MaskStroke(drawing, brush)); drawing = emptyList() }) { change, _ ->
                change.consume(); MaskGeometry.point(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat(), bitmap.width.toFloat()/bitmap.height)?.let { if (drawing.size < 4096) drawing = drawing + it }
            }
        }) {
            if (size.width <= 0 || size.height <= 0) return@Canvas
            val rect = CropEditorGeometry.fitImage(size.width, size.height, bitmap.width.toFloat()/bitmap.height)
            val all = strokes + if (drawing.isEmpty()) emptyList() else listOf(MaskStroke(drawing, brush))
            all.forEach { stroke ->
                val points = stroke.points.map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
                val width = stroke.width * rect.width
                points.firstOrNull()?.let { drawCircle(Color.White.copy(alpha=.55f), width/2, it) }
                points.zipWithNext().forEach { (a,b) -> drawLine(Color.White.copy(alpha=.55f), a,b,width, StrokeCap.Round) }
            }
        }
    }
}
@Composable
internal fun AspectChoices(selected: Float?, onChange: (Float?) -> Unit, enabled: Boolean) {
    var custom by remember { mutableStateOf("") }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        listOf("Original" to null, "Square" to 1f, "4:5" to .8f, "16:9" to (16f/9)).forEach { (label, ratio) -> FilterChip(selected == ratio, { onChange(ratio) }, label = { Text(label) }, enabled = enabled) }
    }
    OutlinedTextField(custom, { custom = it; it.toFloatOrNull()?.takeIf { value -> value in .25f..4f }?.let(onChange) }, label = { Text("Custom width / height (0.25–4)") }, enabled = enabled, singleLine = true)
}
@Composable
internal fun ExpandCanvasPreview(bitmap: Bitmap, aspect: Float?) {
    Canvas(Modifier.fillMaxSize()) {
        if (size.width <= 0 || size.height <= 0) return@Canvas
        val imageAspect = bitmap.width.toFloat()/bitmap.height
        val outer = CropEditorGeometry.fitImage(size.width, size.height, aspect ?: imageAspect)
        drawRect(Color.DarkGray, Offset(outer.left, outer.top), Size(outer.width, outer.height))
        val image = CropEditorGeometry.fitImage(outer.width, outer.height, imageAspect)
        drawImage(bitmap.asImageBitmap(), dstOffset = IntOffset((outer.left+image.left).toInt(), (outer.top+image.top).toInt()), dstSize = IntSize(image.width.toInt().coerceAtLeast(1), image.height.toInt().coerceAtLeast(1)))
        drawRect(Color.White, Offset(outer.left, outer.top), Size(outer.width, outer.height), style = Stroke(2f))
    }
}
