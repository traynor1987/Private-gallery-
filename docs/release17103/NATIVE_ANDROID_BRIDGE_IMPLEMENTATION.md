# Task2c Android native bridge implementation report

Scoped foundation implementation in `/tmp/novaos-code55`. **Not service/runtime integration, C1 acceptance, Android packet-gate approval, or production lifecycle acceptance.** Root requested a scope freeze and will perform the single integrated app/test compile in its Tor-off shipping worktree after independent review. No subagents, commit, push, CI, signing/version/release-approval/workflow changes were made by this implementer.

## Delivered source

- New `native/continuousvpn/android/` module: atomic owner registry, epoch/current-generation admission boundary, owned nonblocking TUN FD adapter, exact bounded single-peer config construction, Go/C JNI, host tests, toolchain/ELF verifiers and README.
- `native/continuousvpn/transport/statistics.go` and tests: allowed narrow read-only numeric statistics accessor. Existing gate/transport semantics untouched.
- `scripts/build_continuous_vpn_native.sh`: pinned source build for arm64-v8a, armeabi-v7a, x86, x86_64; generated outputs under app/build only.
- Package-private `core/vpn/continuity/NativeVpnFence.java`, unreferenced by active production runtime.
- `app/build.gradle.kts`: pinned NDK and generated JNI task/source wiring for both variants, using AGP SDK NDK provisioning. Existing release-credentials guard unchanged.

## API and authority contract

Positive signed longs are opaque process-unique handles in the one JNI owner registry; transport handle and per-fence generation are different values. 0 is success; -1 invalid; -2 denied; -3 concurrent control busy; -4 failure; -5 exhaustion. Handle/epoch counters never wrap. Native sequence conversion checks signed-long bounds (reviewed gate starts at 1 and seals at 2).

Java owner-private methods:

- `create(originalPfd, mtu, trustedName)` -> fence handle.
- `epoch(fence)` -> captured fence epoch; capture inside the future authenticated replacement-admission transaction, never refresh in a worker.
- `replace(fence, generation, expectedEpoch, privateKeyHex, peerKeyHex, presharedKeyHex, numericEndpoint, commaSeparatedAllowedPrefixes, keepalive, listenPort, Protector)` -> transport handle or generic negative code.
- `beginProbe(fence, transport, epoch)`.
- `register(fence, transport, epoch, protocol, numericSourceAddrPort, numericDestinationAddrPort)` -> opaque lease.
- `remove(fence, transport, epoch, lease)` -> success only for the known matching lease after removal or permanently sealed-generation proof. This is not a drain receipt.
- `sequence(fence, transport, epoch)` and `publish(fence, transport, epoch, expectedSequence)`.
- `invalidate(fence)` -> advanced fence epoch; `invalidateTransport(fence, transport)` -> generation-specific revocation sequence.
- `retire(fence, transport)` -> blocking barrier.
- `retireCurrent(fence, originalRequestedGeneration, originalReplacementEpoch)` -> cleanup after negative Replace without receiving its internal sealed handle. Both original identities are checked under control CAS before touching current; stale cleanup cannot revoke a newer transport.
- `release(fence, allSensitiveChildrenDead)` -> final lifetime release, requiring an explicit trusted-owner external proof assertion. Native cannot infer child death.
- `statistics(fence, transport, epoch)` -> exactly `[status, handshakeSeconds, handshakeNanos, rxBytes, txBytes]`.

Only epoch/sequence/publish/invalidate/invalidateTransport are monitor-safe. They use immutable atomic snapshot lookup and atomic current pointers, without registry/configuration/owner mutex, Java callback, I/O or close. Replace/retire/release and all other methods may block and are prohibited inside authentication/session/security monitors. Nonwaiting per-fence busy CAS rejects simultaneous control operations. Invalidation stays outside it. Replacement publishes the initialized CLOSED pointer before its final immutable-epoch comparison; invalidation advances fence epoch before loading current.

Configuration is exact one peer: 64-character key hex, optional empty preshared key, numeric endpoint, 1–16 canonical prefixes, keepalive/listenPort 0–65535. The generic tuple API makes no kernel reservation/provenance claim and does not depend on the rejected ProbeReservations helper.

