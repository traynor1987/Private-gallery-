# NovaOS Preview0.7 — Premium interaction polish (EXPERIMENTAL)

Android17/API37 only, code8, package uk.co.traynor.novaos.preview. Existing development signing identity. Install in place over previous preview; do not uninstall/clear data. Production remains untouched.

The accepted Nova Home wallpaper/gradient, palette, typography, icons, Dock, pages, Store and browser engine are retained.

- Quick Panel now opens from the top through the existing icon or a downward swipe on empty, non-scrollable Home space. Widgets, app tiles, websites and scrollable content own their gestures. Header swipe-up dismisses; downward header swipe or Expand reveals all controls. Rounded translucent theme surfaces and blurred wallpaper backing (disabled with reduced motion), inset-safe cover/inner/landscape layouts.
- Genuine Nova controls: theme cycle, wallpaper, Sanctuary, Mind, Search, Recents, Settings, events, widgets, Calm Now and a preview-only dim overlay. No Android Wi-Fi/data/VPN/brightness claims or new permissions.
- Android17 own-package MediaSession discovery enables only advertised Play/Pause/Previous/Next controls for a single unambiguously matched retained website source (or sole retained web source after excluding known native players). Genuine title/artist/artwork only when provided. No global media keys, notification access, scraped DOM, playback injection or provider-policy bypass. Unknown/missing/ambiguous sessions retain Return to Player; native calm sounds/video keep real controls. Controls are disabled if another preview profile has retained tabs, avoiding cross-profile source attribution. Real YouTube capability depends on its WebView/platform session.
- Widget editing: long-press widget or its top-left handle, drag the handle with live snapped valid/invalid outline, resize using the editing corner, dwell at the horizontal area edge to select another existing page and release to drop. Compatible colliding widgets relocate atomically to free cells; impossible placements preserve the old layout. Done exits editing. Accessible options retain movement/page/size controls and Remove/Undo.
- Quick Notes autosave synchronously to preview-local storage as typed, show Saved/Retry status, preserve text across restart/upgrade, and support keyboard Done. Notes remain unencrypted preview data and excluded from layout exports. Widget content aligns at the top; supported artwork scales with card size.
- Advanced Home and navigation settings use full-screen pages with existing controls, categories, predictable Back and the existing Nova navigation bar.

Existing WebView ownership/lifecycle and internal audio continuity remain intact. No production Browser/VPN/Tor, vault keys or encrypted storage modified. Existing Store/Games/Mind features remain.

Focused verification: host111 Java assertions,11 Python cases, catalogue/package checks, app/test assembly,21 API37 widget/media/polish cases plus pinned code7→8 upgrade. Actual runtime outcome is appended by publication; compilation alone is not physical acceptance. Previous passing unrelated regression evidence is reused.

Known limits: provider metadata/actions may be absent; ambiguous native source attribution falls back safely. Android Home background audio and genuine Fold hardware posture remain unverified. Small/landscape widget areas scroll; page-edge dragging should receive physical owner acceptance. Blur covers the wallpaper backing only. Preview notes/journal/layouts are not production-encrypted. Layout backup omits private text/credentials/image bytes.

Owner checklist: update in place and confirm existing pages/Dock/apps/notes; empty-Home swipe down, expand panel, swipe header up; play YouTube Music and test supported controls/Return; long-press/move/resize widget, dwell at page edge and drop; type note then immediately navigate/restart; use full-screen settings/Back; rotate/fold while playing.

Publication marker: preview-polish-code8

The API37 emulator WebView exposes no navigator.mediaSession. Native discovery therefore also supports a sole retained web source/sole eligible own-package session after excluding the known native Mind session. Multiple web sources with missing metadata fail closed to Return. No website API polyfill or playback workaround is installed.
