# Private Gallery 1.7.5.1 supported Android versions

The owner explicitly authorised Android16(API36) as the new minimum on24October2026. Android17(API37) is supported; the owner's Galaxy Z Fold runs Android17. Android15/API35 and older are unsupported. Installer enforcement is minSdk36 for debug and original-key production candidates. Version1.7.5.1/code39, package uk.co.traynor.privategallery, original certificate and encrypted data formats remain unchanged.

## Mandatory automated gates

- One focused workflow: all existing47 repair/viewer/Panic/authentication cases on Android16 Google ATD(API36), then the same cases on the available Android17 Google ATD system image android-37.0. Both actual outcomes must pass; failed/skipped phases cannot satisfy the final gate. Metadata source: https://dl.google.com/android/repository/sys-img/google_atd/sys-img2-3.xml; entry system-images;android-37.0;google_atd;x86_64 was present on24October2026. The emulator action accepts the37.0 platform/image identifier as a string.
- One stable-candidate full release workflow onAPI36: JVM/lint, all existing security/browser/camera/media/UI regression, fresh focused evidence, synthetic code38→39 debug upgrade/restart, original-key production signature/package/minimum/hash checks and synthetic production upgrade/restart. Baseline38 remains unmodified with its older minimum; all upgrade tests run on a supported device.
- The shipped candidate's minimum must be exactly36 in APK badging. Original certificate SHA256 remains94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. No new key, uninstall or data clear on an owner device.

## Security and evidence boundaries

No production privacy/authentication implementation is changed by this support decision. Foreground screenshot enabled/disabled, vault isolation, native curtain, covered-preview reopening, session expiry, Panic/QuickLock/video/camera, and authentication tests remain enabled. applyScreenPrivacy retains setRecentsScreenshotEnabled(false) independently of the foreground screenshot preference on every supported platform, with a release-policy guard against enabling Overview capture. Android's supported Overview API policy and native background protection are tested/reviewed; this does not claim measured Samsung Recents pixels or physical acceptance.

Android12 runs12–15, their unresolved failures, raw artifact identities/hashes and the historical Pre33Recents1751Test remain preserved. That class remains explicitly restricted to maxSdk32 and is not required or counted as passed on supported platforms. Removing its unsupported-platform matrix entry is an owner-approved compatibility change, not a fix for its capture/permission/cleanup failures. Historical documents retain their original chronological requirements; this document supersedes their platform matrix only.

CI uses one writer, coherent pushes, path filters and cancellation of superseded runs. Focused and full workflows have separate triggers; no full release follows a failed focused gate. Recovery-only commits use[skip ci]. Full gates/signing are pending until fresh results are recorded. Physical acceptance remains [OWNER_ACCEPTANCE.md](OWNER_ACCEPTANCE.md), including real Recents, both Fold screens, screenshots, independent credentials, existing media and signed in-place upgrade.
