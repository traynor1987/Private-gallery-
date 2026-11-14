# NovaOS Code 80 acceptance

Code 80 is in development. The installed production baseline is NovaOS 1.7.38.6, versionCode 79, package `uk.co.traynor.privategallery`. Use Android 17 (API 37) for verification and a Galaxy Z Fold for physical acceptance.

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

## Production upgrade gate

1. Freeze the reviewed Code 80 source and record the exact passing Android 17 run and artifact digest. Prepare Code 80 release controls using the exact distributed, original-key-signed Code 79 APK as baseline. The existing production workflow targets Code 78 → 79.
2. Verify package, SDK levels, versionCode 80 and the original signing certificate. The pinned production certificate SHA-256 is `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.
3. Following explicit owner approval, use the existing signing controls and test an in-place Code 79 → 80 upgrade on a disposable API 37 environment with representative encrypted data. Verify Keystore continuity after restart, both vaults, Guardian and Evidence Locker, Nova Brain feedback, Sanctuary, launcher layouts, and backup. Keep the owner's installed app and data intact.
4. Record the physical Fold results, signed upgrade results, APK checksum and publication evidence before calling the release ready.
