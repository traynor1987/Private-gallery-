# Task3B owned normal Primary PIN unlock — 2026-10-16

Task3B PARTIAL / NOT ACCEPTED; Phase3 NO-GO; biometrics DISABLED. This checkpoint completes only internal B1. Task3C is not started or safe to begin while mandatory Task3B obligations remain.

GitHub parent273e7880e86801eebb699cd23d79dece3ffbd8ba; independently reviewed local implementation52599b07893e98c3608cd87d8a9fb0dde675c22f, treeda1de5a628b8a933cef3e646eb22c34b13558ac1. Exact local history is preserved in the verified incremental bundle. The GitHub checkpoint includes documentation/evidence; verify its actual full remote SHA after pushing rather than confusing it with this local implementation ID.

## Active production migration

Normal MainActivity PIN unlock now calls the fixed PinVaultKeyStore.authenticatePin entry, which enters an exact original authority/attempt producer. Setup deliberately remains on its legacy writer until all-writer migration is coherent. PrimarySessionAuthority and OwnedAuthenticationKey retain the existing branded3-child result/invocation/input and sole-original final admission. No raw result, Cipher getter, arbitrary plaintext producer or selectable production test backend is added.

PrimaryPinUnlock funds and attaches all8 unwrap-phase children before any new destination, credential snapshot, decoded payload or provider acquisition: synchronous coordinator/read, decoded salt/nonce/ciphertext, ASCII PIN bytes, wrapping key, KDF invocation and Cipher/key/parameter facade. Peak11 with the existing result3; this does not fund the still-unmigrated outer worker/UI callback path or establish complete-route acceptance.

The private32-byte destination is bound to the existing result child before Cipher output. Existing producer-active invalidation defers wiping until actual synchronous return; cancellation cannot wipe a buffer still being legitimately used, and late writes/throws are wiped afterward. The same phase original must reach positive terminal actual-return ACK before final handoff. Failed/incomplete cleanup remains charged and prevents fresh admission. Prohibited authority/storage/preference gates reject entry before attempt creation, clock/provider work, ticket allocation or waits; caller PIN is wiped on known-unentered rejection.

Standard existing SCrypt32768/r8/p1/output32 and AES/GCM/NoPadding128-bit tag are unchanged. A private RAW/AES SecretKey lends the exact owned wrapping array throughout actual init/doFinal; no SecretKeySpec clone, general key export, reflection, forced GC, reset-as-wipe or custom cryptography. The key is sealed after supported synchronous return and the owned backing array is wiped. Supported app invocation return and controllable-reference retirement are acknowledged; provider/JVM private memory residues and Android/OEM runtime behavior are not claimed positively erased or tested. No active app resource receives an extra platform exception.

Fixed record validation bounds significant Base64 input before decoding and requires decoded16/12/48 salt/nonce/ciphertext. A bounded scan preserves actual Android standard decoder SKIP/padding semantics, including legacy whitespace/Unicode skip and permitted missing padding, while preventing an unbounded decoder input copy. Android Base64 remains the production decoder. Plaintext PIN compatibility remains all previously supported ASCII values, including four-digit/short/long/non-digit cases; no new PIN length/digits policy or encryption format change.

## Deterministic evidence and corrections

- Genuine baseline capacity RED01:1 test/1 failure. Legacy normal unlock reads credentials despite lacking complete phase capacity; final path denies before snapshot/provider/destination.
- First new test compilation run02 failed due to a test assertion overload; not behavioral evidence. Run03 had50 tests/2 failures caused by explicitly documented proxy-exception and post-ACK failure-injection fixture defects. Fixtures were corrected without weakening original wiping/accounting assertions; full logs/XML remain preserved.
- Genuine inherited-gate RED04:3 tests/1 failure, observing3 tickets allocated under storage gate before rejection. Early preflight corrected this; both fixture-correction cases passed.
- Genuine record-bounds RED05:3 tests/2 failures, oversized decoder input and wrong-sized outputs accepted. Predecode bound and bound-then-size validation corrected both while preserving real decoder compatibility.
- Final coherent focused run06:59 tests/0 failures/errors/skips, six fresh XML files: owned PIN16, auth handoff19, PIN snapshot6, legacy PinEnvelope1, Primary authority14, original terminal3. BUILD SUCCESSFUL1m44s.
- Direct current-compiled JUnit repeat: owned PIN16 plus actual OwnedPlaintextTest10 =26/26, exit0. A nonexistent Task3AOwnedPlaintext Gradle selector is explicitly NOT counted; the actual Task3A class was executed separately.
- SDK-backed Python source/security25/25; no-secret scan PASS; clean diff; exact six-file SHA256 manifest verified independently.
- Debug Android test Kotlin/Java compilation PASS50s; Phase0Evidence Android test Kotlin/Java compilation PASS1m4s, separate sequential Gradle invocations on frozen52599b0 source.

No complete debug/Phase0 JVM or lint acceptance is inferred for whole Task3B from this focused batch. No Android runtime/instrumentation execution or physical-device PASS; local ADB/emulator/KVM unavailable. No release APK, signing, device acceptance request or biometric enablement.

## Independent acceptance and invariants

Fresh independent spec/security/quality review inspected all production paths, supporting reservations/authority, exact HEAD/tree/six source hashes, actual saved59-test XML,26-test repeat, Python25/no-secret and qualified failure evidence. No Critical, Important or Minor defect identified; bounded B1 accepted within the documented synchronous app-controlled provider boundary. Android compilation is separately recorded above, not represented as runtime proof.

Task3A remains intact and its10 tests passed on current source. Original gallery media/credentials are not deleted; this is a read/decrypt migration. Existing encrypted key formats, verification behavior, fixed physical16 capacity and separate Primary/Hidden authorities are preserved. The sole owner-approved platform exception remains Android-internal Keystore retirement where public ACK is unavailable; B1 does not use that exception and no internal Keystore retirement was positively observed.

## Remaining mandatory Task3B

Full blockers A–D remain open: supported biometric lifecycle/runtime evidence; exact ownership for outer workers/queues/UI captures and remaining provider/store/display paths; Native/cleanup outside ranked controller/storage/authority gates; and complete original-attempt/epoch/selected-vault/final cleanup admission for all paths. B1 is not global credential writer exclusion or immutable predecessor revision.

PrimaryKeySlots.changePin and Main.verifySecretPin still use the raw legacy unlock path. Setup, offline recovery, all PIN/conflicting biometric/recovery-slot writers and restore install/rollback must migrate through the complete exact prepared transaction guard. PinVaultKeyStore legacy create/change/replace/install/clearIfOwned, RecoveryVaultKeyStore/RecoverySetupLifecycle, withPrimaryBiometricRecoveryDisable, Main generation/coordinator/coroutine paths and AndroidVaultRepository.restoreBackup remain mandatory B2/C/D dependencies. SecondaryController/store/directory/biometric ownership remains open as already recorded. No root serialization removal or outstanding-worker exemption.

Next implementation: exact owned recovery normalization/UTF8 and unwrap, replacement-PIN preparation with an already bound result, positive phase retirement and complete fixed16 caller census; then coherent all-writer/restore integration. Proposed phase counts are not permission to hide future outer worker resources. Future B2 plan in the archived handoff is NOT implemented acceptance.

Main/protected branch/PR59 are unchanged by this checkpoint; verify exact remote states after saving. Phase3 remains NO-GO. Do not automatically proceed to Task3C.
