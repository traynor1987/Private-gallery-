# NovaOS Build 87 verification — 18 November 2026

Build 86 audit and focused repair: NovaOS 1.7.41 / versionCode 87 / Android 17 only.
Release and automated update verification passed. Live positive owner-token authentication,
actual deployed VPS mode/analysis flag and physical Fold acceptance remain owner-only checks.

## Verified source and artifacts

| Item | Verified identity |
| --- | --- |
| PR | [#65](https://github.com/traynor1987/Private-gallery-/pull/65), merged into main |
| Frozen production source / stable tag target | `9075b111a87e9dbcf39566b71c2763b380b58088` |
| Publication authorization on main | `75d6b23f86b58d82905ac94caba4cd0c70abbb80` |
| Package | `uk.co.traynor.privategallery` |
| Release | [v1.7.41](https://github.com/traynor1987/Private-gallery-/releases/tag/v1.7.41), Latest stable, normal directly installable APK plus SHA256 and metadata |
| APK bytes | 151582387 |
| APK SHA256 | `05b1122c80e6746e242419d08f48882d516ac6730eda4a4befa57a8e83afd0d4` |
| Original production certificate SHA256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |
| Authentic Build 86 source | `15c1a2754d90bba0be58881101a72dc9aa480391` |
| Authentic Build 86 APK SHA256 | `9c7d5918e0321556a42507ed914216724eb0354c01c8359f9022dd253b94fe7d` |

The original production private-key probe and certificate pin passed. The downloaded signed Build 87
APK was independently checked with apksigner/aapt: single original signer, v2 signature,
correct package/version, release/non-debuggable, minimum and target API 37. Integrity metadata
and published GitHub asset digest match the tested APK. The existing workflow created the tag;
no owner tagging or APK download was required. All app and Gradle source remains byte-identical
to the frozen main source; later commits record release controls and verification only.

## Audit and repair

Build 86 shipped a real validated HTTPS status client, encrypted Keystore credential storage,
configurable origin/token and optional reviewed Guardian/Evidence cloud requests. It supplied
no preconfigured production token or origin. Its connection controls were under Settings →
AI editing and in optional Cloud Teacher review; there was no ordinary Nova Brain Settings
category or durable last-authenticated history. Installing Build 86 did not establish VPS authentication.
Local Knowledge “Not yet validated” is unrelated to backend connectivity.

Build 87 adds Settings → Nova Brain using the existing client/store. It shows real check results,
authentication errors, server unavailability, offline/privacy/analysis indicators and encrypted
last-success history. Legacy Build 86 credentials remain readable; saving/replacing/removing them
clears history and stale responses cannot resurrect old configuration. Historical environment
and analysis values are labeled as historical, not a continuous connection or deployment audit.
Opening Settings sends no requests. Test connection sends only read-only status requests.

The updater, production certificate pin, package identity, release metadata format, Vault
cryptography, Guardian/Evidence engines, local learning and Sanctuary are preserved.

## Automated verification

- [Exact-main CI #492](https://github.com/traynor1987/Private-gallery-/actions/runs/44356300929):
  753 JVM cases across 168 original reports; zero failures/errors/skips. All 316 selected API 37
  instrumentation cases passed. The strict original-archive parser also verified all focused
  groups, four Nova Brain cases on each of cover 360dp and inner 884dp viewports, existing updater
  layouts, eight actual backup process-stop/restart phases and a launcher process restart.
- Original runtime artifact `14456675740`, SHA256 `140962bccfcf8479db153941b5fd02dccecd4ce3852c4757087a95fd6a5a4446`.
- Focused local JVM checks (35) and backend checks (13) passed, including local TLS/authentication,
  response/error handling and staging no-OpenAI-call guards. Positive local/mocked authentication
  is contract evidence, not proof of successful owner-token authentication to the live VPS.
- A separate real Android 17/API 37 HTTPS test contacted the existing public VPS using a deliberately
  invalid synthetic token and received authentication rejection (passed in 0.872 seconds).
  Ordinary CA/hostname validation remained enabled; no real token or private content was used.
- [Protected production #42](https://github.com/traynor1987/Private-gallery-/actions/runs/44366264003):
  compiled the reviewed fixture against authentic Build 86 source before signer access; verified its
  original published APK; built Build 87 from the frozen main commit; passed lintRelease, package,
  original signer, OCR distribution, 16 KiB alignment and backup-exclusion gates.
- Authentic populated Build 86 → 87: existing unmodified Build 86 updater, controlled pre-publication metadata,
  actual signed Build 87 bytes and Android installer; PackageManager observed Build 87. Seed, post-install
  and post-restart fixtures passed without uninstall/clear. Vault/hidden encryption, Guardian
  archive/labels, Evidence contents/collections/credentials, local state, Sanctuary, both launcher
  layouts and the legacy encrypted Nova Brain configuration survived. Data was synthetic.
- Production assets artifact `14458302200`, SHA256 `823a5281ff3829f981c84eb20312e68ed6b662c9cea05f8ebb5ecb09f26712d1`;
  original upgrade receipts `14458207246`, SHA256 `075dffe3e0d92b141aff5030cd686f1adcc8eb75b3b7d17c2fae9f3c1dac5e46`.
- [Public in-app verification #14](https://github.com/traynor1987/Private-gallery-/actions/runs/44369923837):
  authentic installed Build 86, real public GitHub metadata and real APK download through NovaOS;
  verification, Android installer, installed Build 87, restart with the same PIN and the real Up to date
  screen all passed. No instrumentation, substituted transport, second test app or candidate
  adb installation was used in this public test. UI XML and `live-upgrade.json` are retained in
  artifact `14459835155`, SHA256 `86ccd8fd94c02c9a27ce1fdcc44e16f7e61a74b445d4e2f91f96e371a20cf648`.

Actual public UI: `Installed: 1.7.40 · Build 86`; `Latest: 1.7.41 · Build 87 available`;
`Download size: 151.6 MB`. After Android installation/restart: `Installed: 1.7.41 · Build 87`;
`Latest: 1.7.41 · Up to date`.

The first protected attempt #41 stopped before signer access on Maven Central HTTP 429 while
resolving the fixture compiler plugin. A fresh owner-authorized protected run #42 succeeded
without app/source/gate changes. Passed main Android/JVM evidence was reused. The earlier
branch guard-routing omission was corrected with failing-before/passing-after shell coverage;
the pinned legacy and modern protected-engine assertions were retained.

## VPS, privacy and explicit verification limits

Actual `https://brain.jamestraynor.co.uk/health` returned HTTP 200 and unauthenticated
`/guardian/status` returned HTTP 401 with normal TLS verification passing. The live Android
check separately verified TLS/rejection. These are reachability and authentication-failure
results, not successful authenticated application connectivity.

No real owner token, SSH agent or configured VPS identity is available in this workspace.
The exact current deployed source, staging/production mode and server `analysis_enabled`
therefore remain unverified. Existing source staging mode forces analysis disabled, avoids
OpenAI credential/budget mounts and binds loopback only. No VPS, NGINX, Portainer, database,
secret, operating-system or paid-analysis change was performed.

Guardian and Evidence Locker share saved configuration for existing optional manual requests:
review a revealed/eligible message or OCR excerpt and explicitly consent before `/guardian/analyze`.
They do not automatically synchronize learning, upload WhatsApp/evidence/private feedback or
change Sanctuary. No real private-content request or paid analysis was exercised in this repair.
Automatic Cloud Teacher was not activated. Current live server analysis availability must be
read from a successful authenticated status response, not inferred from health or a mock.

Automated viewports and emulator installation are not physical Samsung Fold acceptance.
The owner confirmed physical Build 86 installation earlier; physical Build 87 installation remains pending.

## Owner verification

1. In installed Build 86 open NovaOS → Settings → Updates & About → Check for updates.
2. See 1.7.41 / Build 87 available; tap **Download update** and watch real size/progress.
3. Let verification finish and confirm **Update** in Android's installer.
4. Reopen NovaOS and confirm 1.7.41 / Build 87 and **Up to date**, using the existing PIN/data.
5. Open **Settings → Nova Brain → Test connection**. If unconfigured, tap **Connect**, enter
   `https://brain.jamestraynor.co.uk` and the existing backend owner token locally, **Save securely**,
   then **Test connection**. This token is not an OpenAI API key; do not send it in chat.
6. Confirm **Connected · authenticated test passed**, environment, last successful check and
   server-analysis indicator. Do not enable analysis or send private content for this check.

If the token is unavailable/rejected, verify the existing owner-token file at
`/etc/nova-cloud-teacher/secrets/owner-token` through the owner's existing trusted VPS access.
The existing authenticated status response and, if needed, the existing container command's
`--staging` flag establish current deployment state. No setup/deploy commands, key rotation or
service/OS changes are needed for this read-only check. This is the smallest owner-only boundary.
