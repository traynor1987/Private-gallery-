# NovaOS Build 85 — verified release

Completed 18 November 2026. NovaOS 1.7.39, versionCode 85, production package
`uk.co.traynor.privategallery`, Android 17 / API37 only.

Release: https://github.com/traynor1987/Private-gallery-/releases/tag/v1.7.39

PR #63 is merged. The production source is the exact main merge commit
`e062aac04c5de268db52f4b3fc6066e90cb4bbcd`. The stable tag resolves to that commit.
Later main commits contain release controls, publication verification and this report;
application, build and protected-core source remain unchanged from the verified source.

## CI repairs and source verification

CI #464 failed at Kotlin compilation: SettingsHome referenced `category` in its new
About refresh effect before declaring that local variable. The effect now runs after
category declaration through `RefreshUpdatesOnAboutEntry`. A focused regression covers
About entry, model recreation and avoiding automatic retry loops after failed checks.

CI #466 subsequently exposed an existing Evidence Locker test fixture problem during
lazy-list/IME transitions. The fixture now dismisses the keyboard and resolves the
stable evidence list and actual event action. Its data and ordering assertions remain;
displayed/enabled assertions were added. Both regression classes run before the full suite.

- Branch CI #468: https://github.com/traynor1987/Private-gallery-/actions/runs/44248409841
- Exact-main CI #470: https://github.com/traynor1987/Private-gallery-/actions/runs/44254498885
- Both passed all 299 selected API37 runtime cases, with zero failed or skipped.
- Focused updater runtime: 4/4 passed. Evidence Locker fixture class: 7/7 passed.
- Focused updater JVM suite: 21 passed. Release/publication/packaged-backup/checked-command CI suite: 39 passed.
- Protected-core/manifest preservation, relevant launcher/navigation/Guardian/Evidence checks,
  eight actual Backup process-stop/recovery phases and the launcher process restart passed.
- Main runtime artifact: `14407019908`, SHA-256
  `ba496c87b2055a0ef984456406531a1a9c3fbe0e9fb0a33031b779c93b3200d8`.
  Its original reports and source-bound gate were independently checked before signer access.

## Original signing and populated upgrade

Protected production run #36:
https://github.com/traynor1987/Private-gallery-/actions/runs/44261020498

The authentic published Build84 baseline is 1.7.38.8, source
`b79b85efc5bae8e0bedb832e21a62d462e6f4397`, APK SHA-256
`f0086165ec7e54ea90987b7001442161271347abcb820a4e696a0295397fcc30`.

The production run verified the existing permanent private key and original certificate,
built the frozen main source, and verified package, version, API37 identity, signature,
packaged OCR, backup exclusions and 16KiB alignment. The APK has one RSA4096 signer,
verified with APK Signature Scheme v2. Original certificate SHA-256:
`94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.

The disposable API37 rehearsal seeded populated encrypted Build84 data and used the
unchanged authentic baseline application to discover, download and verify the real signed
Build85 bytes. Only prepublication release transport was controlled. Android's real installer
performed the update; no replacement `adb install -r`, uninstall or data clear substituted
for that path. PackageManager reported installed Build85 before success was recorded.

Reauthentication, retained encrypted Vault/Guardian data, evidence/collections/chronology,
AI credentials, Sanctuary policy, both launcher layouts and Android's existing Home default
passed verification, including after force-stop/restart.

Production receipts: artifact `14409596449`, SHA-256
`7c41298156977439e5d1ee29c449b86fc23af209693c0826afad2a89498f86b7`.
Verified stable assets: artifact `14409486800`, SHA-256
`b32e20cd12397bfc1e5d46b2ce127df979d1dfd38fe66745fb7966a2a9df0ee6`.

## Published APK and real public in-app update

APK SHA-256:
`fede7eefd717cbac1806d8eddbd89a6687495852ae24f449675c3a093499401b`

APK size: 151,516,847 bytes. The Latest stable release contains the directly installable
`private-gallery-release.apk`, its SHA-256 file and production identity metadata, with
release notes. The public GitHub asset digest matches the verified candidate. Tag creation
and publication were automatic through the existing release workflow.

Publication run #11 passed:
https://github.com/traynor1987/Private-gallery-/actions/runs/44267631576

Its postpublication API37 test used an authentic production Build84 installation and normal
UI navigation. There was no instrumentation, substituted release transport or secondary test
application. The real public feed, real in-app APK transfer/verification and Android installer
completed the upgrade. Android reported Build85, the same owner PIN worked after restart,
and the actual Updates & About UI displayed:

- Installed: `1.7.39 · Build 85`
- Latest: `1.7.39 · Up to date`

Live evidence: artifact `14412069049`, SHA-256
`86cc8ee6988b1231326d67d4377316e5dec8b4c4307566277449cf379f860c47`.
It retains the Build84 update-available UI, Android installer UI, installed-version receipt,
Build85 Up-to-date UI and successful source/APK-bound live-upgrade receipt.

Publication-only environment repairs reused this identical APK and populated-upgrade proof.
The live runner now uses the same Command-line Tools22/API37 AVD configuration as the
successful production run, verifies the AVD target, waits for package/storage readiness,
honours public GitHub quota reset headers, and waits for a fresh accessibility root during
Activity restart. No security assertion was disabled and no app rebuild was used for these
publication-check repairs. Separate live checks of the original Build84 release-service
source and Build85 release-service source also accepted the actual public metadata.

## Owner acceptance

Physical installation on the owner's Samsung Fold is pending owner confirmation; all
installation results above are automated disposable-emulator results.

On installed Build84:

1. Open NovaOS → Settings → Updates & About → Check for updates.
2. Tap **Download update** (Build84's existing label). If Android requests install-source
   permission, allow NovaOS, return and retry the action.
3. Confirm **Update** in Android's installer, reopen NovaOS and authenticate normally.
4. Open Updates & About; verify `1.7.39 · Build 85` and `1.7.39 · Up to date`.

Build85 labels its future combined action **Download & Install**. Build84's older wording
is immutable before installation. No manual GitHub APK download, separate test APK,
CMS delivery or manual Git tag is required.
