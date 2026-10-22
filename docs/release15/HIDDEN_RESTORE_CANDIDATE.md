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
