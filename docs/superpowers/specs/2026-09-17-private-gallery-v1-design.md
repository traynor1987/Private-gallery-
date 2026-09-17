# Private Gallery V1 Design

## Purpose and scope

Private Gallery is a local-only Android photo and video vault. V1 imports user-selected media into encrypted app-private storage, optionally requests deletion of the source only after verifying the vault copy, and restores media safely through MediaStore. It has no cloud account, sharing, analytics, content classification, automatic imports, or recovery backdoor.

## Platform and architecture

- Kotlin, Jetpack Compose, Material 3, Coroutines/Flow, ViewModel, Room, Hilt, WorkManager.
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`.
- A small clean architecture: Compose presentation layer; use-case layer for import, move, restore, delete, unlock, and reconciliation; repository/data layer for Room, encrypted files, crypto, and Android media adapters.
- Package id: `uk.co.traynor.privategallery`. Launcher label: `Private Gallery`.
- Android Photo Picker is the default normal-gallery selector. It needs no broad media permission. Its selected `content://` URIs are read immediately for import. The app queries only permitted MediaStore metadata and uses MediaStore system confirmation requests for deletion.

## Security model

### Threat model

The app protects encrypted vault data at rest from ordinary gallery applications, file browsing, and an attacker who gains only the encrypted app-private files. It reduces casual visual disclosure in screenshots, recordings, and Recents previews. It does not promise protection against a compromised/rooted device, an already-unlocked app session, malware with accessibility/control privileges, device backups made outside Android's protections, or a user who reveals their PIN.

### Key hierarchy

1. Setup creates a random 32-byte vault data-encryption key (VDEK) using `SecureRandom`.
2. Each full media payload and thumbnail is encrypted with AES-256-GCM using a fresh 12-byte nonce. The item id, format version, MIME type, and purpose are supplied as AAD. Tags are appended by the cipher and verified during every decrypt/read-back.
3. The VDEK has a PIN envelope. The app derives a 256-bit key from the PIN using scrypt (salt, N/r/p parameters retained) then AES-GCM-wraps the VDEK. Plaintext PIN is never persisted or logged.
4. When opted in, a second VDEK envelope is protected by an Android Keystore AES-GCM key requiring `BIOMETRIC_STRONG` authentication via `BiometricPrompt`. This enables biometric unlock without weakening the PIN envelope.
5. Changing a PIN authenticates and unwraps the VDEK once, then replaces only the PIN envelope. Media payloads are not re-encrypted.
6. The decrypted VDEK exists only in process memory while an authenticated session is live and is cleared on lock where practical. It is never written to disk.

If the user loses the PIN and biometric access is unavailable, the VDEK cannot be recovered. V1 intentionally has no backdoor.

## Data model and storage

`VaultItemEntity` records an id, state, encrypted-payload filename, encrypted-thumbnail filename, kind, MIME type, display name, source metadata allowed by Android, sizes, timestamps, nonce metadata, and failure reason. It does not contain paths supplied by outside apps, plaintext file contents, PINs, or keys.

States are `IMPORTING`, `VERIFIED`, `DELETE_PENDING`, `COMPLETE`, and `FAILED`. Payloads live in `filesDir/vault/payloads`, thumbnails in `filesDir/vault/thumbnails`; both are encrypted and app-private. Writes use a staging file in the same private directory followed by atomic rename. There are no plaintext temporary media files: import and restore stream through cipher input/output streams.

On process start, reconciliation removes uncommitted staging data, marks incomplete import records failed, verifies committed-but-noncomplete records where necessary, and never requests source deletion based solely on an interrupted record.

## Media transactions

### Copy

The selected `content://` source is streamed into a staged encrypted payload. The app closes/flushes it, decrypts and hashes/reads it back to authenticate its content, generates and verifies an encrypted thumbnail after unlock where feasible, atomically publishes the encrypted files, then commits a `COMPLETE` Room row. The source is untouched on any error.

### Move

Move uses the copy transaction first. Only after the verified vault record exists does it enter `DELETE_PENDING` and issue `MediaStore.createDeleteRequest` for the source URI. Android shows its own confirmation when required. If denied or failed, the record returns to `COMPLETE` and the UI says: `Saved to Vault, but the original remains in Gallery.` It never silently retries deletion after a restart.

### Restore

Restore creates a pending MediaStore item, streams decrypted media to it, closes it, verifies the written byte count/query success, publishes it by clearing `IS_PENDING`, and reports restored. `Restore and remove from Vault` deletes encrypted vault material only after the restore verifies. The default restore keeps vault media.

### Delete vault

Deletion requires confirmation. The encrypted payload, encrypted thumbnail, and database record are deleted as one recoverability-neutral operation; a missing payload is treated as corruption and is surfaced rather than hidden.

## UI and privacy

First launch: concise encrypted-storage explanation, PIN creation/confirmation, unrecoverability notice, optional biometric enrollment, then the locked app.

The lock screen is neutral. Auto-lock defaults to immediately when the app leaves the foreground; 30 seconds, 1 minute, and 5 minutes are configurable alternatives. On timeout/session lock, the app clears route state and in-memory decrypted key material before vault UI can render again.

The process uses `FLAG_SECURE` whenever the app is visible to prevent screenshots, recordings where Android supports it, and Recents previews. No sensitive notifications are generated. Vault grid thumbnails are decrypted only after unlock into memory and backed by encrypted thumbnail blobs; no ordinary thumbnail cache is used. Fullscreen images and videos stream/decrypt only during authenticated use.

Normal Gallery is a safe selector surface that launches the system Photo Picker for multi-select photos and videos. Vault is a newest-first protected grid with images, videos, full-screen viewing/playback, multi-select, copy/move/restore/delete operations, and basic non-sensitive metadata.

## Permissions, components, and backup

The manifest has no exported activities/services/receivers/providers unless Android requires one, and no broad media permission for V1. Media is selected through Photo Picker and deletion/restoration follows MediaStore APIs and platform confirmation. File sharing uses no exported file provider in V1. Intent input is validated, content URI only, with filenames normalized for display and never used as filesystem paths.

Android Auto Backup and device-to-device transfer exclude vault files, Room database, and key-envelopes. V1 provides no backup/export feature. This avoids silently moving even encrypted private media to cloud backup destinations.

## Tests and acceptance evidence

Pure Kotlin tests cover cipher round trip/tampering, PIN derivation/envelopes, PIN change, lock state, and all import/restore transaction outcomes. Android instrumentation tests cover Photo Picker/MediaStore adapters where testable, secure-window configuration, and Room reconciliation. The core invariant for every test is: an original is never deleted before a verified vault item exists, and no restore-and-remove deletes vault content before a verified restored item exists.

GitHub Actions runs unit tests, lint, debug assembly, and uploads the debug APK artifact. No signing material is committed; release signing is future secret-backed setup only.
