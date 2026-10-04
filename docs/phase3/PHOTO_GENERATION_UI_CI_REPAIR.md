# Phase3 Photo Generate quota fixture CI repair

**PR59 DRAFT/unmerged; Phase3 NO-GO.** New worktree begins at independently fetched published0f6df5464d291ef04383e0e6ea1f3a0c7623814b/treec63075fefdf7774b21795f6579b21a23bab5ff3e. No unpushed earlier state is assumed.

## Observed failure and exact source

Parent0b2f8c1f0129d394d9b79f4307ac1f396cb137dd push567/run37165100568/job111326295461 and PR568/run37165102532/job111326301726 both completed FAILURE in connectedDebugAndroidTest. Both executed493 cases:492 decoded PASSED and1FAILED. All16 new input-ownership ART cases and all three existing AiEditorFlow tests passed. The sole new quota case timed out after10000ms at AiEditorFlowTest.kt144 BEFORE any quota holds, Generate click or provider assertion executed. No execution of those later assertions is claimed from these failed runs.

Both full raw logs, API run/job results, decoded names/failure traces and exact checkout proof are preserved under evidence/photo-ci-repair. PR merge1974b892c141bdbc06d65ee0080dc1ed3b96d758 independently has exact tree813804c8743d1eed0ce3932b9fd9eb8c431b4846 and ordered main93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10/source0b2 parents. This is an independently repeated fixture failure, not a transient packaging issue.

## Source diagnosis and correction

PhotoEditor's initial tool is Crop. Its preview selects CropCanvas, whose Image intentionally has null contentDescription. Only the ordinary Image branch exposes Photo preview. This fixture's sole GENERATIVE_EDIT capability uses that ordinary branch after entering AI Edit. Existing fold-sized tests likewise enter Adjust before the same preview predicate.

Move the existing AI Edit click before the unchanged rendered-preview wait. The timeout remains10000ms. All two quota-denial/no-provider/enabled/error assertions and successful capacity-return/provider-request1 assertions remain verbatim. Existing consent/provenance/background tests and all production/JVM/security code remain unchanged. No retry, skip, lowered quota or removed assertion is used to obtain green CI.

Whole frozen543-input comparison proves ONLY AiEditorFlowTest.kt action/wait order changed from published0f6. The published encoder checkpoint's567 targeted/1135 debug/1142 Phase0 JVM passes continue to match every main/host/build/workflow/scripts/Gradle input; they are preceding-source evidence, not a claimed full-suite rerun at this final whole543 revision. Final lints, four APK assemblies, static and packaged checks are separately recorded after execution. Read-only independent scoped review:0 Critical/Important/Minor; the reviewer independently verified source/diff/hash, while root decoded both preserved raw logs. Exact new-head Android execution remains pending at publication.

The encoder source0f6 push569/PR570 was already dispatched before parent failures became visible; it contains the same pre-repair quota fixture. Its eventual outcomes must be preserved separately. No success is inferred from an in-progress run or from compiled ART. No product gate or whole runtime-ledger row closes.

Original487 attempt1 remains a genuine :app:packageDebug failure with UNKNOWN underlying cause; same-source retry and488 passed. This diagnosed later UI fixture failure does not explain487. Task3 complete Native/provider/array/result/prompt/controller/whole-fit integration, Tasks4–8 and all63 product gates remain pending. No merge, owner-data migration or later phase.
