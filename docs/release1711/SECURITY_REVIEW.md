# NovaOS 1.7.48 (Build 94) release review

Source: 46b9c78c15bcbe18d129e28be8677d21c59ca838
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: ff20be1dcf84324df149f3266651fec29caf0eefea23c825313bf4c0057c286e
Upgrade controls SHA-256: f7a717ff4752b40b4c5e8773b4ca3bdf5f16c384fd04ac10c7e22912c8448a55
Runtime matrix SHA-256: b3a221684edbd0e3462614ff8dbb402fb74d257823a6e9d5b7ea658ebf6242fa
Runtime workflow run: 44762015032
Runtime branch: feature/novaos-guardian-fold-acceptance
Signing workflow SHA-256: 3143b10b28eaaccbe0f743011dd025a877472aeebb2f1572131753ac47f0cd34

Guardian's Build 90 path supported eligible dismissal after encrypted
capture without a content hold. Build 91 added a hold gate and Build 93
only reopened the no-hold path for entirely low-risk bundles. The Build 93
reproduction in run 44746309328 kept a three-entry original visible
without requesting cancellation after the message was marked Uncertain.

Build 94 restores Guardian Only replacement for eligible assessed bundles
without a content-hold requirement. Both modes use the same validated
notification key and produce replacements after Android confirms removal.
Smart Filtering keeps ordinary WhatsApp alerts. Eligible Guardian Only
greetings may show a safe preview under the saved privacy settings.
Hostile and held text stays out of the replacement notification. Existing
encrypted evidence remains available in Guardian. The processing trace
records counts and decisions without message text or sender identifiers.

The full Android 17 run passed 344 reviewed matrix cases, 65
focused report groups with 270 test executions,
776 JVM tests and the original
process-restart checks. The Guardian test includes both mode switches,
grouped same-key updates, reposts, quiet handling, DND and screen-off
delivery, Android-confirmed dismissal, and encrypted evidence access.
The emulator uses a disposable synthetic WhatsApp sender. Galaxy Z Fold
acceptance requires an owner test with the actual WhatsApp application.

The signed upgrade uses published Build 93 as the baseline. The original
production job verifies the certificate and populated upgrade and restart
before stable publication. The existing live updater verification must
discover, download and install Build 94 from Build 93 and confirm it is
up to date after restart.
