package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import uk.co.traynor.privategallery.R

internal data class LauncherIconOrigin(val item: LauncherItem, val slot: LauncherSlot? = null)
internal data class LauncherIconMenuAnchor(val origin: LauncherIconOrigin, val windowBounds: Rect)
internal data class LauncherIconDrag(val origin: LauncherIconOrigin, val position: Offset)
internal data class LauncherDropPreview(val drop: LauncherDrop, val bounds: Rect, val hasTile: Boolean)

/** Owned by the runtime above Home and Drawer so a held pointer survives their transition. */
internal class LauncherIconGestures(
    private val rightToLeft: Boolean,
    private val root: () -> LayoutCoordinates?,
) {
    private val pageContents = mutableStateMapOf<Int, Rect>()
    private val tiles = mutableStateMapOf<Any, Pair<LauncherIconOrigin, Rect>>()
    var pageBounds by mutableStateOf(Rect.Zero)
        private set
    var dockBounds by mutableStateOf(Rect.Zero)
        private set
    var drag by mutableStateOf<LauncherIconDrag?>(null)
        private set
    var menu by mutableStateOf<LauncherIconMenuAnchor?>(null)
        private set

    private fun bounds(coordinates: LayoutCoordinates): Rect =
        root()?.takeIf { it.isAttached && coordinates.isAttached }?.localBoundingBoxOf(coordinates) ?: Rect.Zero

    fun setPage(coordinates: LayoutCoordinates) { pageBounds = bounds(coordinates) }
    fun setPageContent(page: Int, coordinates: LayoutCoordinates) { pageContents[page] = bounds(coordinates) }
    fun clearPageContent(page: Int) { pageContents.remove(page) }
    fun setDock(coordinates: LayoutCoordinates) { dockBounds = bounds(coordinates) }
    fun clearDock() { dockBounds = Rect.Zero }
    fun clearHome() {
        pageBounds = Rect.Zero
        pageContents.clear()
        clearDock()
    }

    fun setTile(key: Any, origin: LauncherIconOrigin, coordinates: LayoutCoordinates) {
        val bounds = bounds(coordinates)
        if (bounds.isEmpty) tiles.remove(key) else tiles[key] = origin to bounds
    }

    fun removeTile(key: Any) { tiles.remove(key) }

    fun sourceAt(point: Offset, page: Int, drawer: Boolean, includeDock: Boolean = false): Pair<LauncherIconOrigin, Rect>? =
        tiles.values.firstOrNull { (origin, bounds) ->
            point in bounds && if (drawer) origin.slot == null || includeDock && origin.slot.page == null else origin.slot?.let {
                it.page == page || it.page == null
            } == true
        }

    fun showMenu(origin: LauncherIconOrigin, bounds: Rect) {
        val coordinates = root()?.takeIf { it.isAttached } ?: return
        menu = LauncherIconMenuAnchor(origin, bounds.translate(coordinates.localToWindow(Offset.Zero)))
    }

    fun showMenu(key: Any) { tiles[key]?.let { (origin, bounds) -> showMenu(origin, bounds) } }
    fun dismissMenu() { menu = null }
    fun move(origin: LauncherIconOrigin, position: Offset) { drag = LauncherIconDrag(origin, position) }
    fun cancelDrag() { drag = null }

    fun targetAt(layout: LauncherLayout, page: Int?, point: Offset, rowTolerance: Float): LauncherDropPreview? {
        val source = drag?.origin ?: return null
        val destinationPage = when {
            point in dockBounds -> null
            page != null && point in pageBounds && isPageLaidOut(page) -> page
            else -> return null
        }
        val items = layout.itemsAt(destinationPage) ?: return null
        val candidates = tiles.values.filter { (origin, _) ->
            origin.slot != null && origin.slot.page == destinationPage
        }.sortedBy { (origin, _) -> origin.slot?.index }
        val hit = candidates.firstOrNull { (_, bounds) -> point in bounds }
        if (hit != null) {
            val (origin, bounds) = hit
            val slot = checkNotNull(origin.slot)
            val central = abs(point.x - bounds.center.x) <= bounds.width * .29f
            if (central && groupLauncherApp(source.item, origin.item) != null) {
                return LauncherDropPreview(LauncherDrop(slot, group = true), bounds, hasTile = true)
            }
            return insertionAt(slot, bounds, isAfter(point.x, bounds))
        }
        val nearby = candidates.filter { (_, bounds) -> point.y in (bounds.top - rowTolerance)..(bounds.bottom + rowTolerance) }
            .minByOrNull { (_, bounds) -> abs(bounds.center.x - point.x) }
        if (nearby != null) {
            val (origin, bounds) = nearby
            return insertionAt(checkNotNull(origin.slot), bounds, isAfter(point.x, bounds))
        }
        val last = candidates.lastOrNull()
        val area = if (destinationPage == null) dockBounds else pageBounds
        val drop = LauncherDrop(LauncherSlot(destinationPage, items.size))
        return LauncherDropPreview(drop, last?.second?.let { insertionMarker(it, after = true) } ?: area,
            hasTile = last != null)
    }

    private fun isPageLaidOut(page: Int): Boolean {
        val visible = pageContents[page] ?: return false
        return !visible.isEmpty && abs(visible.left - pageBounds.left) <= 1f &&
            abs(visible.right - pageBounds.right) <= 1f
    }

    private fun isAfter(x: Float, bounds: Rect): Boolean =
        if (rightToLeft) x <= bounds.center.x else x >= bounds.center.x

    private fun insertionAt(slot: LauncherSlot, bounds: Rect, after: Boolean) =
        LauncherDropPreview(
            LauncherDrop(if (after) slot.copy(index = slot.index + 1) else slot),
            insertionMarker(bounds, after),
            hasTile = true,
        )

    private fun insertionMarker(bounds: Rect, after: Boolean): Rect {
        val x = if (after != rightToLeft) bounds.right else bounds.left
        return Rect(x, bounds.top, x, bounds.bottom)
    }
}

