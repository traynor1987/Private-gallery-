# PRIVATE GALLERY 1.5 — SIGNED OWNER ACCEPTANCE CANDIDATE

Base: d10ea24c844550a5e6cee19264abba3f214ff5a7. Owner authorized Hidden outward Copy/Move and a production-signed acceptance candidate; no installation or release publication.

## Implementation

- Hidden selection uses Restore to… then explicit Copy/Move destination choices.
- Primary destination requires a fresh PIN capability; no active-vault navigation changes.
- Original bytes stream from Hidden authenticated chunks into Primary ciphertext or pending MediaStore. Large legacy Primary payload verification uses bounded lightweight GCM with unchanged on-disk format.
- Moves recoverably trash exact Hidden sources only after destination verification. Cancellation/lock revokes publication/removal. No deletion resumes after restart.
- Phone exports require API29+ pending-row support, fsync, size/SHA readback and successful publication. A durable pre-insert random-name journal reconciles only app-owned pending rows, including MediaStore-added extensions. Published rows are not cleaned.
- Vault-only derivatives remain restricted. Primary→Hidden now preserves origin and restriction, with distinct restricted copies to prevent unsafe deduplication. Historical copies whose provenance was already lost cannot be reconstructed from ciphertext alone.
- Repeated Hidden→Primary copies intentionally create distinct destination identities to preserve provenance/restrictions independently. Collections and favourites are not merged.
- No schema/encryption migration, production ID change, browser/network change or V2 integration.

## Verified candidate

- APK source: `a46288059fd7dc8d094798dd67d4ad87af5ee6a8`, independently verified on `release/1.5-hidden-vault`.
- CI616: https://github.com/traynor1987/Private-gallery-/actions/runs/39816516328/job/119956341056 — all steps passed.
- Production artifact: `private-gallery-1.5-production-candidate`, GitHub artifact ID `12445131729` (CI retention through 2026-11-21).
- Owner file: `Private-Gallery-1.5-production-candidate-a462880.apk`, 30,503,920 bytes.
- Package `uk.co.traynor.privategallery`; label `Private Gallery`; versionName `1.5`; versionCode `29`; not debuggable.
- APK SHA256: `78974e0243f7c433aeaa1e3eb1a7b6da04859805a74bfd68c6543ba542426725`.
- Certificate SHA256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`, exact match with the actual recovered V1 APK. The CI private-key signing probe succeeded; no new key was generated and no CI secrets changed.
- Android apksigner verified the single RSA4096 APKv2 signer. Independent local APKv2 signature/content-digest verification, package/version parsing and SHA256 comparison all passed.
- No owner installation or GitHub Release publication occurred. This is a physically unaccepted candidate, not a completed release.

## Test and review results

| Gate | Result |
| --- | --- |
| Android API36 instrumentation | 237 passed, 0 failed, 0 skipped; includes 17 restore repository tests and 2 restore dialog tests |
| Hidden → Phone Copy/Move | Passed readable destination, source preservation/recoverable trash, write/publication/storage failures and pending-row recovery |
| Hidden → Primary Copy/Move | Passed fresh/wrong/missing auth, independent re-encryption, durable receipt, source preservation and repository restart |
| Existing Phone/Primary → vault operations | Passed full existing suite, including opposite-direction concurrency and restricted-media round trip |
| Large outward transfers | 100MiB original-photo bytes; 500MiB video to both destinations with complete hash/size and playback/seek passed |
| Lock/cancel/corruption/removal failures | Passed revocation and verified-before-source-removal boundaries; no deletion replay |
| Editors/navigation/settings/browser regressions | Existing suite passed; real Fold/live VPN acceptance remains required |
| JVM, debug/release lint and APK assembly | Passed |
| Host checks | Hidden foundation 373 checks plus isolation/revocation/durability suites; 20/100/500MiB streaming/seek/tamper under 48MiB heap; browser helper 3/3; no-secret and WireGuard source audits passed |
| Synthetic V1 upgrade | Both debug and production packages passed seed, install-r upgrade, verify, force-stop and verify again (6 fixture executions) |
| Independent security review | No remaining source-level blocker after corrections; findings and limits retained in evidence |

The production synthetic upgrade rebuilt V1 baseline `a19218479eb9b9bcdb35ff0035fc78522263c891` with the permanent signer, seeded disposable data, and upgraded code28 to this exact code29 candidate without clearing data. It preserved original encrypted file hashes and old-PIN decryption, photos/video, collections/favourites, theme/screenshot settings, encrypted WireGuard profile and encrypted synthetic AI credential/privacy settings, including restart. No destructive schema or encryption migration is introduced. This does not inspect or guarantee the owner's actual device state.

Evidence is under `docs/release15/evidence/`: `ci616-hidden-restore-results.json`, `ci616-candidate-verification.json`, `ci616-production/`, `hidden-restore-v1-original.json`, `hidden-restore-independent-review.md`, and the retained CI615 failure results. The final documentation-only backup commit does not change the APK source listed above.

## Owner data and acceptance safeguards

The recovered original V1 APK was independently reverified from actual bytes: SHA256 `30154bd79792e4fa1ee5e551136e9664e5e249823b6a9dea4b9c96a3aeecb07b`, production package, code28/name1.0.27, permanent certificate `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. The owner's currently installed APK has not been directly inspected.

