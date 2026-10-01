# B02 backup and transfer assurance — 1 October 2026

**Conclusion: app-controlled backup policy is complete for the examined platform implementation; no production code or XML change is needed.** Android 16 QPR2 has an explicit cross-platform admission check that rejects this application's `allowBackup=false` policy. It also requires opt-in platform parameters, which this app does not declare. The generic Auto Backup guide's missing-mode sentence is not the basis for that conclusion. Samsung/OEM behavior outside the supported Android backup contract remains an explicitly bounded platform risk, not a fabricated physical PASS or a requirement for a universal OEM guarantee.

Scope: Primary-only Phase 0. Reviewed project HEAD `fb223993ad69ba3dbaac57b2c5462daffc7f5550`, branch `phase0/security-admission`; audited main `a19218479eb9b9bcdb35ff0035fc78522263c891`. No owner device operation, Phase 1, Hidden feature, migration, key rotation, release, commit or push was performed by this investigation. Concurrent edits outside the inspected backup files were not changed.

## Evidence matrix

| Route | Supported application control and observed configuration | Evidence and bounded disposition |
| --- | --- | --- |
| Android legacy/cloud | Manifest has `allowBackup=false`, `fullBackupContent=false`, no custom backup agent. Android 12+ cloud rules additionally exclude every documented domain recursively. | Source verifier PASS. Pinned QPR2 eligibility returns the allow-backup flag for cloud. App policy complete; no cloud restore execution is claimed here. |
| Android D2D | `allowBackup=false` alone is insufficient on some devices. `device-transfer` explicitly excludes `root`, `file`, `database`, `sharedpref`, `external`, `device_root`, `device_file`, `device_database`, `device_sharedpref`, each at `.`. | Android's documented app control is exercised. Verifier checks separate domains, merged manifests and every packaged rules configuration. Physical OEM compliance is unmeasured. |
| Android 16 QPR2/API 36.1 cross-platform | `allowBackup=false`; no `cross-platform-transfer` or iOS `platform-specific-params`. | Pinned official admission requires allow-backup **and** explicit iOS support. Restore additionally needs matching source/target bundle/team IDs. B02 omission/admission uncertainty is resolved for this implementation. No iOS identity is invented. |
| Platform cache exclusions | Android documents cache, code cache and no-backup directories as excluded. | These are independent platform exclusions, not evidence that a vendor performs arbitrary file copies safely. |
| Samsung Smart Switch consumer routes | Samsung supports choosing content categories or Custom on cable, wireless and external-storage transfers; desktop restore supports deselecting information. | These are user-operated controls. They are not a documented per-app manifest guarantee or an app-callable vendor opt-out API. Actual Samsung route/build behavior is unmeasured residual risk. |
| Samsung Knox Configure | Administrators can disable Samsung Cloud and Smart Switch packages through a configured profile. | Managed-device policy requiring Knox administration. Private Gallery has neither that role nor authority to impose it. No new privileged policy/API is added. |
| Private Gallery encrypted archive recovery | App export/import is independent of OS automatic backup. | Emulator/device restore evidence belongs to the separate recovery assurance record. This research executes no archive restore and does not substitute source inspection for that evidence. |

## Authoritative QPR2 admission and restore implementation

The official `android16-qpr2-release` branch resolved to frameworks/base commit **`45034f0663f960d9ee5fb0a101a4732b71f6e2f4`**, tree `6667cba9cf8225451f0822ad94cb21902025dfa3`, committed 7 November 2025. Files were downloaded from Gitiles `?format=TEXT`, base64-decoded, and then fetched again at that exact commit; all byte comparisons matched. The browser source tool could open the branch tree but errored on some files; direct HTTPS retrieval of official Gitiles content succeeded. Main/HEAD previously inspected in the acceptance procedure was a different, older implementation and is insufficient evidence about QPR2.

`BackupEligibilityRules.isAppBackupAllowed`, cross-platform case (lines 306–309 in the pinned decoded source):

```java
return allowBackup
        && (app.packageName.equals(PACKAGE_MANAGER_SENTINEL)
                || appSupportsCrossPlatformTransfer(app, PLATFORM_IOS));
```

Private Gallery is not the package-manager sentinel. `appSupportsCrossPlatformTransfer` tests for nonempty platform parameters. `PlatformConfigParser` builds those parameters only from supported `cross-platform-transfer platform="ios"` sections. With this app's two sections the map is empty; the manifest denial independently blocks admission. `PerformFullTransportBackupTask` culls ineligible packages, while `PerformUnifiedRestoreTask` builds its acceptance set using the eligibility check. `FullRestoreEngine.findValidPlatformSpecificParams` also requires matching declared bundle/team IDs, setting cross-platform restore policy to IGNORE when none matches. This supports both export and import boundaries.