## FD and callback ownership

Java keeps the original protective PFD and a temporary Java-owned duplicate until native invocation returns (try-with-resources). At JNI entry C creates its own F_DUPFD_CLOEXEC duplicate. Pre-Go errors close only that native duplicate. Go consumes the transferred duplicate on every result: raw close before adoption, os.File alone after adoption. No upstream ioctl Name failure/finalizer ambiguity. An already nonblocking FD is required and shared flags are never altered. One lifetime reader, retirement never closes lifetime TUN, final release alone closes it.

Protector is a JNI global reference held through synchronous actual-outer-FD protection and corresponding retirement. Threads attach/detach correctly; Java exceptions are cleared and protection denied. Only trusted VpnService.protect may run in the callback; it must never reenter the bridge/owner/session. Reviewed native configuration and bind FD-lifetime locks intentionally remain held there. The bridge holds no registry/publication mutex over the callback. A failed lifetime can make core Replace reject before retiring old; a dedicated RED regression exposed premature callback release and the implementation now requires the old core retirement barrier. Final release checks retained failed generations, not only current. JVM-shutdown attach failure intentionally retains C/global-reference storage rather than unsafe free.

## Verification executed

RED before implementation: missing bridge policy API, owned FD adapter, statistics accessor, and ListenPort; later behavioral RED for early core Replace failure disposing an unretired old callback and stale failed-replacement cleanup revoking newer current. Initial failing compile diagnostics were observed, then actual passing suites. Recovery source was reconstructed from tool-call text after external workspace pruning deleted /workspace sources/.git/tools; that was not a repository cleanup by this agent.

Final host commands (all passed):

```
cd /tmp/novaos-code55/native/continuousvpn/android
/tmp/nova-go-toolchain/go/bin/go test -race ./...
cd /tmp/novaos-code55/native/continuousvpn/transport
/tmp/nova-go-toolchain/go/bin/go test -race ./...
cd /tmp/novaos-code55/native/continuousvpn/gate
/tmp/nova-go-toolchain/go/bin/go test -race ./...
```

Bridge tests cover stale/cross-owner handles; exact lease ownership; malformed requests preserving unrelated current; pending replacement invalidation before construction/during blocked protect/between pointer publication and final epoch comparison/after success; concurrent publish/invalidation; close-vs-lookup; callback lifetime; failed construction ownership; generation control; signed exhaustion; numeric tuple/protocol validation; FD consumption, finalizer/reused-FD safety, cancellation and shared nonblocking flags; negative-result cleanup and delayed cleanup rejection. They use the actual reviewed transport/WG/gate with fake TUN/bind boundaries.

Final Gradle native build passed (log `/tmp/nova-native-gradle-final.log`, 46s):

```
cd /tmp/novaos-code55
NOVA_GO_ARCHIVE=/tmp/nova-go1.20.3.tar.gz \
NOVA_GO_ROOT=/tmp/nova-go-android-patched/go \
python3 /tmp/nova-gradle-stable-run.py :app:prepareContinuousVpnNative
```

The helper configures normal trusted proxy/TLS and /tmp tools only; no repository proxy configuration or TLS bypass was added. Script hash-checks original Go archive `979694c2c25c735755bf26f4f45e19e64e4811d661dd07b8c010f7a8e18adfca`, checked-in BOOTTIME patch `149ab7d9a1c20dad61fe279a837e0e0c81c6b2f5584614ac4d4cde67008755f0`, every pinned Go distribution file plus rejection of unexpected extras, Go1.20.3, NDK27.2.12479018, and module hashes. Negative checks explicitly rejected wrong NDK revision, bad Go archive and wrong Go version. Shell/Python syntax and `git diff --check` passed.

Actual output ELF verification: each ABI has four LOAD segments, every alignment **0x4000** with 16KB-congruent offsets, SONAME **libnova-wg.so**, all **14** expected `Java_uk_co_traynor_privategallery_core_vpn_continuity_NativeVpnFence_*` methods, and no original GoBackend JNI exports. Native libc ABI baseline21 does not change APK API37 requirements.

