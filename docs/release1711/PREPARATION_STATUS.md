# Nova Home 2.0 — inactive preparation checkpoint

Owner authority: 2026-11-04 complete recovery directive permits recoverable Home UI components while code55 remains blocked. Existing branch/package/API37/code55 are unchanged. No1.7.11 release is assigned or activated.

## Implemented

- Immutable `HomeDesktop` model: up to8 logical pages; ordered shortcut placement; create/rename/collapse folders; separate configurable dock; remove/hide/show apps; per-Space ownership; grid/icon/reduced-motion preferences; separate built-in wallpaper/dim/blur settings; bounded versioned codec. Codec bytes are plaintext in memory and MUST be encrypted before any future persistence. No plaintext disk writer exists.
- Inactive `HomeDesktopScreen` Compose component: original procedural fullscreen Nova wallpapers; live local clock/date; horizontal Home pager and accessible page controls; dock; swipe-up drawer with application search; folder opening/creation/rename; long-press and explicit three-dot actions; Home editing sheet; built-in wallpaper selection, blur/dimming, icon/grid controls; hidden app restoration; default-Home confirmation; swipe-down internal Quick Panel with caller-supplied real actions; Classic Home callback.
- Responsive column count follows available width/font scale without mutating logical arrangements. Narrow indicator overflow corrected with scrolling; icon size bounded by measured cell width. All direct page selection uses bounded model `pageIndex` after deletion.
- Launch surfaces filter through existing NovaNavigation availability; disabled Tor cannot be enabled by saved data. Authenticated launch logic is not replaced. MainActivity does not reference this screen, so existing Classic remains active.

## Verification and review

`bash scripts/check_home_desktop.sh`:472 behavioral assertions pass. Model absence produced the initial failing compilation; stale-index regression failed before its bounded API was implemented. Includes malformed/truncated/trailing encodings, strict booleans/counts, duplicate/hidden placements, mutation immutability, owner mismatch, disabled Tor in crafted saved dock/folder states and400 deterministic encoding mutations.

Independent read-only review by home_security_review: no Critical/Important model issue. Presentation review found stale-page indexing and fixed-width eight-indicator overflow, both source-corrected; drawer/action-sheet overlap also corrected. This is source review, not runtime/Fold approval.

App Kotlin+Java, unit-test Kotlin and Android-test Kotlin compilation passed locally on API37.1 with Gradle9.5.0, JDK17, pinned existing project dependencies and normal trusted TLS/proxy configuration. Final compile receipt is recorded separately. This is not APK assembly, instrumentation execution, native ABI rebuild or signing. Existing deprecation/nullability warnings remain; no errors. No workflow was created or dispatched.

Supporting checks pass: existing Hidden foundation373 checks and authenticated stream/tamper cases; continuation/authentication/revocation and launch-ownership host checks;21 focused release/packet/approval-control Python tests; NovaOS code55/API37/icon/package check; unchanged native gate/transport/bridge host race suites. None approve C1.

## Explicitly unfinished

- Code55: parent-private readiness stack, persistent-TUN service/backend, child lifetime keeper and shipping runtime/broker/heartbeat integration; raw Android DNS/UDP containment and lifecycle verification; exact-source security approval; original-key54->55 upgrade/restart; production signing/delivery.
- Home: authenticated encrypted persistence and state disposal on revocation, encrypted Gallery wallpapers/crop/reposition/lock backgrounds, real authorised sensitive widgets and their immediate clearing, full theme/accent integration, drag/drop and complete reorder controls, drawer folders, full Fold/large-text/multiwindow/gesture/Back/runtime/visual verification.
- App-wide consistency work and original-key1.7.11 upgrade/release remain future work. Clock is the only live widget-like content; no storage/evidence/download data is invented.

No55 or1.7.11 APK, checksum, runtime PASS or physical owner acceptance is claimed.

## Safe continuation

Fetch this existing release branch's current remote HEAD. Read V1_7_10_RECOVERY.md, CONTINUOUS_VPN_PROGRESS.md and SESSION_PUBLICATION_LOCKING.md. Do not restart reviewed native cores or restore rejected kernel-probe helpers. Complete native readiness/service/runtime integration before one coherent focused API37 packet run; preserve all full-capture no-fallback assertions. Retain Tor deferral. Home components may be completed independently but stay inactive until auth/persistence/revocation review and the secure baseline pass. Do not copy stale local-only commit metadata over the remote branch.
