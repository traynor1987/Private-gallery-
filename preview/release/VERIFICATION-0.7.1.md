# Preview 0.7.1 correction verification

Version 0.7.1-preview/code9. Package uk.co.traynor.novaos.preview. Android17/API37 only. Development certificate SHA256 81cda5b72cb76940fa50972bc1c284b25e641b88814d2b9041c7111cb1d042d8. APK source/tag: 1b1f24c5d76c4791d2bf07280c2d60b6cc1afc7b. Existing branch preview/home2-nova-store. No production merge, production source changes or security approval.

## Corrections

- Browser V1/App Mode share a per-profile, per-host Desktop Site preference. Explicit reload/mobile return, preserved WebView/cookie/session storage, scoped viewport/UA and existing provider restrictions. Original requests are not replayed with loadUrl during redirect handling.
- Root cause of disappearing clock: the old page0 rendering condition required zero widgets. The original clock/date now have an independent default-on preference and protected area; explicit owner disable remains possible. Existing styling/wallpaper/icons/Dock retained.
- Raw translated drag visuals could escape the widget region onto app icons. Previews are now snapped/clipped; widgets and shortcuts occupy separate layout regions. Strict collisions reject invalid move/resize without silently shifting other widgets. Cancel and geometry-only Undo retain current notes/configuration.
- Invalid parseable layouts recover conservatively: keep valid placements first, relocate only invalid records, retain original raw layout privately, preserve notes/stacks/IDs. Unsafe/unrepairable input is preserved with an error rather than reset.
- A second confirmed defect kept editing enabled: Done scrolled outside the viewport after dragging, so the tap missed it and stacks remained locked. A single pinned edit toolbar now remains outside the Home scroller on every page. Final diagnostics show child stack 1→2, persisted active1, outer Home remains1.
- Quick Panel uses distinct icon tiles,12dp row/column gaps, adaptive2/3 columns, accessible minimum touch sizes, translucent surfaces and animated dismissal. Existing routes/theme/dimming behaviour retained.

## Media capabilities and boundaries

Android17 MediaSessionManager.getActiveSessionsForPackage inspects this package only, with no global notification/media permission. Actual advertised native actions are dispatched through MediaController transport controls, with token/profile/ambiguity revalidation. Native Pause and Next callbacks passed; unsupported Previous remained disabled. Genuine duration/position are displayed only if provided. Native artwork is used only when supplied. Exact website metadata matches and package-only sessions are distinguished; the latter is labelled NovaOS native media and its app link is a shortcut, not a provider-ownership claim.

The API37 emulator WebView does not expose navigator.mediaSession (established in previous0.7 diagnosis). Real YouTube Music native metadata/transport publication is still unverified on the Fold. Where interfaces are unavailable, the source-app Return card remains. No playback polyfill, private DOM scraping, injected controls or provider restriction bypass. Retained audio/page continuity within NovaOS passed synthetic fixtures. Android Home background playback remains deferred/unverified.

## WhatsApp findings

The unauthenticated API37 public probe requested desktop UA without Mobile, loaded https://web.whatsapp.com/ with login cues, no mobile fallback and no page error. A QR-like element was not detected. Phone-number linking, QR completion, authenticated chats and authenticated session persistence were not tested; no personal account or messages were collected. A synthetic HTTPS page verified per-site preference persistence and sessionStorage retention through desktop/mobile reload; unrelated YouTube Music UA stayed unchanged. Browser V1/App Mode desktop settings and existing Google OAuth policy regression passed.

No Friends Mode allowlist or DOM compatibility adapter exists in this repository. The preview therefore offers an explicit unsupported compatibility guard, not approved-friend filtering: when requested it conceals web content/accessibility, rejects fullscreen and removes media attribution, persists through recreation and requires explicit normal-unfiltered return. Synthetic approved/unapproved HTML remained unchanged behind the guard. Normal WhatsApp browsing is unfiltered. Do not treat the guard as authenticated Friends Mode support.

## Verification evidence

- Host:118 Java policy assertions,11 Python tests,90 catalogue/icon/package checks passed. Local app/test APK assembly passed. Production source path diff is empty.
- Initial42035432194 atbcf7961182debb1b52c80dc2646858062073c3e7:30/31 focused cases plus upgrade passed. Stack swipe timed out; publication blocked.
- Diagnostic42037831595 atb7de223a222fc9a9ecadb483ba6c73af590a10d1:1/2 passed; stronger assertion proved editingId remained set after the offscreen Done tap. Publication intentionally disabled. No assertions weakened.
- Final42039524262 at1b1f24c5d76c4791d2bf07280c2d60b6cc1afc7b:13/13 directly affected cases passed, plus code8→9 in-place upgrade1/1. Existing30 passing cases are reused; all31 distinct cases now have passing evidence across runs.
- Upgrade fixture preserved wallpaper/preferences, Home pages/folders/Dock, installed app metadata, widget stack order/active item, Quick Notes and synthetic Mind journal. Same package/certificate install-r succeeded; private file sentinel survived.
- Emulated Fold cover904×2316, inner1840×2208 and landscape2316×904 atdensity420/font1.3 passed clock visibility/non-overlap and widget/nav bounds. Panel cover/inner/landscape captures reviewed in dark/light; stack after-swipe image and native session callbacks reviewed. Actual hinge posture/touch/provider acceptance requires physical testing. Short landscape content scrolls within the available Home area.

## Delivery

Raw prerelease APK: https://github.com/traynor1987/Private-gallery-/releases/download/novaos-home2-store-preview-0.7.1/NovaOS-Home2-Store-Preview.apk

SHA256:1ebbe52cafb20c5509e743e707a9866d8220e1aaef5bc10c8cd92bf0671da05d. Bytes:61,008,477. Independently downloaded public asset matches CI hash, package, API37 floor/target, stable development certificate and16KB ZIP/ARM64 ELF alignment. Remote prerelease tag independently matches APK source1b1f24c5d76c4791d2bf07280c2d60b6cc1afc7b. Permanent receipts in receipts/0.7.1 include both failed diagnoses and final passing logs.

Owner checklist: install over existing preview without uninstall/clear; toggle desktop in WhatsApp and return mobile; personally verify linking/chat persistence if desired (Friends guard is unsupported filtering); add/move/resize/cancel/undo widgets while clock remains visible; swipe stack after Done; play YouTube Music and inspect Media interface details while switching Home/apps/Browser, rotating and folding. Verify no Android-level background playback claim.