/** Parent observes taps until the hold threshold, then owns the gesture at the Initial pass. */
@Composable
internal fun Modifier.launcherIconPointer(
    controller: LauncherIconGestures,
    findSource: (Offset) -> Pair<LauncherIconOrigin, Rect>?,
    startDrag: () -> Unit,
    drop: (Offset) -> Unit,
): Modifier {
    val resolveSource by rememberUpdatedState(findSource)
    val onStartDrag by rememberUpdatedState(startDrag)
    val onDrop by rememberUpdatedState(drop)
    return pointerInput(controller) {
        awaitLauncherIconGesture(controller, { resolveSource(it) }, { onStartDrag() }, { onDrop(it) })
    }
}

private suspend fun PointerInputScope.awaitLauncherIconGesture(
    controller: LauncherIconGestures,
    findSource: (Offset) -> Pair<LauncherIconOrigin, Rect>?,
    startDrag: () -> Unit,
    drop: (Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val (origin, anchor) = findSource(down.position) ?: return@awaitEachGesture
        val timeout = viewConfiguration.longPressTimeoutMillis
        val slop = viewConfiguration.touchSlop
        var change: PointerInputChange? = null
        val earlyEnd = withTimeoutOrNull(timeout) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val tracked = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (event.changes.any { it.id != down.id && it.pressed }) return@withTimeoutOrNull true
                if (tracked.uptimeMillis - down.uptimeMillis >= timeout) {
                    change = tracked
                    return@withTimeoutOrNull false
                }
                if (!tracked.pressed || (tracked.position - down.position).getDistance() > slop) {
                    return@withTimeoutOrNull true
                }
                val final = awaitPointerEvent(PointerEventPass.Final)
                if (final.changes.any { it.id == down.id && it.isConsumed }) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        if (earlyEnd == true) return@awaitEachGesture

        try {
            var dragging = false
            while (true) {
                val carried = change
                val tracked: PointerInputChange
                if (carried != null) {
                    tracked = carried
                } else {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.id != down.id && it.pressed }) return@awaitEachGesture
                    tracked = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                }
                change = null
                if (!tracked.pressed) {
                    // Compose forwards ACTION_CANCEL as a consumed up.
                    if (!tracked.isConsumed) {
                        tracked.consume()
                        if (dragging) drop(tracked.position) else controller.showMenu(origin, anchor)
                    }
                    break
                }
                tracked.consume()
                if (!dragging && (tracked.position - down.position).getDistance() > slop) {
                    if (origin.slot == null && origin.item !is LauncherItem.App) break
                    dragging = true
                    controller.move(origin, tracked.position)
                    startDrag()
                } else if (dragging) {
                    controller.move(origin, tracked.position)
                }
            }
        } finally {
            controller.cancelDrag()
        }
    }
}

@Composable
internal fun Modifier.launcherIconBounds(
    controller: LauncherIconGestures,
    origin: LauncherIconOrigin,
    registration: Any,
): Modifier {
    DisposableEffect(controller, registration) {
        onDispose { controller.removeTile(registration) }
    }
    return onGloballyPositioned { controller.setTile(registration, origin, it) }
}

@Composable
internal fun BoxScope.LauncherDragOverlay(
    drag: LauncherIconDrag,
    preview: LauncherDropPreview?,
    app: LauncherApp?,
    accent: Color,
    blocked: Boolean,
) {
    if (preview != null) {
        Canvas(Modifier.fillMaxSize()) {
            val bounds = preview.bounds
            if (preview.drop.group || !preview.hasTile) {
                drawRoundRect(
                    accent, topLeft = bounds.topLeft, size = Size(bounds.width, bounds.height),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(2.dp.toPx()),
                )
            } else {
                drawLine(accent, bounds.topLeft, bounds.bottomLeft, strokeWidth = 3.dp.toPx())
            }
        }
    }
    Box(
        Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset {
            IntOffset(
                (drag.position.x - 25.dp.toPx()).roundToInt(),
                (drag.position.y - 25.dp.toPx()).roundToInt(),
            )
        }.size(50.dp).background(accent.copy(alpha = .88f), RoundedCornerShape(15.dp))
            .testTag("launcher-drag-preview"),
        contentAlignment = Alignment.Center,
    ) {
        when (drag.origin.item) {
            is LauncherItem.App -> if (app != null) LauncherAppIcon(app, Modifier.size(35.dp))
            is LauncherItem.Folder, is LauncherItem.SmartFolder ->
                Icon(Icons.Default.Folder, null, tint = Color(0xFF17233B))
            LauncherItem.Workspace -> Image(painterResource(R.drawable.nova_identity), null, Modifier.size(35.dp))
        }
        if (blocked) {
            Icon(Icons.Default.Lock, null, Modifier.align(Alignment.BottomEnd).size(17.dp), tint = Color(0xFF17233B))
        }
    }
}