Before installation, verify a usable existing encrypted Primary backup and recovery material, preferably by a restore on a separate disposable installation. Existing Primary backup archives do not constitute a full Hidden/settings/VPN/AI rollback. Without independently usable backups there is residual owner-data risk despite synthetic upgrade tests. Android downgrade/database rollback must not be assumed safe. Never uninstall V1, clear its data or reset the Primary PIN to resolve update errors.

Physical acceptance after reviewing and explicitly authorizing the exact final APK:

1. Confirm installed production package/certificate and versionCode below29. Android must offer an update, not a second app.
2. Use the old Primary PIN; check old photos/videos, collections/favourites, Settings, WireGuard profiles and AI configuration, then restart and repeat.
3. In Hidden, use disposable media first: Copy and Move to Phone Gallery and separately authenticated Primary. Confirm readable original-quality destinations, retained Copy sources and recoverable Move sources in Hidden Recently Deleted.
4. Cancel/wrong-PIN/background during restore; verify no unauthorized Primary content, no source loss, truthful partial outcomes and pending-export cleanup on next Hidden entry.
5. Confirm Hidden stays selected after Primary destination authentication; Vault/Favourites remain Hidden. Check original Primary Gallery navigation, both editors and existing Copy/Move directions.
6. Test real large photos/videos, seek/playback and sufficient free storage; screenshots on/off, Fold layouts and live WireGuard fail-closed browsing. Browser state remains shared; no Tor is implemented.

Phone restore requires Android10+. Vault-only derivatives cannot be exported publicly. Historical copies created before provenance preservation may lack restrictions that cannot be reconstructed retrospectively. Repeated Primary restores intentionally create separate copies. Process-death recovery never automatically removes a Hidden source; a completed destination copy may remain if interrupted before source removal.

## Additional mandatory review gates

Coverage review added 500MiB valid padded MP4 restores to both destinations with full hash/size and Media3 playback/seek, corrupted source rejection, post-verification removal failure/revocation, simulated destination ENOSPC/fsync failure, and concurrent opposite-direction moves.

Primary destination receipts now fsync payload/index rename directories after authenticated read-back, before Hidden source removal. Independent review caught opposite transfer lock inversion; Primary source removal now acquires metadata before the final monitor/Hidden authority guard, matching reverse transfers. Existing formats and deletion confirmation behavior remain unchanged.

## Runtime failures corrected before signing

CI614 (`39811385573`, source `1001c182d606bac147a5a808d739d875d53e1a75`) passed 229/231 Android tests and correctly blocked signing on two new failures: 100MiB photo encryption exhausted the Android provider heap, and pending export recovery could not see MediaStore pending rows. The first is addressed by bounded lightweight GCM encryption preserving the V1 nonce/AAD/tag/ciphertext format, with exact-byte provider interoperability coverage. The second explicitly includes pending rows in the recovery query while retaining owner, journal UUID and pending-only constraints. Both failing tests remain unchanged. Independent read-only review found no further blocker in these corrections; a fresh complete CI run is mandatory.

CI616 passed both unchanged tests that failed in CI614/615. All mandatory automated and signing gates now pass. Only owner-device compatibility/backup checks, explicit installation authorization and physical acceptance remain.