`UserBackupManagerService.getBackupDestinationFromTransport` selects D2D first when its flag is present, cross-platform next when enabled and its iOS flag is present, otherwise cloud. The app protects each resulting route: whole-domain D2D exclusions, cross-platform admission denial, and cloud denial. This is a statement about the examined Android implementation, not all future transports.

Upstream `BackupEligibilityRulesTest` contains named not-opted-in rejection and opted-in acceptance cases; `FullBackupTest` covers cross-platform parsing and feature-flag conditions. These tests were **inspected, not executed**. The former rejection test leaves its rules resource ID at zero, so it corroborates opt-in eligibility intent; the actual nonzero-resource/no-cross-platform conclusion above follows the inspected parser. No local mock of Android admission is presented as runtime proof.

## Exact official source snapshots

All links below use the same pinned commit. SHA256 identifies the decoded UTF-8 source bytes, not the base64 response.

| Source | Decoded source SHA256 |
| --- | --- |
| [core/java/android/app/backup/FullBackup.java](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/core/java/android/app/backup/FullBackup.java) | `ec60723acc6e2df5713e6a744763cad9515a843e739cb973438687520f85af73` |
| [core/java/android/app/backup/BackupAgent.java](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/core/java/android/app/backup/BackupAgent.java) | `07148bf73fe3df29d58cbc7fbea51221dd01f28dc80702485115b726b71a5138` |
| [core/java/android/app/backup/BackupAnnotations.java](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/core/java/android/app/backup/BackupAnnotations.java) | `366503846151b8fa829acc7eb5c18ec731fe6a39a32ad292bb2de3c51ef3d58f` |
| [services/backup/java/com/android/server/backup/utils/BackupEligibilityRules.java](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/services/backup/java/com/android/server/backup/utils/BackupEligibilityRules.java) | `f7824ed18d750fdafe0ec34cdccbe5ee4eed2cd01d12d191500fc80a6b0d45d2` |
| [services/backup/java/com/android/server/backup/UserBackupManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/services/backup/java/com/android/server/backup/UserBackupManagerService.java) | `0a71227a44b367107f3c94622f7acd62425bad3c207973b11606fbec82ee2fbd` |
| [services/backup/java/com/android/server/backup/crossplatform/PlatformConfigParser.java](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/services/backup/java/com/android/server/backup/crossplatform/PlatformConfigParser.java) | `5af96c8121026264af24f91f79d3afd67baef9d78822bea425272bc4f43881b7` |
| [services/backup/java/com/android/server/backup/restore/PerformUnifiedRestoreTask.java](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/services/backup/java/com/android/server/backup/restore/PerformUnifiedRestoreTask.java) | `6d354a7cf97d505d75e8541e9c0eef2bfce0e9ceb87ac2574069200fe2fcb7a2` |
| [services/backup/java/com/android/server/backup/restore/FullRestoreEngine.java](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/services/backup/java/com/android/server/backup/restore/FullRestoreEngine.java) | `28163c9409fd66d3ed3431310a34dab3532149953f4430c1af09c765caefdc4b` |
| [services/backup/java/com/android/server/backup/fullbackup/PerformFullTransportBackupTask.java](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/services/backup/java/com/android/server/backup/fullbackup/PerformFullTransportBackupTask.java) | `bdcf6fa8767af7971967314e4fcd044a2bb7c3e7c0a4f3c3bbd55c2be1a480ae` |
| [services/tests/mockingservicestests/src/com/android/server/backup/utils/BackupEligibilityRulesTest.java](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/services/tests/mockingservicestests/src/com/android/server/backup/utils/BackupEligibilityRulesTest.java) | `6a8e231453e73158067a5789364b8ff36f56c494bfcdbaec85295a27530df21b` |
| [core/tests/coretests/src/android/app/backup/FullBackupTest.java](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/core/tests/coretests/src/android/app/backup/FullBackupTest.java) | `0ff8e27cd999a9f80d4613fb72faf0b40074b89f7b909d435bc790ebae3f6fa4` |

## Official documentation and supported Samsung controls

Retrieved 1 October 2026. These live pages have the indicated page version/date; they do not identify a tested Samsung firmware or Smart Switch app version.

