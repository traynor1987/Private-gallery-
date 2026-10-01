# Phase 0 physical acceptance — owner-controlled, no publication

Status as of 1 October 2026: OWNER-REPORTED IN-PLACE UPGRADE / EXISTING PRIMARY ACCEPTANCE PASSED on `c0ce4013e65118843c4c69a9c868d99dc340cace`, signed acceptance run #51 / `36851657287`. The owner reports installing the exact same-signer APK over the existing installation without uninstalling or clearing data, and verifying that the existing encrypted Primary Vault remained accessible and functional. This is physical evidence for that bounded milestone. It does not establish unreported individual biometric, OEM transfer, separate-device restore, interruption, durability or Browser measurements. See [current admission evidence](evidence/OWNER_ACCEPTANCE_ADMISSION_2026-10-01.md). Remaining mandatory gates retain PHYSICAL ACCEPTANCE REQUIRED / UNRESOLVED status; Phase 0 is not yet admitted for Phase 1. The procedure and blank template below remain applicable to those unmeasured checks.

## Evidence identity and safe order

Start on a dedicated clean emulator or spare physical device with no owner Private Gallery installation/data. Normal debug/test APKs use the production application ID; install them only on that clean device. Browser probes use the separate `uk.co.traynor.privategallery.phase0evidence` package. Keep an independent copy of each synthetic encrypted backup and recovery secret, and prove restore before further lifecycle/fault tests. Never uninstall, clear data, delete Vault files, reset PIN/recovery, rotate a key, change biometric enrollment or perform destructive storage tests on the owner's app/device.

For every run, record this information before reporting PASS:

| Field | Required evidence |
| --- | --- |
| Source | Reviewed full Git SHA, baseline `a19218479eb9b9bcdb35ff0035fc78522263c891`, branch, clean/diff state |
| CI | Run URL/ID, event, run head SHA, every required job/check result; distinguish build from execution |
| Artifact | APK and test-APK SHA256, package ID, versionCode/versionName, artifact creation/run ID |
| Signer | `apksigner verify --verbose --print-certs` result and signer certificate SHA256; compare with known installed owner's signer before any owner upgrade |
| Fixture | Frozen `SHA256SUMS` file digest and check result; synthetic archive/media hashes; namespaced canary/run ID |
| Device | Synthetic versus owner, manufacturer/model, Android API/version/build/security patch, storage/encryption condition and available space |
| Browser | WebView package/version, feature support, host/instrumentation PID/process/UID, profile/suffix names; individual measurement coverage |
| Outcome | Exact test/milestone, timestamp, measured result, relevant before/after counts/digests, failures and omissions |

Keep owner backup locations, credentials, media names, pages, prompts and private diagnostics out of published reports. Record owner-only evidence privately; use synthetic markers in shared logs. Hashes identify tested artifacts and ciphertext; they do not prove decryptability or replace possession of the recovery secret.

## Owner manual signing workflow

The existing `Signed Device Acceptance Build` remains `workflow_dispatch` only, read-only repository permission and artifact-only. It accepts exactly `main` or `phase0/security-admission` in `traynor1987/Private-gallery-`; it does not publish a release or install on a device. No agent should dispatch it automatically. Permanent signing secrets remain in the repository's configured Actions secrets; do not paste them into a report or use a new/debug signer as a substitute.

After the owner chooses a reviewed SHA with green ordinary CI:

1. Open the repository's **Actions → Signed Device Acceptance Build → Run workflow**.
2. Select **phase0/security-admission** (or reviewed `main` after integration) in the Branch selector and manually click **Run workflow**. Confirm the run head SHA equals the reviewed SHA; if the branch moved, reject that artifact until the new SHA is reviewed and green.
3. Download `private-gallery-signed-device-acceptance-apk`. Check `build-sha.txt`, `app-release.apk.sha256`, `app-release-signature.txt`, and independently verify the APK hash/signature. From the extracted artifact directory, `sha256sum --check app-release.apk.sha256` verifies the file; use SDK `apksigner verify --verbose --print-certs app-release.apk` for the independent signature check. A successful workflow builds an acceptance artifact; it does not grant permission for an owner install.
4. Complete the synthetic rehearsal and retain independent encrypted owner backup/recovery material with a successful restore rehearsal on a separate clean device. Only then may the owner explicitly authorize and manually perform checkpoint 22's in-place upgrade.

Optional owner-entered CLI equivalent to step 2 (do not execute as automation):

```sh
gh workflow run signed-device-test.yml --repo traynor1987/Private-gallery- --ref phase0/security-admission
```

