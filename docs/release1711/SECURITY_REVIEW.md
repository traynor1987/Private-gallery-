# NovaOS 1.7.11.3 navigation, status and first-launch signing review

Source: ab659cf3578d08cdc0deb643f887e561abb14c63
Decision: APPROVED
Production signing: APPROVED
Upgrade fixture SHA-256: 2e6d4ab486eaf81899c4a4b6679d303990c4cca97b29f4364c3eef0fbb719b5d
Upgrade controls SHA-256: 2246a0d8daf326cabe85bb3a6ebfb6c4e061aa79300b0f125e9358692dc446b4
Runtime matrix SHA-256: 4f565f651cd524d5185de7262b4e3899a1d06fd1e7e609df2dc31721e6912b7c
Runtime archive SHA-256: 194292602306328a64b67c1ba1e173448ac9bd3f6657f37f566ae6dd45c6398f
Signing workflow SHA-256: 590e3294632c21f82933130e2cbca9025c5a15245358f7ececb1cfab4734d8bb

Review date: 2026-11-06 UTC. Reviewer: independent navigation/status security review agent.
Frozen tree: 408c9e0d7b2d0980c45b94c43e91b4933e0bad89.

This authorizes original-key signing of NovaOS 1.7.11.3/code59, package
uk.co.traynor.privategallery, through the reviewed home2-production workflow only.
Publication remains conditional on candidate package/certificate checks and actual original-key
code58-to59 in-place encrypted-data preservation and restart verification. This approval does
not assert signing or upgrade has already occurred. Physical Galaxy Z Fold acceptance is pending.

## Independently verified original evidence

The reviewer independently refetched successful run42209939762, job127865786721 and
artifact13499660758 through connected GitHub, and independently downloaded the original archive:
68233934bytes, SHA256 above. CRC, safe paths and417unique archive members pass. Original
Android XML contains130unique cases across29classes exactly matching the reviewed matrix;
original JVM reports contain406unique cases. Zero failures, errors, skips or duplicates.
All2763current Git blobs match the independently fetched frozen source tree.

The actual isolated test APK has SHA256
782758b2b86dec8e74b032d8469ecbad5c543aea5b2180dfb2c2e0b1fe4c1692,
package uk.co.traynor.privategallery.hiddenprototype, code59/version1.7.11.3-TEST-ONLY,
min/target37, one v2 debug signer. It is not a production upgrade. Actual aapt/apksigner
checks match its original receipts. Parent independently repeated archive integrity, source,
matrix, Android/JVM accounting, APK hash/signer and emulator environment checks.

Full API37 image includes SystemUI, tools22.0, actual AVDtargetandroid-37.0,
15stable surfaceflinger observations and empty crash preflight. Earlier failed/superseded
runtime attempts remain recorded in CODE59_PROGRESS.md and are not approval inputs.
No required test was removed or skipped. The final status lifecycle case strengthens the
original with repeated quick resume, a committed pause, exact subscription counts and fresh data.

## Reviewed application boundaries

No outstanding Critical or Important findings remain.

- Shared NovaOS-owned Recents/Home/Back chrome preserves Android system/IME insets. Eligible
  App Mode alone adds the fourth overflow control. The secure bottom sheet reuses existing
  callbacks, dismisses on Back first, distinguishes disabled actions and rejects stale callbacks
  across backgrounding, changed app identity, lock or revoked authority. Native menus remain
  separate. Real WebView identity and user-started HTML audio retention tests pass.
- Code58 did not already fix first-launch presentation. Saved app intent is retained as
  authenticated admission metadata while actual readiness is unavailable; browser chrome is
  never an automatic fallback for App Mode. Re-admission checks original session, saved
  definition, presentation and the unchanged actual network gate. Explicit OS VPN consent
  handoff retains only admission metadata, never a retired controller/transfer lease. Home,
  ordinary background, lock and concealment cancel it. No artificial delay or weaker gate.
- Compact battery/charging/saver/connectivity data uses supported callbacks/broadcasts;
  association and validated internet remain distinct. Restricted radio details are omitted.
  No location/privileged permission, network identifiers, guessed signal or unverified VPN badge.
  Root inset visibility suppresses duplicate status chrome. Lifecycle/session generation
  guards retire subscriptions and snapshots, including coalesced and committed pause/resume.
- Existing authentication, concealed Hidden entry, separate Hidden PIN/no fingerprint,
  Space-specific encryption, wallpapers, Gallery/Vault/editing, Home appearance, Store and
  native apps remain. V1 engine configuration, ordinary browser operations, stores and session
  file hash are preserved. No VPN/Tor repair, routing changes, new keys or plaintext import.

## Reviewed signing and upgrade controls

Eight controls, fixture, matrix and full signing workflow match these approved hashes.
Authenticated exact-source runtime checks and original baseline-compatible fixture compilation
run before signing credentials become available. Candidate validation and upgrade receipts gate
publication. Signing material is removed on every outcome; evidence excludes instrumentation
APKs, nested archives and key material.

Original signed58 baseline: sourcece743678cca903f41a376e5227eb381f9fbb9574,
activationa7666cf9b44d8f193f252921e62ee0c9b33b0ecd, run42171366749,
job127739753358, artifact13481647850. Actual archive60614078bytes SHA256
5279a40b6a1ca1242af9647cf04d98e9ede3f497ce9991b70fe9f8e6986f5dce.
Actual APK144697004bytes SHA256
d28e0de4a3aa6082922e20940f73e9e5f80d4ae9487dac0761338e6550abc59b.
Nested evidence103867bytes SHA256
bb455929217f870a1630b8ea7469d8c1990010c31fada2a433d61ffe6c2026f1.
Archive, APK, nested evidence, authenticated provenance and actual original signer,
nondebuggable package/version/API37 were independently verified by parent and reviewer.

Required original certificate SHA-256:
94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.

The disposable enforcing API37 experiment must start with production package absent,
install original58, seed synthetic encrypted Primary/Hidden media, collections, evidence,
browser state/cookies and independent Home layouts/themes/widgets/notes/moods/journals/wallpapers,
install59 with adb install -r, verify preservation, force-stop and verify after restart. No
seeded data may be cleared or application uninstalled. This is not the owner's physical device.

## Limits

Physical Fold posture/rotation, Samsung fingerprint hardware, personal website accounts and
live VPN/OS consent/radio transitions remain owner acceptance. Automated tests use simulated
readiness/radio providers where reliable live transitions are unavailable; no live handshake
or hardware verification is claimed. Third-party login/playback restrictions remain.
Any changed frozen source, fixture, controls, workflow, matrix or archive requires new approval.
