# Browser Job caller and public retirement checkpoint (2026-10-03)

**PR59 DRAFT/unmerged. Phase3 NO-GO. Task3, CB01–CB12 and all63 product gates remain incomplete.**

Parent source `5e3a8dd6b4b6604d4b2c54eaf2853fc33e626b22`, tree `3d233c0517df6f90a9f9d58fd747f374ec36f733`. Earlier remote recovery and original487 packaging diagnostics remain in RECOVERY_2026_10_03.md; no old unpushed source was substituted. This is a scoped runtime checkpoint, not Hidden store/transfer acceptance.

## Actual production changes

BrowserV2Session constructs its probe root through the exact Primary owner's preclaimed createOwnedJob factory. Metadata allocations precede native root creation; no-owner neutral sessions create no probe root. Probe children use the exact fork's preclaimed lazy Job before completion-hook installation and start. Creation/hook/start rejection closes the exact fork to dispatch funded cancellation, without an emergency unowned cancellation path. Native completion retains its original acknowledgements. Queued result publication deliberately retains the existing epoch-only publication contract; completing a lease does not mint a new ambient owner.

Independent review found that strict root capacity rejection could escape MainActivity startup. BrowserSessionAdmission and the actual replaceBrowserSession caller now close the exact attempted lease and construct a neutral session on ordinary failure. The caller records the admitted owner, so rejected attempts cannot launch stale-owner cleanup. Fatal Error closes the attempted owner and rethrows without neutral retry. The neutral factory receives no Primary or networking authority.

PR506 failed ProcessRetirementBarrierTest line26, which attempts new authentication after owned.retirement.await. Independently instrumenting the actual ticket monitor reproduced an existing public-signal race: the original final marker/slot return could still be pending. That race is consistent with the CI failure; the CI log does not record its exact in-flight marker state. OwnedResource.retirement now exposes the exact original terminal signal. The earlier internal accounting signal, strict process barriers and original normal-close ordering remain unchanged. This signal concerns only this original; unrelated resources or authority-wide dispatches may still deny admission. No timing sleep, global wait, test weakening or new-auth exception was introduced.

## Regression and independent review

The legacy session constructor failed3 of4 actual component tests (full capacity, closed lease, neutral root). The real caller passthrough failed2 of8 after strengthening the fixture to use nonzero key bytes: capacity rejection escaped, and fatal factory failure left the actual lease key unwiped. Correction passed the eight constructor/caller cases and inherited Job preclaim/ownership cases. The new deterministic public retirement regression holds the actual ticket monitor after accounting returns; earlier source failed1 of3 because public acknowledgement escaped before marker/recycle. Corrected targeted retirement/accounting/manifest/process tests passed27/27, including unchanged process-wide rejection tests.

Independent read-only review closed both Important findings and reported no new scoped Critical/Important issue. It did not run builds, edit source or spawn processes. Its remaining coverage limitation is the actual native WebView probe completion-hook/start failure branch: constructor/caller component tests do not execute that branch. Existing Job hook/failure tests are component evidence, not whole Browser native acceptance. This limitation and broader native/lock/manifest work remain in the runtime ledger.

Component constructor tests compiled the actual session/core code with metadata-only Android Context/ApplicationProvider stubs and cached model dependencies; those runs are not Android execution. Android tests are separately assembled and must execute on exact new-head push/PR CI. Initial component runner errors and an outdated public-retirement runner missing OwnedInput are tooling failures, excluded from behavioral RED. A pre-review full verification was explicitly interrupted when startup availability review found the regression, and is not accepted as green. Its log and interruption record are retained.

## Frozen checkpoint verification

All773 debug JVM and780 Phase0 JVM tests passed with zero failures/errors/skips, both lint variants and all four app/instrumentation APK assemblies passed, and static contracts/vectors/immutable fixtures/Android fixture equality/Browser/security/VPN/model checks and all14 SDK-backed backup mutations passed with no skips. Actual packaged backup exclusion and retired-model checks passed for both built application APKs. Frozen source/workflow/build/scripts inputs matched before each command and after completion. The exact final component run also passed20/20. Source/workflow/build/scripts hashes, commands/logs, full XML manifests, measured RED/GREEN XML and development APK hashes are retained in evidence/browser-job/. Exact new-head remote CI remains pending at checkpoint preparation. Development signing and emulator execution cannot establish permanent-signer/physical-owner gates.

## Accurate CI history

Owned-input source0df2987 now independently passed push503 and PR504, all27 job steps, with202 actual API36 Google ATD Android tests PASSED in each; the17 relevant download/import cases passed in each. PR test merge4f38ab50b03fd817717ca2e841f9fc021046cf9d has treea53217bdba5df43f89873bf750960f2ce5cc8564, exactly the branch tree, with unchanged main93ed56f and0df2987 parents. Retained case lines and merge identity support these exact-source claims.

PR506/run37096982522/job111128798706 at5e3a8dd FAILED the debug JVM suite:772 tests/1 failure, ProcessRetirementBarrierTest line26; Android steps did not execute. No retry masks the failure. Its decoded full job log is retained. Push505 is still running at initial preparation; its eventual conclusion is separate evidence. Earlier497/498/500 failed JVM jobs remain in TERMINAL_RETIREMENT_CHECKPOINT.md; terminal source878 independently passed501/502 with199 Android cases each. Original487 attempt1 :app:packageDebug remains FAILURE with underlying cause UNKNOWN; same-source retry487 and independentPR488 SUCCESS at a8f6467 do not erase or explain it.

## Remaining implementation

The narrow session/probe Job postcreation registrations are removed. MainActivity's composite UI cleanup Job, native WebViews, provider prompts, preview/editor/player caches, all actual stream/payload/backup/video/AI buffers and children, controller/enrollment exact-attempt/lock boundaries, raw API/synchronous-drain removal and global identity/factory-capability auditing still require Task3 integration. RUNTIME_MIGRATION_LEDGER.md records the concrete functions and required adaptations. Eight new browser Android cases are added (expected complete total210) but exact new-head device execution remains pending until verified.

Tasks4–8 remain pending: selected Hidden schema2/media-preserving credential mutations; Primary pinned source/catalog/hold ownership; paired Copy/Move/restart/receipts/restricted restoration and complete negative/fault matrices; concealed scoped UI; exact final CI/security/signer/physical-owner gates. No merge, owner-data migration or later phase.
