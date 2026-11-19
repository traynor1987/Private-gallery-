# NovaOS 1.7.44 (Build 90) release review

Source: b7390f3717bdf4ecc7b905ff265232e0fba80618
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: 7d6dab6b3aa68a2d1f74df6e1d1069d575faf46f8173f000adde27e2f0042cb9
Upgrade controls SHA-256: bf5ba91741435d31594da0cface3a14a973c782a806c6328018062189a713210
Runtime matrix SHA-256: e11825f5d7458d3683dd771fcf2e62421b11f84ee03b448129c419e3d00a8cb0
Runtime workflow run: 44542743754
Runtime branch: feature/novaos-guardian-evidence90
Signing workflow SHA-256: 736b844822ee9b198140eefa82a5b0767225a17723bcfa1bc43e2c88bf950300

Guardian and Primary Evidence Locker access the same authenticated encrypted
notification capture by its existing stable ID. The app writes evidence before
classification, dismissal or cloud work. Build 89 fixed destructive expiry and
the 256-row limit. Existing encrypted captures, recovery copies and Android
Keystore identity carry forward without a migration. Actual owner-message
recovery depends on the authenticated on-device evidence scan. Inbox windows,
archiving, restored visibility, saved originals and deliberate deletion are
separate. Individual and bulk permanent deletion erase the capture, its local
correction history, linked personal impact feedback and queued cloud state.
The existing bounded archive refuses new capture before evicting existing data.

Automatic Cloud Assistance is default-off, requires disclosure and conversation
approval, and processes a single bounded uncertain WhatsApp excerpt. The queue
shares the encrypted evidence record. Two authenticated checks on the same
validated network are ten seconds apart. Preflight retry is bounded. A durable
request claim, final scope check and existing server identity reservation prevent
blind paid resubmission. Automatic advisories never set owner labels or alter
Guardian protection. The backend uses its existing SQLite ledger and an explicit
automatic-analysis server flag. The current deployment, any real provider credit
and live authorized paid analysis remain unverified. No deployment or live paid
request was made. The Shift Tracker Web 2.1.251 source snapshot at
74407ea5fc3315eca571227f67b1275188b6d2be contains the actual uncached 204 probe,
ten-second interval, five-minute deadline, and duplicate-probe protection.
Its current probe passed all 80 synthetic offline, rejection, timeout and
healthy checks, and the three original routing retry tests passed. The source handover identifies the first-request gate as Build 200 and
its zero-cost manual recheck as Build 201. The newer archived web snapshot
was the independently tested source.

CI passed 342 selected Android 17 cases, 63 focused report
groups, 772 JVM tests, protected-core verification and real Backup
and launcher restart checks. The production gate requires an in-place upgrade
from authentic original-key Build 89 into Build 90 using its existing updater
and Android installer. Populated encrypted Guardian, Vault, Evidence and local
learning fixtures must remain readable after restart. Public publication requires
the same verified normal APK, integrity metadata and post-publication native
update test. This review records implementation evidence and does not claim
independent review or physical owner acceptance.
