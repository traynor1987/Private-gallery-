package uk.co.traynor.privategallery.home2

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Videocam

/** Bundled examples for the system catalogue. This file defines presentation data only. */
internal val visualUtilities: List<VisualModule> = listOf(
    VisualModule(
        id = "contacts",
        title = "Nova Contacts",
        subtitle = "Example people and groups",
        icon = Icons.Default.Badge,
        sections = listOf(
            VisualSection(
                title = "People",
                headline = "A private address book layout",
                detail = "Meet fictional members of the Aurora Studio. This demo never opens Android Contacts or starts a call or message.",
                metrics = listOf(VisualMetric("Example people", "3"), VisualMetric("Connected sources", "0")),
                rows = listOf(
                    VisualRow("Mira Fenwick", "Studio coordinator. The sample card has a name, group and reminder to review the venue. No phone number or address is supplied.", "Studio"),
                    VisualRow("Rowan Vale", "Equipment lead. The example profile shows where private notes and contact methods would sit.", "Workshop"),
                    VisualRow("Iona Reed", "Volunteer coordinator. This fictional contact belongs to the sample Workshop group.", "Workshop"),
                ),
            ),
            VisualSection(
                title = "Groups",
                headline = "Keep related people together",
                detail = "Browse how contacts could be grouped without making a mailing list or selecting real people.",
                rows = listOf(
                    VisualRow("Aurora Studio", "One fictional member. A future group view could offer explicit membership editing.", "1 example"),
                    VisualRow("Workshop", "Two fictional members. Opening the group does not initiate a conversation.", "2 examples"),
                    VisualRow("Favourites", "No example favourites have been selected. This screen illustrates a clean empty group.", "Empty"),
                ),
            ),
            VisualSection(
                title = "Sources",
                headline = "Review contacts before importing",
                detail = "Source selection, duplicate review and import consent are future designs. No account or permission is connected here.",
                rows = listOf(
                    VisualRow("Source preview", "A future import would show the selected source and which fields it provides before accepting anything.", "Unavailable"),
                    VisualRow("Possible duplicate", "The sample review compares two fictional Rowan entries without merging records.", "Example"),
                    VisualRow("Private contact card", "A future share action would preview selected fields and omit private notes by default.", "Planned"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "notifications",
        title = "Nova Notifications",
        subtitle = "Example inbox and visibility choices",
        icon = Icons.Default.Notifications,
        sections = listOf(
            VisualSection(
                title = "Inbox",
                headline = "A calmer notification inbox",
                detail = "These are fixed cards from fictional apps. This demo cannot read Android notifications or post an alert.",
                metrics = listOf(VisualMetric("Examples today", "2"), VisualMetric("Live listener", "Off")),
                rows = listOf(
                    VisualRow("10:15 · Studio checklist", "Sample reminder to review the workshop checklist. Opening the card displays this example only.", "Reminder"),
                    VisualRow("09:00 · Quiet review window", "Fictional digest entry. This example is not scheduled and will not appear outside the demo.", "Digest"),
                ),
            ),
            VisualSection(
                title = "Digest",
                headline = "Review at your own pace",
                detail = "Compare a grouped sample and an empty day. No delivery schedule or background job runs.",
                rows = listOf(
                    VisualRow("Morning digest", "The example groups the checklist and review window into one summary card.", "2 examples"),
                    VisualRow("Evening digest", "There are no fictional alerts in this window. The empty state is not a statement about Android alerts.", "Empty"),
                ),
            ),
            VisualSection(
                title = "Visibility",
                headline = "Keep private content off the lock screen",
                detail = "Preview visibility choices without changing Android notification access or any existing NovaOS setting.",
                rows = listOf(
                    VisualRow("Title only", "The example lock-screen card shows a generic title and hides the message body.", "Preview"),
                    VisualRow("Private source", "A future design could suppress names and content from a private workspace. This demo reads no Space metadata.", "Planned"),
                    VisualRow("Android connection", "No notification listener is active or requested by this demo.", "Unavailable"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "media",
        title = "Nova Media",
        subtitle = "Example library, player and collections",
        icon = Icons.Default.PhotoLibrary,
        sections = listOf(
            VisualSection(
                title = "Library",
                headline = "Browse a fictional media shelf",
                detail = "The shelf uses named placeholders for photos, audio and video. It does not scan the Vault, device storage or media providers.",
                metrics = listOf(VisualMetric("Example items", "3"), VisualMetric("Real files opened", "0")),
                rows = listOf(
                    VisualRow("Aurora poster", "Illustration placeholder. The sample metadata shows a portrait orientation and a fictional collection name.", "Image"),
                    VisualRow("Harbour loop", "Audio placeholder with a sample duration of two minutes and twenty seconds. There is no attached audio.", "Audio"),
                    VisualRow("Window light", "Video placeholder with a fictional 36-second runtime. Opening it does not play a clip.", "Video"),
                ),
            ),
            VisualSection(
                title = "Player",
                headline = "Playback layout preview",
                detail = "Explore a sample queue and timeline. The demo does not start playback or affect the working NovaOS media player.",
                rows = listOf(
                    VisualRow("Now playing layout", "Harbour loop is an example at 0:42 of 2:20. The position is fixed and no audio is playing.", "Preview"),
                    VisualRow("Up next", "Window light is the next sample entry. There is no active playback queue.", "Example"),
                ),
            ),
            VisualSection(
                title = "Collections",
                headline = "Group favourite media",
                detail = "Example collections demonstrate sorting and file details. No Vault media is moved, tagged or exported.",
                rows = listOf(
                    VisualRow("Studio references", "The poster and video placeholders appear together in this sample collection.", "2 examples"),
                    VisualRow("Listening shelf", "The audio placeholder illustrates how a future audio-only collection could look.", "1 example"),
                    VisualRow("Offline status", "The sample card distinguishes a local item from an unavailable source. Nothing is fetched here.", "Design only"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "downloads",
        title = "Nova Download Manager",
        subtitle = "Example transfer queue and destinations",
        icon = Icons.Default.FileDownload,
        sections = listOf(
            VisualSection(
                title = "Queue",
                headline = "A single view of sample transfers",
                detail = "The queue illustrates waiting, partial and failed states. It is a fixed sample with no URLs, network activity or files.",
                metrics = listOf(VisualMetric("Sample entries", "3"), VisualMetric("Live transfers", "0")),
                rows = listOf(
                    VisualRow("Aurora catalogue.pdf", "Example progress is 6 MB of 10 MB. No file or connection exists behind this card.", "60% sample"),
                    VisualRow("Harbour sketch.png", "A queued example waiting for an unmetered connection. The demo does not inspect network conditions.", "Queued sample"),
                    VisualRow("Workshop outline.txt", "A fictional interrupted transfer. A future retry would require a valid source and explicit action.", "Error sample"),
                ),
            ),
            VisualSection(
                title = "Completed",
                headline = "Review a completed item layout",
                detail = "These finished examples are text records only. There is no downloaded file to open or delete.",
                rows = listOf(
                    VisualRow("Studio palette.png", "The sample completion card includes a fictional size of 1.8 MB and a review action for a future build.", "Example complete"),
                    VisualRow("Review before opening", "A future manager would keep the destination and source visible and avoid automatically launching unknown files.", "Planned"),
                ),
            ),
            VisualSection(
                title = "Storage",
                headline = "Choose a destination with care",
                detail = "The storage preview does not read free space, request storage permissions or change a download path.",
                rows = listOf(
                    VisualRow("Destination picker", "A future operation would use an owner-approved destination and show it before writing.", "Unavailable"),
                    VisualRow("Private source handling", "This proposed queue does not combine records across Spaces or expose private sources.", "Design only"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "screen-recorder",
        title = "Nova Screen Recorder",
        subtitle = "Example recording setup and review",
        icon = Icons.Default.Videocam,
        sections = listOf(
            VisualSection(
                title = "Setup",
                headline = "Plan a capture before starting",
                detail = "This visual walkthrough does not request screen capture or microphone access. No recording or foreground service is running.",
                metrics = listOf(VisualMetric("Capture", "Off"), VisualMetric("Microphone", "Off")),
                rows = listOf(
                    VisualRow("Capture scope", "The example compares a whole-screen choice and a single-app choice. Both are unavailable here.", "Preview"),
                    VisualRow("Audio options", "System sound and microphone are illustrated as separate controls. Nothing is recorded.", "Unavailable"),
                    VisualRow("Countdown preview", "The fixed example shows a three-second countdown as it could appear before an authorised capture.", "Design only"),
                ),
            ),
            VisualSection(
                title = "Review",
                headline = "Inspect a fictional capture",
                detail = "The preview card is a bundled example. No screen image, thumbnail or recording is present.",
                rows = listOf(
                    VisualRow("Studio walkthrough", "The sample clip would be 18 seconds long with no audio. The play, save and discard actions are unavailable.", "Example clip"),
                    VisualRow("Recording library", "There are no real recordings in the demo. No device folder is queried.", "Empty"),
                ),
            ),
            VisualSection(
                title = "Privacy",
                headline = "Show clear capture boundaries",
                detail = "The design illustrates approval and visibility. It does not capture protected NovaOS screens or alter screenshot protection.",
                rows = listOf(
                    VisualRow("Permission review", "A future recording would use Android's capture consent and visibly indicate when it is running.", "Planned"),
                    VisualRow("Protected screens", "Existing protected-window rules stay in force. This demo cannot override them.", "No access"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "share-hub",
        title = "Nova Share Hub",
        subtitle = "Example share preview and redaction",
        icon = Icons.Default.Share,
        sections = listOf(
            VisualSection(
                title = "Draft",
                headline = "See what a share would include",
                detail = "Inspect fictional share drafts. This demo never opens Android Sharesheet, generates a file or sends anything.",
                metrics = listOf(VisualMetric("Attachments", "0 real"), VisualMetric("Recipients", "0 real")),
                rows = listOf(
                    VisualRow("Aurora workshop brief", "Text-only placeholder for a one-page draft with example filename and estimated size.", "Example"),
                    VisualRow("Harbour reference image", "A fictional image card illustrates file type and a proposed metadata review.", "Example"),
                ),
            ),
            VisualSection(
                title = "Review",
                headline = "Review data and destination",
                detail = "These options show how a future share could make its boundaries explicit. No selected file is inspected or edited here.",
                rows = listOf(
                    VisualRow("Metadata preview", "The example lists descriptive fields that could be included or omitted from a future authorised export.", "Design only"),
                    VisualRow("Destination preview", "No actual app, contact or external service is listed. A future action would require explicit confirmation.", "Unavailable"),
                    VisualRow("Protected content", "An example warning explains that some records cannot be exported through this proposed flow.", "Preview"),
                ),
            ),
            VisualSection(
                title = "Activity",
                headline = "A transparent handoff history",
                detail = "No shares have been made through this demo. The layout distinguishes cancelled and completed sample handoffs.",
                rows = listOf(
                    VisualRow("Cancelled example", "The fictional brief was reviewed but not shared. No action was attempted here.", "Sample"),
                    VisualRow("Clear history", "There is no saved share history to clear in the demonstration.", "Unavailable"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "automations",
        title = "Nova Automations",
        subtitle = "Example rules and dry-run results",
        icon = Icons.Default.Bolt,
        sections = listOf(
            VisualSection(
                title = "Rules",
                headline = "See rules before enabling them",
                detail = "All rules are bundled examples. They are disabled and never subscribe to events, start jobs or change data.",
                metrics = listOf(VisualMetric("Example rules", "2"), VisualMetric("Active rules", "0")),
                rows = listOf(
                    VisualRow("Review a studio checklist", "The example pairs a weekly trigger with an in-app reminder. There is no real schedule.", "Disabled demo"),
                    VisualRow("Suggest a quiet workspace", "The example pairs an explicit shortcut with a suggested layout. It cannot change Home or Android settings.", "Disabled demo"),
                ),
            ),
            VisualSection(
                title = "Builder",
                headline = "Build a sample trigger and action",
                detail = "Review the pieces of a proposed rule. The cards are explanatory and do not register a trigger.",
                rows = listOf(
                    VisualRow("Trigger", "A fictional Friday review window illustrates time-based triggers.", "Example"),
                    VisualRow("Condition", "The example only applies when the owner has chosen to review the checklist.", "Example"),
                    VisualRow("Action", "Show a review suggestion inside the sample. No notification or external app is invoked.", "Example"),
                ),
            ),
            VisualSection(
                title = "Dry run",
                headline = "Preview a rule outcome safely",
                detail = "This is a prewritten outcome from a fictional input. It neither evaluates the current device nor runs a rule.",
                rows = listOf(
                    VisualRow("Example matched", "The fixed scenario uses a Friday date and a selected checklist, so it would suggest a review.", "Sample outcome"),
                    VisualRow("Example skipped", "A Tuesday date would not satisfy the example condition.", "Sample outcome"),
                    VisualRow("Real run history", "No automation has run, and the demo writes no activity log.", "Empty"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "app-usage",
        title = "Nova App Usage",
        subtitle = "Example activity and time distribution",
        icon = Icons.Default.BarChart,
        sections = listOf(
            VisualSection(
                title = "Today",
                headline = "Understand an example usage day",
                detail = "The figures below describe fictional activity. This demo does not request Usage Access or inspect app launches.",
                metrics = listOf(VisualMetric("Sample active time", "1 h 25 min"), VisualMetric("Live collection", "Off")),
                rows = listOf(
                    VisualRow("Studio Board", "Fictional planning app used for 35 minutes in the example.", "35 min"),
                    VisualRow("Harbour Reader", "Fictional reading app used for 30 minutes in the example.", "30 min"),
                    VisualRow("Pocket Sketch", "Fictional sketching app used for 20 minutes in the example.", "20 min"),
                ),
            ),
            VisualSection(
                title = "Trends",
                headline = "Compare sample daily totals",
                detail = "This fixed chart illustrates the difference between a recorded zero and a day with no data. It is unrelated to the owner's app usage.",
                trend = VisualTrend(
                    "Sample active minutes",
                    "minutes",
                    listOf(
                        VisualTrendPoint("Mon", 48),
                        VisualTrendPoint("Tue", 0),
                        VisualTrendPoint("Wed", 64),
                        VisualTrendPoint("Thu", 85),
                        VisualTrendPoint("Fri", 52),
                    ),
                ),
                rows = listOf(
                    VisualRow("Weekend · missing data", "There is no Saturday or Sunday sample. The chart does not treat missing days as measured zero.", "No sample"),
                    VisualRow("Category breakdown", "A future layout could group time by owner-chosen categories without treating time spent as a judgement.", "Planned"),
                ),
            ),
            VisualSection(
                title = "Controls",
                headline = "Choose what may be measured",
                detail = "This proposed settings layout grants no permission and stores no preferences.",
                rows = listOf(
                    VisualRow("Usage Access", "Not requested. A future implementation would explain the purpose and provide a separate opt-in.", "Off"),
                    VisualRow("Local retention", "The design could offer a retention window and deletion controls. There are no tracked records to delete.", "Unavailable"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "update-centre",
        title = "Nova Update Centre",
        subtitle = "Example release review and upgrade safety",
        icon = Icons.Default.Update,
        sections = listOf(
            VisualSection(
                title = "Overview",
                headline = "Review updates before installing",
                detail = "These are fictional release cards. This demo does not check for updates, download an APK or open a package installer.",
                metrics = listOf(VisualMetric("Update checks", "Off"), VisualMetric("Sample channel", "Review")),
                rows = listOf(
                    VisualRow("Studio review build", "A fictional release card shows a change summary and review status without claiming an update is available.", "Example"),
                    VisualRow("Release notes", "The sample separates visual changes, future engine work and known limitations.", "Preview"),
                ),
            ),
            VisualSection(
                title = "Safety",
                headline = "Check identity before upgrading",
                detail = "This proposed checklist describes future safeguards. The status cards are examples and perform no verification.",
                rows = listOf(
                    VisualRow("Package and signing identity", "A future review would show the intended package and original production signer and refuse a mismatch.", "Planned"),
                    VisualRow("In-place upgrade", "The intended flow preserves encrypted data and reports whether an upgrade and restart were verified.", "Design only"),
                    VisualRow("Backup reminder", "Review the existing Backup app separately before an update if desired. This demo has not created or verified a backup.", "No action"),
                ),
            ),
            VisualSection(
                title = "History",
                headline = "Distinguish review from installation",
                detail = "These fixed examples illustrate status labels. No installed version or update history was read.",
                rows = listOf(
                    VisualRow("Review pending", "An example build awaits inspection. A review state does not mean an update has been installed.", "Sample"),
                    VisualRow("Update log", "There is no real update history in this demo. No installer is connected.", "Unavailable"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "browser-privacy",
        title = "Nova Browser Privacy",
        subtitle = "Example privacy information and browser controls",
        icon = Icons.Default.Shield,
        sections = listOf(
            VisualSection(
                title = "Overview",
                headline = "Make browsing privacy easier to read",
                detail = "This demo uses fictional examples. It does not inspect tabs, cookies or sessions, and it does not change browser or routing behaviour.",
                metrics = listOf(VisualMetric("Live inspection", "Off"), VisualMetric("Settings applied", "0")),
                rows = listOf(
                    VisualRow("Sample site card", "The fictional Aurora Archive card shows where a future browser could explain its site settings.", "Example"),
                    VisualRow("Session visibility", "An example label distinguishes current session settings from suggestions. No real session details are shown.", "Preview"),
                ),
            ),
            VisualSection(
                title = "Site controls",
                headline = "Review an example site's settings",
                detail = "These are explanatory controls without permission or site-setting actions.",
                rows = listOf(
                    VisualRow("Camera and microphone", "A sample site card shows proposed per-site status text. No Android permission is requested.", "Unavailable"),
                    VisualRow("Stored site data", "A future design could describe what may be removed and ask for confirmation. This demo cannot clear cookies or storage.", "Planned"),
                    VisualRow("Exceptions", "No sample exception has been selected. Real browser allowlists stay unchanged.", "Empty"),
                ),
            ),
            VisualSection(
                title = "Routing",
                headline = "Understand routing without changing it",
                detail = "This walkthrough does not inspect, start or change VPN or Tor. It makes no claim about the current connection.",
                rows = listOf(
                    VisualRow("Connection information", "A proposed page could distinguish verified status from unavailable information. All status in this demo is synthetic.", "Design only"),
                    VisualRow("Privacy boundaries", "Browser engines, live sessions and existing Primary and Hidden routing remain outside this demo.", "No access"),
                ),
            ),
        ),
    ),
)
