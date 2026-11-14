# NovaOS Code 80 acceptance

Code 80 is in development with candidate version 1.7.38.7 and versionCode 80. The installed production baseline is NovaOS 1.7.38.6, versionCode 79, package `uk.co.traynor.privategallery`. Use Android 17 (API 37) for verification and a Galaxy Z Fold for physical acceptance.

## Changes for owner review

- Nova Store has a first-party Android catalogue alongside the existing web catalogue. Android installs and updates require an owner-approved release entry containing the package ID, version name, version code, HTTPS release asset URL, APK SHA-256 and signing certificate SHA-256.
- Safe Notifications opens from Nova Launcher and remains behind Guardian authentication. Its concern filters and timestamps help navigate retained notifications.
- Personal Impact conversation validation uses feedback that was available before the held-out notification was captured. Older feedback remains readable and editable. Reports without a capture time are excluded from this validation.

## Physical Fold checks

Use an isolated, CI-verified test build and synthetic fixtures for these checks. Record the build commit, display state and outcome for each check.

| Area | Exercise | Expected |
| --- | --- | --- |
| Launcher | Rearrange and open apps, switch between folded and unfolded displays, then relaunch. Test Back, Home, Recents, drawer, folders and live Sanctuary countdown. | Layouts persist, controls stay reachable and the launcher keeps the current Sanctuary policy. |
| Notifications | Send synthetic messages to Main and Friends WhatsApp with Guardian notification access granted. Check quiet hours, lock screen, DND, notification actions, restart, then revoke and restore notification access. | Profiles stay separate. Protected content stays concealed. Review actions open Guardian and respect authentication and cooldown. |
| Sanctuary | Launch protected Main WhatsApp from Nova Launcher and from Android Settings. Try each configured early-exit challenge. Fold mid-puzzle, then test recovery and normal expiry. | Interception continues. Puzzle progress survives the display change. Recovery, emergency access and normal expiry remain available. |
| Nova Brain and Evidence Locker | Save an approved synthetic screenshot and OCR record, link it to Guardian, edit its feedback, and delete the feedback. Compare validation views across apps. | Evidence stays intact. Edits and deletions apply to learning references. Insufficient validation shows no percentage. |
| Keep My Peace | Create, edit and delete a synthetic journal entry, relaunch, and inspect Recents. | Journal data persists privately, with editing and deletion confined to the selected entries. |
| Nova Store | With approved fixtures, exercise install, update, checksum, version-name and signer mismatches, and attempt an install during Sanctuary. | Android requests confirmation for verified APKs. Mismatches and restricted installs are blocked. A verified update preserves the installed app's data. |
| Vaults and backup | Check Primary Vault fingerprint policy, Hidden Vault PIN policy, hidden launcher entry, Panic Exit, and encrypted backup and restore using disposable data. | Authentication boundaries hold and encrypted fixtures restore correctly. |

## Local incident validation

In Guardian Inbox, open Nova Brain settings and select **Incidents**. Supply independently labelled OCR in JSON rows with `incidentId`, `evidenceId`, `text`, and `patterns`. Use stable Evidence Locker record IDs, one row per record, and an empty patterns array for an ordinary example. Use at least 10 incidents, 20 examples with labelled patterns and 20 ordinary examples. Exclude training records and any recrops or copies of the same OCR. Nova Brain rejects repeated record IDs, duplicate or sufficiently similar OCR, and overlap with known training examples. The incident result reports OCR-level accuracy and error counts beside the shared Knowledge Meter. It updates only after a valid local evaluation.

## Production upgrade gate

1. Freeze the reviewed Code 80 source and record the exact passing Android 17 run and artifact digest. The distributed Code 79 baseline is [production run 43226350763, artifact 13949047165](https://github.com/traynor1987/Private-gallery-/actions/runs/43226350763/artifacts/13949047165). Download the original archive and run `python3 scripts/verify_code79_baseline.py /path/to/archive.zip`. This checks the archive and APK digests against the recorded release and checks the original release receipt. The existing production workflow targets Code 78 → 79.
2. Verify package, SDK levels, versionCode 80 and the original signing certificate. The pinned production certificate SHA-256 is `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.
3. Following explicit owner approval, use the existing signing controls and test an in-place Code 79 → 80 upgrade on a disposable API 37 environment with representative encrypted data. Verify Keystore continuity after restart, both vaults, Guardian and Evidence Locker, Nova Brain feedback, Sanctuary, launcher layouts, and backup. Keep the owner's installed app and data intact.
4. Record the physical Fold results, signed upgrade results, APK checksum and publication evidence before calling the release ready.

## Draft release notes

Code 80 adds protected notification review, per-mode Sanctuary challenges, optional Personal Impact feedback, the Keep My Peace journal, and first-party Nova Store verification. Nova Brain also supports local evaluation of independently labelled Evidence Locker OCR. The Store catalogue requires approved release entries before APK installation can be tested.

## Recovery

Keep the currently installed Code 79 app and its data on the owner’s Fold until device and upgrade acceptance are complete. In the disposable upgrade environment, record the baseline and candidate APK digests, package and signer, and validate representative encrypted fixtures after the update and a process restart. If any check fails, retain the logs and fixture evidence and stop delivery. For a regression discovered after an owner upgrade, repair forward with a higher version code and the original signing identity. Keep encrypted data and Keystore entries intact throughout recovery.
