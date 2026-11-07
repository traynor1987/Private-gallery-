package uk.co.traynor.privategallery.home2

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Tune

internal val visualStudios: List<VisualModule> = listOf(
    VisualModule(
        "desktop-mode", "Nova Desktop Mode", "Workspace layouts and window previews", Icons.Default.DesktopWindows,
        listOf(
            VisualSection(
                "Workspace", "A workspace for the unfolded screen",
                "Arrange fictional app windows in the preview. The current NovaOS workspace and window positions stay as they are.",
                rows = listOf(VisualRow("Sample dock", "A future desktop could keep frequently used apps within reach.", "DEMO")),
            ),
            VisualSection(
                "Displays", "Display setup preview",
                "No screens have been detected or queried. These cards illustrate how a future setup could distinguish the Fold screen and a separate display.",
                rows = listOf(
                    VisualRow("Fold workspace", "Example expanded workspace using the inner display.", "Example"),
                    VisualRow("External display", "A future connection flow would let you review the display before opening a workspace.", "Unavailable"),
                ),
            ),
            VisualSection(
                "Layouts", "Workspace layout library",
                "These example arrangements illustrate different ways to organise NovaOS windows. Layout saving is unavailable in this demo.",
                rows = listOf(
                    VisualRow("Split", "Use two sample apps side by side.", "Example"),
                    VisualRow("Focus", "Give the sample writing window the whole workspace.", "Example"),
                    VisualRow("Privacy on shared displays", "A future release will need an explicit privacy review before displaying protected content externally.", "Planned"),
                ),
            ),
        ),
    ),
    VisualModule(
        "control-centre", "Nova Control Centre 2.0", "Quick tiles, layout and visibility", Icons.Default.Tune,
        listOf(
            VisualSection(
                "Overview", "Quick controls preview",
                "Try the example tiles and brightness control. Changes are confined to this demo screen.",
                rows = listOf(VisualRow("Current Quick Panel", "The working Quick Panel still opens through its existing Home entry point.", "Existing app")),
            ),
            VisualSection(
                "Arrangement", "Arrange example tiles",
                "A future editor could let you select visible controls, group them and choose their order.",
                rows = listOf(
                    VisualRow("First row", "The sample layout groups focus and audio together.", "Sample layout"),
                    VisualRow("Visibility", "A future screen could review which controls should appear in sensitive contexts.", "Planned"),
                ),
            ),
            VisualSection(
                "Privacy", "Control ownership and access",
                "These statuses are fictional. This demo cannot read connectivity state or change Android settings.",
                rows = listOf(VisualRow("Permission review", "Live controls would require supported integrations and separate consent where applicable.", "Unavailable")),
            ),
        ),
    ),
    VisualModule(
        "themes-studio", "Nova Themes Studio", "Colour, wallpaper and appearance previews", Icons.Default.Palette,
        listOf(
            VisualSection(
                "Palette", "Make a sample Home your own",
                "Choose a colourway and inspect its contrast in an example Home. The current Home theme is unchanged.",
                rows = listOf(VisualRow("Apply theme", "Applying a theme from the studio is unavailable during visual review.", "Unavailable")),
            ),
            VisualSection(
                "Wallpaper", "Wallpaper and material samples",
                "Review fictional combinations for the cover and unfolded screens. These samples do not read the Vault or photos.",
                rows = listOf(
                    VisualRow("Aurora gradient", "A bundled colour study with large contrast areas for icons.", "Sample"),
                    VisualRow("Separate postures", "A future design can preview how wallpaper cropping differs across Fold sizes.", "Planned"),
                ),
            ),
            VisualSection(
                "Accessibility", "Contrast and type",
                "Inspect example labels and surfaces. Changing device text, contrast or Home preferences is unavailable here.",
                rows = listOf(VisualRow("Reading contrast", "A future contrast review could help show how selected colours work together.", "Example")),
            ),
        ),
    ),
    VisualModule(
        "lock-studio", "Nova Lock Screen Studio", "Clock and privacy layout previews", Icons.Default.Lock,
        listOf(
            VisualSection(
                "Preview", "Design a sample lock layout",
                "Try a sample clock style and decide whether the fictional alert is summarised. Real lock and authentication rules stay in effect.",
                rows = listOf(VisualRow("Sample content", "The clock is fixed and the alert is fictional.", "DEMO")),
            ),
            VisualSection(
                "Privacy", "Content visibility preview",
                "This screen illustrates disclosure choices. It cannot read notifications or change privacy settings.",
                rows = listOf(
                    VisualRow("Minimal content", "A sample layout showing the clock and no alert text.", "Example"),
                    VisualRow("Protected previews", "A future editor would need to respect NovaOS security boundaries.", "Planned"),
                ),
            ),
            VisualSection(
                "Shortcuts", "Shortcut positions",
                "Review how two fictional shortcuts could fit around the clock. This studio cannot add lock screen actions.",
                rows = listOf(VisualRow("Shortcut slots", "Left and right positions are shown as design options only.", "Unavailable")),
            ),
        ),
    ),
    VisualModule(
        "pip-controls", "Nova PiP Controls", "Picture-in-picture layout previews", Icons.Default.PictureInPicture,
        listOf(
            VisualSection(
                "Preview", "A sample player in a compact window",
                "Change the corner and size of a fictional player inside this card. Android picture-in-picture and media playback are inactive.",
                rows = listOf(VisualRow("Sample controls", "Playback and dismissal controls illustrate the layout for a future review.", "DEMO")),
            ),
            VisualSection(
                "Placement", "Compact window behaviour",
                "Explore how a future compact player might fit around the workspace. The sample is entirely inside this demo page.",
                rows = listOf(
                    VisualRow("Safe corners", "A future implementation would respect display edges and system controls.", "Planned"),
                    VisualRow("Folded and unfolded", "The current preview scales within the available screen width.", "Sample"),
                ),
            ),
            VisualSection(
                "Privacy", "Playback and protected content",
                "The preview has no media access and creates no floating window. Existing media playback controls remain available in the working app.",
                rows = listOf(VisualRow("Future content policy", "Live PiP would need a separate review of what can leave the protected app surface.", "Unavailable")),
            ),
        ),
    ),
)
