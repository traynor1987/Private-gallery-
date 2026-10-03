# Bounded ephemeral staging cipher feasibility

**DRAFT / NO-GO. Production DIRECT integration remains disabled. New Android probe execution PENDING.** This checkpoint adds only a public-vector Android test and reproducible JVM/binary review evidence. It adds no production cipher, dependency, global policy mutation, staging file or key-domain bridge.

## Independently verified preceding checkpoints

| Exact source | Push / PR | Actual Android cases |
| --- | --- | --- |
| `4a4240d672d7ebf8ad4ed35070c98eef125177c0` | 531 / 532 SUCCESS | 242 PASSED each, including both real Native output/use cases |
| `193fcad54ca5e567f2f5cebb593e81f6eaeb72c6` | 533 / 534 SUCCESS | 243 PASSED each, including actual sample writing into the original array |

PR merge `aaed0b87e29221344d4e5d53040f88ba077df322` has the exact4a tree `6d728046e8c55047271dd65ce9a749f9038545e4`; merge `271d7f3f26f6db4211b670a7e64837a5d125fe4d` has the exact193 tree `4ac4fbd30e4c0e685a4b66e00950c903395248a0`. Their independently read parents are unchanged main `93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10` and their respective source commits. `evidence/bounded-cipher-feasibility/parent-ci.json` retains raw run/merge metadata, checkout lines, complete PASSED case lines and build summaries. These results supersede publication-time execution-pending observations in the two Native checkpoint records; they do not close complete production Native manifests or product gates.

## Existing dependency and exact binary audit

The existing app dependency is `org.bouncycastle:bcprov-jdk18on:1.79`, JAR SHA256 `0d81ecc3124536b539bce9aa3fe9621b7f84c9cee371b635a5b31c78b79ab1da`. The experiment uses its standard lightweight ChaCha20-Poly1305 implementation for a possible fresh-key, anonymous encrypted staging design. It does not change F1, Primary encryption or any durable format. That possible design is not enabled or accepted here.

`class-inventory.json` records exact base and available multi-release class hashes. Explicit extracted class-file disassembly covers ChaCha20Poly1305, ChaCha7539Engine, Salsa20Engine, ChaChaEngine/chachaCore, Poly1305, KeyParameter, ParametersWithIV, used Pack/Arrays helpers, Integers.rotateLeft, DefaultServiceProperties and CryptoServicesRegistrar with its inner classes. Inspected primitive block operations introduce no per-block private array in these paths. This is binary review of those paths, not a complete heap, Android, JIT/register/cache, GC-copy, side-channel or OEM erasure proof.

The initial `javap --multi-release base` invocation did not resolve ChaChaEngine. Its error is retained in the harness preimage; it is not evidence of a crypto failure or an Android class mismatch. Subsequent explicit class-file disassembly succeeded. The initial Java probe also rejected before key initialization because its empty-schema parser constructed the set containing an empty string. That harness error and exact source are retained separately from the measured crypto results.

## Actual measured host results

The final `BcChunkProbe.java` ran on Corretto17.0.20.12.1 both normally and with `-Djdk.util.jar.enableMultiRelease=false`. Both completed nine feasibility groups:

1. RFC8439 section2.8.2 encryption matches the complete expected ciphertext/tag; the actual library-created one-time32-byte MAC key copy is zero after use.
2. Authenticated opening matches the public plaintext, and its entire unused output tail is zero.
3. Each of130 independently altered ciphertext/tag byte positions is rejected; the complete output and captured persistent state are wiped, and failed-instance reuse is denied.
4. Injected failure after MAC derivation still wipes the exact one-time copied key and persistent state.
5. Following100 warm-up calls,64 complete64KiB encryption calls allocate16896 bytes total in the measured thread on both configurations, below the deliberately conservative8192-byte/call bound. This excludes initialization/warm-up, other threads and Android; it is not a worst-case allocation or universal memory guarantee.
6. A65537-byte plaintext request is denied before private key initialization.
7. Injected first-field cleanup-observer failure remains a thrown failure while all captured known arrays and MAC scalar wipes are attempted and read back zero.
8. Cross-thread/expired key access and inherited `reverse()` are denied.
9. Key/output identity alias is denied before output clearing or key initialization, with the original public key unchanged.

An ordinary unmodified library `reset()` experiment exits1 on the exact assertion that its copied key is retained. This is the expected baseline observation; it is not a production regression or a red test proving the prototype closes ownership. The Android baseline test asserts that same observation rather than pretending reset disposes a key.

