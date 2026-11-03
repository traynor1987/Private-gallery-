# Task2c independent security, spec and quality review

Reviewed the frozen 17-file source in `/tmp/novaos-code55`, task/preflight, implementer report and supplied full diff. `sha256sum -c /tmp/nova-native-bridge-final-manifest.txt` passed for all 17 files. No source edits, repeated passing suites, builds, commits, push, CI, or subagents. The reported four-ABI ELF/JNI, host race and wrapper-javac results are accepted as scoped evidence; the integrated app/test compile remains pending root.

**Spec verdict: core authority/FD/callback boundary substantially conforms, but not an unconditional task acceptance while the resource/secret-lifetime issue below remains. Quality verdict: request changes. No release approval; C1 remains unresolved until actual service/runtime/API37 packet proof.**

## Required correction

### P1 — Retired and released authority entries permanently retain full devices and private identities

`native/continuousvpn/android/bridge/bridge.go:77` copies every prior entry into each new immutable registry map. Instance entries contain immutable strong references to core transport and protector (`:50-55`); leases strongly retain the instance and fence (`:57-63`, insertion `:279`). Retirement only marks disposed and releases the protector (`:231-234`, `:386-390`); final release only sets released (`:428-432`). Neither path unlinks heavy graphs. Even fully released fences remain rooted by the process-global registry (`jni/main.go:19`).

Consequences are deterministic from references, independently of the supplied diagnostic: repeated authenticated replacement or tuple registration grows process-retained memory without a practical bound, and each insertion copies all retained handles. The reported ~104,574 bytes/retired generation over 64 generations is a useful workload illustration, not an Android guarantee or the sole basis of this finding. Lease churn adds entries without requiring generation churn. Closed WG devices retain static private identity: the pinned upstream `device/device.go:50-52` stores it and `:354-381` closes workers/peers without clearing that field. Thus retirement and even final release retain old private keys for process lifetime, beyond the required callback/retirement barriers. This does not by itself reopen packet admission, but stale-authority denial does not make it an acceptable unbounded lifetime implementation.

Minimum correction: after a *successful actual retirement barrier*, replace/remove registry payloads and sever retained lease/fence/current/core references so only small bounded authority metadata needed for stale-handle denial/removal receipts remains. Maintain safe atomic lookup/payload retirement; do not simply nil the currently immutable core field while lock-free readers can race. Failed/blocked retirements must retain the existing containment ownership and callback reference. Released lifetimes must cease rooting their device/TUN graphs. Add an explicit practical bound or reclamation policy for remaining handle/lease metadata, rejecting new work before destructive replacement if capacity cannot be reserved. Never recycle positive handles. Regression evidence must cover repeated replacement/removal/release, retained objects becoming reclaimable, concurrent lookup versus reclamation, and failed-retirement retention. Reuse existing authority and failure tests; no gate changes are needed.

## Additional focused quality issue

### P2 — JNI secret stack cleanup is incomplete and ordinary memset is not guaranteed to survive optimization

`native/continuousvpn/android/jni/jni.c:93-98` copies private and preshared keys into stack buffers. A later malformed peer/endpoint/routes string returns at `:94` without cleaning already-copied secrets. The other cleanup uses ordinary `memset` after last use, which an optimizing compiler may eliminate (build uses `-O2`). This is separate from unavoidable caller-owned Java Strings/temporary Go copies; the C code can directly control its own buffers.

Minimum correction: initialize the secret buffers and use one cleanup exit for every post-copy result, with a compiler-resistant wipe supported by the pinned Android toolchain. Verify a malformed later argument takes cleanup and inspect optimized code or use an appropriate non-elidable primitive. Do not claim comprehensive JVM/Go secret zeroization from this correction.

## Reviewed behavior with no additional blocker found

