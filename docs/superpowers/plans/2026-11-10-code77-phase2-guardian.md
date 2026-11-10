# Code 77 Phase 2 Guardian implementation plan

**Goal** Give the owner control over protected notifications through Guardian Home and existing Sanctuary safeguards.

**Architecture** Separate listener capture, an encrypted archive, classification and authorised presentation. Keep protection and dismissal behind independent compatibility and owner-consent gates.

**Platform** Android 17 API 37. Production baseline NovaOS 1.7.38.3, versionCode 76. Code 77 branch checkpoint `1801feeed7e22ca1c77958b3fd46d8c2b4dc1bd3`.

## Phase 1 physical closure

The owner observed two Android events per WhatsApp profile under Samsung Priority Only, text on all four events, zero unresolved identities, and Guardian Home rendering. Emulator verification covered listener binding and revocation, harmless NovaOS dismissal, and the privacy flag. The Fold checks below are open.

- [ ] With Samsung DND off, start the probe and send one harmless message to Main, then one to Friends. Compare profile counters. Restore the owner's preferred DND mode afterward.
- [ ] Run Test notification presentation. Check the Guardian banner and Samsung shade. Lock the Fold and confirm the test message text stays hidden.
- [ ] Run Test dismissal of NovaOS notification. Confirm the result reads Dismissed and the specific synthetic notification leaves the shade.
- [ ] Revoke and restore the test app's notification access. Confirm Disconnected then Connected and that capture stays off unless independently enabled.

## Implementation and verification order

- [ ] Encrypted capture. Exercise the real listener and a Keystore archive with synthetic data. Require explicit Main capture consent and a matching current user and posting UID. Drop ambiguous, grouped-summary and inaccessible profile input. Deduplicate messages within updates using conversation, profile, timestamp, sender and content. Keep only aggregate health accessible at this checkpoint. Retention is pruned on capture or archive access, and Clear deletes the dedicated Keystore key.
- [ ] Authorised profile-aware dismissal. Require Fold acceptance and separate owner consent. Verify archive commit before calling Android, and verify removal separately. Keep all real-message dismissal disabled at the capture checkpoint.
- [ ] Local contextual classification and Primary Evidence Locker learning. Build from individually selected examples and evaluate against held-out examples and adversarial cases. Record uncertainty, version and owner feedback. Keep cloud and Hidden Vault excluded.
- [ ] Authenticated inbox and privacy-safe presentation. Test count integrity, lock and background handling, retention, owner-initiated review and profile-specific actions. Validate any replies separately.
- [ ] Sanctuary incident integration. Verify four-hour holds, twelve-hour Panic, Pending Review, one-hour extension, evening policy, emergency access and restart. Automatic Panic needs separate consent and reliable classification.
- [ ] Optional call diagnostics. Assess supported Telecom role on the Fold separately from WhatsApp notification metadata. Keep screening or rejection outside the capture checkpoint and preserve emergency access.
- [ ] Full Android 17 regression and original-key upgrade only after the security and device gates pass.

Code 77 continues on the existing branch. The isolated diagnostic package can verify individual checkpoints without updating the production installation.
