# NovaOS 1.7.46 (Build 92) release review

Source: cb99ab3831670f6881dea1d681df98da5ba3d497
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: 9c980cf66a54e939940e2234066d3dedfc358b316f45b570e84c93543237f549
Upgrade controls SHA-256: 6179fdb4e4294ff68116b57532ef34ee4377108eaff006a8a0e28853d8951b58
Runtime matrix SHA-256: b3a221684edbd0e3462614ff8dbb402fb74d257823a6e9d5b7ea658ebf6242fa
Runtime workflow run: 44647382628
Runtime branch: feature/novaos-guardian-repair92
Signing workflow SHA-256: 422464b0a34e6f2fcf99c55aefbd222a716a6578cf2874023a4fa34efd7744d1

The authentic Build 91 app at 022061984debb59f69d917f225ac08f1bfedfe82
classifies Hi and How are you? as low risk by default. Good morning with a
smiling emoji remains uncertain and triggers a hold only
when the owner explicitly chose that uncertainty policy. The synthetic
direct threat triggers the active content hold. Build 91 deliberately
keeps the native app alert for ordinary messages under default settings.
Its default Guardian Inbox filter was Awaiting Analysis even when those
retained rows were in Ready to Read.

Build 92 opens Ready to Read behind the same device-authentication gate.
It increases the bounded capture queue from 16 to 64 and reports a
session-level count when the queue reaches capacity. The regression
exercises 24 simultaneous admissions while the encrypted archive is locked
briefly, then verifies intact unique originals in the Inbox. The local
assessment reads saved examples separately and can apply local rules and
Guardian corrections when the separate Evidence Locker learning file fails
verification. In that case automatic cloud submission remains off for the
affected assessments. Tests use synthetic data and confirm that the
unreadable file is left intact. Diagnostics distinguish a source notification
marked secret from an unsupported message payload and show the last retained
capture and assessment without message content or identifiers.

Source authentication, Android permissions, notification removal conditions,
Sanctuary policy and the existing encrypted evidence and retention controls
remain in place. No owner data was accessed. The public-source Android 17
tests exercise Android notification listener authorisation and synthetic
delivery, WhatsApp-format parsing, retention, and authenticated Inbox access.
They do not establish which stage failed on the physical Galaxy Z Fold.
No paid cloud analysis or infrastructure changes were made.

CI passed 344 selected Android 17 cases, 63 focused report
groups, 775 JVM tests, protected-core verification, and actual
Backup and launcher process restart checks. Production signing requires a
populated, original-key Build 91 to Build 92 in-place upgrade through the
existing Android installer and verification that encrypted fixtures remain
readable after restart. Public publication uses the same verified APK and
integrity metadata, followed by a test of the public in-app path.
