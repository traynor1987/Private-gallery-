# Private Gallery 1.5 — owner regression corrections

Owner acceptance is **not complete**. This milestone produces TEST ONLY software. The previous production candidate is superseded for installation; no new production candidate is authorized until the owner physically accepts these corrections.

## Changes

- Phone Gallery and its viewer distinguish Copy from Move for the authenticated destination. Copy retains originals. Move obtains a verified encrypted-copy receipt tied to the exact source URI, rechecks its size/digest and authorization, then requests Android MediaStore deletion confirmation. Denied, failed, unsupported or interrupted deletion does not authorize a new deletion request. Results distinguish verified copies and observed source removals.
- Primary browsing in Gallery retains the selected Hidden vault and its independent session. Primary browsing still requires Primary authentication. Returning to Vault/Favourites uses Hidden; explicit Return to Primary remains the active-vault switch. Cancellation invalidates pending biometric callbacks.
- Authenticated Primary Gallery selection offers Copy to Hidden and explicitly confirmed Move to Hidden. Each destination is authenticated and verified. Move checks the exact source version and current authorization, then uses Primary's existing 30-day Recently Deleted recovery path. There is no destructive replay on restart.
- Transfer publication and completion are scoped to the destination, keys, session epoch, foreground state and navigation generation. No encryption format, data schema, permanent signing key or VPN architecture was changed.

## Verification history

- CI607 exposed a missing Lifecycle qualification; corrected.
- CI608 compiled app/tests and passed JVM tests, then failed lint on indentation; corrected without behavioral change.
- CI609 passed all 204 Android tests and the synthetic test-package upgrade, including verification after restart.
- CI610 passed 206 of 207 Android tests. The new deletion-result test's cleanup attempted a second delete of an already-removed disposable MediaStore row and threw SecurityException, masking its assertions. Commit 17334bc0d66f6ade752bebf759459c5dd7d9d9fe tolerates cleanup failure only; production code and test assertions are unchanged. CI611 is the required final verification run.
- Independent source review found no remaining blocking finding after the recorded corrections; see OWNER_REGRESSION_REVIEW.md. This is not physical acceptance or a security certification.

## Verified TEST ONLY artifact

- App/test source SHA: `17334bc0d66f6ade752bebf759459c5dd7d9d9fe`.
- CI: https://github.com/traynor1987/Private-gallery-/actions/runs/39789695304 (Android #611), artifact 12431518685.
- All 207 Android tests passed: zero failures, errors or skips. This includes the corrected copy-dialog callback tests and all three deletion-result tests.
- JVM tests, lint, app/test assembly, no-secret scan, browser helper tests, retired-runtime checks and WireGuard dependency checks passed.
- Hidden foundation: 373 checks passed. Authenticated stream/seek/tamper checks passed at 20, 100 and 500 MiB; Android large-media playback/seek, thumbnail, legacy-record and restart tests also passed.
- Synthetic test-package V1 upgrade: seed, post-upgrade verify and post-restart verify each passed. Original encrypted-file hashes, PIN, photo/video plaintext hashes, collections/favourites, settings and encrypted VPN configuration were preserved.
- APK: `Private-Gallery-1.5-TEST-ONLY-17334bc.apk`, 38,206,076 bytes.
- Package: `uk.co.traynor.privategallery.hiddenprototype`; version `1.5-TEST-ONLY`, code **29**, debuggable.
- APK SHA-256: `2d43bbe631de6c3f4c27e2d9de05ed43a65d718000d1a54e46511965a69bc453`.
- Development certificate SHA-256: `944ee6b22ff36018f34e8888056bfaf7ac82ced687e6364757f1f96b40e00992` (Android Debug, RSA 2048).
- Original production certificate SHA-256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. **Different identity and package: this APK cannot update production V1.**
- This certificate also differs from the earlier CI606 test certificate `9218c9476085ba7eaf869767b07ed789b8ddeaa85c7e465bf77e1037986bd5e1`; compatibility with an existing TEST ONLY installation must not be assumed.
- Android apksigner verified APK v2 signing. Independently parsed manifest, verified v2 RSA signature and complete APK content digest, and matched the downloaded APK hash/certificate to CI evidence. Artifact ZIP SHA-256 also matched GitHub's published digest.

Evidence is preserved in `evidence/ci611-*`, including the complete Android test XML, APK inspection/signature/badging/hash, three upgrade phases and selected CI verification lines. No new production-signed APK was produced, no release was published and no owner installation was changed.

## Test coverage and physical checks

| Requested case | Automated coverage | Remaining physical check |
| --- | --- | --- |
| Phone Copy/Move to Primary or Hidden | Real MediaStore source, both destinations, verified receipt, duplicate copy, changed source, revoked authorization, denied publication | Selection UI and Android confirmation approval/denial on the owner's device |
| Hidden → Primary Gallery → Vault stays Hidden | Activity host routing/composition, retained Hidden session, separate Primary lock, cancellation and stale biometric rejection | End-to-end real PIN/biometric entry; Vault and Favourites selection |
| Primary Copy/Move to Hidden | Authenticated streams, destination verification, exact source identity, recovery from Recently Deleted, duplicate selection and collection isolation | Confirm Copy and Move labels/results with disposable photos/videos |
| Lock/background/interrupted transfer | Session revocation, Activity stop, guarded publication, interrupted batch/reconciliation, no deletion replay | Background/navigation/restart during a real transfer |
| Deletion failure/cancellation | Android result handler denial, approval without observed deletion, actual disposable row removal, Activity recreation without receipt | Actual system dialog Back/deny/approve and platform result redelivery |
| Storage exhaustion | Injected ENOSPC and interrupted publication preserve originals | Device low-storage behavior with disposable data |
| Collections/favourites/persistence | Source byte preservation, collection memberships and favourites restoration, independent Hidden collections, repository restart and synthetic upgrade | Owner's familiar collections/settings and post-restart playback |
| Existing media/browser/security | Full Android suite, legacy Hidden records, large-media playback/seek, screenshot settings and VPN fail-closed tests | Device-specific playback, screenshots and WireGuard connection |

## Limits and owner gate

The synthetic upgrade uses V1 source a19218479eb9b9bcdb35ff0035fc78522263c891, code 28, adapted to the separate test package and development signature, followed by candidate code 29 with `adb install -r`. It checks original PIN, encrypted media, collections, settings and VPN configuration without clearing seeded data, then repeats verification after restart. It is not a new production-signed upgrade test.

Android's actual deletion prompt approval/denial, result redelivery after recreation, real PIN/biometric entry, device-specific playback and the two owner-reported navigation/selection flows still require physical acceptance. Android owns an already-launched deletion prompt; external edits during that prompt cannot be atomically guarded by the app.

Large Hidden streaming and authenticated range playback remain bounded. The existing legacy Primary phone importer still has a known memory limit for very large decoded photographs (an approximately 55 MiB PNG exhausted a 192 MiB heap in earlier CI605). This focused correction does not claim to resolve every Primary large-photo import case.

Follow OWNER_REGRESSION_ACCEPTANCE.md using disposable media and a disposable device/emulator, or an existing test installation with a matching certificate. Do not uninstall valuable test data to bypass a signer mismatch. Do not replace production V1. After recorded physical acceptance, a separately authorized production candidate must again pass permanent-certificate, package, version and production-upgrade checks before any installation authorization.