| ABI | Bytes | SHA256 |
|---|---:|---|
| arm64-v8a | 4041824 | 72247c28490a77805df08fc8400bb16a19616b89e1cc46b2f322cc542a99a094 |
| armeabi-v7a | 3797376 | 83043130a422f3a28cb72ff6c1d1de7ffa4d27abc4d44c39be15eb7ed9b17eab |
| x86 | 3852320 | d9a5e32639f41f5ed0ee82de46709b721273324334d38691e5b9e913405c4517 |
| x86_64 | 4184128 | da1d35301170d327cfdd510b24f3124d33ced54706f03cf2d6e61024b4ce856c |

Wrapper compiled successfully:

```
/tmp/nova-jdk17/bin/javac -source 17 -target 17 \
-cp /tmp/nova-android-sdk/platforms/android-37.1/android.jar \
-d app/build/nativeWrapperClasses \
app/src/main/java/uk/co/traynor/privategallery/core/vpn/continuity/NativeVpnFence.java
```

Variant dependency dry-run passed with prepareContinuousVpnNative preceding both debug/release JNI/native merge tasks (`/tmp/nova-native-variants.log`):

```
NOVA_GO_ARCHIVE=/tmp/nova-go1.20.3.tar.gz NOVA_GO_ROOT=/tmp/nova-go-android-patched/go \
python3 /tmp/nova-gradle-stable-run.py :app:prepareContinuousVpnNative \
:app:mergeDebugNativeLibs :app:mergeReleaseNativeLibs --dry-run
```

Full integrated app/test-source compilation is **pending root's single combined shipping-worktree run**, per later direction to avoid duplicate full app builds. No APK packaging, release signing, instrumentation, device routing/16KB runtime test or CI dispatch is claimed. Actual NDK missing-install path was not re-downloaded locally; Gradle uses the pinned SDK provider for normal provisioning, while local verification used the prepared NDK.

## Material retained-object limitation for independent review

The registry is append-only. It retains full `instance -> transport.Transport -> WG device / bind / virtual TUN / gate / fence` graphs after retirement, and after final release, not only small immutable ID tombstones. Lease entries remain after removal and keep their instance graph. Closed virtual queues are drained/zeroed by reviewed transport but their allocations/objects stay referenced. Native socket and lifetime descriptors are closed by the normal barriers; successful JNI protector release deletes the global reference/free C storage, while a Go protector wrapper containing its old C uintptr remains reachable and is protected from repeat release by its disposed flag. Blocked/failed retirement deliberately retains callback/global refs. Upstream Device.Close removes peers/shuts workers but does not explicitly wipe its static private identity; retained closed-device graphs therefore also extend secret retention.

There is **no practical configured registry/generation/lease tombstone count cap** (only signed-long exhaustion). Temporary host diagnostic, using an overlay rather than committed production hooks, measured **6,692,736 extra retained heap bytes over 64 retired generations, about 104,574 bytes/generation**, +2,696 heap objects, 66 snapshot entries. Go1.20.3 Linux AMD64, GOMAXPROCS=2, forced GC before/after; fake TUN/bind, actual reviewed device. It is an approximate host workload observation, not an Android bound. The immutable map also copies all retained entries on insertion. At roughly 1,000 similarly sized generations, the observed order is ~100MB retained, with potentially higher costs for other workloads.

Diagnostic command/log:

```
GOMAXPROCS=2 /tmp/nova-go-toolchain/go/bin/go test -vet=off \
-overlay=/tmp/nova-bridge-retention-overlay.json -run TestRetainedHeapDiagnostic -v ./bridge
```

`/tmp/nova-bridge-retention.log` records results. Vet was disabled only for this temporary added-file overlay because Go1.20 vet attempts to open its nonexistent overlay destination; all normal suites ran with normal vet and race checks. This retention limitation is explicitly frozen for independent resource/lifecycle review at root's direction, not described as overall-safe merely because stale authority checks fail closed. No production lifetime acceptance is claimed.

# Fix round 1 of 5 — P1/P2 correction, scoped re-review requested

This section supersedes the earlier append-only-registry limitation and the earlier native binary hashes. No parent probe, service/runtime, Tor, gate admission, signing, version, workflow, commit, push or CI changes were made. The root explicitly authorized the narrow transport owner-bookkeeping change required to stop the lifetime fence from rooting its last retired device. Integration/C1/release acceptance is still not claimed.

