# Private Gallery 1.5A — implementation and verification boundaries

Owner-authorized spec: 17 October 2026 Hidden Vault release mission. Execute in this session; stop before 1.5B. Preserve Primary bytes, package and signer. No owner data, migrations, installs, merges or changes to V2 branches.

## Baseline and preservation

- V1 baseline: a19218479eb9b9bcdb35ff0035fc78522263c891, version 1.0.27 / code 28; last pre-V2 main release, authenticated bounded video PR #55. Primary index reads versions 2–6 and writes v6; AES-GCM payloads and PGVIDEO1 remain unchanged. No Room/database schema migration.
- Current main is Phase2: 93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10. Do not use it as V1.
- Protected Phase3 branch and open/draft/unmerged PR59: 79bb1928f3fbe16e935e587a3fe594158cb3f529.
- Latest durable V2 recovery: d6c9ab454a9f0ea90bcfe12783eb68e51acdd368, preserved at preservation/v2-task3b-2026-10-17-d6c9ab4; both independently resolved through git ls-remote. This is an incomplete Task3B checkpoint, not completed V2.
- Accessible recovery checkout /workspace/scratch/0881340dd615/private-gallery-task3-recovered was clean at inspection. Other Work services/workspaces cannot be exhaustively enumerated; no claim that inaccessible/unpushed work has been backed up. Nothing was interrupted.
- Historical accepted signer certificate SHA256: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17 (Phase0 #51 and Phase2 #54 evidence). No current binary/signing-key verification or APK compatibility claim. VersionCode must exceed installed version at candidate time; do not install this baseline build.

## Minimal design

Keep all Primary crypto, UI, storage and authentication intact. This checkpoint deliberately has no route that can provision Hidden credentials; collision protection and lifecycle UI are prerequisites for activation. Add isolated Hidden primitives first, then Android persistence and UI only after their prerequisites can be verified. No V2 cleanup framework is imported.

- HiddenPinEnvelope.java: fixed versioned Hidden-domain record; PBKDF2-HMAC-SHA256 600000 iterations, random 16-byte salt and 12-byte nonce, AES-256-GCM / 128-bit tag. Explicit authenticated Hidden namespace and format header. Six to twelve ASCII digits. PIN arrays consumed; best-effort derived-key wiping.
- HiddenSession.java: independent session, opaque one-shot attempt identity, stale/late rejection, explicit revocation, fixed 60-second maximum session duration including suspend; inject Android elapsedRealtime when wired. No sliding prolongation.
- HiddenEntrySequence.java: transient 5 Installed / 1 About version / 4 Installed within 30 seconds. Route signal only; reset on exit/background. Existing V1 Hide Content remains separate.
- HiddenFoundationChecks.java: direct JVM synthetic behavior checks, invoked by a JUnit bridge in normal Android JVM tests and dependency-free runner in focused CI.

## Execution checklist

- [x] Preserve and independently resolve V2 backup.
- [x] Inspect pre-V2 V1 format/version and isolate release branch.
- [x] Write tests, observe absent-feature compilation failure, implement core and run checks.
- [x] Implement (Android verification pending) persistence of a freshly generated independent Hidden key using an Android Keystore outer credential envelope and AtomicFile under noBackupFilesDir; no plaintext persistence, overwrites or reset on corruption.
- [ ] Enforce different Primary/Hidden PINs at Hidden setup AND every Primary creation/change/recovery/restore path, with rejection tests. Namespace separation alone does not guarantee different PIN values.
- [ ] Wire concealed navigation, Hidden PIN setup/confirmation/unlock UI, supported lifecycle cancellation and always-secure window/preview policy. No automatic Primary unlock grants Hidden authority.
- [ ] Android tests for real Keystore, atomic-write failures, process recreation, background cancellation and Primary regression.
- [ ] Final independent review, exact-head checkpoint and 1.5A gate decision.

## Forward compatibility and rollback

The new 84-byte credential record begins PGHPIN + zero + version 1. Unknown versions, lengths and other domains fail closed. Parameters are fixed by version; never reinterpret a future record as v1. Primary and preserved V2 envelopes remain separate formats. Later metadata/payload formats must bind explicit Hidden domain, stable item/collection IDs, record version and nonce context; that work belongs to 1.5B.

No automatic V2 migration is implemented or promised. Future migration requires authenticated source reads, independently verified destination writes, crash recovery and explicit owner approval. A downgrade must not discard Hidden records. Existing Primary backups must remain readable; Hidden backup/recovery is not implemented. A future UI must explain Hidden recovery limits before creating content.

## Security guarantees and limitations

These are currently unintegrated primitives: they do not create a usable Hidden Vault. Hidden credential persistence is implemented but not yet Android-verified. No media, metadata, thumbnails, UI, lock wiring or product backup policy is claimed. Wrong-PIN and tamper rejection are measured on host JCE, not Android hardware. Actual credential separation across app entry points remains pending.

Version1.5 will use ordinary Android lifecycle cancellation and supported cleanup. Array wiping is best effort; JVM/JCE, decoders, graphics and OS copies cannot be proved retired. No universal native retirement proof, 72-worker scheme, resource manifests, unsupported AndroidX acknowledgements, process isolation, forensic invisibility, brute-force immunity or V2-equivalent security claim. Concealment is a UI feature, not denial of vault existence to an examiner.

Android build prerequisites are absent here. Gradle distribution download fails with Network is unreachable. Do not reinterpret host checks as an Android build or product gate pass.

## Verification recorded before source checkpoint

- Host Java compilation with Java17 and 372 behavioral checks PASS; initial absent-class compilation failure was observed before core implementation. This is not a JUnit test count.
- Three existing Node browser-helper tests PASS. Retired-AI distribution, tracked no-secret and WireGuard-only checks PASS. git diff --check PASS.
- Independent reviewer found no scoped code defects in the Java primitives or Android adapter; identified boundary gaps. Host gaps were added; Android gaps are authored but require emulator execution.
- Android adapter uses a random Keystore AES256 outer key in a separate alias and AtomicFile credential under noBackupFilesDir. It returns a freshly random32-byte Hidden key only after ciphertext read-back. Five namespaced synthetic Android tests cover wrong PIN/recreation, corruption/key loss, namespace substitution/length bounds, .bak/.new recovery, unavailable storage/retry. None uses default credentials or owner media.
- No physical Android device, APK signature, full JVM/lint/Android result or owner acceptance is inferred from host checks. One exact-head normal Android CI run is requested by adding this release branch to the existing push filter. No new PR against V2 main is created.
- Primary production source, manifest, extraction rules, storage formats and version/signing settings have zero changes from V1 baseline. The baseline extraction policy has only root exclusions; complete supported-domain policy must be verified before Hidden activation.

Remaining 1.5A activation work: concealment/UI, every credential-change collision check, lifecycle revocation/stale callbacks, secure window and previews, backup exclusions, actual Android tests. Do not begin1.5B or present these components as a usable vault.
