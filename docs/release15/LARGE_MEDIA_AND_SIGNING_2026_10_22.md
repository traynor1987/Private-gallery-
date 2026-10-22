# V1.5 remaining release gates

Recovered branch46f269219390d3aa9284fc899c96af71e81bf542 in a fresh clone beside the surviving worktree, whose external Git metadata was unavailable. No surviving files overwritten. CI603 on product113f9c1 succeeded:194 Android tests and synthetic debug-package V1 code28→29 upgrade plus restart. This supersedes the earlier pending result, not the other release gates.

## Implementation

New Hidden payloads use PGHC2 with64KiB AES-GCM chunks. Each chunk binds Hidden domain, item UUID, per-file nonce, index and length. HMAC-authenticated header binds size/chunk size/file nonce. Exact file length and full SHA256 verification precede publication. Only ciphertext staging is written, and the existing Hidden commit gate promotes payload/thumbnail before committing encrypted metadata. Existing PGHV1 files remain readable; old metadata loads with the legacy zero nonce. Newly generated nonces explicitly exclude zero. New metadata preserves legacy discriminators when collections change.

Imports are capped at2GiB, use bounded buffers and check authority/interruption/available storage between chunks. Read failures wipe the requested output. Playback retains no key or plaintext cache: each epoch-bound repository read retires its own key and bounded plaintext. Large images use sampled, oriented display decoding, preserving alpha; original encrypted bytes remain intact. API26 lacks scaled video frame extraction, so it uses a bounded placeholder thumbnail while retaining protected playback.

Primary-to-Hidden copy streams both chunked and legacy Primary media. Legacy records use the existing Bouncy Castle lightweight GCM dependency with64KiB input buffering, avoiding provider-dependent whole-message buffering. Preliminary plaintext feeds only encrypted Hidden staging. EOF authentication tag, size and full source hash must pass before Hidden publication, including duplicate imports. No Primary migration code or encryption format was changed. The large PNG test also covers legacy Primary copying and corrupted-tag rejection with no staged leftovers.

## Verification

Host tests passed20/100/500MiB encrypt/readback/seek/tamper under-Xmx48m, plus wrong key/id/nonce/hash, truncation, revoked-output wiping, simulated low storage and cancellation cleanup. Existing373 foundation checks and other record/session/durability checks passed. Browser helper3/3, VPN/no-secret checks passed.

New Android tests cover20/100/500MiB padded valid MP4 imports, encrypted verification, thumbnails, seek/read, Media3 readiness/playback, fresh-auth persistence, stale-authority rejection, a4096×4096 random PNG above16MiB with bounded display decoding, legacy Hidden compatibility and interrupted publication. Padded media are controlled size fixtures, not a claim of codec/duration diversity. Actual CI results remain required.

## Signing and upgrade gate

After mandatory debug checks, the branch-only push pipeline checks original certificate fingerprint and proves private-key/password usability by signing a disposable dummy JAR. Secrets are not printed or uploaded. If unavailable, tests may continue but production build is skipped. Production APK is built only after JVM/lint/Android/large-media tests and debug synthetic upgrade pass. apksigner, aapt and SHA256 checks pin package/version/certificate. A second isolated emulator then runs synthetic V1→V1.5 with the real production package and permanent signer, re-signing only its test harness to attach to release apps. No production package is uninstalled or cleared; installation is rejected if an existing production package is detected before seeding. The baseline is rebuilt from pinned V1sourcea19218479eb9b9bcdb35ff0035fc78522263c891. Candidate upload occurs only after production upgrade verification. No GitHub Release publishing or owner-device installation.

Owner acceptance still requires checking the actual installed V1 version/certificate, explicit permission to install, and physical media/navigation/authentication/VPN/screenshot/biometric/background checks. Source checkpoint is not completion.

Independent follow-up review confirmed legacy nonce/AAD compatibility and source authentication before publication. The500MiB video fixture remains a short valid MP4 padded by a free box; sustained high-bitrate physical playback remains an owner check.

## CI605 fixture diagnosis

CI605 (`f39c898d050c4e4056c8e1180ef0f9d9831cc3ae`) passed 197/198 Android tests. The 20/100/500 MiB video import, thumbnail, Media3 playback/seek and revocation test passed, as did the corrected copy-dialog regression. The large PNG had already passed Hidden import/hash/display checks when setup of the Primary source failed in the unchanged `EncryptedPayloadStore.writeAndVerify`. Android logcat records attempted 57,687,293- and 115,374,574-byte allocations and OutOfMemoryError on the 192 MiB application heap. This is the existing provider-dependent legacy Primary importer, before the new copy function is invoked.

The follow-up test seeds a genuine legacy AES-GCM payload and v1 encrypted index using bounded fixture encryption. It retains the same full-size source, copy/hash verification, tag corruption rejection and staging-cleanup assertions. This does not fix or certify large-photo imports through the old Primary importer; that existing limitation remains explicit. It tests copying an already-stored large Primary record, which is the scope of the new copy path. No production implementation or heap limit is changed by the fixture correction. Final CI is still required.
