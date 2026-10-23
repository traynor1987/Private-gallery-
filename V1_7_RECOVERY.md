# Private Gallery 1.7 recovery

Baseline: `affb339e9367627fd9f2f82c075dd76b78883393` on `release/1.6-hidden-tor`.
New branch `release/1.7-encrypted-camera` created and independently read back at that SHA on 23 October 2026.
Owner reports physical Tor Only and VPN Only working; VPN + Tor remains unavailable.
Original production certificate SHA-256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.
Package `uk.co.traynor.privategallery`; target version 1.7/code33, upgrading exact code32 source `7765c79874556e8093612b64eeb529e095c9c287`.

## Design and execution ledger

User supplied the complete specification and requested autonomous implementation. Fresh isolated clone; no existing local changes or build process. Preserve all other branches and existing encrypted formats/keys. No real media operations. CameraX 1.5.3: foreground lifecycle only, file output in noBackupFilesDir, no MediaStore output. Separate photo/video use cases to support lower camera capability levels.

1. Capture ownership and recovery: immutable Primary/Hidden destination; random capture-only key wrapped by that vault's authenticated key before capture. Finalisation may use only the capture key after lock. Authenticated encrypted pending area is separate from existing vault reconcile directories. After fresh authentication, verified complete pending media imports through existing pipeline; interrupted/unparseable bytes remain encrypted pending, never falsely COMPLETE. Private plaintext needed by CameraX is removed only after verified encrypted staging. Process death before sealing can leave private plaintext until next authenticated recovery; no universal recovery claim. Existing Hidden payload ceiling is 2 GiB; stop safely before that byte limit, with no time limit.
2. Camera controls and integration: vault menu action, setup normal/black, photo/video, explicit persisted audio off by default, permissions, quality, switching, flash, zoom, bounded input gate, hardware volume. Lock/onPause stops input and recording immediately, neutral Primary entry, no automatic Hidden prompt. Recovery/details accessible only inside authenticated destination camera.
3. Hidden browser: compact existing Material visual conventions and inset-aware controls/settings; networking source unchanged.
4. Verification: host tests for ownership/input/recovery, Android synthetic encrypted imports, lifecycle/UI, full existing CI, original-key signing and synthetic code32→33 upgrade. Independent final source review and owner Fold checklist. No main merge.

Status: recovery/baseline verified; implementation in progress. Local Gradle distribution unavailable; use existing GitHub Actions for Android build/testing/signing. No acceptance APK yet.

Future only: encrypted off-device backup, owner recovery destination. No cloud upload in 1.7.

## Camera integration checkpoint

Core capture journal checkpoint independently verified on GitHub: `09b58e3ec8079c5b664de3a43b44db30f72b177a`.
CameraX normal/black surfaces, photo/video, audio preference encryption, private file capture, vault menu integration, immediate foreground revocation and neutral lock entry implemented; Android compilation pending CI. Recovery preserves incomplete bytes and imports complete captures after authenticated access; no public outputs. Original capture timestamps use optional import arguments without format migration. Recording Details reads encrypted provenance/hash metadata.

Independent in-progress source review found live-finalisation/recovery race, weak video completeness check and potential renewed-auth authority reuse. Corrections: process-wide live capture lease excluding recovery, strict existing MP4/sample validation before completion, permanent access revocation/cached Hidden authority. Host camera checks now 27 passing. Browser chrome uses Primary rounded TextField conventions and keyboard insets; unavailable combined mode disabled; transport source unchanged.

CI650 (`a43f8bb74e17e012025da1700f95fe8401d95eea`) passed host gates but failed Kotlin compilation: zoom property setter and setZoom function had the same JVM signature. Renamed command to changeZoom; no test assertion removed. Further review corrections add close-on-failed-auth delivery, synchronous camera-entry revocation, frozen audio consent, cancellation-aware Primary import, distinct capture identities with idempotent retry, camera rebinding input exclusion, and stop/restart debounce. Added emulator CameraX actual capture tests (normal/black, both vaults, silent/audio, >10 s, revocation). These are not physical Fold acceptance.
