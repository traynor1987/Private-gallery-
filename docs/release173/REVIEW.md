# Private Gallery 1.7.3 focused review

Baseline: a478b447876640b730c494c6e31e2384f096c786 (verified remote 1.7.2/code35). Candidate implementation: 9293f53cae8a9dc2c6d23005cdae6a15f76cba68. Physical acceptance remains pending.

Independent read-only reviewer examined the change against the approved design, including working-tree lifecycle corrections. No concrete authentication bypass found. Primary still requires an authenticated BiometricPrompt CryptoObject and production Keystore unwrap; no UI-only unlock. Hidden continues independent PIN/key authentication and its existing discreet entry; no biometric control or credential-prefix probing was added. PIN derivation, credential envelopes, payloads and binary index serialization remain unchanged. Legacy PIN length is unknown, so explicit submission remains necessary. Numeric presentation never shows digits or invokes a text keyboard.

## Findings and corrections

- Hidden transfer sources used raw index items, losing supplemental capture dates; authenticated source decoration now forwards them to Primary.
- Hidden edited copies lost parent capture dates; authenticated decoration and explicit forwarding now preserve them.
- Already-locked lifecycle cleanup reset biometric cancellation state. Cancellation now survives locked cleanup/recreation; only a newly ended authenticated session/camera resets eligibility.
- Immediate Hidden timeout revoked authority before eligibility transition was observed. The transition is now captured before background revocation, retaining a fresh neutral Primary entry.
- Queued automatic prompt intent and active callback are invalidated when PIN intent begins or the Activity stops. Successful callback also requires resumed lifecycle before unwrap.

Focused tests cover these transitions and date propagation. Real Activity tests require the disposable biometric-disabled identity; enrolled fingerprint hardware and Samsung lifecycle behavior remain owner checks. Automated test execution is still pending; source review alone does not establish release readiness.

## Data and privacy boundaries

Supplemental presentation metadata is encrypted using existing VaultCipher, independent vault keys/roots and domain-specific authenticated data. It contains dates, opaque IDs and sort preference, not public caches. Corrupt reads fall back safely; writes refuse to overwrite corrupt metadata. Hidden atomic publication remains bound to its authenticated operation. Old payload/index formats remain compatible; legacy dates use existing import timestamp or authenticated capture provenance where available. MediaStore queries retain existing permission selection; EXIF reading is limited to granted content URIs and does not modify device media. No browser networking, camera recording implementation or launcher artwork changed.

## Sorting follow-up review

Independent sorting review found two further correctness blockers: collection membership removal reloaded repository order while the selected sort remained displayed, and supplemental dates imposed a 20,000 historical-item limit below the existing 100,000 index capacity. Collection reload now reapplies the active sort. New publication prunes records absent from authenticated current live/trash IDs, retains the existing index capacity and enforces the same 32 MiB bound before save and load. Added collection-order and 20,001-record encrypted round-trip/pruning regressions. Validation remains pending.

## Remaining review limits

Fresh JVM, Android, UI, browser/security, restart and original-key synthetic35→36 upgrade gates must pass for the exact candidate. Large-list comparator coverage does not substitute for physical scrolling/performance testing. No owner device or real media was used.
