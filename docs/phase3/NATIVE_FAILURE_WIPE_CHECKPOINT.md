# Native original output failure clearing

**DRAFT / NO-GO. New Native case execution PENDING.** This scoped correction clears the original owned output array when an admitted synchronous callback or its result checks fail. It does not enable production DIRECT/staging or close a full runtime/product gate.

Previously, `OwnedNativeOutputBuffer.useBytes` released its active borrower after failure while retaining bytes until a later funded disposal. Four new JVM cases distinguish failed output from successful output, including a paused funded wipe worker. The actual initial and strengthened baseline runs each executed18 cases with3 failures/0 errors/0 skips: byte9 remained where zero was required after callback IOException, normal-retirement result denial and revoked-result denial. Their exact source preimages, failed XML/logs and hashes are retained. A candidate-result marker ensures the latter two failures follow callback return rather than an unrelated callback error.

The correction catches Throwable only after this borrow's exclusive admission. After the synchronous callback unwinds, it fills the complete original array before finally clearing activeThread/notifying disposal. An admission failure from another concurrent/reentrant borrower never reaches this catch and cannot clear live Native data. Actual Native/source unblockers, quiescence, original ownership, slot accounting and retirement acknowledgement remain unchanged; this array fill is not a release or acknowledgement. The original callback Throwable is preserved. Successful callbacks retain their bytes until actual disposal.

The final component run passes18/18 with no failures/errors/skips. It covers IOException and AssertionError, exact failure identity, immediate full clearing, paused normal/revoked cleanup, retained authentication denial/key wiping, successful data retention, and intact active canaries after rejected concurrent/reentrant use. All14 existing component cases remain. Independent read-only review found no remaining scoped Critical/Important/Minor findings after adding those canaries.

An additional Android case performs a real `MediaExtractor.readSampleData` JNI write through a view into the original256KiB array. It requires a positive bounded count, changed prefix and preserved canary tail, then injects the exact Java IOException after actual Native return. It verifies the real-write marker, original exception identity and whole-array zeros while the guard is live, Native disposal has not run and owner retirement is still incomplete. Only then does it retire the owner and require actual Native release/terminal completion. This is a Java failure following a real JNI write, not an asserted error from the Native API. Source review and APK compilation pass; actual execution must occur at the new exact remote head (expected246 complete Android PASSED cases).

## Exact candidate verification

All477 recorded source/app/workflow inputs match the candidate and remained unchanged throughout the sequential verifier. Only the production helper, its JVM regression file and its Android regression file differ from parent63da8bb in those inputs.

| Actual check | Result |
| --- | --- |
| Scoped component |18 tests,0 failures/errors/skips |
| Broader owned/reservation/capacity targeted suite |140 tests,0 failures/errors/skips |
| Complete debug JVM |884 tests,0 failures/errors/skips |
| Complete Phase0 JVM |891 tests,0 failures/errors/skips |
| Debug / Phase0 lint |0 Error/Fatal;53+4 and70+4 Warning/Information issues respectively |
| Both app and both instrumentation APK assemblies |PASS; hashes retained |

The verifier, raw logs, failure/green preimages, full-suite XML attributes/hashes, relevant XML, lint XML and actual development APK hashes are in `evidence/native-failure-wipe`. All14 fresh backup mutation checks passed with ANDROID_HOME/JDK/SDK correctly configured. Source/merged/actual APK backup exclusion and model/runtime policy checks passed for both freshly assembled app APKs, as recorded in `fresh-package-static-results.json` and completed logs. Earlier unchanged design/vector/corpus/Browser/VPN proofs retain their source-specific qualifications and are not relabeled as new Native execution. New-source CI remains required; local APK assembly and development signing do not substitute for device/permanent-signer/owner acceptance.

## Preceding cipher and APK policy evidence

Exact parent63da8bb PR536 independently passed all27 steps and245 Android cases, including both public-vector/reset probes. Its actual checkout merge373e9df805bbe7f364288a9751e9b03c043788ce has exact source treef39e3e50b04741b19da2d66a03c6b7cb87af1df0 and independently verified parents main93ed56fb and63da8bb. Push535 was still running at this observation and is not inferred successful from536. `preceding-cipher-ci.json` preserves source/run/job/checkouts/all PASSED cases and raw merge/run metadata.

A direct bytecode audit of the preceding development app APK SHA2561a9d2aa71cb50efb747430b9417e7fe0ed084a86a994c6d4082ec417c000810b inspected all13 DEX entries,75825196 DEX bytes. It found no direct named invocation of `CryptoServicesRegistrar.setServicesConstraints`; the two name occurrences are its declaration/disassembly header. DEX and disassembly hashes/context are retained. An initial strictUTF8 decoder failure made that first scan incomplete; its record is preserved. The completed scan searches exact ASCII target bytes and renders context using backslashreplace, preserving unrelated non-UTF8 output rather than silently interpreting it as valid Unicode. This is not proof against reflective, dynamically constructed or native policy mutation, and does not close the blocking supported-process policy invariant. It predates this helper correction and is not represented as a scan of its new APK.

All63 product gates, complete Task3/CB integration and Tasks4–8 remain pending. Original487 attempt1 remains preserved as a `:app:packageDebug` FAILURE with unknown cause; same-source487 retry/488 success and later511 heap diagnostics do not erase or explain it. PR59 stays DRAFT/unmerged. No GO, owner-data migration or later phase.