- Real JNI boundary duplicates a still-Java-owned temporary FD with F_DUPFD_CLOEXEC (`jni.c:81-89`); pre-Go failure closes that duplicate. Go pre-adoption errors raw-close, post-adoption errors close only its os.File (`bridge/fd.go:30-49`). The adapter requires preexisting O_NONBLOCK and never changes shared flags. Retirement leaves lifetime TUN open; final release alone closes it after explicit external child-death proof assertion.
- C/global-reference ownership transfers once to Go Replace. Actual protector invokes the owner callback on attached threads, clears/denies exceptions, and detaches only threads it attached (`jni.c:36-65`). Busy CAS is a nonwaiting control exclusion, not a registry mutex. No registry mutex spans Java. Corresponding transport Close barriers precede protector disposal, including the previous-generation early-core-error path (`bridge.go:204-209`). Shutdown attach failure conservatively retains storage. The callback's non-reentrancy and future owner monitor restrictions remain mandatory integration preconditions.
- Immutable map lookup and atomic current/epoch checks satisfy the specified monitor-safe bridge lookup requirement. Replacement validation precedes destructive work; busy CAS serializes all replacement/retirement/release control. Replacement stores initialized CLOSED current before its final epoch check (`:202-229`); Invalidate advances epoch before reading current (`:330-350`). The specified missed-current versus missed-epoch race is covered by that ordering. Gate revocation/publication use the reviewed atomic admission path; no gate weakening found.
- Negative Replace cleanup checks original generation and immutable replacement epoch under the control CAS (`:456-478`), so delayed cleanup cannot seal a newer current. Stale/cross-fence handles fail identity checks; known removed leases retain idempotent receipt semantics; unknown handles cannot acquire those receipts. The retention correction must preserve these properties.
- Configuration is bounded exact single peer with numeric endpoint and canonical prefix parsing; tuple validation delegates remaining exact numeric/family/port checks to the reviewed gate. Statistics returns fixed numeric fields, bounds raw output, wipes its captured buffer, and remains outside monitor-safe APIs. It exposes no raw UAPI text.
- Build changes preserve independent library/JNI naming, four ABIs, pin verification, generated output and Gradle NDK provisioning; wrapper remains unreferenced by the active backend. Production double-Go-runtime loading and packet routing are not validated by packaging.

This review does not authorize integration or release. Resolve the lifetime findings, independently review the correction, and perform root's pending combined app/test compile before any narrower foundation-complete statement.


# Fix round 1 scoped re-review — P1/P2 and correction-related failure ownership

Reviewed `/tmp/nova-native-bridge-fix1-review.diff`, frozen source, appended implementer report and new reclamation/JNI tests. Root verified all 22 files against `/tmp/nova-native-bridge-fix1-manifest.txt`. No source edits, subagents, CI or release approval. Accepted reported passing race/reclamation/four-ABI/optimized-wipe evidence without repeating it. One concrete uncovered failure path justified the targeted overlay regression below.

**P1: NOT ADDRESSED in full.** Normal-path heavy-graph reclamation and bounded metadata are addressed: immutable numeric instance/lease identities plus atomic immutable payloads (`bridge.go:60-78`), 4096-entry CAS reservation before destructive Replace (`:94-112`, `:291-297`), successful-barrier payload detachment and callback release (`:331-349`), released-owner pruning (`:570-575`) and same-current successful close cleanup (`transport/transport.go:213-219`) fix the original resource growth. Existing readers retain safe local references; failures with an exposed nonnil core remain retained. Gate high-water preservation is unchanged.

However the required failed-construction retention/receipt rule remains broken. When protection fails during device Up and the internal close also reports an error, `transport/transport.go:102-104` ignores that close result and returns a nil transport. The failed transport remains in the core fence, but the bridge installs a nil-core payload (`bridge.go:297-303`). `retirePayload` interprets that as successful retirement, drops the payload and releases its callback (`:336-348`). Final `Release` then calls the core fence release, which ignores `f.current.closeLocked()` errors (`transport/fence.go:154-155`), and returns a successful lifetime receipt, closes TUN and prunes ownership (`bridge.go:565-575`). Sticky `releaseFailed` does not help because core Release reports nil. This is an uncovered P1 failure-ownership case, not evidence that normal reclamation reopens admission.

**Concrete targeted counterexample (FAILED as expected):** An overlay adds `TestReviewFailedConstructionCannotAcknowledgeFailedBarrier` to the existing bridge tests. Its actual reviewed fake bind starts normally; the actual protector callback sets `failingCloseBind.fail=true` and returns false. Thus initialization reaches Up, protection fails, and the corresponding actual close path reports an error. Replace returns Failed. Both `RetireCurrent(f,1,1)` and `Release(f,true)` nevertheless return OK; protector releases=1 and TUN closes=1. Command:

```
cd /tmp/novaos-code55/native/continuousvpn/android
/tmp/nova-go-toolchain/go/bin/go test \
-overlay=/tmp/nova-review-fix1-counterexample-overlay.json \
-run '^TestReviewFailedConstructionCannotAcknowledgeFailedBarrier$' -count=1 ./bridge
```

Observed output: `failed internal barrier acknowledged as 0; callback releases=1` and `failed internal barrier manufactured lifetime receipt 0; TUN closes=1`. Overlay source: `/tmp/nova-review-fix1-counterexample_test.go`. Production source was untouched.

Minimum correction: preserve a reachable exact failed internal transport/barrier result when construction teardown fails, so bridge cleanup can neither reclaim its payload/callback nor acknowledge successful retirement/final release. Propagate or conservatively retain failure in the core final-release path as needed; do not use nil-core alone as proof of retirement. Add this failed-Up + failed-close regression alongside the already-passing exposed-core failed-retirement and failed-final-TUN-close tests. Preserve generic JNI numeric results and existing gates/epochs.

**P2: ADDRESSED.** `jni.c:18-21` uses noinline volatile-byte stores; nReplace initializes both secret buffers and funnels malformed-input, callback-creation failure and transferred-call exits into both wipes (`:101-124`). The actual-C host harness covers the previously missed later-argument paths and bounds; the reported actual optimized four-ABI helper/store and two-call evidence supports non-elision. No comprehensive JVM/Go erasure is implied.

No further correction-related blocker found in numeric stale-handle/removal receipt checks, current-pointer identity CAS, original-generation/epoch negative cleanup, lock-free monitor reads, or pre-destructive capacity admission. The practical cap and inability to recover until externally proven final release are explicitly documented, with no fallback or handle reuse.

**Updated scoped spec/quality verdict: request one focused P1 failure-ownership correction.** P2 is closed; normal P1 resource correction is accepted, but P1 remains open for the demonstrated false receipt path. Integrated app/test compilation and service/runtime/API37 C1 packet proof remain separate outstanding work. No release approval.


# Fix round 2 scoped re-review — remaining P1 failure ownership

Reviewed the five-file `/tmp/nova-native-bridge-fix2-review.diff`, frozen source and appended fix report. Root verified the 23-file manifest. Inspected the exact unchanged reviewer-overlay GREEN log and focused related bridge/transport race-result logs; accepted the reported coherent four-ABI build evidence. No new concrete concern justified repeating those passing commands. No source edits, subagents, CI or release approval.

**P1: ADDRESSED.** `transport/transport.go:105-111` now returns the exact newly constructed partial Transport with `errors.Join` when construction and its teardown both fail. The bridge's existing `bridge.go:297-311` stores that nonnil core in the retained payload and returns generic Failed. Consequently `retirePayload` (`:331-348`) encounters the actual sticky failed Close, returns Failed before payload/callback detachment, and final bridge Release (`:553-558`) also stops before lifetime close/pruning. This removes the nil-core-as-success ambiguity demonstrated in fix1.

Independently, `transport/fence.go:151-156` now propagates the current transport's failed barrier before `f.released=true` and real-TUN Close (`:164-168`). A failed internal transport therefore remains reachable and cannot acquire a final-release receipt through the core shortcut. Successful construction-error retirement still returns nil plus the construction error, preserving the accepted normal reclamation path. No changes to gate admission, epoch ordering, original cleanup identity, registry capacity, FD handoff or P2 cleanup appear in this focused patch.

The unchanged reviewer counterexample now passes (`/tmp/nova-native-fix2-review-overlay-green.log`), and the committed equivalent plus direct transport regression check exact failed handle identity, error preservation and retained lifetime ownership. Related focused race logs pass (`/tmp/nova-native-fix2-bridge-green.log`, `/tmp/nova-native-fix2-transport-green.log`). No new correction-related finding was identified.

**Current scoped verdict: P1 ADDRESSED; P2 remains ADDRESSED.** The reviewed native foundation has no remaining finding from this review, subject to root's pending integrated app/test compilation. This is scoped source/spec/quality acceptance of the reviewed foundation and fixes; service/runtime/API37 packet proof and C1 remain unresolved. No release approval.
