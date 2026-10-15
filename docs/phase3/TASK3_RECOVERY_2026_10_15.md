# Task 3 recovery and focused lifecycle repairs — 2026-10-15

**Task 3 remains INCOMPLETE. Phase 3 remains NO-GO.**
This checkpoint is not product, signer, device or owner acceptance.

## Recovered state and exact source repair

Continuation starts at the latest recovery branch commit
`9e9129e0f3db27fca00ef7f7214446c9c28c0553`, not the older PR59 head
`79bb1928f3fbe16e935e587a3fe594158cb3f529`. Main remains
`93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10`.

`MainActivity.kt` at the recovery head is not compilable source. It includes a
literal `Warning: truncated output` header and an inline `tokens truncated`
marker joining unrelated source. History inspection establishes the first
truncation at `a7bd95d`, with a further omission at `5cee22b`.

Recovery restored the exact 2,634-line missing span from intact `a6f7ec5` and
removed only the three tool-output header lines. All nontruncation recovery
edits were retained. Before the additional focused repairs, the resulting
Activity differed from the intact version by only 48 additions/29 removals.
No lost implementation was reconstructed from conversational memory. Changes
that existed only in an omitted tool-output span cannot be asserted recovered.

`scripts/tests/test_source_integrity.py` failed on the corrupt recovered source
and passed after the repair. The existing CI Python test discovery also runs
this guard without a workflow change.

## Focused repairs

- Retain browser upload copies together with their original expiry Job in
  `BrowserUploadLease`. Explicit clear dispatches copy retirement and cancels
  the timer, including an unstarted timer. Repeated clear no longer leaves
  these timers live for five minutes.
- Replace the lazy browser session cleanup body with an originally funded,
  inert active `AbstractCoroutine` root. The exact coroutine is retained before
  attachment and ATOMIC-started in the outer finally, including failed factory
  postchecks. Cancellation before dispatch cannot complete successfully without
  running and awaiting its cleanup. The lifecycle parent is retained;
  NonCancellable cleanup still executes on Main. Failed cleanup remains failed
  ownership. This uses internal coroutine APIs and requires the pinned-runtime
  regressions; it does not close the composite Native-child capacity audit.
- Repair the inaccessible private video-reader constructor via an internal
  companion factory. Share the same private mutex between reader initialization,
  reads, verification, disposal and its copied-key wiper. Descriptor close keeps
  its independently funded worker and can unblock a reader while key wiping
  awaits quiescence. The normal-retirement test now awaits the actual original
  acknowledgement instead of assuming enqueue means finished.
- Supply the downloader's existing WebView-cookie lookup as an injectable
  dependency with the identical Android default. The saved JVM ownership test
  can use fake HTTP without calling an unimplemented Android CookieManager stub.

These changes were independently reviewed. No new Critical/Important finding
was reported within this narrow repair; that is not a clean review of all Task 3.

## Additional bounded ownership groundwork

`SecondarySessionAuthority.createAttemptOwned` now funds an exact authentication
attempt's IO manifest before its factory can run. Cancellation, replacement,
backgrounding, revocation and premature final admission retire those original
reservations. Preparation can admit that attempt's live resources; final key
admission cannot exempt them. The supplied key is still wiped on rejection.
Factories are explicitly rejected beneath the authority gate before capacity or
Native creation. These APIs are not yet wired through the controller, worker,
biometric backend or store-result factories, and do not complete CB1 handoff.

`OwnedPlaintext` provides a preattached, session-owned presentation cell for a
fresh exclusive decrypted array. The producer's normal lease close does not
invalidate the admitted source. Synchronous borrowing validates the exact
attached original, denies retirement immediately, and delays wiping and original
acknowledgement until construction and the admitted consumer return. Normal
close returns the original capacity. There is no array getter or ambient owner
registry. This primitive is deliberately **not wired into `verifiedBytes`**:
doing so without migrating callback ownership, cache tokens and asynchronous
Media3 source/player lifetimes would preserve or introduce unsafe disposal.

The missing-API tests failed compilation before implementation. Independent
review then identified the authority-gated factory entry; two further behavioral
tests demonstrated that entry and an unattached same-pool plaintext cell were
incorrectly admitted. Both failed before their guards and passed afterwards.
The first focused selection passed 47 tests; the final two primitive classes
passed 12 tests. Neither selection is a whole-Task3 or full-suite pass.

The initial full-debug diagnostic executed 1,196 tests with six failures. These
were traced to tests assuming immediate completion of asynchronous legacy
close/cancel dispatch and to draining a synthetic dispatcher before the real
cancellation callback had queued. Tests now use bounded completion observations,
thread-safe counters and `finally` release of synthetic blocking gates. No
production timeout, authentication barrier or failed-cleanup rule was relaxed.
Old XML results are removed before the final broad run to prevent retained
results from filtered runs being misreported as fresh complete verification.

