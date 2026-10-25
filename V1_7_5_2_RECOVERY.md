# Private Gallery 1.7.5.2 recovery

Status: investigation / baseline reproduction fixtures, NO-GO. No production code changes, new version, signed APK, or physical acceptance claim.

## Authoritative baseline
- Repository traynor1987/Private-gallery-; branch release/1.7.5.1-privacy-viewer-repair.
- Remote/local SHA independently matched: 00509aa729d0aba4283fa0d000186153984cf87f.
- Tree: 43227bf4328a13ba7fbc605af324bdef79724b22.
- Code39/version1.7.5.1. Prior tested app source b0f41f0519ade0d00b96442df20996b5651ca130.
- Prior recorded acceptance APK SHA256 d19f65de02d3fac05def62b377829d6eb54221ff3e68bcb998a1451784e892b6; original certificate94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Read final-acceptance/ARTIFACTS.json and recovery/release documentation. Current private-key availability has NOT been probed.
- Target branch release/1.7.5.2-recents-browser-repair was absent at remote check. New isolated worktree created; no active Gradle/adb/emulator writer observed. Original checkout contains untracked older-main files, preserved untouched; clean linked worktree avoids contamination.
- Fetched history has no versionCode40 assignment; this is preliminary only and must be rechecked against all remote release evidence before reserving40.

## Owner scope and constraints
Exactly two repairs: new authenticated photo viewing after brief overview return, and general Primary WebView consent/modal compatibility. Preserve actual-background retained-preview concealment, secure Recents cards, Privacy Shield, Panic revocation/neutral entry, screenshot settings, encryption/PINs/media, Primary/Hidden isolation and all working video/camera/AI/browser features. No new feature release. No owner-device/media access, uninstall, data clear, key replacement, force push or main merge. Generic browser testing uses harmless local HTML only; no external adult service or upload testing, no automated consent acceptance in production. All release security and synthetic original-key39->40 gates required before APK delivery.

## Source findings (not yet runtime-confirmed root causes)
HiddenVaultDialog sets requireReopen=true on EVERY ON_STOP, including while viewerState is null. That value survives new HiddenViewerSelection creation. Thus a new user selection can inherit a concealment requirement intended for a previously displayed selection. Added grid-stop/new-photo RED candidate plus existing-photo-stop retained-concealment positive security test in isolated synthetic storage.

Native MainActivity coverSensitiveContent increments a retained concealment epoch on external focus/stop; FullscreenMediaViewer invalidates decoded content when epoch changes. This deliberately protects Recents, including launchers which leave Activity RESUMED. Public focus/lifecycle alone does not prove Recents versus another external window. No timing-based auto-uncover or native curtain weakening is proposed. Existing-photo Recents auto-return remains unproven/possibly indistinguishable; must document limitation if reliable evidence is unavailable.

Primary normal browser actually routes to BrowserV2ProductionDestination and SecureWebViewFactory. Legacy PrivateBrowser.kt is not the active destination. Active factory enables JS/DOM storage, uses engine provider identity, mobile viewport and native JS dialogs; no evidence yet identifies the blank modal cause. BrowserConsent1752Test exercises production session/factory with local HTTPS-origin delayed HTML dialogs, long text, transform/light/dark, rendered dimensions/hit testing, actual window screenshot pixels and Android pointer events, checkbox enforcement, accept/decline, local storage/cookies, pause/resume focus and same-origin reload. It does not yet cover synthetic file chooser completion, fold resizing, actual Samsung Recents gestures or complete network transport proof. No browser setting/network change made.

## Environment and next actions
Host has Java17 but no Android SDK/adb. ./gradlew --version fails downloading Gradle9.5.0 with Network is unreachable. Use one marker-gated focused CI workflow to compile/JVM/lint and run these baseline reproduction fixtures on API36/37. Inspect exact-source XML, provider/version, screenshots and logs before any production correction; never treat compilation or fixture failure as a demonstrated app defect. Preserve raw evidence. Do not start full/signing while focused is red. Resume using plan docs/superpowers/plans/2026-10-25-recents-browser-repair.md. Do not repeat runs for unchanged source.
