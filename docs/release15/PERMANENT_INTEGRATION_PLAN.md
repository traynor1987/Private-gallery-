# Permanent Hidden integration — 17 October 2026

Owner execution specification: continue Stage A–D on release/1.5-hidden-vault. Existing prototype and CI583 evidence remain preserved. Implementation is authorized inline; no production enablement, owner-media use, transfers or V2 changes.

Design: reuse VaultHome and FullscreenMediaViewer with Hidden-only callbacks. Primary defaults remain unchanged. Hidden credentials use the existing independent PIN envelope and Keystore namespace. Authenticated HiddenSession operation tokens reject stale reads, commits and callbacks. Hidden data lives in noBackupFilesDir/hidden-media-v1, never in Primary vault/cache/browser/export paths. Versioned AES-256-GCM records bind Hidden domain, record kind and stable ID. Ciphertext is staged, synced, authenticated and atomically promoted; metadata is the final commit point. Recover only after authenticating metadata; retain corrupt committed records and report errors. Original picker URIs are read only.

Stages and verification:
- A: add optional presentation capabilities to VaultHome; defaults preserve Primary. Reuse adaptive grids and swipe viewer, scoped bottom navigation, disable unsupported egress/AI/biometrics. Keep synthetic prototype callable for historical tests.
- B: add bounded authenticated record store and encrypted metadata repository. Test wrong keys, domain/kind swapping, corruption, interrupted writes, size limits, restart, encrypted thumbnails, collections and Primary directory isolation. No permanent plaintext format.
- C: extend HiddenSession with revocable operation tokens, integrate PIN setup/unlock, close/background/timeout revocation, and stale callback cleanup. Test lock during operations and reject tokens from earlier unlocks.
- D: focused host/JVM tests, Android persistence/UI/lifecycle tests and existing regressions; independent review; checkpoint and one CI build after coherent integration. Physical Fold testing and owner acceptance remain separate.

Limits: media imports are explicitly bounded to 16 MiB each in this first implementation; imports fail without deleting originals when unsupported/oversized. Metadata bounded to 4 MiB; thumbnails bounded to 1 MiB. No Hidden backup/recovery or migration promise. No claim of V2 native retirement guarantees. Keep production gate disabled until acceptance.

Review focus: lock racing final commit, late image/video results, authenticated metadata corruption, interrupted metadata/payload promotion, external-picker lifecycle and no plaintext temporary files. Tests must pin these behaviours rather than weaken failures.
