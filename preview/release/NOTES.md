# NovaOS Preview 0.6.1 — Widgets, Media & Home (EXPERIMENTAL)

Android17/API37 only, code7, package uk.co.traynor.novaos.preview. Same development certificate as0.5/0.6; update in place without uninstalling/clearing data. Production NovaOS is untouched.

## New Home experience

- Widget Centre: searchable catalogue and size previews for Clock & Date, Now Playing, Recent Apps, Nova Mind/Calm Now, local Quick Notes and Nova Search. No fabricated weather/calendar/tasks.
- Widgets occupy a separate four-column snapped grid above existing shortcuts, preserving app arrangement and Dock. Long-press/drag the top-left handle; drag the lower-right handle to resize. Accessible options provide movement, size and Home-page choices. Collision/bounds checks, automatic free-position placement, remove and Undo. Larger/large-text layouts scroll inside the bounded widget area.
- Same-size widget stacks: combine compatible positions, swipe, persist selected page/order, reorder/remove members, resize compatible groups and delete/Undo. Widget configuration survives normal restarts. Unknown types preserve position with an unavailable placeholder.
- Nova Search indexes only local application/shortcut/folder names, widgets, settings, available Store games, retained apps and Mind tools. Category filters, recent searches/clear and keyboard submit. Recent results resume their exact retained WebView. Web search is a separate explicit action. Notes, journal contents, cookies and production vaults are not indexed.
- Layout backup uses versioned plain JSON through Android's file picker. Includes pages, Dock, folders, safe installed-app identifiers, wallpaper settings, grid, widgets/stacks and non-sensitive theme/motion/demo-lock preferences. No URLs, credentials, cookies, journal/mood data, note text, search history, media files or vault keys. Custom shortcut identifiers resolve only to already-installed local entries; wallpaper image files are not bundled. Missing images fall back to Nova wallpaper. Validation, change preview, Confirm Restore/Cancel, rollback and durable interrupted-restore recovery are included.
- Optional internal demonstration lock with wallpaper, clock/date/brand and simple Enter/Disable actions. Underlying app pages, browser previews, notifications, profile identifiers and media titles are absent while displayed. It is not authentication or encryption and does not replace Android's lock screen.

## Media and video

- Native Nova Mind sounds now publish their own Android MediaSession and expose real playback state, position/duration, Play/Pause, Previous/Next and Return. No global media/notification-listener permission is requested. Media surfaces are available as widgets, Quick Panel and Nova event centre.
- Websites retain the existing bounded public Media Session title/state interface and Return to App. No new DOM scraping, stream extraction, spoofing, fabricated progress/artwork/transport controls or provider bypass. Unsupported website controls are disabled. Native source information does not override a genuinely playing web source with stale paused media.
- Nova Video opens explicitly selected local files with real Play/Pause/Seek, duration, internal movable/resizable mini player, expand, close and Android PiP where the device supports it. Playback view is retained through internal navigation. Android PiP is opt-in and native-local-video-only; failures are reported without discarding the source. Other Android background playback stays deferred.
- Standard website fullscreen requests use WebChromeClient's custom-view API, without recreating the WebView. Website mini/PiP eligibility cannot be established generically, so those controls remain unavailable with an explanation. YouTube playback/Premium checks are not bypassed.

## Verification and limits

Host policy, collision/stack/PiP, backup validation and rollback tests precede Android verification. Focused runtime checks include widgets/drag/resize/stacks, Search, backup/privacy, lock concealment, native media/video, retained browser/audio, Fold viewports and pinned0.6→0.6.1 upgrade. Runtime results are appended to this release; source compile is not device approval. Physical Galaxy Z Fold acceptance remains with the owner.

The previous emulator crash was SurfaceFlinger/mapper.ranchu ReadColorBufferDma, followed by system_server death. The workflow now replaces the runner's actually-used cmdline-tools/latest with22.0 and validates android-37.0 AVD metadata before boot. Test assertions remain strict.

Known limits: previews and Quick Notes are ordinary non-encrypted local storage; website metadata/controls depend on the provider; no web artwork, artist/channel or progress is inferred; no offline game guarantee; local video format/PiP support depends on Android; layout-only backups omit image/media bytes; native Android background audio remains deferred. Existing90 Store entries and production isolation are preserved.

Owner checklist: update over0.5; check old Home data; add/move/resize clock/media; create/swipe a stack; play Music and return Home; search Breathing/Browser/2048; export a layout, preview/cancel then restore; try demo lock; choose a local video, mini/expand/PiP/close; fold/unfold and check navigation remains clear.

Publication marker: preview-widgets-code7

Media continuity repair: foreground app selection no longer pauses retained WebViews merely because the website omits Media Session playback state. Configuration hand-offs preserve the original WebView/document; explicit Close destroys it. Native calm sounds/video use Android audio focus. No automatic play commands or provider-policy bypass. Physical YouTube Music acceptance still required; Android Home audio remains outside the supported scope.

Visual verification patch: widget surfaces explicitly use theme onSurface text/icons, correcting inherited white text on light cards. Compact landscape Home puts Apps in the header, removes redundant profile/footer rows, and keeps branding/profile labels to one line. Published code6 is retained; this is a new code7 APK.
