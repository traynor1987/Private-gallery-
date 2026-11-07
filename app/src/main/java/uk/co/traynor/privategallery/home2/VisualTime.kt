package uk.co.traynor.privategallery.home2

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule

/** Fictional time and earnings examples, with no clock, account or storage dependencies. */
internal val visualTime = VisualModule(
    id = "time",
    title = "Nova Time",
    subtitle = "See where a whole day goes",
    icon = Icons.Default.Schedule,
    sections = listOf(
        VisualSection(
            title = "Today",
            headline = "Your day, in one place",
            detail = "A complete 24-hour example for a fictional profile on Thursday, 14 May 2026. Times, work, earnings and activity below are bundled demo data.",
            metrics = listOf(VisualMetric("Paid activity", "8h 30m"), VisualMetric("Sleep", "8h 45m")),
            timeline = listOf(
                VisualTimeSlice("Sleep", 405),
                VisualTimeSlice("Preparation", 45),
                VisualTimeSlice("Commute", 30),
                VisualTimeSlice("Paid shift", 240),
                VisualTimeSlice("Unpaid break", 30),
                VisualTimeSlice("Paid shift", 180),
                VisualTimeSlice("Commute", 30),
                VisualTimeSlice("Free time", 60),
                VisualTimeSlice("Gig shift", 90),
                VisualTimeSlice("Personal", 30),
                VisualTimeSlice("Interruption", 15),
                VisualTimeSlice("Personal", 165),
                VisualTimeSlice("Sleep", 120),
            ),
            rows = listOf(
                VisualRow("Personal and free time", "1 hour of unallocated free time and 3 hours 15 minutes of personal activity. The timeline distinguishes time with no scheduled use from chosen activities.", "4h 15m"),
                VisualRow("Unpaid work support", "45 minutes of preparation, 60 minutes of commuting and a 30-minute unpaid break.", "2h 15m"),
                VisualRow("Interruptions", "One fictional 15-minute interruption. It appears separately from planned personal time, without identifying anyone.", "15m"),
            ),
        ),
        VisualSection(
            title = "Timeline",
            headline = "24-hour activity timeline",
            detail = "Each minute belongs to one primary category. The sample covers midnight to midnight without overlap.",
            rows = listOf(
                VisualRow("00:00–06:45 · Sleep", "First sleep interval in the sample calendar day.", "6h 45m"),
                VisualRow("06:45–08:00 · Before work", "06:45–07:30 preparation and 07:30–08:00 outbound commute. Both count toward the work-related window.", "1h 15m unpaid"),
                VisualRow("08:00–15:30 · Domino’s shift", "08:00–12:00 and 12:30–15:30 paid work. The 12:00–12:30 break is unpaid and remains visible as its own interval.", "7h paid"),
                VisualRow("15:30–17:00 · Travel and free time", "15:30–16:00 return commute, then 16:00–17:00 unallocated free time.", "1h 30m"),
                VisualRow("17:00–18:30 · Gig shift", "A fictional delivery block. The example attributes all 90 minutes to paid activity and shows gig earnings separately from the hourly shift.", "1h 30m paid"),
                VisualRow("18:30–22:00 · Personal time", "18:30–19:00 decompression, 19:00–19:15 interruption, then 19:15–22:00 personal activity. The interruption is counted only once.", "3h 15m personal"),
                VisualRow("22:00–24:00 · Sleep", "Second sleep interval. A future overnight session could be split at the day boundary for daily reporting.", "2h"),
            ),
        ),
        VisualSection(
            title = "Work",
            headline = "Shifts and actual time cost",
            detail = "The example separates paid work from breaks, travel and preparation. It uses no employer, platform or payroll data.",
            metrics = listOf(VisualMetric("Work-related window", "10h 45m"), VisualMetric("Paid activity", "8h 30m")),
            rows = listOf(
                VisualRow("Domino’s example shift", "7 paid hours and one 30-minute unpaid break within the fictional shift. Preparation and commuting appear separately. The sample has no rota or payroll connection.", "7h paid"),
                VisualRow("Gig example shift", "90 minutes of fictional delivery activity. A future record could distinguish active delivery, waiting and travel when a connected source supports them.", "1h 30m paid"),
                VisualRow("Work preparation", "45 minutes in the sample. This category can illustrate getting ready without counting it as paid work.", "45m unpaid"),
                VisualRow("Commute", "Two 30-minute sample journeys. Commute remains separate from a paid delivery journey.", "1h unpaid"),
                VisualRow("Breaks and waiting", "The fictional 30-minute break is unpaid. Future imports would keep paid and unpaid labels explicit and flag uncertain classifications.", "30m unpaid"),
            ),
        ),
        VisualSection(
            title = "Earnings",
            headline = "Two ways to view earnings per hour",
            detail = "All amounts are fictional gross examples before costs and tax. This screen does not estimate actual income or take-home pay.",
            metrics = listOf(VisualMetric("Per paid hour", "£12.53"), VisualMetric("Per work-related hour", "£9.91")),
            rows = listOf(
                VisualRow("Hourly shift example", "7 paid hours × £12.00 gives £84.00 fictional gross pay. The example rate is a visual fixture.", "£84.00"),
                VisualRow("Gig example earnings", "The fictional 90-minute block earns £22.50 gross before any vehicle or operating costs.", "£22.50"),
                VisualRow("Paid-hours view", "£106.50 divided by 8.5 paid hours gives £12.53 per paid hour after rounding to pence.", "£12.53 / hr"),
                VisualRow("Time-cost view", "£106.50 divided by 10.75 hours including the sample preparation, commute and unpaid break gives £9.91 per work-related hour.", "£9.91 / hr"),
            ),
        ),
        VisualSection(
            title = "Personal & rest",
            headline = "Rest, personal time and interruptions",
            detail = "This example separates chosen personal activity, unscheduled free time, sleep and an interruption. Nova Time has no access to messages, calls, sleep readings or personal records.",
            metrics = listOf(VisualMetric("Personal + free", "4h 15m"), VisualMetric("Interruptions", "15m")),
            rows = listOf(
                VisualRow("Sleep and recovery", "8 hours 45 minutes of synthetic sleep across two intervals. A future linked Nova Health source could add recovery context with separate consent. This view calculates no recovery score.", "8h 45m"),
                VisualRow("Personal activity", "The sample has a 30-minute decompression break and 2 hours 45 minutes of evening personal activity.", "3h 15m"),
                VisualRow("Unscheduled free time", "One hour is left unallocated in the example. Free time stays separate from a deliberately logged personal activity.", "1h"),
                VisualRow("Interruption example", "19:00–19:15 in the sample. No people, calls or messages are identified. A future owner-entered interruption could be assigned a category and duration.", "15m"),
            ),
        ),
        VisualSection(
            title = "Weekly",
            headline = "Weekly breakdown",
            detail = "The example week of 11–17 May has 168 hours, with paid minutes shown for each day. Wednesday is a fictional day off with zero paid minutes.",
            metrics = listOf(VisualMetric("Paid activity", "39h"), VisualMetric("Personal + free", "60h")),
            trend = VisualTrend("Paid activity by day", "min", listOf(
                VisualTrendPoint("Mon", 420), VisualTrendPoint("Tue", 390), VisualTrendPoint("Wed", 0),
                VisualTrendPoint("Thu", 510), VisualTrendPoint("Fri", 480), VisualTrendPoint("Sat", 420), VisualTrendPoint("Sun", 120),
            )),
            rows = listOf(
                VisualRow("How the week adds up", "57 hours sleep, 39 hours paid work, 10 hours unpaid work support, 60 hours personal and free, and 2 hours interruptions. The sample categories total 168 hours.", "168h"),
                VisualRow("Day-off example", "Wednesday has a known sample value of zero paid minutes. A missing source reading would be marked separately from a recorded zero.", "0m paid"),
            ),
        ),
        VisualSection(
            title = "Monthly",
            headline = "Monthly breakdown",
            detail = "Four complete fictional weeks from 27 April through 24 May illustrate comparison without a partial week. They cover 672 hours.",
            metrics = listOf(VisualMetric("Paid activity", "156h"), VisualMetric("Personal + free", "240h")),
            trend = VisualTrend("Paid hours by week", "hr", listOf(
                VisualTrendPoint("Week 1", 35), VisualTrendPoint("Week 2", 42), VisualTrendPoint("Week 3", 39), VisualTrendPoint("Week 4", 40),
            )),
            rows = listOf(
                VisualRow("Four-week allocation", "228 hours sleep, 156 hours paid work, 40 hours unpaid work support, 240 hours personal and free, and 8 hours interruptions. The sample categories total 672 hours.", "672h"),
                VisualRow("Week-to-week comparison", "Week 3 has 39 paid hours and Week 4 has 40. A future comparison could also show changes in travel time and work-related earnings.", "Sample trend"),
            ),
        ),
        VisualSection(
            title = "Trends",
            headline = "Compare time patterns",
            detail = "A second view of the same fictional four weeks shows time outside work by category. It does not infer habits or make recommendations.",
            trend = VisualTrend("Personal and free hours by week", "hr", listOf(
                VisualTrendPoint("Week 1", 58), VisualTrendPoint("Week 2", 62), VisualTrendPoint("Week 3", 60), VisualTrendPoint("Week 4", 60),
            )),
            rows = listOf(
                VisualRow("Date range and filters", "A future chart could compare daily, weekly and monthly intervals and filter by source or category. This chart uses a fixed example period.", "Demo controls"),
                VisualRow("Gross earnings and time cost", "A future comparison would keep paid-hour and work-related-hour rates separate. Weeks with missing earnings would show an unavailable rate.", "Planned"),
            ),
        ),
        VisualSection(
            title = "Sources & sync",
            headline = "Source connections",
            detail = "Preview the source and sync status layout. No account, app, calendar or health connection is active, and no sync has run.",
            rows = listOf(
                VisualRow("Shift Tracker", "A future opt-in import could read selected shift totals, breaks and commute categories after a source-specific review. No Domino’s payroll or rota credentials are requested.", "Not connected"),
                VisualRow("Gig shift records", "A future opt-in import could include shift times and recorded earnings. This preview has no access to gig platforms, orders or customer information.", "Not connected"),
                VisualRow("Nova Health", "A future optional connection could include sleep intervals and recovery context where allowed. Health data and wearables remain disconnected.", "Not connected"),
                VisualRow("Manual categories", "Future manual entries could cover work, commute, preparation, sleep, personal activity, free time and interruptions. No categories are saved in this demo.", "Planned"),
                VisualRow("Sync status", "Last successful sync: none. A future status card would show pending updates, duplicate detection and failed imports.", "Never synced"),
            ),
        ),
        VisualSection(
            title = "Reconcile",
            headline = "Review overlapping records",
            detail = "Inspect what the owner might see when two example sources describe the same time. No record is imported, resolved or overwritten.",
            rows = listOf(
                VisualRow("Duplicate shift example", "Two sample sources both describe the 17:00–18:30 gig shift. A future review could link the source references while counting elapsed time and earnings once.", "Same time range"),
                VisualRow("Overlapping category example", "A hypothetical personal activity covers 18:30–19:30 and overlaps the 19:00–19:15 interruption. The proposed resolution keeps 45 minutes as personal and 15 minutes as interruption.", "15m overlap"),
                VisualRow("Uncertain paid status", "If a source leaves the paid status of a break unclear, a future review would let the owner confirm it before including it in earnings-per-hour reports.", "Needs review"),
                VisualRow("Source updates", "A future import would identify its source and schema version, show the proposed changes, and keep manual corrections visible. This screen performs no sync or merge.", "Future review"),
            ),
        ),
        VisualSection(
            title = "Privacy",
            headline = "Consent and data controls",
            detail = "All views are bundled examples. A future implementation would ask separately for each source and data category, keep consent versioned, and explain the effects of disconnecting.",
            rows = listOf(
                VisualRow("Review source consent", "A proposed screen would show the source, selected categories, access scope and consent version before connection. No permission or approval is recorded here.", "Unavailable"),
                VisualRow("Export preview", "A future export would let the owner choose categories and dates and inspect the selection before writing a file. This demo creates no export.", "Unavailable"),
                VisualRow("Delete or disconnect", "A future confirmation would separate revoking access from deleting imported records. Nothing is disconnected or deleted in this demo.", "Unavailable"),
            ),
        ),
    ),
)
