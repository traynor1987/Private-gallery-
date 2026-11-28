package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision

@Composable
internal fun LauncherSharedDock(
    items: List<LauncherItem>,
    appearance: LauncherAppearance,
    options: LauncherDockOptions,
    apps: Map<String, LauncherApp>,
    decisions: Map<String, Decision>,
    compact: Boolean,
    gestures: LauncherIconGestures,
    open: (LauncherItem) -> Unit,
    visible: (LauncherItem) -> Boolean,
    smartMembers: (LauncherItem.SmartFolder) -> List<LauncherApp>,
    active: Boolean,
    onFolder: ((LauncherItem.Folder, LauncherSlot) -> Unit)? = null,
) {
    val canEdit = active && !options.locked
    if (canEdit) {
        DisposableEffect(gestures) { onDispose { gestures.clearDock() } }
    }
    val floating = appearance.studio.look == LauncherLook.FLOATING
    val width = if (floating) Modifier.widthIn(max = 470.dp).padding(horizontal = 9.dp) else Modifier
    Surface(
        width.fillMaxWidth().padding(top = 1.dp).testTag("launcher-shared-dock")
            .then(if (canEdit) Modifier.onGloballyPositioned(gestures::setDock) else Modifier),
        color = when (appearance.dock) {
            LauncherDock.GLASS -> Color.White.copy(alpha = if (floating) .21f else .14f)
            LauncherDock.SOLID -> Color(0xFF273550)
            LauncherDock.TRANSPARENT, LauncherDock.HIDDEN -> Color.Transparent
        },
        contentColor = Color.White,
        shape = RoundedCornerShape(if (floating) 34.dp else 27.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scroll = maxWidth < (items.size * 48 + 12).dp
            val size = when (appearance.iconSize) {
                LauncherIconSize.SMALL -> 38.dp
                LauncherIconSize.MEDIUM -> 48.dp
                LauncherIconSize.LARGE -> 58.dp
            }
            val labelStyle = MaterialTheme.typography.labelMedium
            val labelHeight = if (options.labels) {
                with(LocalDensity.current) { labelStyle.lineHeight.toDp() * 2 } + 4.dp
            } else 0.dp
            val verticalPadding = if (compact) 4.dp else 10.dp
            val slotHeight = maxOf(48.dp, size + 4.dp + labelHeight)
            Row(
                (if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                    .height(slotHeight + verticalPadding * 2).padding(horizontal = 6.dp, vertical = verticalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val cell = if (scroll) Modifier.width(52.dp) else Modifier.weight(1f)
                    Box(cell.fillMaxHeight().testTag("launcher-dock-slot-$index"), contentAlignment = Alignment.Center) {
                        if (visible(item)) LauncherTile(
                            item, appearance, apps, decisions, {
                                if (item is LauncherItem.Folder && onFolder != null) onFolder(item, LauncherSlot(null, index))
                                else open(item)
                            }, compact = true,
                            interactive = active,
                            gestures = gestures.takeIf { canEdit },
                            origin = LauncherIconOrigin(item, LauncherSlot(null, index)),
                            showLabel = options.labels,
                            smartMembers = smartMembers,
                            folderMembers = { folder ->
                                folder.apps.filter { visible(LauncherItem.App(it)) }.mapNotNull(apps::get)
                            },
                        )
                    }
                }
            }
        }
    }
}
