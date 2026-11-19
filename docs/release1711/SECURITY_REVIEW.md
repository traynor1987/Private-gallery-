# NovaOS 1.7.45 (Build 91) release review

Source: 022061984debb59f69d917f225ac08f1bfedfe82
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: a137143347bb9eb0b3b0dfed7e201db386418f4fe60b35c3b7df12830c76d348
Upgrade controls SHA-256: 62054b8efa4f9c830f4c222221fb5cb474dab92d8514a56206b6f2a02018dc98
Runtime matrix SHA-256: 5470a97cdfe0e2ce863c69122fab33bebed7dbbade6e07e1b04a3d4a95b441ac
Runtime workflow run: 44587356051
Runtime branch: feature/novaos-guardian-reliability91
Signing workflow SHA-256: e3e429d603d10986d6003b5bf7adac924c9bed8504ae1e3d8c495d51798436a3

Tagged Build 90 reversibly reproduced the owner-observed Hi defect. The local
nearest-example model returned UNCERTAIN with zero features. WhatsApp automatic
dismissal did not require a hold, and the Nova alert could replace the ordinary
source notification with a privacy-only summary. Build 91 recognises routine
greetings and requests before any delivery decision. Uncertainty is available
immediately unless the owner has explicitly selected an uncertain-message hold.
Direct threats and insults retain protection. Corrections use the single
encrypted learning store. Short approved examples are exact matches, and the
held-out evaluation uses the same classifier and invalidates prior metrics.

Only durable, exact, still-active originals can be dismissed for an active
hold or an explicit quiet-collection rule, with the existing test-token path.
A replacement is issued only after Android confirms removal. Secret source
notifications are ineligible. Listener reconnection considers eligible recent
default SMS as well as WhatsApp. All known source permissions, identity checks,
Sanctuary and DND policy are preserved. UI decision diagnostics never record
message text or credentials.

Capture, Evidence Locker links, archive windows, encrypted retention and the
recovery journal are preserved. The in-app test uses an authentic Build 90
APK with populated synthetic encrypted Guardian, Evidence Locker, learning and
Vault fixtures. Missing owner data can only be determined by an authenticated
on-device scan of valid encrypted recovery copies.

Backend tests cover the existing staging, authentication, two-step connectivity
policy, opt-in, reservations, deduplication, and privacy limits. The public VPS
health endpoint responded over valid TLS and anonymous status access returned
401. No live production authentication, paid analysis or deployment change
was performed. Physical Main and Dual Messenger notification acceptance on the
owner's Fold remains pending.

CI passed 343 selected Android 17 cases, 63 focused report
groups, 775 JVM tests, protected-core verification and real Backup
and launcher restart checks. The production gate requires an in-place upgrade
from authentic original-key Build 90 into Build 91 using its existing updater
and Android installer. Populated encrypted Guardian, Vault, Evidence and local
learning fixtures must remain readable after restart. Public publication requires
the same verified normal APK, integrity metadata and post-publication native
update test. This review records implementation evidence and does not claim
independent review or physical owner acceptance.
