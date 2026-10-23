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
