# Hidden screenshot preference and return to Primary

Owner reported that enabling screenshots did not affect Hidden. Root cause: media Dialog forced SecureOn even after owner-authenticated Activity screenshot preference cleared FLAG_SECURE. The media window now inherits the Activity policy, matching the existing browser dialog pattern. Default blocking is retained. Hidden PIN/setup stays SecureOn, regardless of media capture consent. Allowing screenshots also allows system capture/previews where Android permits; do not treat enabled capture as screen protection.

Settings → Security & privacy → Hidden Vault → Return to normal Vault revokes Hidden keys and operation authority first, then opens Primary Vault only if its session and key are valid. Otherwise it shows Primary PIN or setup. No key/media transfer or shared session is introduced. The button existed previously as a scope reset but did not navigate; now it performs the requested explicit return.

Added Android coverage checks actual visible dialog window FLAG_SECURE for allowed/default grid, image viewer and relocked PIN, plus actual Activity return with/without Primary authority and Settings action. Local Android red/green execution unavailable; CI must verify these tests before the new source is presented as a tested APK. Local foundation373 checks, storage/domain/corruption/recovery/operation/timeout checks, Node3/3, secret scan, WireGuard path and diff checks passed. Independent review identified two test fixture assumptions; corrected scroll action and configured/unconfigured Primary routing. No production acceptance claimed. Previous CI592166-test result applies only to its previous source, not these changes.

No bottom inset adjustment, Tor, main/V2/PR59 changes, production install or owner-media operation. Bottom clipping still awaits the complete Samsung bottom-edge image.


## Verified candidate

[Download CI593 APK/report ZIP](https://github.com/traynor1987/Private-gallery-/actions/runs/39178559174/artifacts/12155494257). Extract `outputs/apk/debug/app-debug.apk`. Tested source `15368857bbed327b0bd369b9464f01caa3fb53eb`. CI593 SUCCESS:169Android tests,0failures/errors/skips; JVM/lint/build passed. Both new actual window-flag tests and return-authority test passed. Earlier pending-verification notes above describe the pre-CI checkpoint.

APK SHA-256 `a4be0b08ae465dd6b1492a2bd031dae91ad7d7e82b799fd02781e1445b66590d`. Package `uk.co.traynor.privategallery.hiddenprototype`,1.0.27-TEST-ONLY/code28, debuggable. APK v2 signature/content digest independently verified. Debug certificate SHA-256 `fd111d389b88feba2bc366a12fd01d83e08dfb1b27f04614b64de96119a024f8`; differs from CI592, so Android may reject an in-place test update. Do not uninstall any app containing needed media or production V1. No production acceptance or real-media use authorized.

Physical checks: with disposable media, enable screenshots through the existing owner-authenticated setting; verify Hidden grid/photo capture, then turn it off and verify blocking; PIN stays protected. Settings → Security & privacy → Hidden Vault → Return to normal Vault must open normal Vault and require Primary authentication if expired. Reopening Hidden must require Hidden PIN; its stored synthetic imports persist. Bottom clipping remains unresolved pending full Samsung bottom-edge screenshot.

Evidence: [summary](evidence/ci593-summary.txt), [APK inspection](evidence/ci593-apk-inspection.json).