## P1: bounded authority metadata and actual-barrier reclamation

- Registry entries now separate immutable numeric identities from atomically loaded immutable heavy payloads. Instances contain only handle/fence-ID/generation/epoch plus an atomic payload pointer. Leases contain numeric fence/transport/generation/epoch plus an atomic lease-payload pointer. Successful retirement detaches the transport/core/protector graph and matching lease payloads only after `Transport.Close()` actually returns success. Concurrent monitor readers hold a local Go reference to the immutable loaded payload; reclamation never races by mutating a formerly immutable core field. Failed/blocked Close retains the heavy payload and protector.
- The bridge current pointer is conditionally cleared only for that same retired instance. The narrow change in `transport.go` clears `Fence.current` only if it still equals that same transport **and** `closeErr == nil`, after pinned device shutdown/wait and gate drain. `gate.Registry.high` remains unchanged, so a forgotten current still cannot reuse an old generation. The existing `f.children` deletion already follows device shutdown/wait and drain; its gating/quiescence order was not changed.
- Exact known removal receipts remain idempotent, but now contain numeric metadata only. `Remove` clears its gate/tuple payload once future admission is removed or independently proven sealed. Successful transport retirement also clears any remaining matching lease payloads. Unknown, cross-owner, wrong-generation/epoch and post-final-release handles still cannot claim receipts.
- Global practical bound: **4,096 total registry entries** counting active owners, transport/lease entries, numeric retired/removal receipts and pending capacity reservations. Replace reserves its slot before any destructive old-generation work. Full capacity returns EXHAUSTED and preserves current transport. Every positive handle is still allocated monotonically and never reused; failed reservations may burn IDs. Immutable map copies are bounded by that entry cap.
- Receipts remain until their fence's successful final release, preserving known-handle semantics rather than silently evicting receipts. Final release atomically clears the core fence/current payload and prunes every owner entry and reservation, restoring metadata capacity. A long-lived owner reaching the cap must handle exhaustion and satisfy external child-death/final-release conditions before obtaining a fresh lifetime. There is no automatic release/fallback.
- A concurrent lease publisher cannot resurrect a released owner: it commits only into an existing reserved slot, rechecks the current payload after publication, and drops an unsuccessful late insertion. Final-release CAS pruning retries against new snapshots.
- A focused failed-final-close test exposed that the underlying fence's already-released shortcut would return nil on a retry after an initial close error. The bridge now retains a sticky failed-release owner, preventing that shortcut from manufacturing a successful receipt. This is failure ownership handling within P1, not a new service behavior.

Direct reachability evidence uses test-only Go finalizers on actual `transport.Transport`, `transport.Fence`, and pinned `device.Device` objects, not production hooks or mock-only object counters. Tests demonstrate retired generations become collectible while the owner registry/lifetime fence remains alive, and released lifetimes become collectible while the registry remains alive. A focused device-finalizer test also proves the actual WG Device becomes collectible while the lifetime TUN stays open. These tests establish reference reclamation, not immediate byte erasure or comprehensive Go/JVM secret zeroization.

The prior temporary 64-generation heap diagnostic was rerun as supplemental observation only: **1,626,536 bytes (~25,414/generation)** delta versus the prior 6,692,736 bytes (~104,574/generation). This aggregate includes transient/runtime/cache effects and is not a measurement of numeric receipt size or an Android upper bound. The meaningful ownership evidence is the explicit payload graph change, 4,096-entry cap, pruning, and successful finalizers for actual transport/device/fence objects. Log: `/tmp/nova-bridge-retention-after-p1.log`.

## P1 regression commands and results

RED evidence:

- `/tmp/nova-bridge-p1-red.log`: original code failed `TestRetiredGenerationReclaimedWithKnownLeaseReceipt` (object remained rooted) and `TestRegistryCapacityRejectedBeforeDestructiveReplace` (replacement not rejected at capacity).
- `/tmp/nova-transport-p1-red.log`: original owner bookkeeping failed `TestSuccessfulRetirementForgetsOnlyCurrentAfterBarrierAndKeepsHighWater` (closed current retained device).
- `/tmp/nova-bridge-p1-release-red.log`: original final-release retry failed `TestFailedLifetimeReleaseRetainsOwnershipAcrossRetries` (retry manufactured success).

