package uk.co.traynor.privategallery.home2

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey

/** Fictional examples only. These modules have no access to device settings or Space records. */
internal val visualPrivacy: List<VisualModule> = listOf(
    VisualModule(
        id = "keys",
        title = "Nova Keys",
        subtitle = "Credential organiser concept",
        icon = Icons.Default.VpnKey,
        sections = listOf(
            VisualSection(
                title = "Library",
                headline = "Credential library",
                detail = "Browse fictional labels to review the layout. Nova Keys has no credential store in this demo.",
                metrics = listOf(VisualMetric("Example entries", "3"), VisualMetric("Example groups", "2")),
                rows = listOf(
                    VisualRow("Aurora Workshop", "Sample label: Workshop sign-in. The value field is intentionally empty. There is no reveal, copy or autofill action.", "Login layout"),
                    VisualRow("Atlas Studio", "Sample label: Demo recovery record. No recovery codes or account identifiers are included.", "Recovery layout"),
                    VisualRow("Cedar Desk", "Sample label: Workspace note. This illustrates metadata without showing a key, token or password.", "Note layout"),
                ),
            ),
            VisualSection(
                title = "Entry",
                headline = "Create an entry",
                detail = "Preview the fields a future entry form could show. No credentials can be entered or saved here.",
                rows = listOf(
                    VisualRow("Entry type", "Future choices could include a sign-in, a recovery record or a private note. This example does not create an entry.", "Sample field"),
                    VisualRow("Secret field", "The planned value field is unavailable. Keep real passwords and recovery material out of this demo.", "Unavailable"),
                    VisualRow("Reveal and copy", "A future action could require explicit confirmation. This example reveals and copies nothing.", "Planned"),
                ),
            ),
            VisualSection(
                title = "Recovery",
                headline = "Recovery and portability",
                detail = "Inspect the proposed review steps for moving credentials. No import, backup or export runs in this demo.",
                rows = listOf(
                    VisualRow("Import review", "A proposed review step would identify the selected source and show a count before any records are accepted.", "Planned"),
                    VisualRow("Export review", "The example confirmation calls for destination verification and an explanation of what the export includes. No file is created.", "Unavailable"),
                    VisualRow("Empty recovery state", "This sample has no recovery material to display.", "Empty example"),
                ),
            ),
            VisualSection(
                title = "Privacy",
                headline = "Credential privacy",
                detail = "These are proposed controls for a future credential feature. This page cannot inspect or change security settings.",
                rows = listOf(
                    VisualRow("Require confirmation", "The layout reserves a place to explain when a future entry would require authentication.", "Concept"),
                    VisualRow("Clipboard handling", "No clipboard is read or written. Future copying would need explicit consent and a clear lifetime.", "Unavailable"),
                    VisualRow("Delete entry", "The sample confirmation shows where a deletion summary could appear. No credential exists to delete.", "Demo confirmation"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "app-permissions",
        title = "Nova App Permissions",
        subtitle = "Permission review and explanation concept",
        icon = Icons.Default.AdminPanelSettings,
        sections = listOf(
            VisualSection(
                title = "Overview",
                headline = "Permissions at a glance",
                detail = "These are fictional app and permission examples. The demo does not read installed apps or Android permissions.",
                metrics = listOf(VisualMetric("Example apps", "3"), VisualMetric("Example requests", "3")),
                rows = listOf(
                    VisualRow("Camera", "An example scanning flow would explain why it needs a camera before asking. This demo has made no request.", "Example only"),
                    VisualRow("Microphone", "An example voice note flow shows an explanation before recording. This screen cannot record audio.", "Example only"),
                    VisualRow("Notifications", "An example reminder feature shows what could appear in an alert. No notification is scheduled.", "Example only"),
                ),
            ),
            VisualSection(
                title = "Apps",
                headline = "Review by app",
                detail = "Open a fictional app card to see how a future permissions review could group access by purpose.",
                rows = listOf(
                    VisualRow("Cedar Scanner", "Camera for an example document capture. Cedar Scanner is fictional and no camera access was checked.", "Sample app"),
                    VisualRow("Atlas Voice", "Microphone for an example voice note. No system permission or microphone status is shown.", "Sample app"),
                    VisualRow("Aurora Planner", "Notifications for sample task reminders. No device notification setting is shown.", "Sample app"),
                ),
            ),
            VisualSection(
                title = "Request",
                headline = "Before a request",
                detail = "This sample walkthrough shows how a future app could explain an optional permission. It cannot open an Android permission prompt.",
                rows = listOf(
                    VisualRow("Explain the purpose", "Example: a reminder can tell you a task is due. The explanation would come before any request.", "Step 1"),
                    VisualRow("Show a fallback", "Example: check due tasks inside the app if you prefer to leave alerts off.", "Step 2"),
                    VisualRow("Review access", "A future review could explain how to change a choice. This demo cannot change device settings.", "Step 3"),
                ),
            ),
            VisualSection(
                title = "Activity",
                headline = "Permission activity example",
                detail = "This is a static preview of an activity list. There is no device observation or permission history.",
                rows = listOf(
                    VisualRow("Monday · Camera explanation", "An illustrative explanation screen was shown in the sample story. No real event occurred.", "Demo event"),
                    VisualRow("Tuesday · Notifications review", "A fictional review ended without asking for access. No request or grant was recorded.", "Demo event"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "privacy-dashboard",
        title = "Nova Privacy Dashboard",
        subtitle = "Review sample access, sharing and retention",
        icon = Icons.Default.Security,
        sections = listOf(
            VisualSection(
                title = "Overview",
                headline = "Privacy at a glance",
                detail = "The dashboard contains a fixed example report. It does not inspect NovaOS settings or assess device protection.",
                metrics = listOf(VisualMetric("Sample events", "3"), VisualMetric("Example reviews", "2")),
                rows = listOf(
                    VisualRow("Access summary", "See how future explanations could identify what an app needs and why.", "Demo report"),
                    VisualRow("Sharing summary", "Preview a breakdown of the choices that could be shown before an export or connection.", "Demo report"),
                    VisualRow("Report unavailable", "This example shows how the dashboard could explain missing information without claiming that everything is safe.", "Empty example"),
                ),
            ),
            VisualSection(
                title = "Activity",
                headline = "Sample privacy activity",
                detail = "These events were written for the demo. The dashboard does not collect activity from this phone.",
                rows = listOf(
                    VisualRow("09:15 · Review prompt", "An example calendar module explained a possible future connection. No prompt was displayed by Android.", "Example"),
                    VisualRow("11:30 · Export review", "A fictional user read an export confirmation and cancelled. No file was opened or shared.", "Example"),
                    VisualRow("16:45 · Retention review", "A fictional user inspected a proposed retention period. No record was altered.", "Example"),
                ),
            ),
            VisualSection(
                title = "Sharing",
                headline = "Consent and sharing",
                detail = "Preview the information a future module could show before sharing data. This demo sends nothing.",
                rows = listOf(
                    VisualRow("Destination", "A future review would identify the receiving service or file location before allowing confirmation.", "Planned"),
                    VisualRow("Scope", "A future consent screen would list the data categories and let the owner decline optional sharing.", "Planned"),
                    VisualRow("Revoke access", "The sample shows where a connection removal control could appear. No account is connected.", "Unavailable"),
                ),
            ),
            VisualSection(
                title = "Retention",
                headline = "Retention and deletion",
                detail = "Inspect how a future module could explain retention, empty states and deletion. No policy is applied here.",
                rows = listOf(
                    VisualRow("Retention summary", "The proposed summary would identify which records are affected and when a policy takes effect.", "Concept"),
                    VisualRow("Deletion confirmation", "The proposed review would explain the effect on local records and exported copies. Nothing is deleted.", "Demo confirmation"),
                    VisualRow("Download report", "There is no live report or personal information to export from this screen.", "Unavailable"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "spaces",
        title = "Nova Spaces",
        subtitle = "Workspace organisation and layout concepts",
        icon = Icons.Default.Layers,
        sections = listOf(
            VisualSection(
                title = "Workspaces",
                headline = "Example workspaces",
                detail = "These fictional workspaces illustrate grouping apps and tools. This page does not list, discover or switch any real Space.",
                metrics = listOf(VisualMetric("Sample workspaces", "2"), VisualMetric("Example layouts", "2")),
                rows = listOf(
                    VisualRow("Studio", "Example tools: sketchbook, reference board and a demo project list. This is a fictional layout.", "Example"),
                    VisualRow("Field desk", "Example tools: trip notes, an offline checklist and a demo timeline. This is a fictional layout.", "Example"),
                ),
            ),
            VisualSection(
                title = "Layouts",
                headline = "Workspace layouts",
                detail = "Review how a future layout chooser could distinguish the phone cover display and the unfolded screen.",
                rows = listOf(
                    VisualRow("Compact layout", "An example workspace starts with one column of shortcuts and a small status area.", "Cover display"),
                    VisualRow("Expanded layout", "An example workspace has shortcuts on one side and a sample planning panel on the other.", "Unfolded"),
                    VisualRow("Preview changes", "The future layout editor could show a draft before applying it. Nova Home is unchanged by these samples.", "Unavailable"),
                ),
            ),
            VisualSection(
                title = "Setup",
                headline = "Create a workspace",
                detail = "This walkthrough illustrates a proposed setup sequence. The example cannot create a workspace or write to existing Space data.",
                rows = listOf(
                    VisualRow("Choose a label", "An example first step uses the fictional name Reading desk. No name is saved.", "Step 1"),
                    VisualRow("Choose a layout", "The example compares a compact shortcut panel and a wider dashboard.", "Step 2"),
                    VisualRow("Review and finish", "The future confirmation would explain where the layout is stored. This example has no finish action.", "Step 3"),
                ),
            ),
            VisualSection(
                title = "Boundaries",
                headline = "Workspace boundaries",
                detail = "The visual examples have no relationship to the real Space catalogue. Existing lock, authentication and separation rules still apply.",
                rows = listOf(
                    VisualRow("Switch confirmation", "The proposed confirmation would describe a workspace change without exposing content or metadata from another Space.", "Concept"),
                    VisualRow("Shared items", "The example shows a review step for items an owner explicitly chooses to share. This demo has no sharing action.", "Unavailable"),
                    VisualRow("Remove a workspace", "This sample confirmation explains what a future removal could affect. Nothing is removed.", "Demo confirmation"),
                ),
            ),
        ),
    ),
)