| Official source and page version | What it establishes |
| --- | --- |
| [Android Auto Backup guide](https://developer.android.com/identity/data/autobackup), updated 26 February 2026 | Nine domains, recursive exclusion rules and platform cache exclusions; API36.1 cross-platform syntax requiring iOS identity/version parameters. Its generic missing-mode guidance describes content selection; the pinned admission code above establishes whether this app enters that mode. |
| [Android 12 behavior changes](https://developer.android.com/about/versions/12/behavior-changes-12) | Some manufacturers do not use `allowBackup=false` to disable D2D; cloud and D2D require separate extraction rules. |
| [Samsung transfer guide](https://www.samsung.com/us/support/answer/ANS10001345/), current retrieved page, no displayed update date | Custom/content selection on cable, wireless and external-storage transfers. A user selecting Everything is not an app admission-policy test. |
| [Samsung Smart Switch FAQ](https://www.samsung.com/us/support/answer/ANS10001344/), current retrieved page, no displayed update date | Content categories can be deselected; supported data varies by connected device; source content remains after transfer. |
| [Samsung UK Android transfer guide](https://www.samsung.com/uk/support/mobile-devices/how-do-i-switch-my-android-device/), updated 14 May 2026 | Desktop backup/restore and deselection; broad statements about secure/encrypted data and limitations. Those statements do not identify Private Gallery or prove exclusion of its arbitrary encrypted files. |
| [Samsung Knox Configure backup/restore restriction](https://docs.samsungknox.com/admin/knox-configure/kbas/kba-735-how-to-disable-backup-restore-device-data/), updated 30 May 2025 | An administrator can restrict `com.osp.app.signin` and `com.sec.android.easyMover`. This is a Knox management control, not an ordinary third-party application privilege. |

No official public Samsung application-level Smart Switch opt-out API beyond Android backup/extraction controls was established in this investigation. That is a bounded research result, not proof that no undocumented vendor interface exists. No extra metadata, broad permission, custom agent, dummy iOS identity or enterprise dependency is warranted by the retrieved evidence.

## Local verification and artifact identity

1. `python3 scripts/verify_backup_exclusions.py` — PASS for source policy.
2. `python3 -m unittest discover -s scripts/tests -p test_verify_backup_exclusions.py -v` — **14 tests PASS, zero skips** when `ANDROID_HOME` points to a temporary SDK layout linking the existing AAPT2 36.0.0 and API36 android.jar. Tests reject missing/narrow domain exclusions, unsafe merged manifests, unexpected transfer modes, backup enablement, unsafe binary APK rules, shortened ZIP paths and unsafe resource-qualifier overrides. An earlier run without that SDK layout ran seven tests and skipped the packaged-test class; it is superseded by the complete run.
3. `python3 scripts/verify_backup_exclusions.py --apk /tmp/pg-phase0-acceptance-51.apk --aapt2 /tmp/pg-phase0-sdk/build/android-16/aapt2` — PASS for current source and the previously identified signed acceptance #51 APK's compiled policy. APK SHA256: `306d1f29a7bcaa7c28eccf024c04ecd59ff3da0e5562847182e14b72d217827d`. This check does not assert that artifact was built from the current HEAD or supply a current merged-manifest measurement.

Inspected source SHA256 values:

| File | SHA256 |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | `6b9aee37c460fe7a6ad3cf9bab21370ad08f050737cef751b553311bf74539b3` |
| `app/src/main/res/xml/data_extraction_rules.xml` | `d92463297f4ed5cd11c53638720729f85dd084c7d3577913df1311d048598f47` |
| `scripts/verify_backup_exclusions.py` | `36c66427926d56f0862cb898722fcf68a08c3f3cbc8c6e3947f457b16f0911f6` |
| `scripts/tests/test_verify_backup_exclusions.py` | `8af42d9c563f0b987440c55fa09ff8a214bf2baffac1827a81abbca9d535515c` |

The verifier already rejects every new transfer section and every custom backup agent, guarding against future unreviewed opt-in. No implementation change, new runtime dependency or mirrored platform test was necessary. Whole Gradle, owner-device and API36.1 runtime/transport tests were not run by this investigation.

## Bounded Phase 0 disposition

Under the owner's explicit authorization to accept documented residual platform risks, **B02 app-policy assurance can close with the evidence above**. Record Samsung firmware/Smart Switch behavior, privileged OEM transport behavior and future-platform/provider changes as **ACCEPTED RESIDUAL PLATFORM RISK / NOT PHYSICALLY MEASURED**, preserving their actual status. Do not label them measured PASS. Future optional Samsung canary tests can improve confidence, but cannot establish all-OEM compliance.

This closes the earlier omission/admission research uncertainty. It does not itself close recovery, signer, durability, biometric or other Phase 0 gates, and it does not authorize Phase 1 or publication. Exact final merged/APK policy verification remains part of ordinary artifact assurance. If actual future transfer copies a sensitive app-domain canary, that observation contradicts the supported-control assumption and requires a new platform assessment.
