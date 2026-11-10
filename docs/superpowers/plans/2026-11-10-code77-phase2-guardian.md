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

## Capture checkpoint test

After running the Phase 1 checks, use **Encrypted Main WhatsApp capture** in the test app's diagnostics. Enable it explicitly, then send one harmless message to each account. Verify that only supported Main data increments the retained total. Repeat after leaving the app and reopening it, then revoke notification access and confirm capture turns off. Use **Clear encrypted capture** to delete the test archive.

The five-minute probe also counts visible call and missed-call notification events for the Android default dialer and WhatsApp profiles. Test each source on the Fold with your preferred DND settings. Missing events may mean Android or the app did not expose a listener-visible notification. These counts do not verify Telecom call visibility or provide any call-control capability.

## Inbox and count checkpoint

Guardian Home reads counts from the encrypted archive. New captures remain Awaiting analysis. Ready to Read counts records the owner explicitly marked ready. Cooling Down counts active manual four-hour holds. Expired holds move to Protected for deliberate review. History contains records the owner filed. Opening the inbox requires the Android device credential and shows dates and sections first. Opening a message requires a separate reveal action. The screen locks on backgrounding or after two minutes and uses Android's secure window flag.

The owner confirmed encrypted capture and the harmless NovaOS dismissal test on the Fold. DND-off profile delivery, lock-screen presentation, and reauthorisation on the Fold still require confirmation. The isolated APK contains manual holds and review actions. Automated classification, Evidence Locker learning, real WhatsApp dismissal, and automatic Panic remain separate work.

## Nova Brain 1.0 local checkpoint

The inbox shares the navy Guardian palette and shows archive-backed counts, concealed previews and owner review actions. The local engine uses hashed ordered phrase features from owner-approved examples with a separate AES-GCM Keystore archive. A bounded optional window uses up to three earlier same-sender messages in the same conversation and profile within 30 minutes. Missing or conflicting evidence abstains. Only newly stored message identities are analysed automatically. Manual re-analysis and corrections are available after Android authentication. Assessments are advisory and keep all dismissal and Sanctuary decisions separate.

Teach Guardian imports one explicitly selected excerpt from an existing Primary Evidence Locker OCR transcript. Unselected and Hidden Vault records are excluded. The owner chooses the excerpt and label, and may correct or reset examples. This checkpoint does not infer speakers from screenshots or perform broad training imports.

### Optional hybrid infrastructure

Cloud analysis needs a separately deployed HTTPS backend with verified owner sign-in and short-lived app session tokens. Store the OpenAI project key in the backend secret manager. Require a per-request consent grant, use strictly bounded redacted text from uncertain messages, disable raw payload logs, and validate the three-class response. Enforce daily request limits and monthly spend limits server-side with transactional accounting, including retries. Show usage and allow the owner to revoke the feature. API errors, budget limits or invalid outputs preserve Uncertain. Screenshot upload and Hidden Vault input are excluded.

GPT-5 nano is a candidate, configured on the server after an availability and quality check. The published standard text rate is USD 0.05 per million input tokens and USD 0.40 per million output tokens. A thousand requests at 500 input and 100 billed output tokens each would be about USD 0.065 in model charges, before hosting and any additional tokens. API usage is billed separately from ChatGPT. Review API retention and organisation controls before any private text is sent. Cloud transport, consent UI and server accounting are not deployed in this checkpoint.

## Fold dismissal correction

The owner reproduced a visible failure in Samsung's notification panel. The old presentation button posted one UUID-tagged notification, then the dismissal button posted and removed a second notification with another UUID. The emulator test explicitly expected the original to remain.

The corrected flow posts a tagged NovaOS test notification, then cancels the newest still-active matching test notification by its exact tag and ID. It checks Android's active notifications after the request. Other notifications with the same integer ID are left alone. A separate Android test exercises listener cancellation of a harmless shell fixture by its exact key, with callback and active-list verification. No automatic external dismissal is enabled.

On the Fold, post the test notification, note it in the shade, then tap Test dismissal of NovaOS notification. Check both the diagnostic result and the actual Samsung panel. Repeat with DND off and on, locked where possible, and on both displays. A prior test result does not close the physical acceptance gate.
