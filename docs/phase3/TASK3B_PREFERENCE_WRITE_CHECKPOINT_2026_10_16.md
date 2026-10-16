# Task 3B Primary biometric preference-write checkpoint

Parent: b3b20715e2d3d2bf5e802b4f3145d4a057baca4c. Task 3B remains PARTIAL / NOT ACCEPTED; Phase 3 NO-GO. AndroidX production biometric acquisition remains disabled. This is a coherent implementation batch, not completion or Task 3C authorization.

## Implementation

Primary biometric enrollment persistence reserves an exact original one-child synchronous preference-write invocation before slot claim, encoding or Editor construction. A process-wide opaque CAS slot refuses competing mutations without queues or capacity expansion. Preparation and actual SharedPreferences.Editor.commit run outside authority gates. A short original-epoch command admission permits only that exact already-prepared once-only editor. The existing Phase 0 contract permits a durable command linearized before revoke to finish; this does not grant authentication authority after revoke.

Actual invocation return clears editor references and returns the exact slot before its acknowledgement. The original is explicitly released, terminal retirement awaited and failure checked. A separate authority check occurs after mandatory cleanup before successful return. False commit and exceptions fail truthfully; interruption is restored, unfinished retirement stays charged, and stale cleanup cannot free a replacement slot. This acknowledges supported application invocation/reference cleanup, never Android-internal Keystore retirement.

The legacy enrollment wrapper now uses the existing owned envelope and owned temporary-key production, waits for envelope retirement outside authority gates, preserves original exceptions with suppressed cleanup failures, and independently rechecks authority after envelope cleanup. The raw saveAuthenticated production helper was removed. Its Android codec fixture now uses the real owned wrapper, transfers a copy of its synthetic master into authority, and waits for authority cleanup before deleting preferences. Cross-vault negative assertions remain intact; synthetic-key codec tests do not establish hardware authentication.

Recovery disable shares the exact slot and seals its synchronous callback to the original actual thread and lifetime before PIN mutation. Escaped, repeated and foreign-thread callbacks are rejected. This does not resolve the broader existing recovery transaction, temporary key/PIN ownership or Native-under-authority-gate obligations; these remain mandatory Task 3B work.

## Verification

Initial security regression: six tests ran, two passed and four failed against the extracted old behavior (Native-under-gate, inherited gate, capacity exhaustion and blocked revocation). Those failures are preserved. First corrected selection: 27 passed. Expanded selection: 59 passed, zero failures/errors/skips, comprising preference-write 13, temporary-key 14, Primary authority 14, recovery-read 7, plaintext 10 and prepared-envelope borrow 1. Controlled scheduling repetitions: 80/80, twenty each for held admitted write/revoke, revoke during preparation, escaped recovery callback/replacement and conflicting mutation/original cleanup. SDK-enabled Python security/source checks: 25/25.

After the final enrollment-wrapper and Android codec fixture extension, the focused selection passed 35/35 (preference-write 13, temporary-key 14, recovery-read 7, envelope borrow 1). Android test compilation for that exact extension: debug Kotlin/Java PASS; Phase0Evidence Kotlin/Java PASS (1m5s), separate serial executions. Earlier pre-extension debug and Phase0 Kotlin/Java compilations passed; those are not represented as verification of later edits. No Android runtime or physical device evidence is available.

Independent source reviews, including the final wrapper addendum, found no introduced Critical/Important defect in this narrow batch. They explicitly do not accept full Task 3B. Raw logs, XML, reviews and source blob preservation manifest accompany this checkpoint. Historical failed executions are retained, not counted as passing.

## Remaining security obligations

A: a functioning, independently verified biometric lifecycle remains unavailable. An API35 supported private-process candidate is being investigated; it is not enabled or accepted. B: complete authentication worker/queue, provider, stream/store, copied secrets and display manifests remain open. C: existing broader recovery/PIN and storage Native/cleanup under ranked gates remains open. D: complete exact-attempt, selected-vault and mandatory-retirement final barriers across all authentication paths remain open. This batch does not relabel them as later phases.

Fixed physical cleanup capacity remains 16. No encryption encoding, PIN wrapping, key alias, selected-vault identity or Task 3A implementation changed. The sole approved platform-managed exception remains Android-internal Keystore operation retirement without supported positive acknowledgement. No internal retirement is claimed observed. No release APK or owner physical acceptance is requested. Main, protected Phase 3 branch and PR59 are outside this batch.
