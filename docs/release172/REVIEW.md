# 1.7.2 focused security and release review

## Final reviewed acceptance state — 24 October2026
Acceptance run40046703818/job120729980481 SUCCESS at sourcee0cc1f0d8ddb8482910f01a9de5abf1e9472332b. All336JVM/44focused/73preflight/329full Android cases pass for identical app tree e33ae81c3ebde2736583c18ef39ff69d69470927 at sourcec9f7e0712614d7af1d301d74ad6abe9d1a912225. Fresh launcher, corrected debug upgrade/restart, original-key production-package upgrade/restart and certificate/package checks pass. Downloaded APK independently verifies v2 signature with original94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17 certificate; SHA256f2f0760fb6f942002da0ce763e12b0b6a5a39fa6004a1fb0afa303e1a0a107f1. Owner artwork pixel-exact in all five foreground densities. Reading cover/landscape screenshots inspected; exit/record controls visible and article scrollable.

No Critical/Important issue remains identified. GO for owner acceptance, production GO pending physical testing. Generic evidence-gate suite-counter/duplicate-identity hardening remains a deferred minor; actual pinned artifact was independently checked clean. Signing/upgrade runtime availability previously set aside by source review is now established by CI and downloaded evidence. Physical Fold/live owner endpoints remain unclaimed. No main merge, V2/face work or owner-data operations.

Earlier review notes below preserve their historical pending statuses and are superseded by this verified state.

## Independent review
Fresh reviewer examined the branch against397c3b480c60f5ea494020804dc0117705099d62 plus in-progress changes, including approved design and plan. No new cross-vault destination, thumbnail cache, encrypted-import ordering or browser transport defect was found. This is source review, not proof of physical camera or model output quality.

Important findings and corrections:
1. Default-On guidance can exceed4,000-character effective prompt limit: reject with actionable UI message before AiParameters, never truncate owner text; retry-Off boundary test added.
2. Idle microphone permission flow after normal capture entry could trigger strict active-camera onPause locking: pauseForSettings only when idle, unbind/invalidate pending binds, reject inputs and cancel authority monitor; re-entry starts one monitor. Active camera pause, screen-off and authority-expiry still revoke/lock. Added idle/active pause Activity test.
3. Fold fixtures initially rendered obsolete split viewfinder: shared CameraViewfinderLayout now drives production and synthetic screenshots.

Minor findings addressed: collapsed filtered search gets visible primary tint and accessible active-filter label; launcher resources restored byte-for-byte (no branding delta).

CI668 found a Compose DSL implicit receiver compile error in the overlay; capture orientation before nested scope. Fresh CI669 tests that correction. Later idle-monitor correction and screen-off fixture also require final fresh-source CI before acceptance.

## Boundaries inspected
- Origin immutable VaultCaptureAccess, independent Hidden authority and Primary session-key identity remain intact.
- Capture command type matches actual bound use case; camera controls check authority and journal controls duplication.
- Lock revokes synchronously; callbacks seal using existing capture journal and recovery behavior. Encrypted destination verification and durable import precede journal cleanup.
- Reading surface has no network, publisher impersonation, Preview use case, background service or public media output. Android permissions/privacy indicators remain.
- Editor sources use existing authenticated callbacks, bounded decodes and memory-only buffers with cancellation/wiping.
- Filename query is memory-only and vault-scoped; semantic model/index is explicitly deferred, no cloud inference or fake visual results.
- No browser transport, encryption algorithm, migration, signing key or launcher artwork change.

## Remaining release gates
Fresh JVM/build/lint, focused UI/Fold screenshots, full Android/security/browser/process regression, original production signing and code34→35 both-vault synthetic upgrade/restart. Hardware Fold camera permissions, recording/audio/VolumeDown/power lock, actual media/AI/Tor/VPN owner acceptance remains physical-only. Production GO is pending owner acceptance even after automated gates pass.

## Completed app tests / upgrade-harness continuation — 24 October2026
CI671 sourcec9f7e0712614d7af1d301d74ad6abe9d1a912225 passes336JVM,44focusedUI,73preflight,329fullAndroid and independent browser/camera process restarts, no XML errors/failures/skips. Reading swipe capture corrected and all four Fold Reading cases pass. CI overall failed at subsequent synthetic-upgrade seed because the fixture rejected approved34→35.

Fresh independent review approves exact fixture-pair addition plus pinned-evidence acceptance workflow: archive/source/digest/app-tree checks, root-path allowlist, fixture exact-delta check, all XML test passes and fresh mandatory debug/production original-key upgrades. No Critical/Important finding. Deferred minor generic gate hardening: suite-level counters/duplicate testcase identities; pinned actual archive counters independently checked clean. No signing or physical acceptance success implied until final gates complete.
