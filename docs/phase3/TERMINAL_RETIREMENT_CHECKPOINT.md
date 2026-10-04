# Phase3 terminal retirement correction (2026-10-03)

**PR59 OPEN/DRAFT/unmerged. Phase3 NO-GO. Full Task3 and product exit gates remain PENDING.**

Parent `8d51f5af807e60e6e84042c561da9fc17a802628`, tree `4013f9ef5a4f342035ebe330ba3e2f16a66280b3`.

## Observed source regression and correction

Push497/PR498 at95e24e8 and PR500 at8d51f5a failed the existing ScopedConnectionTest.normalInputDisposalDispatchesTransportWithoutExternalRevocation assertion at line23 (746 tests/1 failure, complete JVM step17). They did not reach Android instrumentation. No job was retried and the assertion was not weakened. Original complete decoded failed logs, exact IDs and hashes are retained in evidence/terminal-retirement/.

The original per-reservation accounting acknowledgement occurs after actual native invocation and owning accounting publication hooks return, but before the child sets its final groupReturned marker and its physical execution slot returns. Normal guard close waited that earlier acknowledgement, allowing the immediate caller to observe an occupied original slot. This is a real source synchronization gap; no premature native return, double native close, global process admission success or root cause for487 is inferred.

A separate terminal acknowledgement is preallocated in the exact original reservation and group. Each independently funded child signals it only after its final marker and physical slot return. ScopedIoGuard normal close now waits that original terminal acknowledgement; it never waits unrelated live resources or an entire authority. The original owning admission acknowledgement ordering remains intact. Native or accounting failures remain charged and cannot signal successful terminal completion. Unexpected terminal bookkeeping failure also closes process retirement admission. No native await, callback or extra cleanup worker was added under an authority/controller gate.

The deterministic regression holds the actual ticket gate after the owning publication hook returns. It exposes the prior early-close result while its physical slot remains occupied:2 tests/1 failure on the prior implementation, then2/2 passing after correction. Initial fixture compilation failure was tooling, not a behavioral RED. The second case proves an unrelated live original does not block this normal close. The complete targeted set passed28/28. Independent read-only review found no unresolved Critical/Important findings in this scoped correction; a minor comment clarification is queued with the next manifest integration, without changing these frozen source hashes. This is not full Task3/CB acceptance.

## Measured verification

Fresh frozen local source/workflow/build inputs passed748 debug JVM and755 Phase0 JVM, zero failures/errors/skips; both lint variants; all four application/instrumentation APK assemblies. Static30 design contracts,5 vectors,14 SDK-backed backup mutation tests/no skips, immutable fixtures and identical Android copies, Browser helper, secrets/source backup/VPN/model checks passed. Backup exclusion and retired-model checks passed against both actual packaged application APKs. Logs, exact input hashes, complete suite XML manifests, behavioral RED/GREEN XML and development APK identities are in evidence/terminal-retirement/. Development signing and instrumentation assembly do not satisfy permanent signer or physical/native execution gates. Exact new-head push and independent PR CI remain required.

At parent8d51f5a push499 independently completed SUCCESS with all27 job steps and199 actual API36 Google ATD x86_64 Android cases PASSED, including all12 download ownership and2 real encrypted-import integration cases. Its independent PR500 FAILED as above; this parent is not fully CI verified. At7a75c8e push495/PR496 both independently succeeded, with179 actual Android cases each. Raw failure history is preserved even when later source succeeds.

Original487 attempt1 failed :app:packageDebug with PackageAndroidArtifact$IncrementalSplitterRunnable, but the available log exposed no underlying cause. Root cause remains UNKNOWN; same-source retry487 and independentPR488 succeeded at a8f6467. See RECOVERY_2026_10_03.md. No later timing finding is retroactively attributed to that packaging failure.

## Remaining implementation and gates

The connection/input complete manifest and exact-stamped input adoption are being integrated next. Current parent consumers reserve transport and stream separately, and the encrypted-import forwarding wrapper adds another release worker that waits downstream retirement. Those transitional paths do not satisfy full CB01–CB12. Video/Media3/AI transports, copied buffers/readers/player, presentation/Jobs, controller/exact-attempt cleanup, already-created raw APIs and synchronous drain remain pending. Hidden schema2, authenticated Primary source/catalog/holds, paired Copy/Move receipts/restart/restore, concealed UI and final CI/security/signer/physical-owner acceptance also remain pending. All63 product gates remain Pending/NO-GO. No merge, owner-data migration or later phase was performed.
