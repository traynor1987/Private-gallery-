# Task2c native Android bridge preflight

Read-only review at source `6241b98a`, 2026-11-03. Inspected the new task contract, reviewed transport/gate APIs, pinned WG Android sources/Makefile, app Gradle configuration and existing workflow toolchain setup. No source changes, builds, repeated tests, or subagents. This is not Android integration approval.

**Verdict: feasible after one locking-contract clarification and explicit bridge race/ownership rules. No inherent four-ABI/build blocker found by source inspection.**

## Dispatch blocker: protect callback lock wording

The instruction “never call Java while a native/session publication lock is held” is incompatible with the current transport if “native publication lock” includes `Fence.owner`: Replace holds owner through `Device.Up -> Bind.Open -> protect`, and owner also serializes native current-transport selection/publication. Bind.Open must additionally retain its FD-lifecycle lock throughout protection to prevent close/reuse. Task2c permits only a statistics accessor change in that module.

Concrete correction: prohibit Java calls while any Java session/authentication lock or **bridge registry/security-publication lock** is held, but explicitly allow the reviewed transport configuration and FD-lifecycle locks around the narrowly defined synchronous protector. Require the protector to perform only VpnService.protect, never reenter any bridge/owner/session API, and require Replace to be called outside session monitors. Do not remove the lifecycle lock or move protection after Open merely to satisfy the original broad wording.

## Interface rules to make explicit before implementation

1. **Invalidate during construction:** define whether invalidation targets an exact generation or the whole fence. Atomic lookup/current pointers do not by themselves fence a replacement still being constructed and not yet published. For fence-wide invalidation, use a persistent atomic fence revocation epoch/tombstone: invalidate advances it even with no current transport; Replace captures it before construction and must reject/seal its result if that epoch changes before publication. Alternatively make the native operation explicitly generation-only and state that it supplies no fence-wide construction cancellation receipt, leaving that transaction obligation to the future owner. Test the paused-protector/Invalidate/publication interleaving. Never relabel an old-generation invalidation as proof the pending generation was invalidated.

2. **Failure ownership must remain reachable:** core Replace can fail before touching the old generation (invalid/stale options) or after creating a new internal transport. State that rejected stale/malformed requests preserve an unrelated live current generation; once replacement actually starts, failure leaves admission sealed. Keep bridge fence ownership and any necessary failure/tombstone entry reachable after errors. Do not delete the registry handle or its protector reference while retirement is blocked or failed. Define removal success for a known revoked lease separately from an unknown/foreign lease; sealing does not authorize releasing an arbitrary Java reservation.

3. **Handle versus generation:** distinguish an opaque globally unique positive-long transport handle from its monotonic generation number. Core generation values are per-fence and are not process-unique authority. Check fence ownership on every handle combination. Specify positive-long overflow for handles/epochs/sequences, separate from the transport’s uint64 range.

4. **FD handoff:** choose one precise boundary (for example, Java transfers a detached duplicate at JNI entry; native consumes that duplicate on every result). Close it through one owner on all pre-fence failures; never raw-close an already adopted os.File. Java’s original PFD remains untouched. Since the task supplies trusted name/MTU, an owned unmonitored adapter can avoid the upstream Name ioctl/adoption failure entirely. Adopt a pollable nonblocking descriptor so final Close can cancel the sole reader; do not clear shared FD flags casually on a dup.

## Build and statistics clarifications

- Existing workflows install SDK/build tools but do not explicitly install this NDK revision. The script must obtain the pinned NDK automatically under its documented CI default (with archive verification), or Gradle must provision it without workflow edits. Merely finding an absent `$ANDROID_HOME/ndk/27.2.12479018` and failing is not sufficient for the stated no-workflow-change default.
- Existing release tasks require permanent signing credentials. Verify the native release packaging wiring while preserving that guard; local Java/app/test compilation can use debug. Do not promise a successful local signed release when credentials are unavailable.
- Shipping both SO files is distinct from loading both. Keep the new wrapper unreferenced by current runtime integration and load its library only on explicit owner use; no production initialization should load it while the existing eagerly constructed GoBackend still loads wg-go.
- Pinned peer counters are private; the allowed transport accessor is genuinely needed. Keep statistics out of monitor-safe methods, bound the validated one-peer configuration/output, return only selected numeric fields, and never expose or log IpcGet’s secret-bearing full configuration. ELF LOAD alignment must be inspected on each actual ABI output; source flags alone are not verification.

With the locking wording corrected and these choices recorded, Task2c can proceed without changing accepted gate semantics or claiming C1/runtime completion.


## Follow-up: revised contract accepted for dispatch

Scoped reread of the added callback-lock and fence-epoch paragraphs: the blocking wording conflict is resolved. The reviewed transport owner/FD-lifetime locks are explicitly permitted for the non-reentrant protector, while session/security and bridge registry/publication locks remain prohibited.

The specified epoch ordering closes the pending-replacement gap: if Invalidate reads the old current pointer, its earlier epoch increment must be visible to the replacement’s final check after new-pointer publication; if replacement’s final check wins first, a subsequent Invalidate must see that published current pointer and seal it. Thus a revocation cannot both miss the new pointer and evade the final epoch comparison. The new object is fully constructed but CLOSED before pointer publication, so that publication is compatible with the initialized-before-visible rule. Capturing expectedEpoch inside the caller’s admission transaction, rather than in the worker, prevents queued old work from refreshing its own authority.

Implementation interpretation to preserve: store the captured replacement epoch immutably with the transport; BeginProbe must validate that stored epoch against the supplied/current fence epoch and the actual current transport identity. Epoch/handle exhaustion must seal rather than wrap. Concurrent replacements still need monotonic conditional current-pointer publication (or equivalent safe serialization) so an older completed call cannot overwrite a newer bridge pointer after core Replace returns. No bridge publication/registry lock may be held across transport construction/protection. These are implementation obligations consistent with the revised contract, not requests to change accepted gate semantics.

**Updated verdict: no remaining security-contract blocker identified; Task2c can be dispatched.** The earlier concrete FD, failed-operation ownership, handle-number distinction, build provisioning, release-signing and statistics notes remain implementation guidance. No implementation, builds, tests or Android-runtime claims were made during this preflight.
