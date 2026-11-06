# NovaOS owner acceptance fixes implementation plan

> For agentic workers: execute the authorized fixes in this session; independent domains use disjoint files and root integrates/reviews the result.

Goal: deliver an original-key in-place patch restoring immersive web App Mode, native theme persistence and media-action isolation while preserving the accepted encrypted application.

Architecture: keep the existing V1 WebView factory, profile/data directory, crypto, app catalogue and retained view registry. Change app presentation and identity-aware history emission, bridge the existing Home/native theme preferences through captured Space authority, and restrict media presentation/recovery to media destinations. Existing generic origin policy remains strict; app presentation gets explicit validated YouTube aliases.

Tech stack: Kotlin/Compose, Java policy checks, Gradle/JVM, focused API37 instrumentation and existing gated release workflow.

Spec: owner acceptance directive in this conversation, 2026-11-06: immersive full-height app content and top-right Back/Forward/Refresh/Home/Open in Browser/App Settings/Exit; standalone V1 separation; persisted native dark/light/system theme; legitimate mobile YouTube scope; no media editor action in web apps; encrypted/native/Home/Store data preservation; verified original-key upgrade.

## Global constraints

- Android17/API37; package uk.co.traynor.privategallery; original certificate 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.
- Installed signed baseline NovaOS1.7.11/code56 source0e7f7c07608a6b439a4471e613531a758028c849. Next patch1.7.11.1/code57, subject to remote history check.
- Continue integration/novaos-1.7-home2 from f1835912; preserve source branches, prior accessible snapshot and encrypted app data. No reset/force-push/uninstall/clear-data.
- No Home redesign, web CSS darkening, browser engine/profile replacement, VPN/Tor work or unofficial messaging.
- Use one coherent API37 verification batch and existing workflows. Signing requires exact-source runtime evidence/review and original56-to57 upgrade/restart gates.

## Review focus

- App menu retained across Home/background/authority changes must dismiss and cannot execute stale actions.
- Existing app preferences must not permanently restore browser chrome in immersive App Mode.
- Scope aliases must not accept credentials, suffix lookalikes, Music/other origins, nondefault ports or scope escapes.
- Hidden theme/preferences and editor tickets must not leak into Primary or web destinations; media recovery files remain recoverable.
- Standalone history and app navigation remain distinct; preserve existing unclassified history and cookie/login data.

## Tasks

1. App Mode: PrivateAppControls.kt, BrowserV2Home.kt, BrowserV2Session.kt and focused tests. Overlay overflow over unchanged full-size retained WebView; explicit ordinary-tab Open in Browser; app-origin events excluded from future standalone history/snapshots. Keep normal V1 tab operations and valid error surfaces.
2. Native theme/editor isolation: MainActivity.kt, IntegratedHome.kt, PreviewData.kt and theme/media helpers/tests. Reconcile legacy explicit Primary preference once, then share owner-encrypted theme authority across Home/native UI. Hidden stays owner-scoped. Gate global viewer/editor/recovery by destination, Home coverage and current owner; preserve valid media recovery.
3. YouTube scope: PrivateAppPolicy.inAppScope(url,startUrl,savedScope), preserving sameOrigin/inScope; explicit HTTPS/default443 apex/www/mobile YouTube aliases with path validation. Caller uses app identity. Positive and negative host checks first.
4. Root integration: inspect diffs, compile app/instrumentation, run full JVM and host checks; focused API37 tests cover real full-height UI/menu, theme reopen/native surfaces, editor-route negatives, scope, history/session retention and existing auth/Panic/camera/encrypted storage regressions. Commit/push verified checkpoint, retain failures honestly.
5. Release: adapt existing controls to pinned signed56 baseline and candidate57; preserve fixture assertions and seed/check Home2 settings where feasible. Independent review of source/control/evidence before original-key signing. Verify candidate identity/cert/hash, fresh enforcing API37 install56 -> install-r57 -> verify/restart. Publish signed owner-acceptance APK only after gates pass; final report includes exact fixes/tests/source/hash/link and physical limitations.

Each implementation domain documents root cause, observes the regression with a focused test, implements the minimal correction, verifies it and reports readiness. Root coordinates Gradle builds and commits/pushes coherent verified work.

## Additional owner release blocker

Remove Primary Home Switch Space discoverability and reject ordinary navigation, intents or restored state as concealed-entry authorisation. Preserve existing secret version-number sequence, separate Hidden PIN, approved concealed lock-screen authentication and authenticated Hidden exits/Panic Exit. Include real MainActivity/navigation and restoration regressions in the exact Android17 matrix before release.
