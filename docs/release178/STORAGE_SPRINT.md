# Private Apps storage sprint — development only

Candidate source: d391681820126b4108669b48f56bb4cc32b92bef
Baseline: dd9a25977bea5cdaee1df82f80d2f7e7181f7cb7

## Implemented
- One PrivateAppStore interface for Primary, Hidden VPN and Hidden Tor, with explicit immutable owner identity.
- PrimaryPrivateAppAccess rejects handles from an older authentication identity, checks authority and commits under a shared caller-provided gate, copies keys per operation and wipes them on all exits.
- Existing encrypted Primary/Hidden storage formats retained. Catalogue definitions remain separate from WebView cookies, credentials and browser history.
- Shared CRUD/restart tests exercise add, rename, ordering, metadata round-trip and remove in each store.
- Corruption/ciphertext mode substitution tests ensure a failed read cannot silently overwrite existing data.
- Missing catalogue initializes additively without touching existing browser records.

## Verification scope
Three Java host check programs passed locally and in the grouped workflow. The new JVM suite contains four integration tests plus the existing Primary encrypted-store test. Run40909283419/job123572509730 succeeded. Downloaded JUnit XML confirms5 tests,0 failures/errors/skips; artifact SHA256 adb692a0ef020c81cf323b3b7902520017285335348f4f7f2a474908811f351b. Evidence is preserved in storage-sprint-evidence/.
One narrow branch-specific job in the existing fast-development workflow replaces the broad job for this sprint branch. No emulator, release regression, signing or APK publication.
Independent source review found no concrete Important bugs. This is not release or runtime approval.

## Deliberately unfinished
No production UI, MainActivity factory or Hidden broker consumer is connected to these storage handles yet. No saved-app launch or PWA discovery exists. Icons are bounded Base64 metadata; renderer must validate actual image content and dimensions. Session retention, per-app reauthentication, document/screenshot handling and browser callbacks remain future work.
No schema migration is needed beyond additive first-use for the current v1 catalogue. Unknown/corrupt formats fail closed; no destructive fallback is introduced. Physical restart/upgrade testing remains outstanding.

## Exact next task
Create an owner-bound handle at authenticated browser entry: Primary must capture sessionKey identity and phoneTransferGeneration under phoneTransferGate; Hidden must use existing broker authority and selected immutable transport mode. Bind library/add/edit/remove callbacks to that handle, recheck ownership before publishing results or launching, and test queued operations across lock/re-unlock and vault/mode changes. Reuse existing browser surfaces and transports.

## Preservation
Version metadata remains1.7.7/code46 pending the later coordinated1.7.8 release. The1.7.7 branch, recovery, release177 evidence, signing workflow and upgrade fixture are unchanged. No signing material accessed.
