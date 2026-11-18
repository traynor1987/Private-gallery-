# NovaOS 1.7.39 (Build 85) production controls

Source: e062aac04c5de268db52f4b3fc6066e90cb4bbcd
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: c80dc547781e514b90b514e888dd5c7296a9c25d9b3a0db04c0ab1c897431a3c
Upgrade controls SHA-256: 242e0847c885e4fc0cd6a29a50f9e972e138914e60c3833f708f491e16a3eea4
Runtime matrix SHA-256: 60e03f0de9ff69fd9ca74471eea557d442f0779ef58da859b0dfd7cefc0e01be
Runtime workflow run: 44254498885
Signing workflow SHA-256: 6fc41108d4cda026b2975afd9e76fe24a2d02dec6e9873ccb8435ba95750ed32
Runtime branch: main

Reviewed scope: the existing Updates & About updater, read-only update FileProvider,
API37 package identity, increasing versionCode, original production certificate and
bounded SHA-256-verified APK transfer. CI #464 was a Kotlin declaration-scope error;
the About entry effect now follows the category declaration. Its focused runtime
regression verifies entry and recreation without retry loops after failed checks.
This is an implementation self-review, not independent reviewer approval.

The existing protected workflow must authenticate the exact main source run and its
unchanged original artifact, validate every reviewed Android17 report, eight Backup
process restarts and the launcher restart before signer access. It compiles the
reviewed fixture against the authentic Build84 source and independently verifies
the published Build84 APK hash, package, API37 identity and original signing certificate.

The disposable emulator seeds populated encrypted Build84 data, executes Build84's
unchanged updater with controlled release transport and actual signed Build85 bytes,
requires Android's real installer and PackageManager confirmation of Build85, then
reauthenticates and verifies encrypted Vault and Guardian data after upgrade and restart.
No adb replacement installation substitutes for the in-app installer path. The signed
candidate cannot be staged until those checks pass. Only the exact candidate and
source-bound receipts may enter the existing publication workflow. Controlled release
transport is not evidence of live GitHub discovery; that check follows publication.

The owner's November 18 directive authorizes completion, main merge, original-key
signing and stable publication. The workflow's authenticated owner push and immutable
receipt checks remain in force. Physical installation on the owner's Fold is pending.
