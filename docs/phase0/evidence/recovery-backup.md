# Phase 0 recovery and backup evidence

Current focused gate closure is recorded in [the 1 October Phase 0 closeout report](../../../PRIVATE_GALLERY_2_0_PHASE_0_CLOSEOUT_2026-10-01.md). This record retains its historical execution scope. The later bounded owner upgrade, supported B02 policy and new synthetic restore/interruption evidence supersede corresponding earlier absent/unresolved statements only; unreported individual physical results are not inferred.

Date: 2026-09-29. Synthetic keys/envelopes only. Baseline: a19218479eb9b9bcdb35ff0035fc78522263c891.

## IMPLEMENTED PHASE 0 CHANGE

`RecoveryVaultKeyStore` delegates to a pure ciphertext-only lifecycle. States are NOT_CONFIGURED, PENDING_CONFIRMATION, CONFIRMED and fail-safe CORRUPT. Creation writes only `pending_*` encrypted envelope fields. Pending recovery cannot unlock/export. Authenticated Primary access can restart pending setup after a lost display. Confirmation AEAD-unwraps owner re-entry, compares the result with the existing active VDEK using `MessageDigest.isEqual`, wipes temporary material, and atomically promotes the record through the caller's final operation commit boundary. A changed record rejects stale promotion.

Legacy records with exactly the original salt/nonce/ciphertext remain readable/exportable as CONFIRMED and explicitly `isLegacyExisting=true`, `isPossessionVerified=false`. Creation/restart cannot replace them. Explicit successful re-entry adds possession metadata without replacing the envelope bytes. Authenticated archive restore preserves original envelope bytes and records possession already proven by the archive reader. Restore rollback is confined to the envelope installed by that store instance.

A failed preference commit is unavailable in the current lifecycle and across Activity/store reconstruction in the same process, even if SharedPreferences published the attempted change in memory. The failure registry uses the SharedPreferences identity and contains no secret. Process-death reconstruction reads the platform's durable record; tests model an atomic persistence rejection retaining the old record. Partial/mixed/unknown/wrong-type or invalid-length records never permit fresh setup. The actual Android disk-fault behavior is still a device acceptance requirement.

`allowBackup=false` and `fullBackupContent=false` remain unchanged; the latter explicitly disables legacy full backup rather than enabling a new legacy XML rule file. API31+ cloud and device transfer now exclude all nine applicable domains at their whole-domain roots: root, file, database, sharedpref, external, device_root, device_file, device_database, device_sharedpref. FileProvider paths remain unchanged.

The verifier parses source and optional merged manifest, then decodes APK binary XML using SDK aapt2 and resolves the manifest resource ID. It checks every packaged/source resource configuration found for the designated rule. Missing sections/domains, narrower exclusions, backup enablement, a custom backup agent, changed rule resource, or a new unreviewed transfer mode fail closed.

## TESTED EVIDENCE

| Check | Result | Evidence |
| --- | --- | --- |
| Lifecycle skeleton RED | 8 failures, including wrong legacy/corrupt state assertions | `recovery-backup/recovery-lifecycle-red.txt` |
| Pending policy RED | 1 failure in 2 tests | `recovery-backup/recovery-policy-red.txt` |
| Failed write reconstruction RED | expected CORRUPT, observed CONFIRMED after store reconstruction | `recovery-backup/recovery-reconstruction-red.txt` |
| Pure lifecycle/policy GREEN | 12 JUnit tests pass | `recovery-backup/recovery-pure-green.txt` |
| Android store source compile | exit 0 against API36 android.jar | `recovery-backup/recovery-android-compile.txt` |
| Backup source baseline RED | original root-only XML leaves 16 missing domain exclusions | `recovery-backup/backup-source-red.txt` |
| Backup behavior GREEN | 10 Python tests pass; 18 independent domain-removal mutations plus narrow path/missing mode/new mode/manifest changes; actual binary APK mutation cases | `recovery-backup/backup-mutations-green.txt` |
| Actual debug merged manifest | source + merged manifest PASS | `recovery-backup/backup-merged-green.txt` |

The pure runner uses the locally cached Gradle-distribution Kotlin1.9.24 compiler, JUnit4.13.2 and BC1.78.1, not the app's declared Kotlin2.0.21/BC1.79. This establishes algorithm/state behavior and Android source compilation, not the complete app build. Gradle with the downloaded complete JDK17 was attempted but initially blocked by proxy PKIX trust; full app-suite evidence belongs to the final admission run. The binary APK mutation tests use SDK build-tools36 aapt2 and API36 android.jar to package synthetic minimal apps; they are executable binary-format checks, not text grep or a production app APK claim.

Commands:

```sh
python3 -m unittest discover -s scripts/tests -p '*.py'
python3 scripts/verify_backup_exclusions.py \
  --merged-manifest app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
# Required once actual app APK exists:
python3 scripts/verify_backup_exclusions.py \
  --merged-manifest app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --aapt2 /tmp/private-gallery-android-sdk/build-tools/36.0.0/aapt2
```

## SOURCE FACT

Android's [Auto Backup documentation](https://developer.android.com/identity/data/autobackup) defines independent cloud/D2D domains and excludes cache, codeCache and noBackup directories by platform contract. It warns that some manufacturers do not disable D2D migration with allowBackup=false. The [application manifest reference](https://developer.android.com/guide/topics/manifest/application-element) independently documents that manufacturer limitation. The installed API36 platform's `data/res/values/attrs_manifest.xml` declares fullBackupContent as `reference|boolean` and documents boolean opt-in/out; false retains the project's explicit legacy opt-out. These are source contracts, not measured OEM behavior.

## PHYSICAL ACCEPTANCE REQUIRED / UNRESOLVED

No Samsung physical device transfer, OEM cloud restore, signed owner upgrade, reboot/low-storage SharedPreferences disk failure, or recovery UI interruption was executed here. All remain physical acceptance requirements as applicable. No physical Samsung guarantee is claimed. Actual app packaged XML check remains unresolved until a complete APK build is available; the verifier and synthetic packaged mutation checks are ready. Primary VDEK, media/index crypto formats and existing ciphertext were not changed by this work.