Passing final commands:

```
cd /tmp/novaos-code55/native/continuousvpn/android
/tmp/nova-go-toolchain/go/bin/go test -race ./...
# PASS, log /tmp/nova-bridge-p1-final.log

cd /tmp/novaos-code55/native/continuousvpn/transport
/tmp/nova-go-toolchain/go/bin/go test -race ./...
# PASS, log /tmp/nova-transport-p1-final.log

/tmp/nova-go-toolchain/go/bin/go test -race \
-run TestRetiredWireGuardDeviceCollectibleWhileLifetimeFenceRemains ./...
# PASS, focused test added after the full transport run;
# log /tmp/nova-transport-p1-device-gc.log
```

New focused bridge tests:

- `TestRetiredGenerationReclaimedWithKnownLeaseReceipt`
- `TestRepeatedReplaceAndRemoveReclaimHeavyPayloads`
- `TestReleasedLifetimeReclaimedAndMetadataPruned`
- `TestRegistryCapacityRejectedBeforeDestructiveReplace`
- `TestFailedRetirementRetainsHeavyPayloadAndProtector`
- `TestConcurrentLookupRemovalAndReclamation`
- `TestBlockedRetirementRetainsPayloadUntilActualBarrier`
- `TestFailedLifetimeReleaseRetainsOwnershipAcrossRetries`

Existing ownership, epoch, pending replacement, publication, FD, callback, generation and stale-cleanup tests continue to pass under race detection. The existing failed-configuration transport test now checks that the fully retired failed current was forgotten, instead of dereferencing a deliberately retained closed current. The new transport high-water test verifies generations 1/2/3 are rejected after retiring generation3 and that an old Close cannot clear the newer generation4 current. Gate source is untouched; its previously accepted scoped evidence is not superseded by gate edits.

## P2: all-exit C secret-buffer cleanup and optimized non-elision

`jni.c` now initializes both owned secret buffers (`priv[65]`, `preshared[65]`) and has one `cleanup` label/return for every nReplace result, including malformed later arguments and protector-allocation/JNI failures. Cleanup calls a noinline `wipe_secret` that performs volatile byte-zero stores. Those stores are observable C operations and cannot be optimized away after last ordinary use. Go still consumes the protector on every transferred call; C cleanup does not release it twice.

The focused host harness includes the **actual production jni.c** and uses only test-build function instrumentation to count entry into the real wipe helper. A fake JNI string/reference table supplies the external VM boundary. No test hook or conditional is compiled into the Android library. It tests malformed peer length after private-key copy, malformed endpoint after both secrets, malformed routes after both secrets, missing callback, and the transferred Go-call return (including one global-reference deletion), plus bounded zeroing with unchanged surrounding guard bytes.

RED: `/tmp/nova-bridge-p2-red.log` initially failed compilation because the wipe helper did not exist. After introducing the helper but before rerouting old returns, `/tmp/nova-bridge-p2-path-red.log` failed `wipes == 2` on the malformed-later-argument path. GREEN:

```
cd /tmp/novaos-code55
bash native/continuousvpn/android/jni/tests/check_secret_cleanup.sh \
/tmp/nova-android-sdk/ndk/27.2.12479018
# PASS, /tmp/nova-bridge-p2-green.log
```

Optimized final Android disassembly was inspected using pinned NDK `llvm-objdump --disassemble-symbols=wipe_secret` and `--disassemble-symbols=Java_uk_co_traynor_privategallery_core_vpn_continuity_NativeVpnFence_nReplace` on **every actual final ABI library**. Each contains **65 volatile byte-zero store instructions** in wipe_secret and **two calls** to that helper from nReplace cleanup. This independently confirms that the release build's `-O2` did not elide the owned-buffer wipe. Artifacts: `/tmp/nova-fix1-{arm64-v8a,armeabi-v7a,x86,x86_64}-secret-wipe.asm`; summary `/tmp/nova-bridge-p2-optimized.log`. The initial inspection regex omitted LLVM's x86 `calll` mnemonic; correcting that diagnostic regex yielded the two already-present calls. No production code changed for that diagnostic adjustment.

