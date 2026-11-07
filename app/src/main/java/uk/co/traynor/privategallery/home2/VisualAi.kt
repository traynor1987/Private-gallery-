package uk.co.traynor.privategallery.home2

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search

/** Fixed illustrations of possible AI workflows. No prompts, index, provider or record access. */
internal val visualAi: List<VisualModule> = listOf(
    VisualModule(
        id = "ai",
        title = "Nova AI",
        subtitle = "Assistant workspace and request review",
        icon = Icons.Default.AutoAwesome,
        sections = listOf(
            VisualSection(
                title = "Workspace",
                headline = "A quiet place to think",
                detail = "Explore prewritten prompts and responses from a fictional workspace. No model runs and no messages are sent.",
                metrics = listOf(VisualMetric("Model connection", "Off"), VisualMetric("Examples", "3")),
                rows = listOf(
                    VisualRow("Plan a small project", "Example prompt: Help organise the Aurora Workshop handover. Prewritten reply: List the open decisions, agree the review date and leave a short handover note.", "Example reply"),
                    VisualRow("Compare two ideas", "Example prompt: Compare a checklist with a timeline. Prewritten reply: A checklist makes completion visible. A timeline makes handoffs and dependencies visible.", "Example reply"),
                    VisualRow("Draft a summary", "Example source: A fictional workshop moved from Room A to Room C. Prewritten summary: Update the invitation and confirm the new room before the event.", "Example reply"),
                ),
            ),
            VisualSection(
                title = "Review",
                headline = "See what a request would include",
                detail = "Inspect an example approval screen before any future provider connection. The sample request is prewritten and cannot be sent.",
                rows = listOf(
                    VisualRow("Prompt preview", "Summarise the Aurora Workshop checklist in three short bullets. This is a built-in sample and contains no owner input.", "Synthetic prompt"),
                    VisualRow("Included context", "The fictional checklist contains three items: Confirm the room, assign a reviewer and share the draft agenda. No app records are attached.", "One example"),
                    VisualRow("Destination and approval", "No provider is selected or connected. A future request would show its destination, selected context and a clear choice to decline before transmission.", "Unavailable"),
                ),
            ),
            VisualSection(
                title = "Connections",
                headline = "Working apps stay separate",
                detail = "These cards explain possible future connections. This demo cannot read Nova Mind or Evidence Locker.",
                rows = listOf(
                    VisualRow("Nova Mind context", "A future request could let you choose a note or reflection explicitly. Nothing from Nova Mind is visible here.", "Planned"),
                    VisualRow("Evidence Locker context", "A future workflow could identify the exact items selected in the working Evidence Locker before asking for approval. This demo opens and analyses no evidence.", "Planned"),
                    VisualRow("Empty connection state", "No provider, account or app source is connected. There is no model download or sign-in in this demo.", "Off"),
                ),
            ),
            VisualSection(
                title = "Privacy",
                headline = "Control what leaves the workspace",
                detail = "Preview the information a future AI privacy page could show. These example states do not change app or account settings.",
                rows = listOf(
                    VisualRow("Request approval", "The proposed design shows selected material and the destination before approval. Opening this screen gives no permission.", "Design only"),
                    VisualRow("Conversation history", "There are no stored conversations here. The example replies ship with the app and reappear whenever the demo opens.", "Synthetic only"),
                    VisualRow("Deletion review", "A future deletion screen would explain what is stored locally and what a provider retains. There is nothing to delete from this demo.", "Unavailable"),
                ),
            ),
        ),
    ),
    VisualModule(
        id = "ai-search",
        title = "Nova AI Search",
        subtitle = "Example questions, results and citations",
        icon = Icons.Default.Search,
        sections = listOf(
            VisualSection(
                title = "Discover",
                headline = "Start with a question",
                detail = "Open a bundled example to see how an answer could cite its sources. No files are indexed and no query runs.",
                metrics = listOf(VisualMetric("Example sources", "3"), VisualMetric("Live index", "Off")),
                rows = listOf(
                    VisualRow("What is left for Aurora Workshop?", "Prewritten answer: Confirm the room and collect the review notes. Sample sources: Aurora handover, checklist items 1 and 2.", "Example answer"),
                    VisualRow("When is the draft review?", "Prewritten answer: The fictional calendar lists Friday at 14:00. Sample source: Studio planning calendar, Review entry.", "Example answer"),
                    VisualRow("Find the north gate code", "No answer in the fictional collection. This empty result shows how a future search could acknowledge missing evidence.", "Empty example"),
                ),
            ),
            VisualSection(
                title = "Sources",
                headline = "Read the example citations",
                detail = "These citations point only to fictional snippets below. They cannot open real documents or records.",
                rows = listOf(
                    VisualRow("Aurora handover", "Fictional snippet: Confirm the room and collect review notes before Friday. Location in the example: checklist items 1 and 2.", "Sample note"),
                    VisualRow("Studio planning calendar", "Fictional snippet: Draft review, Friday 14:00. Location in the example: Review entry.", "Sample event"),
                    VisualRow("Workshop brief", "Fictional snippet: Bring a one-page summary and one question for the group. Location in the example: Preparation.", "Sample document"),
                ),
            ),
            VisualSection(
                title = "Scope",
                headline = "Choose where a future search could look",
                detail = "This layout illustrates source selection. The bundled examples always use the same fixed collection.",
                rows = listOf(
                    VisualRow("Nova Mind", "A future search could include explicitly selected Mind entries. There is no access to those entries here.", "Unavailable"),
                    VisualRow("Evidence Locker", "A future search could require separate approval for a selected collection. This demo cannot see any evidence or collection names.", "Unavailable"),
                    VisualRow("Current Space", "This catalogue contains the same fictional sample texts for every owner. No personal records or Space metadata appear here.", "Synthetic only"),
                ),
            ),
            VisualSection(
                title = "Privacy",
                headline = "Review search controls",
                detail = "These are proposed controls. There is no index, external provider, search history or background search process in this demo.",
                rows = listOf(
                    VisualRow("Index status", "The status panel shows the inactive example. No device files or app content have been scanned.", "Off"),
                    VisualRow("Provider approval", "A future external search would identify what is sent and who receives it. This screen cannot enable transmission.", "Planned"),
                    VisualRow("Clear search data", "There is no saved query or index to clear. A future confirmation would describe which data is affected.", "Unavailable"),
                ),
            ),
        ),
    ),
)
