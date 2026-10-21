# V1.5: READY FOR OWNER ACCEPTANCE

Technical release-candidate gate only. Production promotion is not authorized.

## Recovery and exact candidate

Remote V1.5 head independently checked as
`a7dd72d6d43aaa3461062c6a7b5cd34824bca820`; recovered clean local tree was identical
(`e9e5ac9ed21f531ce5c139d8ff8c7e2856208da8`). No active build/implementation process.
No application changes since tested source
`fef455091cf759b0f932753f43502c7cfddfb038`. Preserve CI596 as the final candidate:
rebuilding identical source would unnecessarily change its ephemeral debug signer
and invalidate the owner's installed-candidate continuity. This pass changes only
acceptance documentation and records the known browser issue; no new APK build
is claimed or required for unchanged source.

[CI596](https://github.com/traynor1987/Private-gallery-/actions/runs/39615390388)
was independently rechecked: completed/success on the exact source above.
[APK/report artifact](https://github.com/traynor1987/Private-gallery-/actions/runs/39615390388/artifacts/12352638413).
Extract `outputs/apk/debug/app-debug.apk`.

## Verification this pass

- Fresh local foundation 373 assertions, synthetic gallery/isolation, operation
  revocation, encrypted record roundtrip/corruption/recovery, durability and
  background timeout checks passed.
- Fresh Node browser tests 3/3, secret scan, WireGuard-only dependency/notice,
  retired runtime/source-integrity and diff checks passed.
- Re-read downloaded CI596 JUnit XML: 171 Android tests, zero failures/errors/
  skips. Tests cover independent credentials, stale repository authority,
  Primary ciphertext/metadata preservation, persisted media/collections,
  deletion/recovery, navigation, timeout, screenshots and image/video lifecycle.
- Exact-source CI596 JVM tests, lint and app/instrumentation builds passed.
  Android tests were not rerun locally; local Gradle distribution access remains
  unavailable. Prior exact-source CI execution is distinguished from fresh local
  host checks and fresh artifact inspection.
- Reverified artifact ZIP digest and APK manifest/v2 RSA signature/content digest.
  CI's Android apksigner verification is also preserved with the artifact.
- Fresh independent read-only security review found no material issue in reviewed
  Hidden storage/session/authentication/media-cleanup/backup-export boundaries.
  Hidden ciphertext is in noBackupFilesDir, backup/device transfer excluded,
  stale authority rejected, no Hidden plaintext disk cache/export path found,
  sensitive previews/buffers retired and video released. This is scoped code and
  test evidence, not a universal forensic claim about every Android subsystem.

## APK identity and installation

- Package: `uk.co.traynor.privategallery.hiddenprototype`
- Version: `1.0.27-TEST-ONLY`, code 28, debug build
- APK SHA-256: `27835b282bb55db294be02234987e25acf806f767c582868de96904f6d411271`
- Certificate SHA-256: `0117c5fac91e3ee0fcedb5dcb40ee7871bfdf860df20ceb386b15d1c48524d5b`
- Signer: Android Debug (RSA 2048); this is not production signing approval.
- ZIP SHA-256: `2ab000de5d9eac4e0d0abf7bc57fadc9915a39bc92c119066cf0d33abdd937bc`

The application ID is separate from production V1. CI596 cannot update CI595 in
place because the certificates differ. Keep an already installed CI596. Do not
uninstall any test app containing needed media. No data reset/migration occurs.

## Physical acceptance and known limits

Owner confirms restored five-tab navigation and Hidden screen visibility.
Remaining Fold/keyboard/screenshot/timeout/media checks are listed in
[PHYSICAL_TEST_CHECKLIST.md](PHYSICAL_TEST_CHECKLIST.md). They are not assumed
passed. No additional confirmed layout defect was supplied in this request.

Open separate maintenance issue: owner observed a blank page in the existing
WireGuard browser. Not fixed or attributed to Hidden without reproduction; VPN
requirements/fail-closed enforcement remain unchanged. Primary AI functionality
is retained; live provider/network success is not certified here.

Existing Hidden limitations: 16 MiB per imported item; no backup/export/recovery;
no move-originals or browser vault upload/import while Hidden is selected.
Use synthetic disposable media for acceptance. These limits are not expanded
into a new feature programme in this pass.

Stop feature development at this candidate. Resume only for confirmed acceptance
failures or explicit owner direction. Main, production V1, V2 branches and PR59
remain untouched. Owner approval is required before production promotion.