This only wipes the C stack buffers directly owned here. It does **not** claim complete Java String/Go copy/register/historical heap zeroization.

## One coherent amended native build

```
cd /tmp/novaos-code55
NOVA_GO_ARCHIVE=/tmp/nova-go1.20.3.tar.gz \
NOVA_GO_ROOT=/tmp/nova-go-android-patched/go \
python3 /tmp/nova-gradle-stable-run.py :app:prepareContinuousVpnNative
```

PASS in 56s; `/tmp/nova-native-fix1-gradle.log`. One rebuild of the amended production source covered all four ABIs. Each has four LOAD segments aligned **0x4000**, independent SONAME `libnova-wg.so`, all **14** owner JNI methods, and no original GoBackend JNI exports. The added final device-GC test does not alter compiled production bytes.

| ABI | Bytes | Fix-round-1 SHA256 |
|---|---:|---|
| arm64-v8a | 4058376 | 639388504b816ba8f712a47d42f67ab3e3cf6c3c3665015a0359dc8cabc63a8e |
| armeabi-v7a | 3815224 | 4d4b7fd8417b80c71510327833376b88b43c0107b7eb445be9eff7681c7d686f |
| x86 | 3870084 | 364e27db878971281ffa9d8c5b56bd83a881240f12cddcc59d17941c477de692 |
| x86_64 | 4201352 | 75641326ec44db7cec1ddc475d4df1bc5a262a6dc380cbae3ee9b11051a1cb55 |

`git diff --check` passed. Java wrapper and Gradle API signatures are unchanged in this round; the prior wrapper compile/variant dependency evidence remains applicable. Full integrated app/test compile remains root's pending combined worktree run. No instrumentation, actual VPN routing, release signing, CI or C1 acceptance is claimed.

# Fix round 2 of 5 — failed-construction retirement ownership

The round-1 re-review accepted P2 and normal P1 reclamation/bounds, but demonstrated that failed device Up plus failed internal teardown could still lose its partial transport handle and manufacture successful cleanup/lifetime receipts. This round changes only that focused error ownership, its regressions and the corresponding README paragraph. P2, registry reclamation, metadata bounds, gate semantics, JNI/Java API, parent-probe/runtime/Tor work, signing/version/workflows and integration remain unchanged. No commit, push, CI or subagents.

## Correction

`transport.Fence.Replace` now checks the internal `t.closeLocked()` result after construction/configuration/Up fails. If teardown also fails, it returns **the exact partial Transport together with `errors.Join(constructionError, closeError)`**. A nil transport is returned after a newly constructed failed generation only when its retirement succeeded. The existing bridge error path therefore stores the nonnil failed core in its immutable payload and keeps the corresponding protector/containment references; later RetireCurrent/Release cannot reclaim it or return success while Close continues to fail. JNI still returns only its existing generic numeric Failed code, with no secret UAPI/error text exposed.

`transport.Fence.Release` now propagates a remaining `f.current.closeLocked()` error **before** marking the lifetime released, closing the real TUN or proceeding toward a successful receipt. Failed current identity remains reachable. This closes the independent core-release shortcut even for callers below the bridge. The bridge's existing sticky final-TUN-close failure handling remains intact.

Focused source diff against the accepted fix1 snapshot: `/tmp/nova-native-bridge-fix2-review.diff` (five changed files: transport.go, fence.go, transport_test.go, bridge_test.go and README). Gate source, normal reclamation ordering and C wipe implementation are untouched.

## RED → GREEN

The reviewer's unchanged overlay was reproduced RED using the exact requested command:

```
cd /tmp/novaos-code55/native/continuousvpn/android
/tmp/nova-go-toolchain/go/bin/go test \
-overlay=/tmp/nova-review-fix1-counterexample-overlay.json \
-run '^TestReviewFailedConstructionCannotAcknowledgeFailedBarrier$' -count=1 ./bridge
```

`/tmp/nova-native-fix2-red.log` shows both original failures: failed internal barrier acknowledged as 0 with callback releases=1; failed internal barrier manufactured lifetime receipt 0 with TUN closes=1.

