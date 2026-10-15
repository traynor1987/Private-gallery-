# Task 3A — secure decrypted source ownership — 2026-10-15

Status: Task3A PASS (source/handoff implementation and host verification). Phase 3 remains NO-GO; PR #59 remains draft and unmerged.

## Scope and baseline

Continues the verified remote recovery head `3fd3c6230cc02ca0dd9e48893f1ba5ba668643ef` on
`recovery/phase3-task3-wip-2026-10-06`. Existing recovery implementation and evidence
are preserved. No reset to an older checkpoint, encryption/schema change, release,
signer change, main/PR branch modification or work on Task3B–3H/Tasks4–8.

## Implementation

- All six decrypted repository read entrypoints return `OwnedPlaintext`. A bounded
  session presentation original attaches its cell before decryption; the read
  producer's normal closure does not discard the admitted source. There is no raw
  source array return or discarded ownership token.
- MainActivity preview/reference preparation, fullscreen images/crop, editor
  preview/save/generation and fallback video consumers retain that facade and
  explicitly close it. Trusted synchronous borrowing returns Unit, checks exact
  original identity/current epoch before and after consumption, and forbids array
  mutation, retention or unjoined work. At most 16 readers share one physical source;
  excess admission fails without copies or new workers. Actual final reader return
  and factory completion precede wiping and acknowledgement. Fixed physical pools
  are unchanged. Cancellation/revocation denies new readers and stale publication.
- Main delivery enqueues the already-funded source outside authority gates.
  Rejected posts, stale/late distinct results, cancellation before dispatch and
  consumer exceptions retire the correct original. Duplicate callbacks carrying
  the same accepted facade cannot revoke the active consumer's source.
- Media3 and retriever adapters retain the facade, copying only requested ranges
  during synchronous read callbacks. They never hold a raw source array between
  callbacks. Failed/revoked reads clear their destination range; EOF remains -1
  for all positions at/beyond the end. Page disposal retires its owned source.
- Editor generation copies into the existing separately funded input original.
  If the source postcheck rejects after producer creation, the exact queued work
  is cancelled; job completion does not substitute for input retirement.
- Minimal IO prerequisites: scoped decrypted reads preclaim the legacy encrypted
  input, format-sniff descriptor, and chunked reader/key/descriptor manifests before
  opening/copying. Close dispatches without waiting beneath cache/storage locks;
  exact originals retain accounting until terminal cleanup, including failure.
  Whole-read tests await source and IO acknowledgements outside those locks.

## Review and limits

Independent read-only review found two Important issues: an unfunded format-sniff
file and exclusive borrowing that rejected overlapping editor operations. Both
were reproduced with regression admission/behavior failures and fixed. A minor
EOF behavior regression was also reproduced and fixed. No Critical finding was
reported. Review is scoped to Task 3A and is not whole-program security approval.

The encrypted source is now owned across repository handoff and consumption.
Derived bitmaps/caches, generated output buffers, provider workspaces, general
producer manifests, full player/retriever Native teardown, video-source fan-out,
secondary authentication/controller migration and transitional cleanup remain
later-phase obligations. Dispatch of IO close is deliberately not a claim of
completion. A failed cleanup stays charged and blocks fresh authority as tested.
No owner data or credentials were used. Android test fixtures were migrated;
compilation/build evidence must not be called Android runtime/device evidence.

## Verification

Frozen source/test/build/workflow inputs:583, unchanged across focused/full verification.
Evidence is under `evidence/task3a-plaintext-2026-10-15/`.

| Check | Actual result |
| --- | --- |
| Final focused plaintext/read/store/photo ownership |49 passed,0 failures/errors/skips |
| Complete debug JVM task |1222 passed,0 failures/errors/skips |
| Complete Phase0 JVM task |1229 passed,0 failures/errors/skips |
| Debug Android test Kotlin and Java compilation |Passed |
| Python script/security source contracts |20 passed |
| Future-format vectors and Phase3 reference contracts |35 passed |
| Browser helper Node tests |3 passed |
| Secret/model/VPN/backup/corpus/identical Android fixtures |Passed |
| Debug and Phase0 lint |Passed,0 Fatal/Error findings |

The first full invocation completed all debug tests, then was interrupted during
Phase0 compilation. It is retained without an overall pass claim. The resumed
serial invocation finished BUILD SUCCESSFUL in6m51s: unchanged debug task up-to-date,
all1229 actual Phase0 tests executed, Android Kotlin/Java compilation passed.
The earlier API/admission REDs and actual queued-producer/overlap/EOF behavioral
failures are retained, separately from successful final regression evidence.
No interrupted or stalled test is counted as passing. Class totals, raw compressed
logs, source hashes, commands and review dispositions are retained in the evidence.
No Android instrumentation/emulator/device run or APK packaging was performed for
this phase; compile/lint/JVM evidence does not establish OEM behavior.

## Remaining obligations and handoff

Task3A covers the decrypted repository source original and its consumer handoff,
not all resources produced by those consumers. The existing fixed presentation/IO
pools and independent retirement accounting remain unchanged; no weak registration
substitutes for the migrated source ownership. Resource cleanup dispatch never
counts as completion. Cryptographic bytes/formats, verification-before-delete and
original encrypted media are preserved.

No remaining Task3A source/handoff blocker was identified. Task3B may begin as a separate implementation phase using
this handoff. Task3B has not begun. The later-phase obligations listed above do not
constitute acceptance of their current implementations. Phase3 remains NO-GO and
PR59 stays draft/open/unmerged. Only the existing recovery branch is checkpointed;
main, the PR branch, workflow/signing/release configuration and earlier verified
evidence remain unchanged. One coherent skip-CI checkpoint avoids needless Actions.

