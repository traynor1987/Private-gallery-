# Session publication locking prerequisite — 2026-11-04

Recovered baseline: ab2d8a9428937a01cbb615768fabd028d7e4e3fb. Same release branch, package, API37 and code55.

Implemented the Task3 locking prerequisite already conditionally accepted in CONTINUOUS_VPN_DESIGN_REVIEW.md. HiddenBrowserContinuation previously synchronised every method on itself while querying synchronised HiddenSession methods. A final authenticated publication transaction holding Session and calling continuation could therefore deadlock with a concurrent continuation observer.

A deterministic regression holds Session, starts an observer and observes its BLOCKED state, then queries continuation inside the Session transaction. Recovered production source fails with `Continuation -> Session / Session -> Continuation deadlock`. Corrected source passes. All mutable continuation fields and the constructor authentication/time snapshot now use the same owning Session monitor. No continuation monitor, new key/operation capability, broker lookup, transport I/O or native teardown is introduced. Existing auth/grace/attempt/route semantics remain.

Verification: `bash scripts/check_hidden_continuation.sh` passes authentication/operation revocation separation, synchronous signal/lock-inversion stress and exact child/auth/grace/boundary/repeat/clock/route/attempt checks, including the new regression. This existing test class is already run by the focused workflow; no workflow was added or dispatched.

Independent read-only review by continuation_security_review: no Critical/Important regression in this change. Reviewer inspected broker gate -> Session ordering and atomic/queued revocation listeners and ran the host runner. This is scoped source/host review, not Android or signing approval.

Additionally, the recovered unchanged native gate, transport and Android bridge host race suites pass using hash-verified Go1.20.3. Gate and bridge vet pass; the original native transport race suite passes. These results do not establish shipping runtime containment.

Critical C1 remains unresolved: shipping backend/service/private readiness and persistent-fence runtime/child lifetime integration are unfinished. No new Android packet run, original-key54->55 upgrade, signed55 APK, acceptance marker or release approval exists. The same-Network/fresh-generation publication transaction itself is not implemented by this locking prerequisite.
