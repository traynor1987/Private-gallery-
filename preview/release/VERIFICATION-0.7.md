# Preview0.7 — polish verification

Version0.7-preview/code8. Package uk.co.traynor.novaos.preview. Android17/API37 only. Stable development certificate SHA25681cda5b72cb76940fa50972bc1c284b25e641b88814d2b9041c7111cb1d042d8. Source milestone c97692122becc17a8c6ebe7a34e049ca09780c9b; branch preview/home2-nova-store. Production not modified or merged.

## Scope and preservation

The accepted Nova Home wallpaper/gradient, palette, typography, icons, Dock, pages, Store, Browser V1, App Mode and Recents remain. New interactions: top Quick Panel, supported package-local media controls, explicit widget editing/live snapped feedback/atomic relocation/page-edge targeting, immediate notes autosave and full-screen advanced settings. No widget storage schema or owner configuration reset.

## Evidence

- 111 Java assertions,11 Python tests and90 catalogue/icon/package checks passed. The exact-title regression executed the actual public metadata read against synthetic130/513-character titles; the old prefix truncation failed before the fix. Complete bounded titles match exactly; over512 rejects rather than inventing a shortened identity.
- Local app and test APK assembly passed; stable package/certificate, API37 floor,16KB native alignment and production-class exclusion verified. No permissions added.
- Initial focused API37 run42023130870: upgrade passed and20/21 cases passed. Only native-media case failed; diagnostic42024962455 established that the emulator WebView has no navigator.mediaSession.
- Final narrow run42026621706 at3de66dd289713f003c1e8f6a064f97071ffb1709: native-control case and3 retained-audio cases passed (4/4), plus upgrade (1/1). The17 other unchanged UI/widget cases passed in42023130870. All21 distinct cases have passing evidence.
- Existing code7 receipts and the owner's physical acceptance are reused for unchanged features; no VPN/Tor or broad production suite run.

## Media boundaries

Android17 getActiveSessionsForPackage(context.packageName) only; never global notification/media-key access. Unique native controller and source ownership required: complete bounded metadata matching, or a sole retained web source after excluding known native players. Multiple web sources with unknown metadata fail closed. Mixed-profile retained tabs disable the bridge; ambiguity/unavailable actions fall back to Return. Native calm sounds/local video retain their real controls. No scraping, forced play commands, authentication or provider-restriction bypass.

Synthetic native-session callbacks test Play/Pause/Next capability handling, disabled unsupported Previous, genuine long native title, missing website metadata, ambiguity and cross-profile/source-close revocation. Real YouTube Music/native WebView session publication remains a physical acceptance item, distinct from the owner's already-confirmed internal background audio. Android Home background playback remains unverified/deferred.

## Owner checklist

1. Update in place without uninstall/clear data. Confirm wallpaper/pages/Dock/apps/notes remain.
2. Swipe down on empty Home (or use Quick icon). Expand; use theme/wallpaper/dimming/Sanctuary/Mind/Search/Recents/settings/events. Swipe up on the panel header to close.
3. Play YouTube Music, open panel, verify advertised controls or Return. Open other apps/Browser, rotate and fold/unfold; confirm continuity. Close the source to release it.
4. Long-press widget/handle, drag its handle and resize corner; dwell at horizontal area edge for another page and release. Verify snapped outline, safe collision relocation and Done. Options retain accessible page/size/move and Remove/Undo.
5. Type a Quick Note then immediately navigate/restart; check Saved status and keyboard Done. Check advanced settings/Back and cover/inner/landscape panel positioning.

## Limits

Blur uses the wallpaper backing, not a capture of sensitive pages. No Android connectivity/brightness/VPN controls. Short widget areas scroll; cross-page edge dwell and genuine hardware fold posture require owner acceptance. Preview notes/journal/layout storage is ordinary local data, not authenticated production encryption. Production integration remains separately authorised; see PRODUCTION-INTEGRATION-HANDOVER.md.

## Published delivery

APK source:3de66dd289713f003c1e8f6a064f97071ffb1709. SHA256:05297a501b6236832b7a2b0731446c086a944f61c22ad35047461dcdcdd9830f.

Direct APK:https://github.com/traynor1987/Private-gallery-/releases/download/novaos-home2-store-preview-0.7/NovaOS-Home2-Store-Preview.apk

Final runtime:https://github.com/traynor1987/Private-gallery-/actions/runs/42026621706 . Earlier UI/widget/Fold evidence:https://github.com/traynor1987/Private-gallery-/actions/runs/42023130870 .

The final native test uses a real own-package MediaSession and deliberately absent website metadata; Pause/Next callbacks actually execute, Previous is disabled, ambiguity/profile/source-close revocation are asserted. No fake navigator API, test-only hook in product code or weakened assertion. Pure sole-source ownership regression was observed RED→GREEN before implementation.

Cover/inner/landscape panel screenshots from synthetic owned decor were visually reviewed without disabling secure-window flags. Larger content scrolls on short landscapes. Actual Android17 YouTube native-session availability remains a physical-device check; existing internal audio continuity is preserved and reverified synthetically.

Independent public APK:60,910,177bytes; SHA256, package/API37, stable development certificate and16KB native alignment match publication. Permanent receipts are in receipts/0.7. The earlier UI log retains its one unresolved-at-that-time native fixture failure; the final native/media log records its passing replacement plus audio regressions, so the history is not hidden. No production security approval is claimed.
