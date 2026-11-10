# Code 77 notification compatibility implementation plan

**Goal** Establish the notification and DND compatibility gate on the signed Code 76 baseline.

**Architecture** Bind an Android notification listener after explicit OS consent. During an owner-started, five-minute probe, count only new WhatsApp callbacks. Keep the evidence in memory and discard it on close or disconnect.

**Tech stack** Kotlin, Jetpack Compose, NotificationListenerService, Android 17.

**Scope** Repository production identity and existing release metadata remain at the Code 76 baseline during the diagnostic phase. The notification test uses the isolated CI package.

## Phase 1

- [ ] Test exact profile bucketing and bounded update detection in `NotificationProbeTest`.
- [ ] Register the listener with Android's signature binding permission and add the Launcher Settings compatibility screen.
- [ ] Run `GuardianNotificationCompatibility77Test` on Android 17 to verify grant, binding, explicit test start and revocation.
- [ ] On the Fold, send harmless notifications to Main and Friends separately with Samsung DND off and on. Verify distinct callback buckets, text availability, quiet and grouped posts, updates, disconnect and lock-screen privacy.
- [ ] Preserve the measured compatibility results. Keep dismissal and automation disabled if profile identity is ambiguous.

## Fold test

Install the test-only APK alongside production NovaOS. Leave the current default Home app selected. Open NovaOS Guardian TEST ONLY. It opens diagnostics directly and checks whether Android exposes a unique WhatsApp clone. The test app has its own app data.

From the compatibility screen, grant Android notification access to the test app and start the five-minute test. Use harmless messages from a known sender. Send to Main and Friends separately with DND off, then repeat with Samsung DND on. Note which counters change for each event. Report counters and any visible shade or lock-screen behaviour without sharing message text. Revoke notification access from the test app after the check.

## Later phases

- [ ] Add encrypted capture and bounded retention before testing notification dismissal. Use synthetic dismissal tests first, then verify profile-safe behaviour on the Fold.
- [ ] Build Nova Centre and neutral Android and in-app presentation.
- [ ] Connect owner-selected Primary Evidence Locker learning and verified classification. Keep Hidden Vault outside the integration and cloud transmission opt-in.
- [ ] Verify independent message and Panic timers, explicit acknowledgement, Pending Review, Evening Protection, and authenticated recovery.
- [ ] Integrate status bar and three themes without modifying stored layouts or Vault security.
- [ ] Verify original-key upgrade and exact-source Android 17 regression before delivering a production APK.