The reviewer's final test alone was added to the real bridge_test.go (not its copied base file). A separate direct transport regression, `TestFailedConstructionReturnsExactPartialTransportAndReleasePropagatesBarrier`, was also run RED before implementation. `/tmp/nova-native-fix2-transport-red.log` records discarded exact partial handle/barrier error, nil final-release error, and released lifetime ownership. That test uses the actual reviewed transport/Up/protection path, checks exact returned/current handle identity, `errors.Is` preservation of the failed close, no real-TUN close or released flag, and continued failed Close from the returned partial handle.

After the correction, the **same unchanged reviewer-overlay command passed** (`/tmp/nova-native-fix2-review-overlay-green.log`). Focused related checks passed under race detection:

```
cd /tmp/novaos-code55/native/continuousvpn/android
/tmp/nova-go-toolchain/go/bin/go test -race \
-run 'TestReviewFailedConstructionCannotAcknowledgeFailedBarrier|TestFailedRetirementRetainsHeavyPayloadAndProtector|TestFailedLifetimeReleaseRetainsOwnershipAcrossRetries|TestStaleFailureCleanupCannotRetireNewerReplacement|TestReplacementFailureRetainsFenceAndCallbackOwnership' \
-count=1 ./bridge
# PASS: /tmp/nova-native-fix2-bridge-green.log

cd /tmp/novaos-code55/native/continuousvpn/transport
/tmp/nova-go-toolchain/go/bin/go test -race \
-run 'TestFailedConstructionReturnsExactPartialTransportAndReleasePropagatesBarrier|TestFailedConfigurationRetiresWorkersAndNextGenerationCanStart|TestSuccessfulRetirementForgetsOnlyCurrentAfterBarrierAndKeepsHighWater' \
-count=1 ./...
# PASS: /tmp/nova-native-fix2-transport-green.log
```

These cover the reviewer construction-plus-close failure, already-exposed failed close, failed final TUN close, stale cleanup, successful retirement of an ordinary failed construction, and retained generation high-water behavior. Earlier passing broader suites/P2 C harness/optimized wipe evidence are reused where their source/behavior is unchanged, per root direction; no unrelated repeated test campaign was run. `git diff --check` passed.

## Coherent amended native build

One rebuild of all four ABIs was invoked from the frozen amended production source:

```
cd /tmp/novaos-code55
NOVA_GO_ARCHIVE=/tmp/nova-go1.20.3.tar.gz \
NOVA_GO_ROOT=/tmp/nova-go-android-patched/go \
python3 /tmp/nova-gradle-stable-run.py :app:prepareContinuousVpnNative
```

Final result and binary hashes follow below. Full integrated app/test compilation, Android packet/runtime proof and C1 acceptance remain separate pending work. No release approval is claimed.

**Build passed**, log `/tmp/nova-native-fix2-gradle.log`. All four actual final ELFs have four LOAD segments each aligned 0x4000, verified congruent offsets, independent libnova-wg.so SONAME, all 14 owner JNI exports and no original GoBackend JNI exports. These hashes supersede the earlier round binaries:

| ABI | Bytes | Fix-round-2 SHA256 |
|---|---:|---|
| arm64-v8a | 4059664 | 76389d5b08b624b1a2829459ceb42385c37408076a2ce2e8946ca1ad4b08ea1a |
| armeabi-v7a | 3816724 | 098e79a14eceecdb9a395b12e185d6952c5473f8103cf8178d38141cccbb9acd |
| x86 | 3871248 | 5478595156d8a64347c237d41da377fcf17b73277d3846deeb75fd0e8c18773b |
| x86_64 | 4202640 | 6507b171f7c836dd3b6efdcb80fd1087ac042d77d3687510f06972509c8c28c0 |

## Root integrated verification

After P1/P2 source review closure, root copied all23 hash-verified source files into the Tor-off shipping worktree and ran compileDebugKotlin, compileDebugAndroidTestKotlin and mergeDebugNativeLibs: BUILD SUCCESSFUL in1m38s,35tasks (8executed/27up-to-date). All four separately rebuilt library hashes exactly match fix-round-2 outputs above. No source/runtime backend activation, Android test execution, C1 closure, signing or upgrade is implied.
