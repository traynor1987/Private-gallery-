# NovaOS Code 82 / Build 86 — verified production release

18 November 2026: publication and automated in-app acceptance passed. Physical installation on the owner's Samsung Fold remains subject to owner confirmation.

## Source and merge

- PR [#64](https://github.com/traynor1987/Private-gallery-/pull/64) merged into main at `426f76ba2854dba221d5f27a485355d18aa3b6f8`.
- Exact verified production APK source: `15c1a2754d90bba0be58881101a72dc9aa480391`, an existing main commit containing the merged modern NovaOS codebase.
- Automatically published tag `v1.7.40` resolves to that exact source. Later main commits contain release controls, verification-driver repairs, tests and receipts; application and Gradle sources remain identical.
- Owner-installed baseline: 1.7.39 / 85, package `uk.co.traynor.privategallery`; original source `e062aac04c5de268db52f4b3fc6066e90cb4bbcd`.
- Authentic baseline APK: 151516847 bytes; SHA256 `fede7eefd717cbac1806d8eddbd89a6687495852ae24f449675c3a093499401b`. Original protected producer #36: `44261020498`, head `f67091037a92560cde320a2e22d83cb8f6bcf52b`.

## Signed APK and stable release

- [NovaOS 1.7.40 / Build 86](https://github.com/traynor1987/Private-gallery-/releases/tag/v1.7.40): Latest stable, draft=false, prerelease=false; initially published at 14:12:49 GMT.
- Package `uk.co.traynor.privategallery`; versionName `1.7.40`; versionCode `86`; min/target API 37.
- Normal installable APK `private-gallery-release.apk`: 151566003 bytes.
- APK SHA256: `9c7d5918e0321556a42507ed914216724eb0354c01c8359f9022dd253b94fe7d`.
- Original certificate SHA256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.
- One RSA 4096 signer; APK Signature Scheme v2 verifies. Both baseline and final APK were independently inspected with official Android build-tools 36, in addition to the protected workflow checks.
- Existing APK, `.apk.sha256` and `.metadata.json` assets, five-field identity metadata and GitHub Releases source are preserved. Release notes are in the release body. No CMS delivery or second channel.

## Mandatory verification

Exact-main [CI #487](https://github.com/traynor1987/Private-gallery-/actions/runs/44304942793), source 15c1 above, passed:

- 751 JVM cases across 167 original XML reports; zero failures/errors/skips.
- All 309 selected original Android 17 / API 37 cases; original reports independently validated against source and reviewed matrix.
- Protected-core security, Vault encryption/authentication, Guardian, Sanctuary, Evidence Locker, shared learning, launcher/navigation, updater and offline/credential regressions passed.
- Updater UI: 2/2 cover cases at 720x1600/density320 and 2/2 inner cases at 1768x2208/density320.
- Cold Evidence Locker correction/import/incident/search/remove/reopen regression 1/1; eight real encrypted Backup process-stop/restart boundaries and existing launcher restart checks passed.
- Focused local Kotlin updater/connectivity suite: 30 cases; backend suite: 13 cases; authenticated synthetic HTTPS/status checks used non-production credentials. No owner messages or Vault data.

Original runtime artifact `14431890753`, SHA256 `ae5901a76c27598a9144531c4f482f5881c4e56f2454008e9b1bb3dda3da2dc1`.

## Authentic populated 85 → 86

Protected [production #40](https://github.com/traynor1987/Private-gallery-/actions/runs/44318035057), authorization head `9ebf562a428e65c0b7d0f423f878c8dd69f980be`, passed. The reviewed fixture was compiled against original Build 85 source before signing access.

The exact original-key Build 85 APK was verified, installed on a fresh disposable API 37 emulator and populated with synthetic encrypted owner data. Its unchanged updater discovered, downloaded and verified the actual signed Build 86 APK; Android's real installer performed the upgrade and PackageManager reported 86. No replacement adb candidate install, uninstall or clear was used.

Seed, verify and after-restart phases passed. Preserved data includes primary/hidden encrypted records, Guardian, owner-encrypted evidence, connected collections, chronology corrections, evidence AI credentials, Sanctuary policy, both launcher layouts and Android Home default. Only prepublication release transport used a controlled loopback fixture; the genuine baseline, verification, FileProvider and installer were retained.

- Signed assets artifact `14434956528`, SHA256 `10622b44b8d513bb7088cd12d7921d4c5ba936a53dd660f24b6da3243cb4122f`.
- Upgrade receipts artifact `14433794397`, SHA256 `044204ddf065a5cff13777610ad73fc74e8d40e9d6d4c77503d6d5e7a43b64ce`.

## Actual public in-app acceptance

Existing [publication #13](https://github.com/traynor1987/Private-gallery-/actions/runs/44323070778), head `ba4d059fd7fcb4066cfa6be5f5808f1d621dc1b8`, passed with the same APK/receipts; no rebuild or re-sign for publication verification.

Authentic installed Build 85 was driven through its ordinary UI and real public GitHub metadata/APK download. No instrumentation, substituted transport, app-data editing or replacement adb candidate installation was used. Original UI and PackageManager evidence confirms:

1. Installed `1.7.39 · Build 85`; latest `1.7.40 · Build 86 available`.
2. Actual `Download & Install` action, revealed by scrolling past release notes.
3. Real download, original APK verification, Android installer confirmation and installed versionCode 86.
4. Process restart, same saved PIN authentication, installed `1.7.40 · Build 86` and `1.7.40 · Up to date`, including an explicit fresh check.

Public acceptance artifact `14435264900`, SHA256 `699907e2428dd3ad368f85abfc8c8fb16f19f4afecea3cc246bdc5b00acf28b2`. Independently validated `live-upgrade.json` records PASS, exact source/APK hash, installed_code=86, public discovery/download, real installer, same-PIN restart and Up-to-date; instrumentation=none; physical_fold=owner_confirmation_pending.

## VPS, privacy and offline results

- Actual existing API inspected: GET `/health`, authenticated GET `/guardian/status`, existing consented POST `/guardian/analyze`.
- Fresh live checks around 14:13 GMT: `https://brain.jamestraynor.co.uk/health` returned 200/`{status:ok}`; unauthenticated `/guardian/status` returned 401. Normal TLS certificate validation passed (verification 0).
- Synthetic authenticated integration passed. Live owner authentication remains unclaimed until the owner enters the existing backend token locally in NovaOS.
- Status client uses validated HTTPS, rejects redirects/ambiguous origins, bounded timeouts and read-only retries; clear disconnected/auth/error/offline states. No messages, evidence or feedback in requests/logs.
- Origin/token use existing Android Keystore AES-GCM storage and are excluded from backups; no hardcoded production secret.
- Guardian/Evidence retain explicit sensitive-request preview and consent. No automatic WhatsApp, Evidence Locker or private-feedback uploads, Sanctuary changes or Cloud Teacher activation.
- Offline local learning, encrypted knowledge, Guardian, Evidence Locker and Sanctuary remain available. Existing staging/OpenAI-disabled restrictions are preserved; connecting does not enable paid analysis.
- No backend deployment, VPS OS/unrelated-service change, Vault encryption rewrite, new release infrastructure or signing-key change.

## Verification repairs

CI #485 exposed an Evidence Locker Save gesture racing the IME layout change. The fixture now observes real focus/keyboard state and resolves Save after closure, retaining raw OCR/correction/data assertions. CI #486 caught a Unit-returning Compose edit incorrectly chained to an assertion; the separate assertion compiled and passed CI #487.

Producer #39 reached Build 85's verified installer-handoff marker, then the host observer aborted because the fresh UI dump file was absent during transition. Bounded fresh-hierarchy retries discard stale dumps, retain diagnostics and still require the real enabled Android action and PackageManager 86. Three regressions and producer #40 passed.

Publication #12 already captured genuine Build 85 discovery of 86. Its driver incorrectly expected download controls and the version summary in one viewport despite long release notes. Observed-container scrolling now reaches real controls and returns to the summary. Three regressions and publication #13 passed. The publication receipt fixture derives current codes from the pinned candidate and explicitly rejects mismatched baseline/candidate/installed codes; all 15 publication tests pass. Total focused host verification tests: 21.

Release-only repairs reused unchanged source 15c1 and original CI #487; publication retries reused the exact signed production #40 artifact and upgrade receipts. Mandatory source, cryptographic, installed-version and preservation assertions were not weakened.

## Owner steps and remaining acceptance

1. NovaOS → Settings → Updates & About → Check for updates (scroll past release notes if needed).
2. Confirm 86 available; tap Download & Install, allow the existing Android install-source permission if requested, and confirm Update in Android.
3. Reopen/authenticate normally; confirm 1.7.40 / Build 86 and Up to date.

Installed Build 85 uses its existing MB/bar interface to install 86. The new byte-based percentage/size/speed/cancel/retry dialog becomes active in Build 86 for subsequent updates. Installation is confirmed only from Android's installed version.

Optional backend setup after updating: Settings → AI editing → Nova Brain connection → Connect; enter `https://brain.jamestraynor.co.uk` and the existing backend owner token (not an OpenAI key), Save securely, then Check connection. No paid analysis or content upload is activated.

Remaining owner-only confirmations: physical installation/folded/unfolded acceptance on the owner's Fold and live authentication using the owner's backend token. The verified path requires no manual APK download, uninstall or data reset.

