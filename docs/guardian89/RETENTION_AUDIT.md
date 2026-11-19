# Guardian retention repair — Build 89

Audited installed Build 88 source: `430774e549a86d5c0db159ec24230bc4ac05bbc3` (production tag v1.7.42). Repair starts from main `cce2f3fb8b13da4ebaeb2c07712dec42f826daee`.

## Confirmed defects

Guardian capture is an AES-256-GCM JSON archive in Android private `noBackupFilesDir/guardian-notifications-v1.enc`, not a SQLite message database. It uses the existing Android Keystore capture key and authenticated v1 format.

* `GuardianCaptureStore.retainedRows` rewrites the archive during ordinary reads, retaining only records whose **message timestamp and capture timestamp** pass the selected cutoff. Reading counts, opening Inbox, model evaluation and background listener refresh can permanently prune messages. Old notification timestamps can shorten the time since capture.
* `append` silently keeps only the latest **256** records, independent of the configured number of days. A busy Inbox can lose messages well before 30 days.
* Archived records and owner-labelled training originals receive neither an expiry exemption nor capacity protection.
* The default capture window is 7 days. Personal Impact feedback separately defaults to 30 days; Android alert timeout is a third setting. A 30-day value in either other setting does not configure the capture archive. The owner's actual stored preference and lost record history cannot be inferred remotely.
* Clear encrypted capture deletes both the file and capture Keystore key, including inline training source rows.

No dedicated timed message-deletion Worker was found. Opportunistic reads/captures perform cleanup. Call attempt history and Personal Impact feedback have separate retention; their storage is not a backup of message bodies.

## Immediate protection and recovery limits

No owner storage was accessed, migrated, reset or decrypted during development. Avoid Clear encrypted capture, Clear storage and uninstalling. Force-stopping NovaOS and avoiding relaunch prevents its code running, but pauses Guardian/Sanctuary and new capture; a default Home launcher can restart it. Notification access off alone does not prevent cleanup on an Inbox read. Reopening old Build 88 may still execute its old cleanup before the update is installed.

Build 89 copies any existing capture base, AtomicFile `.bak` and `.new` ciphertext into private no-backup storage **before** `AtomicFile.openRead` can promote/discard them. No key/AAD/version replacement or destructive migration is performed. Inspection authenticates copies with the original capture key. Recovery is explicit, local, limited to genuine missing records, respects known deletion tombstones and restores into History without releasing active holds.

These transient AtomicFile copies are not historical backups and may not exist. Completed old overwrites normally remove earlier ciphertext. Standard Android Auto Backup excludes `noBackupFilesDir`; the current Nova Backup preference allowlist does not back up this archive. Feature-only learning cannot reconstruct original wording. The owner has not manually saved missing messages to Evidence Locker. No such copies are assumed, and no Hidden Vault is searched. Actual recoverability requires the authenticated on-device storage check after installation. A missing key or failed GCM authentication is not bypassed; no recovered text is invented.

## Repair behavior

7-day and 30-day choices now define **Inbox visibility from capture time**, then move messages to History. Neither reads, background refresh, restart, new captures nor time expiry permanently delete messages. Keep Forever disables automatic archiving. Explicitly saved training sources (including legacy owner-labelled originals) remain independently protected. Capture capacity is bounded by the existing 6 MiB ceiling and a larger explicit row bound; full storage refuses new capture, preserves existing data and withholds notification dismissal.

Guardian Settings → Messages, retention & recovery exposes the real capture policy, all retained/History rows, saved training originals, storage inspection, explicit recovery, archive, individual and bulk deletion. Review / Teach Guardian opens the existing authenticated review and four-hour hold rules; local Teach Guardian previews the captured text, saves its encrypted original and owner label to the existing shared Nova Brain. No Evidence Locker copy is required and no network call or Cloud Teacher activation occurs.

Ordinary Inbox clearing/deletion keeps saved training originals in History. Explicit permanent deletion of training sources requires a separate checkbox. It deletes capture bodies, inline training labels and their on-device checkpoint copies, retaining minimal deletion tombstones to avoid resurrection. Active holds are skipped. Separate Evidence Locker records, feature examples from other sources and external backups are not silently deleted. Reset/forget learning keeps saved originals; it removes labels rather than erasing evidence. Existing encryption keys, Vault access, updater, paid-analysis gates and backend configuration are unchanged.

## Verification

Focused policy, encrypted runtime, real background refresh, reopen, >256 captures, capacity refusal, authenticated AtomicFile recovery, corruption handling, training preservation, holds and folded/unfolded UI cases are required by the existing CI and production report gate. The authentic original-key Build 88 → 89 upgrade fixture seeds synthetic aged captures and a legacy owner label using Build 88 classes, verifies unchanged ciphertext and the checkpoint, preserves populated encrypted data through the actual installer and checks again after process restart. Final run IDs and release receipts are recorded in the release security review after passing verification. Physical Galaxy Z Fold acceptance remains owner confirmation.
