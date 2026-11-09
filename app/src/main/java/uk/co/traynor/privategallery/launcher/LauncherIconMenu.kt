package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

private data class LauncherMenuAction(val label: String, val icon: ImageVector, val run: () -> Unit)

@Composable
internal fun LauncherIconMenu(
    anchor: LauncherIconMenuAnchor,
    app: LauncherApp?,
    layout: LauncherLayout,
    currentPage: Int,
    dockVisible: Boolean,
    protectedBySanctuary: Boolean,
    actions: LauncherUiActions,
    dismiss: () -> Unit,
    open: () -> Unit,
    editHome: () -> Unit,
    save: (LauncherLayout) -> Unit,
    onEditSmartFolder: ((LauncherSlot, LauncherItem.SmartFolder) -> Unit)? = null,
    recentPinned: Boolean = false,
) {
    val (item, source) = anchor.origin
    if (item is LauncherItem.App && app == null) return
    val label = when (item) {
        is LauncherItem.App -> checkNotNull(app).label
        is LauncherItem.Folder -> item.name
        is LauncherItem.SmartFolder -> item.name
        LauncherItem.Workspace -> "NovaOS workspace"
    }
    val applicationId = LocalContext.current.packageName
    val items = buildList {
        add(LauncherMenuAction(
            if (item is LauncherItem.App) "Open app" else "Open",
            if (item is LauncherItem.Folder || item is LauncherItem.SmartFolder) {
                Icons.Default.FolderOpen
            } else Icons.Default.OpenInNew,
            open,
        ))
        if (source != null && item != LauncherItem.Workspace) {
            val original = layout.itemsAt(source.page)
            if (original?.getOrNull(source.index) == item) {
                if (item is LauncherItem.SmartFolder && onEditSmartFolder != null) {
                    add(LauncherMenuAction("Edit smart folder", Icons.Default.Edit) { onEditSmartFolder(source, item) })
                }
                add(LauncherMenuAction(if (source.page == null) "Remove from dock" else "Remove from this page", Icons.Default.Close) {
                    save(layout.withItemsAt(source.page, original.filterIndexed { index, _ -> index != source.index }))
                })
            }
        }
        if (item is LauncherItem.App || source != null) {
            if (source == null || source.page == null) {
                val pageItems = layout.itemsAt(currentPage).orEmpty()
                val added = layout.dropLauncherItem(item, source, LauncherDrop(LauncherSlot(currentPage, pageItems.size)))
                if (added != layout) add(LauncherMenuAction(
                    if (source == null) "Add to this page" else "Move to Home",
                    if (source == null) Icons.Default.Add else Icons.Default.Home,
                ) { save(added) })
            }
            if (dockVisible && (source == null || source.page != null)) {
                val docked = layout.dropLauncherItem(item, source, LauncherDrop(LauncherSlot(null, layout.dock.size)))
                if (docked != layout) add(LauncherMenuAction(
                    if (source == null) "Add to dock" else "Move to dock", Icons.Default.Apps,
                ) { save(docked) })
            }
        }
        if (item is LauncherItem.App) {
            val builtIn = LauncherBuiltIn.find(applicationId, item.component)
            val favourite = item.component in layout.favourites
            if (favourite || layout.favourites.size < MAX_LAUNCHER_FAVOURITES) {
                add(LauncherMenuAction(
                    if (favourite) "Remove favourite" else "Add favourite",
                    if (favourite) Icons.Default.Star else Icons.Default.StarBorder,
                ) {
                    val next = if (favourite) layout.favourites - item.component else layout.favourites + item.component
                    save(layout.copy(favourites = next))
                })
            }
            val appInfo = actions.openAppInfo
            if (!protectedBySanctuary && appInfo != null && builtIn == null) {
                add(LauncherMenuAction("App info", Icons.Default.Info) { appInfo(item.component) })
            }
            val renameApp = actions.renameApp
            if (!protectedBySanctuary && renameApp != null && builtIn == null) {
                add(LauncherMenuAction("Rename app", Icons.Default.Edit) { renameApp(item.component) })
            }
            val toggleRecentPin = actions.toggleRecentPin
            if (toggleRecentPin != null) {
                add(LauncherMenuAction(
                    if (recentPinned) "Unpin from Recents" else "Pin to Recents", Icons.Default.PushPin,
                ) { toggleRecentPin(item.component) })
            }
        } else {
            add(LauncherMenuAction("Edit Home", Icons.Default.Edit, editHome))
        }
    }
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(anchor.windowBounds, gap, margin) {
        val bounds = anchor.windowBounds
        val tile = IntRect(
            bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.right.roundToInt(), bounds.bottom.roundToInt(),
        )
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val left = if (layoutDirection == LayoutDirection.Ltr) tile.left else tile.right - popupContentSize.width
                val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
                val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
                val below = tile.bottom + gap
                val above = tile.top - popupContentSize.height - gap
                val top = if (below <= maxY || above < margin) below else above
                return IntOffset(left.coerceIn(margin, maxX), top.coerceIn(margin, maxY))
            }
        }
    }
    Popup(position, onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        Surface(
            Modifier.widthIn(min = 176.dp, max = 248.dp).testTag("launcher-icon-menu"),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.heightIn(max = 328.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                Text(label, Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                items.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        leadingIcon = { Icon(action.icon, contentDescription = null) },
                        onClick = { dismiss(); action.run() },
                    )
                }
            }
        }
    }
}