## Verification record

At the first recovery checkpoint, the focused four-class selection (ten test
methods) passed. Reversing ATOMIC startup, timer cancellation and key-mutex
binding then produced three actual behavioral assertion failures; all mutated
production files were restored byte-for-byte. The complete debug/Phase0 JVM,
lint and assembly run is still in progress at this checkpoint; no broad pass is
inferred. Source guard/Python checks passed 15/15, vectors/design checks 35/35,
and Browser helper checks 3/3. Frozen corpus, identical instrumentation fixtures,
source backup exclusions, retired-runtime, secret and VPN source checks passed.

Verification is recorded in the accompanying evidence summary after the final
serial run. Initial failures are retained separately: corrupt source integrity,
the inaccessible Reader constructor, missing new symbols, Android-excluded
java.management compile types, a JVM CookieManager call, and a delegated-parent
test fixture which did not preserve its own CoroutineContext identity.

Separate-compiler-daemon startup failed in this execution environment. The
subsequent verifier uses in-process Kotlin compilation and two Gradle workers.
Gradle, JDK17, API36 SDK, NDK27.3 and CMake3.22.1 were installed in scratch;
no build/dependency version or product workflow was changed to get a pass.
No signed/release APK or physical-device acceptance is claimed.

## Remaining mandatory Task 3 implementation

The authoritative runtime ledger is still required, but its historical entries
must be reconciled against current source rather than assumed current.

1. `AndroidVaultRepository.verifiedBytes` still returns a raw array while losing
   its ownership handle. Its two strongly retained IO children never normally
   retire until lock. Introduce ownership-bearing plaintext handoff and migrate
   consumers and disposal; do not hide this by increasing the quota or weakening
   wiping. Presentation resources must use the proper bounded partition.
2. Nineteen unrestricted production `own`/`ownForSession` call sites remain
   outside their API definitions/forwarders. Prompts, editor/fullscreen producers,
   payload/backup IO, AI connections, video sessions and Hidden request/setup
   resources need complete precreation manifests and exact normal retirement.
3. Secondary authentication has no complete exact-attempt funded worker,
   prompt/provider and temporary-result factory/handoff. Controller request
   closure still reaches Native work beneath its gate. Detach bounded state,
   release outside ranked gates, positively acknowledge the original attempt,
   then perform separately checked short key admission. No self-join promotion.
4. Video sessions still need their two-source limit, complete reader/player
   child ownership and acknowledged normal disposal. The chunked restore caller
   still enqueues reader close without independently confirming actual retirement;
   surrounding storage-lock scope must be addressed before adding a wait.
5. Protected preview/editor/fullscreen bytes, bitmaps, effects, cache ownership
   and shared-token disposal are not fully migrated. Raw array wiping or weak
   registration alone does not satisfy count/capacity/normal-retirement gates.
6. Reference-bearing AI transport and final image handoff, buffered Browser
   download plaintext/header arrays, transformed-video/provider/Media3 teardown
   and the complete Native child inventory remain pending. Upload-file deletion
   outcomes and provider-held descriptors also need positive acknowledgement;
   dispatching a deletion callback alone does not prove bytes were removed.
7. The transitional `SessionCleanup` has one worker and an unbounded queue.
   Remove it only after every generic caller migrates; it is not a substitute
   for the fixed process-shared 72-slot independent-release contract.
8. Complete CB01–CB12 capacity, blocked-release/failed-ack, controller/provider,
   stale/ABA and supported producer-fit verification, plus required Android
   Native/Main execution evidence. Passing narrower tests cannot replace these
   implementation and integration gates.

The video migration additionally requires authority-wide admission for the one
protected pipeline, rather than a session-local source counter. Player Native
ownership belongs in the IO partition; the current Main-native factory charges
the presentation partition and cannot simply be reused unchanged. Media3 can
reopen the same `DataSource`, so every source generation must have immutable
cleanup identity and retain its admission through actual retirement. An old
cleanup callback must never close a newer mutable reader. Complete Transformer,
provider and player child manifests and their Android return evidence remain
required; a local two-source limit alone is not a safe completion.

## Scope preserved

Tasks 4–8 were not started. No owner data or credentials were used. No source-format,
schema, crypto vector, signer, release or workflow changes. Main, the Phase 3 PR
branch and PR59 remain unchanged; PR59 remains open/draft/unmerged. Combine Photos
was not implemented by bypassing this migration.
