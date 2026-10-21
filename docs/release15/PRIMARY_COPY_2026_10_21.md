# Copy Primary media into Hidden — focused V1.5 feature

Baseline remote `a1773cb02d79b8ccb9c3ef770a0cb5871fe8f319`; baseline application CI596.
Owner reports clipping fixed and working navigation. Preserve existing components.

Hidden Add media now offers From device Gallery and From Primary Vault. The latter
opens shared full-screen PIN presentation and requires a fresh Primary PIN before
listing any Primary records. A temporary protected multi-select picker shows
Primary-only thumbnails, supports up to 100 selected IDs and Copy to Hidden,
progress, partial results and return to the refreshed Hidden grid. Copy only;
Primary files, metadata, collections and originals are unchanged. Existing Hidden
16 MiB/item limit applies and is shown before selection. Oversized items disabled.

PrimaryCopyAccess is a single-use foreground authorization created only while the
Activity owns an unlocked Primary session. It owns fresh independently unwrapped
Primary key material and a Hidden operation epoch. MainActivity synchronously
closes it on Primary lock, background and destruction. Picker disposal/cancel
also closes it. Keys, PIN arrays and bounded payload buffers are wiped; thumbnails
are picker-local and recycled. No plaintext files, global thumbnail cache or
transfer journal is created. Process restart requires fresh authentication.

ReadForHiddenCopy reads current Primary metadata and bounded authenticated payload
without migrating video formats; it checks the source digest before import.
Hidden's existing stage/encrypt/read-back/promote pipeline performs destination
writes. Its optional publication guard defaults to existing behaviour; the copy
source guard checks Primary authorization and Hidden epoch under its monitor,
then Hidden's commit gate checks its own authority at payload and metadata publish.
After durable import the destination payload is read and verified before counting
success. Duplicate selections are deduplicated and existing Hidden duplicates
verified without creating another record. No collections or favourite state copy.

Item failures can leave earlier verified items; cancellation/lock stops the batch.
Interrupted promoted ciphertext is reconciled immediately if Hidden is still
usable, otherwise on next authenticated entry. A successful commit interrupted
before its acknowledgement may remain in Hidden; retry deduplicates. Originals
are never removed. No cross-vault atomic MOVE claim.

Focused Android tests cover photo/video/multi-item/duplicate copying; wrong PIN;
Primary byte-for-byte preservation; isolated metadata/thumbnails; restart;
Primary and Hidden revocation before publication and between items; real Activity
stop/Primary-lock hooks; injected ENOSPC storage failure; interrupted promotion
and fresh-session orphan cleanup; source digest mismatch; and complete UI flow.
Process interruption is fault-injected at durable publication boundaries, not an
OS process-kill test. Actual device process death remains an owner check.

Independent review requested and findings addressed: source digest precheck,
API26 video preview fallback, PIN cancellation cleanup and authentication retry.
Review also corrected fault injection to occur after payload promotion, and final
metadata-boundary revocation coverage was added. No material remaining issue was
reported in reviewed code; CI compilation/runtime validation pending checkpoint.
Fresh local foundation 373 assertions and existing host security/browser checks
passed. Android red/green cannot run locally because Gradle download is unavailable.

No production release, main/V2/PR59 changes, encryption-format migration, Tor,
source deletion or owner-media operation. Existing WireGuard blank page remains
separate maintenance; enforcement is retained. Final exact-source CI and APK
identity/signature/hash evidence must be appended before claiming a tested build.

## Final automated acceptance evidence

Exact tested application source: `51f70c356826b8e60c4f0632e2a2a2dee955d2d4`.
CI597: https://github.com/traynor1987/Private-gallery-/actions/runs/39687957061
completed successfully. JVM tests, lint, app/test APK compilation, source/security
checks, TEST ONLY identity and signature checks passed. Android API36 emulator:
183 tests passed, zero failures, errors or skips (including all 12 added tests).
Independent downloaded XML report count confirms the result. Archive SHA-256:
`fe6f7c22a2aed0539d0a0e8d4a03ac0922a9862e2133b470d8f2e9552f92f8b3`.
Artifact ID 12387932218, private-gallery-debug-and-instrumentation-diagnostics.

APK SHA-256: `e50d076f38a95de18040018a4e49a5ecda62ba4955eb05da88f244140951b10c`.
Package `uk.co.traynor.privategallery.hiddenprototype`; versionCode 28,
versionName `1.0.27-TEST-ONLY`, 38140544 bytes. RSA2048 Android Debug signer,
certificate SHA-256 `fad6d29cf8b997af9286b10aabe5a58eab3128feb4e77a98316693e8b43c0730`.
CI apksigner and independent APK v2 signature/content-digest verification passed.
This package cannot overwrite production V1. Its certificate differs from CI596:
it cannot update that test installation in place. Do not uninstall a previous
test installation with needed media. Use a separate test device/profile or a
compatible existing signing-key build for nondestructive installation.

Owner acceptance remains pending. Use synthetic photos and video under16 MiB:
1. In Hidden, Add media > From Primary Vault; wrong Primary PIN exposes nothing.
2. Authenticate correctly, select multiple items and Copy to Hidden; check result.
3. Verify playback/thumbnails in Hidden and unchanged originals in Primary.
4. Restart; confirm persisted copies, separate favourites/collections and keys.
5. Cancel, background or lock during a batch; completed copies remain and no
   further items publish. Repeat with actual OS process termination on test data.
6. Check folded/unfolded layouts, full-screen PIN, tabs and screenshot preference.

No production acceptance claimed. Known limits:100 selected IDs, existing16 MiB
per-item cap; process interruption/storage exhaustion are fault-injected in CI,
not an actual OS-kill/full-device test. WireGuard blank page remains a separate
known maintenance issue; VPN enforcement is unchanged. Feature development stops
here pending owner acceptance; no main or preserved V2 branches were modified.
