# NovaOS 1.7.10.2 / code54 — unsigned recovery checkpoint

Application source: `2b67f751a91961c138b4290c165415931e7c8b89`.
Branch: `release/1.7.10-private-home`. Package: `uk.co.traynor.privategallery`. API37 only.
Latest delivered original-key candidate remains 1.7.10.1/code53. Code54 was unused when assigned; no replacement signing key was created.

## Implemented

- Retained approved N branding, Nova Home root, adaptive launcher, Primary/Hidden authority boundaries and removed legacy bottom navigation. Hidden-only Tor launcher entry has fixed Tor admission; Hidden Browser has fixed VPN admission.
- Shared themed browser connection/status shell, address/tab/bookmark/history presentation, retry/settings and Home/Lock/Panic controls. Existing actual interactive VPN and Tor transport engines preserved. Modes retain separate profiles/catalogues. Tor JavaScript/history defaults remain off; New Identity retires the complete session and requires a fresh launch, without promising a changed exit IP or full Tor Browser anonymity.
- Evidence dashboard: counts, import, recent incidents, search, Library/Timeline/AI shortcuts and Information page. Existing full-page original-selection/OCR/correction/grouping/AI payload consent/export workflows preserved with original encrypted engine and provenance.
- Camera viewfinder layout with top controls, bottom/adaptive side capture dock, zoom, mode, shutter, switch and recent encrypted capture. Added real CameraX tap focus with exact binding/owner/request guards and truthful transient feedback. Existing encrypted photo/video, concealed capture, microphone, volume capture and recording finalisation preserved.
- Editor stable large preview, grouped crop/adjust/AI controls, rotation-aware aspect presets, bounded exposure/temperature rendering and Save Copy. AI provider/model disclosure collapses; source replacement and existing exact transmission consent remain. Existing secure external-edit discovery/export leases retained.
- Modal focus/IME corrections and full-page sensitive Evidence window handling; selected light/dark theme inherited. Eligible screenshot policy remains authenticated, foreground and focused; sensitive pages/Recents/background stay protected.

## Verification scope

- Source e3c8a97: app/test compilation and host/JVM passed; initial253 UI cases234 passed19 failed. Failures were investigated, not counted as release success.
- Source4f4dd4f1, run41362013309:109 UI cases107 passed2 failed,0 errors/skips. All3 live VPN/Tor/network-loss cases passed original XML and actual packet/DNS/body verifiers. Downloaded artifact13105328926 archive SHA256 `c83d50172d840c6139ffa63e9c38b03e954e29cd670bc25347616e1707af7025`.
- Source07c14135, run41363619033: host checks and app/test compilation/focused JVM passed. Final83-case API37 batch:79 passed4 failed,0 errors/skips. Original artifact13106692387 independently downloaded; archive SHA256 `9d599e3be0d4f03c564b88bb23771bcf3114ed262f156be2f5fd2b54feebb606`. Focused JVM7/7 passed (5 VaultDocumentPolicy,2 EditorToneModel). Actual tone pixel/Save Copy/rotation and camera focus/hardware tests passed; prior grouping and modal consent failures pass. Remaining exposure-slider UI, AI source replacement, concealed-camera and Evidence lazy search failures were corrected and passed the closure batch below. It covers final Evidence/AI modal fixes, editor/camera and focused regression tests; browser production source is unchanged from4f4 routing evidence.
- The current full266-case matrix has NOT been claimed green on one source. No physical Samsung Fold acceptance is claimed.

- Final source `2b67f751a91961c138b4290c165415931e7c8b89`, run [41365205828](https://github.com/traynor1987/Private-gallery-/actions/runs/41365205828): **25/25 API37 closure cases passed**,0 failures/errors/skips; focused JVM7/7 and host/app/test assembly passed. All four prior failures now pass, plus new concealed-gesture regression. Original artifact13107287404 downloaded and exact XML case accounting verified locally. Archive SHA256 `1794260bd5bbf24eb1fc5b8c5d9a06c616c8ce427086a7eb6d4f6e0ca8227056`.
- Closure fixes: immutable composition ownership for editor disposal; exposure swipe starts away from Android Back edge and asserts actual slider/history before Save Copy; black-only gesture ownership with original CameraX capture assertion; lazy Evidence search materialization plus authoritative encrypted search assertion. Original timeouts and security checks retained.

## Security and release decision

Independent review: `SECURITY_REVIEW_CONDITIONAL.md`. No additional unresolved Critical/Important implementation finding in reviewed changes; full signing approval withheld.

**Blocking requirement:** Android WebView provider cookies, DOM/local storage and session profile files use ordinary isolated transient app-private directories. They are not encrypted with the originating Hidden vault key. Bookmarks/history/catalogues are vault-encrypted. Device encryption, isolated profiles, cleanup and encrypted snapshots do not encrypt live provider-managed files. A strict implementation requires a separately reviewed architecture and active-session/crash/lock/cleanup proof; do not waive this requirement implicitly or claim it met.

No54 production activation, signed APK, APK checksum or code53-to54 upgrade result exists. Original package/certificate/data are preserved. Signing can proceed only after the storage requirement is explicitly resolved or correctly implemented and all applicable gates pass.

## Fold owner acceptance after a valid signed candidate exists

Install over the existing original-key app without uninstall/data clear. Check cover/inner display, portrait/landscape, fold/unfold, large text and keyboard; Home/Back/Lock/Panic; independent Hidden PIN/session expiry; eligible screenshots versus sensitive pages/Recents; VPN loss fail-closed and Tor separation; Evidence selection/OCR/group save; encrypted camera photo/video finalisation; crop/adjust/AI consent/Save Copy; existing media, incidents and Private Apps after restart.
