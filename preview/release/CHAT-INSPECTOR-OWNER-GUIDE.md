# Local Nova Chat compatibility inspection

Install the Preview update over the existing Preview. Do not uninstall, clear data, log out WhatsApp or link a new device for this task.

1. Open your existing authenticated WhatsApp Web app in NovaOS.
2. Open its menu → App settings & compatibility → Nova Chat Compatibility Inspector. Browser V1 offers the same entry in Settings when the current origin is exactly HTTPS web.whatsapp.com.
3. Tap Start Inspection. Until this tap, no inspector script runs. Three bounded read-only structural samples run locally, roughly one second apart.
4. Stop Inspection cancels further scheduling and discards in-flight results. Leaving/backgrounding the inspector stops it; there is no automatic restart. A previous completed report may remain available and is labelled as such.
5. Tap View Report. Review the entire sanitised JSON report. Copy is disabled until the report is previewed and while an inspection is running.
6. Tap Copy Sanitised Report only if satisfied. Voluntarily paste only that reviewed report into the development conversation. No personal conversation screenshot, password, verification code, linked-device token or account access is needed.
7. Delete Report removes its private local saved copy. Copies you chose to put in the clipboard or share are not remotely deleted.

## What the report means

Container/row/unread detections refer to fixed structural selectors, not message or contact contents. Counts are bucketed. Across-sample consistency means only bounded tag categories, child-count buckets and grid-cell presence were unchanged; it does not establish complete DOM equivalence. Virtualisation is a geometry hint, not proof. Open shadow roots are checked in a bounded sample; closed roots remain unknown. Document navigation is observed separately from unknown single-page-app navigation. The inspector does not scroll or open chats, so scrolling consistency remains unknown.

Stable non-personal conversation identity and filtering attachment remain unverified. A recognised row does not prove that approved conversations can be identified or excluded chats safely hidden. The report guides investigation; this APK does not implement reliable WhatsApp filtering.

## Friends launcher prototype

Nova Chat Lite in the app drawer holds explicit manual approvals locally. It never imports contact names/messages or matches ambiguous display names. It is an unsupported launcher prototype: selecting a friend opens compatibility tools, not an unverified chat. Groups are unsupported. Optional Sanctuary entry uses the same native guard.

Entering Friends Mode conceals the retained WhatsApp source before presentation and keeps it concealed across Home/reload/recreation. Normal WhatsApp requires explicit confirmation of unfiltered browsing; its existing WebView and session are reused. Unrelated Browser V1 tabs are not concealed. Native web media attribution may be suppressed while a Friends guard is active to avoid leaking unmatched private session metadata; playback itself is not paused. Opening Inspector directly from ordinary WhatsApp settings does not itself enable Friends Mode.

This is a visual wellbeing feature, not encryption or an access-control boundary. No absolute concealment guarantee, no Android WhatsApp notification filtering and no production security claim. Preview manual approvals are not production encrypted.

No raw IDs, attributes, HTML, names, phone numbers, message bodies, pictures, cookies, storage or tokens are read by the inspector. No networking, screenshot capture, account actions, sending, read acknowledgement automation, remote JavaScript, arbitrary selectors or general-purpose execution bridge are added. Reports remain on-device until you explicitly copy and choose to share them. Future adapters require a signed Preview APK and separate verification.
