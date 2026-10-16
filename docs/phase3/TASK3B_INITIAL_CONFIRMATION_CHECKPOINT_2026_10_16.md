# Task 3B initial Hidden confirmation checkpoint

Parent: be82d965bc010b06da40486c5856ad434adb6a0a. Task3B remains PARTIAL / NOT ACCEPTED; Phase3 NO-GO; production biometrics DISABLED. This is a bounded verified implementation batch, not full B/C/D acceptance or Task3C work.

## Production changes

PendingSetup now exposes a bounded internal master borrow instead of masterCopy. Initial SecondaryStore.confirm retains that exact existing pending master through actual Native verification, preparation, selected publication and readback. Pending close seals new admission without wiping a legitimate active borrower. The only new master copy is the late authenticated result, with immediate exception cleanup; pending retirement is awaited after both borrower and root gate return. The existing prepareVerified authentication original retains the exact confirmation secret through actual worker return, so confirm no longer makes an unaccounted secretSnapshot. Fixed physical capacity16 and independent result/pending children are unchanged.

The production copying recoverySecret getter was removed. Synthetic test callers use source-set-only adapters borrowing the real secret cell; no production raw getter or alternative retirement mechanism remains. Selected credentials and original media are never deleted as cancellation rollback. Task3A ownership source and encryption formats are unchanged.

## Actual verification and qualifications

The new controlled Native-publication regression failed on old production source at the original-master lifetime assertion (genuine behavioral RED). The first implementation run executed39 tests with one fixture failure: final roundtrip compared distinct opaque DomainIdentity objects using reference equality. All lifetime/key assertions passed. The fixture now checks both namespace components through identity.matches; no security assertion was weakened. Corrected expanded selection:75 tests across9 classes, zero failures/errors/skips. After source-set-only getter migration, final relevant consumer selection:27 tests across6 classes, zero failures/errors/skips; Gradle exit0. Controlled actual-publication borrow repetitions:20/20, zero failures. Both Debug and Phase0Evidence Android test Kotlin/Java compilation completed successfully in separate serial Gradle executions (55s and1m7s). SDK source/security tests passed25/25 before getter removal and again on the exact final source (2.431s).

Raw logs, genuine RED XML, first fixture failure XML, corrected75 XML, final27 XML,20 repetitions, independent narrow source review, verified source copies and Git-blob/SHA256 manifest are preserved under evidence/task3b-pending-confirmation-2026-10-16. Earlier broad suites/lint apply to their documented parent sources, not a new full-suite pass for this delta. No Android runtime/device result is claimed; no ADB/KVM runtime is available. A compile pass is not lifecycle acceptance.

Independent narrow review found no introduced production defect. It explicitly leaves complete authentication acceptance open. Required further work includes replacement-confirmation key/secret ownership, outer worker/queue/captured inputs, transient unwrapped confirmation keys, complete provider/KDF/stream/store child manifests, root-held Native cleanup, prepared Primary persistence and all final admission barriers. The remaining implementation plan records these as Task3B obligations, not Task3C deferrals.

The approved exception remains limited to Android-internal Keystore operations lacking public retirement acknowledgement. No app-owned resource exception is introduced and no internal retirement is claimed. Main, protected Phase3 branch and PR59 must remain unchanged; no release/signing workflow or owner physical acceptance is requested.
