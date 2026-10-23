# Private Gallery 1.7 review and verification

Baseline: affb339e9367627fd9f2f82c075dd76b78883393. Branch: release/1.7-encrypted-camera.
Owner reports Tor Only and VPN Only accepted on physical Galaxy Z Fold for 1.6. This does not constitute 1.7 acceptance.

## Source review

An independent agent reviewed capture ownership, authentication, finalisation and recovery. It reproduced a live-capture/recovery collision in a temporary host harness before the fix. Findings corrected in source:

1. Shared live/finalising capture leases exclude recovery until finalisation releases ownership.
2. Complete video requires existing strict container and sample validation, not duration metadata alone.
3. Camera access is permanently revoked on close/lock; Hidden repository retains its original authority; Primary is bound to key identity.
4. Failed post-auth delivery closes AutoCloseable ticket results and releases leases/keys.
5. Camera entry is marked before asynchronous binding; onPause stops/locks even during startup.
6. Audio loading/saving gates entry; audio choice is frozen for capture.
7. Primary import uses cancellation-aware existing VaultImportCoordinator.
8. Distinct capture IDs preserve original timestamps/provenance despite identical content; retries reuse only the same capture reference.
9. Rebinding disables input; finishing applies a new debounce interval.

10. Completion events and ordinary camera exit retry recovery with fresh authenticated access; lock-close defers recovery.
11. Primary payload, vault metadata and parent directories are synced before retiring the journal; failed durability retains the recovery copy.

12. Recovery requests arriving during an older recovery queue one fresh authenticated rerun.
13. Capture creation reserves its lease before publishing its directory to recovery.

Independent final review confirmed all 13 corrections and found no remaining concrete critical or important blocker in the reviewed camera security paths. Source review is not device verification.

## Verified local gates

- 13 capture input-policy checks and 18 encrypted-journal checks passed.
- 373 Hidden foundation checks plus session, gallery, record, durability and 20/100/500 MiB streaming checks passed again on the 1.7 source checkpoint.
- 53 Hidden browser policy checks passed again on the 1.7 source checkpoint.
- 12,167 MP4 container checks and 3 JavaScript browser helper tests passed again on the 1.7 source checkpoint.
- Public-source no-secret checks and diff whitespace checks passed.
- No file under core/browser changed from baseline.

## Android CI

CI650: source a43f8bb74e17e012025da1700f95fe8401d95eea failed Kotlin compilation (zoom JVM setter name clash). Host gates passed. Corrected command name; failure retained.
CI651: source 0d7ee8480cd4508a0947273c5444d9dec8771a16 passed app/unit/instrumentation compilation; 333 unit tests passed (zero failures/errors/skips); lint failed 5 API-usage errors. Fixed public volume-key callbacks and LocalActivity access, with no lint suppression.
Latest source checkpoint: ba6b270f4fd2895a00908928dbbbed946136e599; remote independently verified and tree matched local source exactly.

Original-key signing, package/version verification, synthetic code32→33 upgrade, final APK SHA and full test totals remain pending. NO-GO for installation until those gates pass. No main merge, owner media operation or signing key replacement occurred.

CI653: 333 unit tests and lint passed; emulator preflight61/62 passed, including all12 camera-related tests and4 layout cases. Disposable onion retrieval failed while BBC HTTPS over Tor succeeded. CI654: live Tor and all9 CameraX tests passed, but2 new UI tests failed (66/68 passed): swipes could trigger capture, and the no-configured-PIN synthetic camera lock retained a stale route. Production fixes reject swipe/multitouch and explicitly choose neutral setup for that no-PIN camera edge; configured owner locking remains unchanged. Both regression assertions retained. Theme screenshot fixture corrected to match the actual app Surface.
