# Phase3 stream/import checkpoint (2026-10-03)

**PR59 DRAFT / Phase3 NO-GO. Complete Task3 migration remains pending.**

Parent: `a96d1a5f12a18bf97373fd9479e5ece30e481a47`. This scoped checkpoint changes protected stream creation and legacy Primary import disposal ordering. It does not implement Phase3 selected Hidden media storage, full Primary source/holds, paired transfer/recovery or concealed media UI.

## Scope

`ScopedIoGuard.input/output` now accept creation factories. The original operation must pass scope checks and reserve a fixed physical stream-disposal slot before the native stream is opened. The native child attaches immediately; facade allocation failure retires that same original obligation. Normal facade close dispatches through its original reservation and waits outside authority gates for actual native close return and owning acknowledgement. Failed native release remains charged and cannot become successful `.use`. All inherited input accessors require the original live operation and unretired facade; sliced read failure still wipes the requested bytes. Network streams and the repository alias caller use the creation API.

Legacy Primary import provider acquisition/disposal now run outside the storage monitor. A nonclosing preparation facade prevents nested cipher `.use` from waiting for provider disposal while storage is held. No item is selected before source close successfully acknowledges. After reacquiring storage, staged ciphertext is verified again and current authenticated metadata is freshly loaded before the merge. A verified empty first import initializes the existing encrypted index format before staging; this selected empty index remains if later source/staging work fails. No absent-index fallback recreates a stale snapshot. No legacy encrypted format or credential bytes change.

The coordinator allocates its cancellation-checking forwarding node before opening the native source, then binds by field assignment. Source creation callbacks still must attach all their children through their own supported manifests; this checkpoint does not claim their complete producer/transport/native migration.

## Regressions and independent review

The initial missing factory API was a compilation RED, not a behavioral regression run. Accessor bypass produced an actual 9-test/2-failure RED, then 9/9 GREEN. Final pure guard/import ordering suite: 12 tests, no failures/errors/skips. Actual source-close latch tests require provider creation outside storage, prepare/commit inside storage, disposal before selection, fail-closed native disposal, revocation and original admission/key wiping.

Independent review caught an unmigrated alias caller and a native-close/storage wait cycle. The lock split removed that cycle. Re-review then caught stale `prepared.initial` reuse when selected metadata disappeared during the unlocked close gap. First-import empty-index initialization plus mandatory fresh snapshot removed the fallback. Final read-only scoped review found no unresolved Critical/Important finding. The existing post-promotion legacy payload-deletion catch remains a separate unaccepted write-path concern; this checkpoint grants no transfer/deletion safety authority.

Seven new Android tests use isolated synthetic data/pools and preserve failed cleanup obligations; no production pool is reset. They cover storage reacquisition by actual provider close, first import, first close failure retaining empty index/no item, existing close failure retaining selected index, index disappearance without recreation, concurrent collection update retention and corrupt metadata denying before provider opening. They are assembled locally; actual execution must be verified from exact remote CI and supported-device evidence.

## Verification

Fresh frozen debug JVM:732/732; Phase0 JVM:739/739, all zero failures/errors/skips. Debug and Phase0 lint, and all four app/instrumentation APK assemblies passed. Source/workflow hashes matched before each command and after completion. The 30 design contracts, five frozen vectors, all 14 SDK-backed backup mutation tests with no skips, immutable fixture checksums/Android copies, browser helper, secret/source-backup/VPN/model distribution and actual packaged backup/model checks passed. Evidence includes complete raw build/static logs, suite XML manifests, scoped XML, exact source hashes, lint counts and development-signer APK hashes in `evidence/stream-import/`. Development APKs are not permanent-signer acceptance. Exact new remote CI is still required. No complete CB01–CB12, runtime/native, product, signed-candidate or owner gate gains PASS from these scoped results.

Raw disconnect/file ownership, producer and presentation Jobs, copied buffers, readers/player/controller/credential-attempt handoff, and legacy synchronous cleanup remain transitional. Tasks3–8 and all final Phase3 acceptance gates remain pending. PR59 must remain DRAFT, unmerged and NO-GO. No owner data was accessed or migrated.

## Parent CI observations

Remote recovery retains original487 attempt1 packaging failure with unknown underlying cause, same-source retry487 and independent488 successes at a8f6467. At checkpoint preparation, 8136025 push489 and PR490 were independently verified SUCCESS; a96d1a5 push491 and PR492 were still running instrumentation. Their results are not substituted for this new source. New-head CI and seven added native cases remain pending until measured.
