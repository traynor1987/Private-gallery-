# Android17 preview0.5 result

Source: f2750627165fd379a4eee5d509df018542bf6062. Run: https://github.com/traynor1987/Private-gallery-/actions/runs/41948755747

Host policy/catalogue checks and app/test assembly passed. Package uk.co.traynor.novaos.preview, code5, existing development certificate and 16KB alignment verified.

Android runtime tests executed: **0**. The emulator booted and preflight reported ready, then installation of the pinned previous preview0.4 failed: `cmd: Failure calling service package: Broken pipe (32)`. Neither upgrade test nor 14-case experience suite started. This is not an observed app assertion failure and is not a runtime pass. No screenshots were captured. Fold viewport checks, real in-place data preservation, browser/audio/UI behaviour and Mind regressions require a healthy Android17 environment or owner acceptance. Do not uninstall/clear owner data to test an update.

The existing experimental-publication path released the package with runtimeVerification=failure. Public APK: https://github.com/traynor1987/Private-gallery-/releases/download/novaos-home2-store-preview-0.5/NovaOS-Home2-Store-Preview.apk
SHA256: d02a1a924cec9f5ac6f8fd5b6781052a14c3800b4945dae45e3e5b674ed843c0

Safe continuation: retain this branch/package/key/code5; do not restart features or merge production. Inspect this run's emulator-preflight/logcat receipts before choosing a demonstrably healthier API37 test environment. Run the existing pinned0.4 upgrade and 14 experience cases without weakening assertions. Production storage/signing/security requirements remain in PRODUCTION-INTEGRATION-HANDOVER.md.
