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
