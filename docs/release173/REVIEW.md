# Private Gallery 1.7.3 focused review

Baseline: a478b447876640b730c494c6e31e2384f096c786 (verified remote 1.7.2/code35). Candidate implementation: 86fb35f13976ce200e2b3eff2f516183953c51e4. Physical acceptance remains pending.

Independent read-only reviewer examined the change against the approved design, including working-tree lifecycle corrections. No concrete authentication bypass found. Primary still requires an authenticated BiometricPrompt CryptoObject and production Keystore unwrap; no UI-only unlock. Hidden continues independent PIN/key authentication and its existing discreet entry; no biometric control or credential-prefix probing was added. PIN derivation, credential envelopes, payloads and binary index serialization remain unchanged. Legacy PIN length is unknown, so explicit submission remains necessary. Numeric presentation never shows digits or invokes a text keyboard.

## Findings and corrections

- Hidden transfer sources used raw index items, losing supplemental capture dates; authenticated source decoration now forwards them to Primary.
- Hidden edited copies lost parent capture dates; authenticated decoration and explicit forwarding now preserve them.
- Already-locked lifecycle cleanup reset biometric cancellation state. Cancellation now survives locked cleanup/recreation; only a newly ended authenticated session/camera resets eligibility.
- Immediate Hidden timeout revoked authority before eligibility transition was observed. The transition is now captured before background revocation, retaining a fresh neutral Primary entry.
- Queued automatic prompt intent and active callback are invalidated when PIN intent begins or the Activity stops. Successful callback also requires resumed lifecycle before unwrap.

Focused tests cover these transitions and date propagation. Real Activity tests require the disposable biometric-disabled identity; enrolled fingerprint hardware and Samsung lifecycle behavior remain owner checks. Final Android #677 passed343JVM,62focused,74preflight and344full Android tests, lint/build, debug and original-key production35→36 upgrades plus restart. Exact tested source569b3ea2e7bf600980e331b3b634843d01d0042e has identical production app source to86fb35f. Source review alone was not treated as release evidence.

## Data and privacy boundaries

Supplemental presentation metadata is encrypted using existing VaultCipher, independent vault keys/roots and domain-specific authenticated data. It contains dates, opaque IDs and sort preference, not public caches. Corrupt reads fall back safely; writes refuse to overwrite corrupt metadata. Hidden atomic publication remains bound to its authenticated operation. Old payload/index formats remain compatible; legacy dates use existing import timestamp or authenticated capture provenance where available. MediaStore queries retain existing permission selection; EXIF reading is limited to granted content URIs and does not modify device media. No browser networking, camera recording implementation or launcher artwork changed.

## Sorting follow-up review

Independent sorting review found two further correctness blockers: collection membership removal reloaded repository order while the selected sort remained displayed, and supplemental dates imposed a 20,000 historical-item limit below the existing 100,000 index capacity. Collection reload now reapplies the active sort. New publication prunes records absent from authenticated current live/trash IDs, retains the existing index capacity and enforces the same 32 MiB bound before save and load. Added collection-order and 20,001-record encrypted round-trip/pruning regressions. Validation remains pending.

## Recovery namespace review

A further compatibility issue was identified in the original flat supplemental-file placement: Hidden orphan cleanup would delete it, and its presence before the first index would trigger missing-record protection. The new unreleased presentation format now lives in private presentation-v1/state.enc, outside the unchanged flat media-record namespace. Independent re-review found no remaining blocker in that fix. Empty-vault/reconcile/reopen tests and candidate36 synthetic upgrade assertions now cover independent preferences through reconcile and force-stop/restart; the fixture uses reflection to preserve baseline35 API compilation. Existing media/hash/credential assertions and cleanup guards remain intact.

## Remaining review limits

Those automated gates passed on the exact candidate. Physical Samsung fingerprint, camera, display and owner-media acceptance remain pending. Large-list comparator coverage does not substitute for physical scrolling/performance testing. No owner device or real media was used.


## Final evidence disposition

Android #677 (40054106390), source569b3ea, succeeded with0failures/errors/skips. The last test-only correction strengthened configured Hidden keypad re-unlock/dismissal assertions; product code did not change. Full real Tor returned upstream BBC200 and exact controlled onion200; earlier transient controlled-onion transport failure did not lead to routing or assertion changes. Both package identities completed35→36 synthetic preservation and restart; independent sort preferences survived authenticated reconcile/restart. Original signing identity94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17 verified independently from downloaded APK. No remaining concrete automated release blocker; production acceptance is pending owner device testing. Full hashes and artifact IDs are in V1_7_3_RECOVERY.md.
