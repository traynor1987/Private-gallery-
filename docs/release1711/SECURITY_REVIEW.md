# NovaOS 1.7.43 (Build 89) production review

Source: 699bc866905c52c46dfca44a397ea36bb225c471
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: d8584eddca467b65657d40fc86273c6ef63638332bed7fe06a10274aaa4e5ff4
Upgrade controls SHA-256: 0b64164fe2c1c4f60dea592f309aaef25274ec3495d38b35563aa32c586e992d
Runtime matrix SHA-256: cae16dbf9c88ae07e7f2498027b94a900076d251c03917558a63b8e15414c4f2
Runtime workflow run: 44506338536
Signing workflow SHA-256: 0be1c139eb7b690013b365dc921df8e88ce686969964ee2f62123f7270727f41
Runtime branch: feature/novaos-guardian-retention89

The owner authorized this critical retention repair, main merge, original-key
signing and the existing GitHub Releases/in-app publication workflow. This is
implementation self-review, not independent review or physical owner acceptance.

Build88 source430774e549a86d5c0db159ec24230bc4ac05bbc3 could silently discard
capture rows above256 and permanently prune them on ordinary reads, considering
both message and capture timestamps. Capture retention is a separate preference
from30-day Personal Impact feedback and Android alert timeout. The owner's actual
preferences and missing rows cannot be inspected from this environment.

The repaired archive keeps the existing capture key, AES256-GCM format/AAD and
private no-backup location. It preserves original base/bak/new ciphertext before
AtomicFile reads; no destructive migration, key replacement or storage reset is
performed. Seven/thirty-day policies archive from capture time; Keep Forever has
no automatic expiry. Capacity refuses new captures without evicting older rows
or dismissing unretained notifications. Legacy owner-labelled and newly saved
training originals survive Inbox clearing. Direct authenticated local Teach
Guardian uses the shared learning engine without requiring Evidence Locker or
making a cloud request. Explicit individual/bulk deletion distinguishes ordinary
captures from independently saved training originals, respects active holds,
scrubs private recovery copies and commits encrypted deletion intent before main
writes to prevent resurrection after a crash/rollback. Only such owner-approved
pending deletion may finish during a later read; time alone never deletes rows.

Actual recovery requires surviving authenticated ciphertext and its original key.
Feature-only examples cannot reconstruct message bodies. No owner data has been
accessed, uploaded or claimed recovered; no Evidence Locker copies are assumed.
Ordinary Android/allowlisted Nova Backup does not contain the no-backup capture
archive. The authenticated on-device check reports actual records/copies and
explicit restore respects known deletion intent; older unknown deletion history
is disclosed before an owner elects to restore.

The exact source's CI must pass all339 selected API37 identities, all62 focused
report groups,770 JVM cases without skips, protected-core guards, backend mocked
security/budget checks and actual Backup/launcher killed-process recovery. New
retention UI cases run at folded and unfolded resolutions. Existing Vault crypto,
manifest, update discovery/download/signature pin, paid-analysis gates and VPS
configuration remain unchanged; no token request/rotation, paid API call or
unrelated deployment is authorized.

Production run44 verified the original key/certificate, candidate package/signature
and populated Build88 seeding, then failed before download because the rehearsal
fixture asserted release code88 although the immutable Build88 updater correctly
discovered code89. Correct the fixture expectation to89; do not weaken the
assertion. Application source699bc866905c52c46dfca44a397ea36bb225c471, signed APK
build inputs and CI524 reports remain unchanged. Reuse their original evidence;
the complete signed populated upgrade and public-feed gates remain required.
No production signing or installation success is claimed for that failed run.

The production signer must compile this fixture against authentic Build88 before
signer credentials, verify baseline package/version/certificate/hash, then perform
the original-key populated88-to89 in-app download/verification/Android installer
upgrade. Aged synthetic Guardian originals, legacy owner-labelled training source,
unchanged ciphertext checkpoint, populated Vault/Evidence/shared learning/Nova
Brain configuration, launcher and Sanctuary must survive reauthentication and
real process restart. No uninstall, data reset or adb candidate replacement can
satisfy the test. Build88 APK SHA256:
c13cd18c78246b77ea90fdfbe0c12fc82ffa6c66ad4a1c434fe0b27725e42f11.
Original certificate SHA256:
94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.

After protected receipts pass, only the existing publisher creates/verifies
v1.7.43 and publishes a normal APK with the unchanged integrity metadata format.
It must test authentic Build88 against the actual stable GitHub release feed,
observe download/integrity/installer, installed89 and restart/Up-to-date. Existing
Build88 release remains untouched. Physical Galaxy Z Fold installation and
on-device recovery of the owner's own messages remain owner confirmation.
Paid Cloud Teacher remains disabled pending separately verified safe deployment,
live budget enforcement and explicit paid-test approval. Staging restrictions stay.
