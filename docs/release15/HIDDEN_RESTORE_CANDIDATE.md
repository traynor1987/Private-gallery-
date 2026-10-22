# Hidden restore candidate — verification in progress

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

## Evidence so far

Local Hidden foundation 373 checks and related isolation/revocation/durability suites passed; 20/100/500MiB authenticated streaming/seek/tamper passed under 48MiB heap. Browser helper 3/3; no-secret and WireGuard source audits passed. These are not Android runtime results for the new code.

Independent review identified MediaStore filename normalization and Primary→Hidden restriction loss; both addressed with recovery and round-trip regression coverage. Final review/runtime gates pending.

Android tests cover fresh/wrong auth, copy/move preservation and recovery, repeated selections, Phone write/publication failures, process-death-equivalent pending cleanup, restriction round-trip and 100MiB original photo transfer. Production upgrade fixture additionally verifies encrypted synthetic AI credential and privacy configuration survive upgrade/restart.

Production signing is gated after JVM/lint/Android/debug-upgrade checks, probes actual private-key use against the pinned original certificate, and requires production-package synthetic upgrade before candidate artifact upload. No candidate should be recommended without completed evidence and original certificate comparison.

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
