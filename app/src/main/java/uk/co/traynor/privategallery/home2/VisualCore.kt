package uk.co.traynor.privategallery.home2

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TaskAlt

/** Fixed examples for the first visual checkpoint. None are sourced from the device. */
internal val visualCore: List<VisualModule> = listOf(
    VisualModule(
        id = "health",
        title = "Nova Health",
        subtitle = "The next home for JamesOS health views",
        icon = Icons.Default.FavoriteBorder,
        sections = listOf(
            VisualSection(
                title = "Today",
                headline = "Health dashboard",
                detail = "Nova Health is the planned successor to JamesOS. This dashboard uses a fictional profile. Existing JamesOS records stay where they are and no source is connected.",
                metrics = listOf(VisualMetric("Nova Recovery · demo", "74 / 100"), VisualMetric("Nova Strain · demo", "42 / 100")),
                rows = listOf(
                    VisualRow("Today’s movement", "A fictional 24-minute walk contributes to 6,420 example steps. Activity entries would show their source and time of last sync.", "6,420 steps"),
                    VisualRow("Last night", "The example sleep window runs from 23:06 to 06:24. These are fixed times for a fictional profile.", "7h 18m"),
                    VisualRow("Latest check-in", "The example mood is Calm and the example resting heart rate is 59 bpm. No sensor or personal record was read.", "Sample"),
                ),
            ),
            VisualSection(
                title = "Vitals",
                headline = "Heart rate and variability",
                detail = "See how readings could be presented with their units, time and source. Every value is synthetic and this screen provides no clinical interpretation.",
                metrics = listOf(VisualMetric("Heart rate", "78 bpm"), VisualMetric("HRV", "49 ms"), VisualMetric("Resting HR", "59 bpm")),
                rows = listOf(
                    VisualRow("Heart rate", "A snapshot of 78 beats per minute at 10:00. The timestamp is a fixed illustration.", "Demo reading"),
                    VisualRow("Heart rate variability", "Example overnight HRV is 49 milliseconds. A future detail screen could explain how the source recorded it.", "Demo reading"),
                    VisualRow("Resting heart rate", "Example resting heart rate is 59 beats per minute. The reading is separate from the instantaneous heart rate.", "Demo reading"),
                ),
            ),
            VisualSection(
                title = "Sleep & recovery",
                headline = "Sleep and Nova Recovery",
                detail = "This visual separates a sleep summary from the proposed Nova Recovery concept. The recovery value is a fixed example and no score is calculated.",
                metrics = listOf(VisualMetric("Sleep", "7h 18m"), VisualMetric("Nova Recovery · demo", "74 / 100")),
                rows = listOf(
                    VisualRow("Sleep session", "Example bedtime 23:06 and wake time 06:24. Future sessions could show interruptions and source information where available.", "Sample night"),
                    VisualRow("Nova Recovery", "A proposed NovaOS view of recovery context across sleep and resting signals. 74 / 100 is a visual example, with no score model connected. WHOOP Recovery would be a separately attributed source value.", "Concept only"),
                    VisualRow("Missing overnight data", "The empty state distinguishes a missing session from a zero-length session. It offers no assessment when evidence is missing.", "Empty example"),
                ),
            ),
            VisualSection(
                title = "Workouts & strain",
                headline = "Training and Nova Strain",
                detail = "Preview activity detail and a separate proposed Nova Strain concept. No workouts are tracked or imported.",
                metrics = listOf(VisualMetric("Sample workouts", "2"), VisualMetric("Nova Strain · demo", "42 / 100")),
                rows = listOf(
                    VisualRow("Park loop", "24 minutes · example average heart rate 112 bpm. The workout detail pairs activity time with the source of any heart rate readings.", "Walk"),
                    VisualRow("Studio circuit", "32 minutes · fictional strength session. The sample does not estimate energy expenditure.", "Strength"),
                    VisualRow("Nova Strain", "A proposed NovaOS view of activity load. 42 / 100 is a fixed layout example, with no score model connected. WHOOP Strain would be shown separately with its own source label.", "Concept only"),
                ),
            ),
            VisualSection(
                title = "Weight & goals",
                headline = "Progress toward a goal",
                detail = "The example profile shows how a weight log and an optional goal could sit together. None of these values belong to the owner.",
                metrics = listOf(VisualMetric("Sample weight", "71.4 kg"), VisualMetric("Example goal", "70.0 kg")),
                rows = listOf(
                    VisualRow("Example weigh-in", "71.4 kg on 14 May 2026, compared with 72.0 kg on 1 May. This fixed series illustrates progress without offering advice.", "Sample entry"),
                    VisualRow("Goal preview", "A future goal editor could change the target and choose whether to show a milestone. This preview does not save goals.", "Unavailable"),
                ),
            ),
            VisualSection(
                title = "Mood",
                headline = "Mood check-ins",
                detail = "Browse fictional check-ins and the proposed entry layout. The demo collects no mood or journal data.",
                rows = listOf(
                    VisualRow("Calm", "14 May · Example note: A quiet break outside. The mood and note are bundled examples.", "Sample check-in"),
                    VisualRow("Focused", "12 May · Example note: Finished a reading session. No observation or analysis was performed.", "Sample check-in"),
                    VisualRow("New check-in", "The proposed editor would offer a mood choice, time and optional note. Entries cannot be created in this visual phase.", "Unavailable"),
                ),
            ),
            VisualSection(
                title = "Daily",
                headline = "Daily trend",
                detail = "Example heart rate snapshots across a single fictional day. The chart does not display live readings.",
                trend = VisualTrend("Heart rate during the day", "bpm", listOf(
                    VisualTrendPoint("06:00", 62), VisualTrendPoint("10:00", 78), VisualTrendPoint("14:00", 84), VisualTrendPoint("18:00", 71),
                )),
                rows = listOf(VisualRow("Reading detail", "The fictional 14:00 snapshot is 84 bpm. Gaps between snapshots do not represent continuous monitoring.", "Sample")),
            ),
            VisualSection(
                title = "Weekly",
                headline = "Weekly trend",
                detail = "Compare five fictional activity days and see how an incomplete week is represented.",
                metrics = listOf(VisualMetric("Days shown", "5 / 7")),
                trend = VisualTrend("Steps in the sample week", "steps", listOf(
                    VisualTrendPoint("Mon", 5820), VisualTrendPoint("Tue", 7210), VisualTrendPoint("Wed", 6100), VisualTrendPoint("Thu", 3840), VisualTrendPoint("Fri", 6420),
                )),
                rows = listOf(VisualRow("Weekend gaps", "The sample has no Saturday or Sunday records. Missing days are distinct from measured zeroes.", "No sample")),
            ),
            VisualSection(
                title = "Monthly",
                headline = "Monthly trend",
                detail = "A fictional four-week sleep view with a separate value for each example week.",
                trend = VisualTrend("Average nightly sleep by week", "min", listOf(
                    VisualTrendPoint("Week 1", 414), VisualTrendPoint("Week 2", 428), VisualTrendPoint("Week 3", 442), VisualTrendPoint("Week 4", 438),
                )),
                rows = listOf(VisualRow("Week 4 detail", "The example average is 438 minutes, or 7 hours and 18 minutes, for the week.", "Sample")),
            ),
            VisualSection(
                title = "History",
                headline = "Activity timeline",
                detail = "Inspect a fixed set of historical examples across workouts, sleep, weight and mood. JamesOS data has not been imported.",
                rows = listOf(
                    VisualRow("14 May · Morning", "Fictional sleep session ends at 06:24, followed by an example weight entry at 07:00.", "Sleep · weight"),
                    VisualRow("14 May · Afternoon", "Example activity: Park loop. Example mood check-in: Calm.", "Workout · mood"),
                    VisualRow("Earlier history", "This example timeline ends on 12 May. A future date filter would make longer histories easier to inspect.", "End of sample"),
                ),
            ),
            VisualSection(
                title = "Sources",
                headline = "Sources and sync status",
                detail = "The source setup preview shows where connection, permissions and sync details would appear. No accounts, devices or health permissions are connected.",
                rows = listOf(
                    VisualRow("WHOOP", "Future optional account connection. WHOOP metrics would keep their original source labels. No login, token or API access is used here.", "Not connected"),
                    VisualRow("Health Connect", "Future optional Android source. A real setup would explain the requested data types before showing a permission prompt. This demo requests none.", "Not connected"),
                    VisualRow("Nova Health for Wear OS", "A separate future watch app would provide its own setup and on-watch experience. This release does not install, pair or communicate with a watch.", "Future watch app"),
                    VisualRow("JamesOS transition", "A future migration could provide a source review and confirmation. No existing JamesOS records or settings are read, copied or moved.", "Planned"),
                    VisualRow("Last sync", "No source is active and no sync has run. The completed source view could show the last successful sync and any pending errors.", "Never synced"),
                ),
            ),
            VisualSection(
                title = "Privacy",
                headline = "Consent and data controls",
                detail = "Preview the proposed controls before any health data is connected. This module only displays bundled synthetic examples.",
                rows = listOf(
                    VisualRow("Consent review", "A future consent screen would name the source, explain what is read and show how to revoke access. No consent is recorded and no permissions are requested.", "Unavailable"),
                    VisualRow("Export preview", "The planned export flow would show selected dates and data categories before an export. No file is written or shared.", "Unavailable"),
                    VisualRow("Deletion preview", "A future deletion confirmation would list which local records are affected and explain any connected-source limits. This demo deletes nothing.", "Unavailable"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "notes",
        title = "Nova Notes",
        subtitle = "Notebooks, drafts and reading views",
        icon = Icons.Default.StickyNote2,
        sections = listOf(
            VisualSection(
                title = "Library",
                headline = "Notes library",
                detail = "Browse the bundled notes and see how the library could separate pinned work, notebooks and drafts. Existing NovaOS notes are outside this demo.",
                metrics = listOf(VisualMetric("Notebooks", "2"), VisualMetric("Pinned", "1")),
                rows = listOf(
                    VisualRow("Window Garden", "Sketch: start with three small planters, label the seedlings, then compare morning and afternoon light.", "Pinned"),
                    VisualRow("Pocket Observatory", "Field note: bring a folding chair, a dim torch and a printed map of the northern sky.", "Ideas"),
                    VisualRow("Reading list", "Example entries: A map of night skies, A guide to rain gardens, and An illustrated atlas of clouds.", "Reference"),
                ),
            ),
            VisualSection(
                title = "Notebooks",
                headline = "Organise by topic",
                detail = "Example notebooks group related writing with a title, a colour and a count.",
                rows = listOf(
                    VisualRow("Ideas", "Sample notebook with Window Garden and Pocket Observatory. A future notebook could show its pinned entries before the rest.", "2 samples"),
                    VisualRow("Reference", "Sample notebook with the Reading list. The final UI could offer tags and sorting without requiring a title search.", "1 sample"),
                ),
            ),
            VisualSection(
                title = "Draft",
                headline = "Reading and editing layout",
                detail = "Preview a blank draft and a formatted sample. Editing and saving are unavailable in this visual checkpoint.",
                rows = listOf(
                    VisualRow("Untitled note", "The empty draft introduces a title, body and notebook choice. Nothing has been created or saved.", "Empty example"),
                    VisualRow("A page from Window Garden", "Start small\n\nPick a shelf by the window. Place the seeds where they get morning light and leave room for a watering tray.\n\nNext step\nMark the planting date on each pot.", "Formatted sample"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "files",
        title = "Nova Files",
        subtitle = "Folder browsing and file information",
        icon = Icons.Default.FolderOpen,
        sections = listOf(
            VisualSection(
                title = "Browse",
                headline = "File browser",
                detail = "Explore a sample folder tree. This preview does not list device storage, Vault files or document providers.",
                metrics = listOf(VisualMetric("Folders", "3"), VisualMetric("Example items", "5")),
                rows = listOf(
                    VisualRow("Project Aurora", "Example contents\nBrief.pdf · 280 KB\nTrail-map.png · 1.8 MB\n\nThese entries represent a folder view. There are no attached files.", "2 items"),
                    VisualRow("Field recordings", "Example contents\nWind-in-pines.m4a · 5.1 MB\nEvening-loop.m4a · 3.5 MB\n\nThe items are text examples. No audio is opened.", "2 items"),
                    VisualRow("Reading room", "Example contents\nCloud-atlas.txt · 18 KB\n\nA future folder could offer sorting by name, size or modification date.", "1 item"),
                ),
            ),
            VisualSection(
                title = "Recent",
                headline = "Recent file examples",
                detail = "This fixed list shows how recently viewed items could be grouped by date. Nova Files is not tracking file opens.",
                rows = listOf(
                    VisualRow("Brief.pdf", "Project Aurora · 14 May 2026 at 10:12 · PDF document · 280 KB.", "14 May"),
                    VisualRow("Trail-map.png", "Project Aurora · 13 May 2026 at 15:32 · PNG image · 1.8 MB.", "13 May"),
                    VisualRow("Cloud-atlas.txt", "Reading room · 12 May 2026 at 18:05 · Plain text · 18 KB.", "12 May"),
                ),
            ),
            VisualSection(
                title = "Info",
                headline = "File details and actions",
                detail = "The sample information panel groups common file properties and explains which controls are planned.",
                rows = listOf(
                    VisualRow("Example metadata", "Name: Brief.pdf\nFormat: PDF document\nSize: 280 KB\nParent: Project Aurora", "Read-only example"),
                    VisualRow("File operations", "Open, move, rename and delete would act on the selected file after validation. The demo cannot perform these operations.", "Unavailable"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "calendar",
        title = "Nova Calendar",
        subtitle = "An agenda, week view and event details",
        icon = Icons.Default.CalendarMonth,
        sections = listOf(
            VisualSection(
                title = "Agenda",
                headline = "Thursday, 14 May",
                detail = "A fictional day in May 2026. Nothing here comes from the device calendar, contacts or accounts.",
                metrics = listOf(VisualMetric("Example events", "3"), VisualMetric("Day view", "14 May")),
                rows = listOf(
                    VisualRow("09:30 · Studio planning", "09:30–10:15 · Studio calendar · Example location: Cedar Room. Review the sample brief and outline the next draft.", "45 min"),
                    VisualRow("13:00 · Garden walk", "13:00–13:30 · Personal calendar example · Example location: Willow Gardens.", "30 min"),
                    VisualRow("18:00 · Reading hour", "18:00–19:00 · Personal calendar example · No location in this sample.", "1 hr"),
                ),
            ),
            VisualSection(
                title = "Week",
                headline = "11–17 May 2026",
                detail = "A sample weekly overview, including an empty day and an event that takes the whole day.",
                rows = listOf(
                    VisualRow("Tuesday, 12 May", "16:00–16:30 · Sketch review. The sample meeting has a 30-minute duration.", "1 event"),
                    VisualRow("Wednesday, 13 May", "No example events for this day. The agenda remains open for inspection.", "Empty"),
                    VisualRow("Saturday, 16 May", "Example event: Studio open day. All-day events have no start time.", "All day"),
                ),
            ),
            VisualSection(
                title = "Event",
                headline = "Event setup preview",
                detail = "Inspect the proposed event controls. Creating events, sending invitations and scheduling reminders are unavailable.",
                rows = listOf(
                    VisualRow("Time and recurrence", "Example: 09:30–10:15 on 14 May, Europe/London, no recurrence. Time and recurrence values are sample labels.", "Demo fields"),
                    VisualRow("Calendar and reminder", "Example calendar: Studio. Example reminder: 15 minutes before. No reminder is scheduled and no account is connected.", "Demo fields"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "tasks",
        title = "Nova Tasks",
        subtitle = "Daily planning and project progress",
        icon = Icons.Default.TaskAlt,
        sections = listOf(
            VisualSection(
                title = "Today",
                headline = "A focused task list",
                detail = "A sample plan with priorities, a completed item and a later action. These are bundled examples.",
                metrics = listOf(VisualMetric("Open", "2"), VisualMetric("Done", "1")),
                rows = listOf(
                    VisualRow("Review the Aurora brief", "Project Aurora · Studio list · Example due time 11:00. The sample checklist includes opening the brief and recording one question.", "High"),
                    VisualRow("Label the seed trays", "Window Garden · Home list · No due time. The example has two labels still to add.", "Open"),
                    VisualRow("Collect route notes", "Pocket Observatory · Example item marked complete at 08:45. This status is part of the sample data.", "Done"),
                ),
            ),
            VisualSection(
                title = "Projects",
                headline = "Progress by project",
                detail = "Preview how tasks, checklists and milestones could be grouped into smaller plans.",
                rows = listOf(
                    VisualRow("Project Aurora", "1 of 3 sample steps complete. Brief received is done. Review brief and send first draft remain open.", "1 / 3"),
                    VisualRow("Window Garden", "2 of 4 sample steps complete. Pots and compost are ready. Seed labels and watering plan remain open.", "2 / 4"),
                ),
            ),
            VisualSection(
                title = "Review",
                headline = "Plan, move or defer",
                detail = "This section previews the fields for triage. No real task records, reminders or notifications are changed.",
                rows = listOf(
                    VisualRow("Inbox", "A task with no project or due date would wait here for review. The sample inbox is empty.", "Empty"),
                    VisualRow("Due later", "Example task: Send first draft, 18 May 2026. A future editor could change the due date and reminder independently.", "1 sample"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "clipboard",
        title = "Nova Clipboard",
        subtitle = "Clip previews, pins and retention controls",
        icon = Icons.Default.ContentPaste,
        sections = listOf(
            VisualSection(
                title = "Recent",
                headline = "Recent clip examples",
                detail = "This is a fixed sample list. Nova Clipboard does not read, write or monitor the Android clipboard.",
                metrics = listOf(VisualMetric("Recent", "3"), VisualMetric("Pinned", "1")),
                rows = listOf(
                    VisualRow("Short text", "Meet at the Cedar Room entrance at 09:30. This sentence was bundled with the app.", "Plain text"),
                    VisualRow("Link example", "https://example.invalid/aurora\n\nThe address is a text sample. It does not open a browser.", "Link"),
                    VisualRow("Longer excerpt", "Bring the map, a dim torch and a spare battery. Leave a blank page in the notebook for observations.", "Plain text"),
                ),
            ),
            VisualSection(
                title = "Pinned",
                headline = "Keep examples in reach",
                detail = "Pinned examples show how frequently used snippets might be grouped. Pinning and pasting are unavailable.",
                rows = listOf(
                    VisualRow("Studio signature", "Aurora Studio\nExample project correspondence\n\nThis is a fictional signature with no personal details.", "Sample pin"),
                    VisualRow("No other pins", "The empty state would invite the owner to review an existing clip before pinning it.", "Empty"),
                ),
            ),
            VisualSection(
                title = "Controls",
                headline = "Retention and privacy",
                detail = "Inspect proposed controls before they are implemented. Clip history is inactive.",
                rows = listOf(
                    VisualRow("History collection", "No collection is running. This preview does not install a listener or request access.", "Inactive"),
                    VisualRow("Retention period", "Example options: session only, one day or one week. The demo does not store real clips.", "Planned"),
                    VisualRow("Clear history", "A future confirmation would identify which clips were selected and whether pins were included. No data is deleted here.", "Unavailable"),
                ),
            ),
        ),
    ),
)
