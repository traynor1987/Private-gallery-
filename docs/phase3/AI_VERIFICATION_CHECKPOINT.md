# Owned account/model response checkpoint

**DRAFT / NO-GO; new ART execution PENDING.** Production OpenAI model and Replicate account/model verification now consume the exact owned response locally. Image-generation/download paths, upstream credentials/producer Jobs, immutable copies and complete runtime integration remain pending.

PrivateAiHttpTransport overrides consumeVerification with a five-child original declared before connection or Job construction: result0, connection1, input2, workspace3 and inert cancellation Job4. It preserves original REMOTE_AI_EGRESS, HTTPS/URL/redirect/body/status/byte checks. The supported read-only callers issue GET with no body. The new verification bound is2MiB; existing1MiB OpenAI verification and2MiB Replicate verification limits remain. Other OpenAI48MiB and Replicate16MiB image response limits remain unchanged in their separate transitional path.

At most two actual arrays exist: workspace<=2MiB+1 and exact result<=2MiB, each with its own preclaimed slot. Constructor checks bind original operation/child before last allocation. Synchronous Native/consumer callbacks execute outside the buffer gate, exclusive use prevents overlapping borrowers, and actual callback return precedes whole-array disposal. Admitted failures clear the whole original; rejection cannot wipe another active borrower. EOF is checked with one overflow byte. A zero bulk-read count forces bounded scalar progress rather than another allocation. Invalid provider counts and excess length deny publication.

The input/connection/workspace and actual Job retirement phase dispatches all independent actions before waiting. Only the exact result root remains for the synchronous parser; finally waits for its original terminal retirement. The consumers validate account/model fields locally and return Unit; no response array is handed off to an image caller. The public interface default adapter only wipes its returned bytes and establishes no reservation proof; Android's production PrivateAiHttpTransport override is the reviewed owned path.

The initial lazy cancellation coroutine had a real scheduling flaw: its finally could be skipped if cancellation preceded body dispatch. Independent review identified this Important finding. A dedicated test with JAVA_TOOL_OPTIONS=-Dkotlinx.coroutines.io.parallelism=1 reproduced a failed Native-read unblock before any authority close/revoke. The revised cancellation child is an inert Job attached immediately before installing its public completion callback; it has no body or dispatcher dependency. The callback only dispatches exact original retirement, never Native work or an await. Its original adapter separately acknowledges actual cancellation return and completion. The parent is captured from the caller before the fallible IO scope. normalFinish suppresses normal cancellation only after production finishes; postchecks continue to deny cancelled publication. Resolved runtime coroutines1.7.3 is recorded without dependency/API/global-policy mutation.

Evidence preserves the initial compile failure (missing API plus a mistaken test-only guard overload, corrected before execution) separately from behavioral RED, two real caller failures at the legacy raw-execute boundary, an8-case first-run exception-identity failure caused by coroutine recovery copying a standard AssertionError, a subsequent overflow cleanup failure, and the deterministic queued-closer RED. The non-copyable injected exception fixture retains the identity assertion. Failed logs/XML/source preimages remain intact. Final13 ownership plus2 caller plus18 existing API cases passed33/33 under one IO thread. Boundary cases include complete-manifest exhaustion before Native construction, wrong scope/route/method/body/limit, exact-limit acceptance, declared and streamed oversize, zero-count progress, no error-body acquisition, exact result wiping, failed disconnect accounting/fresh-key denial, and input close unblocked independently by disconnect.

## Fresh verification of the candidate

All482 recorded source/app/workflow inputs match the candidate and remained unchanged throughout the sequential verifier.

| Actual check | Result |
| --- | --- |
| One-IO-thread final ownership/caller/API suite |33 tests,0 failures/errors/skips |
| Broader owned/reservation/capacity/API targeted suite |173 tests,0 failures/errors/skips |
| Complete debug JVM |899 tests,0 failures/errors/skips |
| Complete Phase0 JVM |906 tests,0 failures/errors/skips |
| Debug / Phase0 lint |0 Error/Fatal;53+4 and70+4 Warning/Information respectively |
| Both app and both instrumentation APK assemblies |PASS; development hashes retained |
| Configured backup mutations |14 tests PASS; ANDROID_HOME/SDK/JDK set |
| Source/merged/actual APK backup and model/runtime policies |PASS for both fresh app APKs |
| Immutable corpus/fixtures/vectors/design/Browser/secret/VPN checks |PASS; raw logs retained |

Thirteen corresponding Android fixtures compile for both variants, exercising ART Job/guard/cleanup behavior with public fake transport fixtures. New exact-head execution remains pending (expected261 complete Android cases). These fixtures do not execute real TLS, consume owner data, or prove actual provider/OEM acceptance. Independent final read-only review reports no remaining scoped Critical/Important/Minor findings. Evidence, complete-suite XML attributes/hashes, relevant XML, lints, APK hashes, verifier and preimages are under evidence/ai-verification.

Exact preceding121 push537/PR538 independently passed all27 steps and246 Android cases each, including the real JNI-write failure clearing. Exact preceding4c6f758 push539/PR540 independently passed all27 steps and248 cases each, including both anonymous descriptor probes. Their decoded results, exact source checkout/merge tree and main/source parents are retained in the two completed-parent CI records. Descriptor evidence remains API30+/x86_64/public ciphertext feasibility, not production staging or disk wiping.

No full AI ledger row, CB integration, Task3, Task4–8 or any of63 product gates gains full PASS. Upstream setup credentials/Jobs, image handoff, immutable copies, actual TLS/platform behavior and complete measured supported-producer capacity remain unresolved. Original487 attempt1 remains an unexplained packageDebug failure; its same-source retry/488 successes and later511's explicit heap failure do not erase or explain it. PR59 remains draft/unmerged and Phase3 NO-GO. No owner migration or later phase.