The prototype captures exact known array identities, types and sizes before borrowing its public vector key; it checks unchanged identities and the observed default policy before initialization. Its private borrowed key is thread/lifetime bound, rejects copying through reverse, and is unbound in finally. The custom MAC only receives the exact plain library-created KeyParameter, captures its actual32-byte copy before initialization, and wipes it in finally. Cleanup never invokes reset as disposal. It attempts all known state wipes even if one observer/reflective operation fails and reports failure. These properties are measured/tested only within this public-vector prototype, without a funded production original or native-use coordinator.

## Blocking process-global policy requirement

Exact Registrar bytecode loads an AtomicReference then invokes the current constraints callback. Its public setter changes that reference without a shared adapter lock. DefaultServiceProperties retains the key-bearing ParametersWithIV. The observed default Registrar$1 callback is stateless and return-only. Observing that default before/after a call does not prevent a concurrent setter installing a retaining callback and restoring the default before the second observation. Synchronizing on the registrar, its AtomicReference or an adapter gate cannot constrain an uncooperative setter. The probe neither mutates nor claims to freeze this global policy.

A future production adapter therefore requires an explicit supported-process invariant that direct/reflective constraints mutations are absent or all participate in one audited gate, with complete application/dependency/packaged-code closure and rejection of an unexpected preexisting policy. Under an arbitrary concurrent-setter threat, this requirement cannot be guaranteed through the unchanged public BC API. That boundary remains unresolved here; a local before/after check is not its fix.

## New Android test and remaining acceptance

`Phase3BoundedCipherProbeTest.java` uses only public RFC data. It derives the fixed-buffer/copy-disposal cases from the host prototype, adds the separately asserted reset observation and runs164 full64KiB calls. It deliberately omits JVM ThreadMXBean allocation measurement; ART allocation bounds are not claimed. It uses a portable hex parser. Its final reviewed actual Android instrumentation APK assembly passed in31s. After compilation all477 recorded source/app/workflow inputs match the publication candidate and build inputs; only this new Android file differs from exact193. Production, JVM tests, build configuration, workflows and frozen media assets are unchanged. Parent JVM/static/package proofs are retained with their exact qualifications; they are not rerun or relabeled as this new test's runtime result. New-source CI must independently execute both new cases (expected245 full Android PASSED cases).

Independent read-only review identified one Minor false-positive path: the injected post-derivation check accepted any IllegalStateException even if no MAC clone was acquired. A separately compiled mutation injecting a different pre-MAC failure demonstrably passed the earlier nine-group probe. The final check requires the exact injected message and a nonnull32-byte actual clone; the identical wrong-stage mutation now fails on its stage assertion. Exact preimages and before/after logs are retained. Bounded borrow-test join and nonnegative/nondecreasing host allocation counters were also added. Final unmutated normalMR/base probes pass nine groups. Independent review remains scoped to this test/evidence. Production still requires original pre-creation ownership and exact whole-manifest fit; fresh ephemeral key generation and exclusive quiescent cleanup; distinct private bounded workspaces; immutable checked nonce/index/length/total framing without nonce retries; authentication before any caller plaintext publication; whole destination/private-output wipe on failure or revocation; anonymous encrypted descriptor construction and independently dispatchable closure; complete Native formats/CSD/duration parity; Android class/provider and supported-device proofs. No named plaintext spool, arbitrary Primary/F1 key parameter or production fallback is introduced.

All63 product gates, complete Task3 integration and Tasks4–8 remain pending. Original487 attempt1 is preserved as an unknown-cause `:app:packageDebug` failure; its same-source retry/488 success do not erase or diagnose it. Later511's explicit heap error applies to511 only. PR59 remains DRAFT/unmerged, with no GO, owner-data migration or later phase.

## Reproduce the host experiment

Use the exact hashed1.79 JAR and JDK17, a separate output directory, and the retained source in `evidence/bounded-cipher-feasibility/BcChunkProbe.java`:

```bash
javac -cp "$BC_JAR" -d "$PROBE_OUT" docs/phase3/evidence/bounded-cipher-feasibility/BcChunkProbe.java
java -cp "$BC_JAR:$PROBE_OUT" BcChunkProbe baseline
# Expected exit1, specifically: baseline reset retains its copied key.
java -cp "$BC_JAR:$PROBE_OUT" BcChunkProbe
java -Djdk.util.jar.enableMultiRelease=false -cp "$BC_JAR:$PROBE_OUT" BcChunkProbe
```

The reference vector is the public numerical example in [RFC8439 section2.8.2](https://www.rfc-editor.org/rfc/rfc8439#section-2.8.2). Artifact/source/log hashes and exact bytecode records are beside the prototype.