The workflow already exists on the default branch, which GitHub requires for manual dispatch. [GitHub's manual workflow guide](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow) documents the Branch selector and `--ref`. Signing workflow changes are reviewed source until the branch is pushed; no signed workflow has been dispatched by this document.

## B02 cross-platform transfer limitation

UNRESOLVED: The current [official Auto Backup guide](https://developer.android.com/identity/data/autobackup) also documents Android16 QPR2/API36.1 `cross-platform-transfer platform="ios"`, with required `platform-specific-params` bundleId/teamId/contentVersion. Its general missing-mode statement does not establish a safe omission policy for this new mode. The publicly inspected [AOSP FullBackup.java HEAD](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/app/backup/FullBackup.java) exposes only cloud/D2D configuration and cannot prove the new mode's runtime admission policy. Existing source/merged/packaged verification establishes all nine exclusions for those two modes only. Do not claim complete current transfer protection, add invented iOS identities, or treat omission as opt-out without authoritative implementation/provider evidence. B02 remains a mandatory unresolved admission gate until the missing-mode semantics and a supported exclusion/opt-out policy are established, then verified on the relevant platform. The verifier rejects an added unreviewed mode; this is a regression guard, not proof that an absent cross-platform mode is disabled.

## Twenty-two acceptance checkpoints

Record each as PASS with linked measured evidence, FAIL with a fixed failure category, NOT_SUPPORTED/CLOSED where the contract permits it, or PHYSICAL ACCEPTANCE REQUIRED. Keep the scope distinction between synthetic Primary acceptance and the separate Browser experiment.

1. **Review and CI identity.** Match Git SHA, artifact run head SHA and baseline. Ordinary CI must verify frozen input hashes, future reference vectors, recovery/session/fault/privacy JVM tests, lint, debug/test APKs, backup policy in source/merged/APK, and normal API36 instrumentation. Compile/unit-test the separate evidence variant. A missing check is unresolved.
2. **Synthetic isolation.** Confirm the initial acceptance device contains no owner app/data. Inventory package/application ID and test namespaces. Normal instrumentation may clean only its own synthetic namespace; evidence commands may force-stop only the evidence package. No owner uninstall/data-clear/reinstall step is permitted.
3. **Artifact and fixture integrity.** Check APK/test-APK and corpus digests before installation. Run `sha256sum --strict --check SHA256SUMS` in `app/src/test/resources/phase0/legacy-v1`, and compare instrumentation fixture copies. Never regenerate the frozen corpus to make a failed reader pass.
4. **Permanent signer continuity.** Verify the signed acceptance APK and record its public certificate SHA256. Compare it with the known installed release signer. Stop on mismatch or unverified signer; never work around it by uninstalling the owner app, clearing data or debug signing.
5. **Execution prerequisites.** Record device availability and test runner; execute the complete normal instrumentation suite on the synthetic API36 device. Preserve KVM/emulator acceleration/provider prerequisites on CI. No connected device or emulator boot failure means NOT RUN, not PASS. Emulator results identify that emulator only.
6. **Physical platform inventory.** Record Samsung/OEM model, API/build/security patch, WebView provider/version, available space, reboot state and actual biometric/Keystore capabilities. Cover the owner's actual supported configuration separately from the CI emulator.
7. **Playable synthetic backup.** Import a real decodable synthetic image and playable video, then export encrypted backup with collection/membership/favourite, current/previous crop, provenance, vaultOnly and Recently Deleted metadata. Record inventory, ciphertext archive digest and plaintext media digests privately within the synthetic run. Frozen PGVIDEO1 bytes prove format compatibility; codec playback needs this separate media sample.
8. **Bad restore isolation.** On a fresh synthetic target, reject wrong recovery, truncated archive, bad index/payload tags, duplicate entries and the rehashed video-tag archive. Existing verified ciphertext/key/envelope must survive failures unchanged; no failure may produce a writable empty Vault or fresh setup over existing data.
9. **Actual death/reboot restore.** Restore a verified synthetic archive, stop/kill the test app at bounded restore checkpoints as supported, and reboot. Restart locked, authenticate deliberately, verify key/envelope identity, every media digest/metadata field and actual playback. Reconstruction in a JVM test alone is insufficient.
10. **Simulated device loss.** Restore the retained verified synthetic archive on a second clean acceptance device using recovery possession and a deliberately chosen new local PIN. Confirm all contents and envelope bytes; prove restore without access to the source device.
11. **Pending recovery interruption.** Interrupt before one-time display, during display, after display and before confirmation using lock, Activity reconstruction, actual process death and reboot. Legitimate PIN/biometric Primary access can restart only pending setup. The original Vault/key remains intact; pending recovery cannot unlock/export.
12. **Recovery possession confirmation.** Re-enter the displayed secret; correct AEAD unwrap plus active-VDEK match alone promotes atomically. Wrong input, a different synthetic VDEK, revoked/expired epoch, commit failure and late confirmation must fail without rotation or old-envelope loss. Verify secret entry is cleared and not persisted in preferences/diagnostics.
13. **Legacy compatibility.** On synthetic legacy data, unlock/export the old envelope unchanged. Automatic creation/restart cannot overwrite it; explicit re-entry can mark possession verified without replacing bytes. Verify existing PIN and biometric access, unchanged Primary VDEK, index contexts and whole/PGVIDEO1 payloads.
14. **Packaged backup policy.** Run the verifier on the exact installed APK and its merged manifest. Confirm `allowBackup=false`, `fullBackupContent=false`, the designated rule resource, and all nine whole-domain exclusions in both cloud/D2D sections. Record the API36.1 cross-platform omission/admission policy separately; B02 remains UNRESOLVED until that policy is authoritatively established and verified. Existing narrow FileProvider cache paths must remain narrow.
15. **OEM cloud and device transfer.** On synthetic source/target devices only, place distinct canaries in root/file/database/sharedpref/external and each device-protected counterpart; separately observe cache/codeCache/noBackup platform exclusions. Exercise the actual OEM cloud restore and D2D/Smart Switch route available on the target model. Record each location's source/target result and coverage. A manifest check is not a physical Samsung guarantee; any copied sensitive-domain canary fails the gate.
16. **ABA and expiry under real callbacks.** Begin synthetic import/download/export/crop/AI work, lock and reopen Primary with a new epoch, then let old callbacks arrive. Old publication, metadata commit, upload handoff and source deletion remain denied. Cross the monotonic timeout while foreground callback/timer delivery is delayed; every protected admission independently rejects expiry.
17. **Owned cleanup.** While a video reader/editor/AI job/Browser upload is active, revoke by lock/background timeout and observe resource closure, job cancellation/completion, editor/preview/index clearing and absence from recents/screenshots where policy requires it. Lock-screen drawing alone is not cleanup completion. Native/GPU buffers remain best-effort zeroization only.
18. **Biometric lifecycle.** On synthetic physical devices, test success/cancel/error, late callbacks, enrollment changes and removal of device credential where relevant. Enrollment/sensitive confirmation bind their original attempt/epoch and cannot fall through to another purpose. Preserve independent existing PIN/recovery access and do not silently replace keys. Change enrollment only on the test device.
19. **Storage and interruption faults.** On expendable synthetic devices/storage only, measure low-space/write/fsync/rename failures and bounded process/power interruption. Preserve a sole verified copy; never commit staged incomplete media or delete its source after failed durability checks. Log limitations when power-cut/directory durability cannot be measured. Do not induce these faults on owner storage.
20. **Separate Browser/profile measurements.** Use only the evidence package and [Browser procedure](evidence/browser-process.md): exercise each cookie/HttpOnly/localStorage/IndexedDB/CacheStorage/service-worker/geolocation/session dimension; record provider/features/process identity. Unsupported dimensions remain NOT_SUPPORTED/CLOSED or UNMEASURED and fail the full probe. No fallback to the production default profile, remote login or owner page is permitted.
21. **Residue and diagnostic privacy.** Scan only synthetic canaries after destruction, evidence-package force-stop, reboot, and supported cold-profile deletion before loading profiles. Preserve scan coverage, omitted paths and per-dimension persistence; marker absence proves neither encryption nor forensic erasure. Check sanitized fatal/acceptance reports and portable archives for synthetic secret/URL/media/prompt markers. No Chromium tree/WAL patching or live engine directory copying is permitted.
22. **Owner manual in-place upgrade milestone.** Proceed only after required CI/synthetic gates pass, unchanged independent encrypted owner backup/recovery is held, a successful restore rehearsal is recorded on a separate clean device, signer/version continuity is verified, and the owner explicitly authorizes this specific reviewed signed artifact. The owner installs forward **in place**; never uninstall, clear data, replace the signer, use downgrade flags or perform migration/rotation. Reauthenticate and verify owner-selected existing media/playback/collections/crop/trash and PIN/biometric/recovery access; retain the prior verified backup. Any incompatibility stops acceptance and uses the reviewed recovery route rather than destructive repair. No release/tag/publication or Phase 1 starts automatically.

## Blank result record

```text
Milestone/checkpoint:
Status: PHYSICAL ACCEPTANCE REQUIRED
Reviewed Git SHA / CI run URL / event / head SHA:
APK / test APK SHA256; package/version; signer certificate SHA256:
Fixture SHA256SUMS digest / synthetic run ID:
Device synthetic-or-owner / model / OEM / API / build / security patch:
WebView provider/version / required features / UID/PID/process:
Procedure and timestamps:
Measured before/after results; evidence links:
Failure category / omissions / unmeasured conditions:
Owner manual authorization (only where required, kept privately):
```

The owner-reported signed in-place upgrade and existing Primary functionality are recorded above and in the linked admission evidence. Physical Samsung transfer, separate-device recovery, Browser provider/death/reboot and other unreported measurements are not asserted. Fill the remaining record from actual runs and preserve every unresolved mandatory gate in the admission decision.
