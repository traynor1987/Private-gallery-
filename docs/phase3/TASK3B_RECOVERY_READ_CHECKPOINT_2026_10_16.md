# Task 3B recovery-record read ownership — partial checkpoint

Parent: fc159ed6694c74bd3db7132baecaf612d6f7d05a. Task 3B remains PARTIAL, not accepted; production biometric acquisition remains disabled. Phase 3 remains NO-GO. No Task 3C work is authorized by this checkpoint.

## Narrow production change

PrimaryKeySlots.recoveryEnvelope now reserves one exact physical invocation child before entering the synchronous recovery-envelope read. The read runs outside the Primary authority gate. Its receipt is completed only after that synchronous invocation actually returns, including exceptional return. Normal retirement releases the same original and awaits its terminal acknowledgement before a separately checked original-epoch commit can return the encrypted record. Revocation while the provider is held cannot publish a late record. Capacity exhaustion and inherited authority entry deny before provider entry. Interrupted retirement restores the interrupt flag and cannot return the record. This receipt does not assert Android-internal Keystore retirement.

This does not finish ownership of the surrounding provider, preferences, MainActivity coroutine, decoded metadata buffers or other authentication paths. Existing preference persistence under prohibited gates, SecondaryController/store manifests, and the production biometric lifecycle remain Task 3B obligations. Encryption formats and Task 3A source remain unchanged.

## Actual negative evidence

The first run had five failures: four demonstrated the baseline's provider-under-authority / unreserved acquisition behavior; the fifth was a test-fixture error expecting IllegalArgumentException where requireScope intentionally throws IllegalStateException. The initial implementation run executed 29 tests: 28 passed, one failed because the test incorrectly treated authority.cleanupComplete as excluding live nonretiring originals. The stronger replacement checks the exact original reservation and its actual terminal acknowledgement instead. Both failed XML files and logs are preserved; no failed run is counted as passing.

Independent read-only review identified interrupt-status loss in the first helper, corrected before the final run. Final source inspection found no additional introduced defect in this narrow helper; acceptance is conditional on actual verification. This is not independent acceptance of blockers A–D as a whole.

## Final actual verification

`primary-recovery-read-final`: Gradle exit 0; 31 tests, zero failures/errors/skips: PrimaryRecoveryReadOwnershipTest 7, PrimarySessionAuthorityTest 14, OwnedPlaintextTest 10. Revocation and interrupted retirement repeated 20 times each through actual compiled JUnit classes: 40/40 pass. `python3 -m unittest discover -s scripts/tests -p test_source_integrity.py`: one test passed. Final independent source review accepted this narrow helper conditional on this green result. The review did not accept overall Task 3B.

Evidence archive includes both failed runs, final logs/XML, repeat results and independent review. Broad 1294/1301 JVM and lint results recorded in the previous checkpoint apply to their earlier source, not this new source; they are not silently reused as final whole-phase acceptance. No Android runtime or physical-device verification is available. No release artifacts were produced.
