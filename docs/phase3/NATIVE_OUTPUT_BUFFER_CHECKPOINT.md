# Phase3 original Native sample buffer prerequisite

Status: DRAFT / NO-GO. No production Native consumer is wired. This scoped helper does not complete the DIRECT validation producer, encrypted anonymous staging, runtime migration ledger or any product exit gate.

The bounded original sample array is a distinct supported256KiB action; the existing64KiB transfer buffer limit remains unchanged. The exact operation, reservation and preclaimed child construction are verified before the final array allocation. Native use requires that exact child actual identity, a completed child factory, an unsealed child phase and a nonretiring original. Checks run outside both reader and reservation gates. Enclosing constructor validation remains supported after the child attaches.

One active synchronous callback is claimed under the private reader gate. The callback runs outside it, allowing framework readAt callbacks to enter independent readers. Overlapping/reentrant use rejects immediately. On all exits activity clears/notifies; close rejects its own active invocation, seals local use, waits outside application locks for the actual callback to return, then wipes the entire original. Interrupted or failed wipe throws and remains charged. Native unblockers must have independent original actions and must not wait for this buffer's quiescence.

Consumers may not retain the array/backing views, perform asynchronous Native retention or await their own original retirement from the callback. Int-only return is not downstream publication authority; consumers must recheck the exact operation at publication. No JNI or OEM behavior is claimed from Java scheduling tests.

## Measured development history

The deliberately gate-held unpublished prototype ran9 tests/4 failures (cross-thread gate entry, immediate overlap denial, reentry and self-close). This is a prototype failure, not proof of a prior production deadlock. The corrected active-use prototype passed9, then10 including funded interrupted retirement. Independent review then found an Important normal-owner retirement gap while the release worker was delayed. A new actual delayed-worker regression reproduced it:11 tests/1 failure.

The revised exact-operation/original/child binding passes14 component tests. Added cases deny new use during delayed normal/phase cleanup, deny returning a callback result after normal owner retirement, and reject a foreign operation guard before payload acquisition. Existing independently dispatched unblocker, physical callback return, whole-original acknowledgement, overlap, exception, revocation, size, reentry and self-close negatives remain. Source preimages, hashes and raw standalone logs are in evidence/native-output. These executions compile the five actual source files against Android-module classes with module name app_debug; they are JVM component evidence.

Actual Android-module targeted suite passed118/118, zero failures/errors/skips. Full debug/Phase0 JVM, lint and four APK builds are running against473 frozen source/workflow/build/script inputs. Independent read-only exact-source review reports no unresolved Critical/Important/Minor in this scoped helper; it confirms the original/phase retirement correction and no Native callback under the reader/reservation/authority gates. The reviewer ran no builds/tests and made no edits. Results for a parent/source or component never substitute for current-head CI, actual JNI tests or final security/owner gates.

Original487 attempt1 remains FAIL with unknown packageDebug cause; its same-source retry and488 success remain preserved separately. No merge, later phase or owner-data migration.

## Completed exact19d source verification

All473 frozen source/workflow/build/script inputs remained unchanged through serialized verification. Actual Android-module118 targeted,862 full debug JVM and869 full Phase0 JVM tests passed with zero failures/errors/skips. Both lint variants have zeroError/Fatal; debug57 issues and Phase074 are retained, including15/10 additional GradleDependency advisories compared with the parent's42/64 counts. Dependency declarations were unchanged; no advisory was suppressed. Both raw lint XMLs are preserved. All four APK assemblies passed. Actual Phase0 log contains BUILD SUCCESSFUL in5m45s,50tasks49executed1uptodate; instrumentation assembly succeeded16s.

All14 SDK-configured backup mutations, contracts30, vectors, identical frozen corpus/Android fixtures, Browser helpers, no-secret/VPN/weights source checks and actual backup/model policies of both app APKs passed. Full JUnit XML records, raw commands, hashes, summaries and original input list are in evidence/native-output-complete. These results apply to19d with473 inputs, before the subsequent JNI fixture was added. Exact19d push527/PR528 remain running at this observation; no Android runtime PASS is inferred. Development APK signing does not satisfy permanent-signer or owner acceptance.

Exact19d push527 and PR528 subsequently both completed all27 steps and240 actual Android PASSED cases. The PR merge65e27eddcf01cad1735caab7f065f8f2e1647330 has exact source treec0992628300f64d50da5a2e0f49d9acb89f00a75, parents unchanged main93ed56fb plus19d3879e. Evidence is retained in evidence/native-use. This supersedes the preceding pending observation for19d only.
