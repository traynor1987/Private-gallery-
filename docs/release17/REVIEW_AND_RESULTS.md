# Private Gallery 1.7 — implementation and acceptance report

Implementation and independent source review are complete. CI656 attempt2 passed all 333 JVM tests, 69 focused Android tests and 292 full Android tests, with zero failures/errors/skips. The focused tests are a subset of the full suite, not additional unique coverage. CI657 is rerunning the unchanged app source with the corrected code32→33 upgrade fixture. Signing and production upgrade remain pending: NO-GO for owner installation until those gates pass.

## Baseline and release identity

| Item | Exact value |
|---|---|
| Repository | traynor1987/Private-gallery- |
| Baseline branch | release/1.6-hidden-tor |
| Authoritative baseline | affb339e9367627fd9f2f82c075dd76b78883393 |
| Original-key code32 upgrade source | 7765c79874556e8093612b64eeb529e095c9c287 |
| Development branch | release/1.7-encrypted-camera |
| CI657 candidate source | 8c11f2b3a8ebc084a4ecd661741aaa6b5862759f |
| Production package | uk.co.traynor.privategallery |
| Candidate version | 1.7 / code33 |
| Required original certificate SHA-256 | 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17 |

The owner accepted Tor Only and VPN Only on the physical Galaxy Z Fold for 1.6. This is not 1.7 acceptance. Existing release/recovery branches are preserved; no main merge, owner device operation, key regeneration or real-media modification occurred. The 641-photo collection and later additions were not accessed; only the owner can confirm their current real-device count.

## Implemented behaviour

| Area | Delivered behaviour |
|---|---|
| Vault integration | Camera action in each vault menu, immutable capture destination, independent Hidden authority; no extra permanent navigation tab. |
| Normal camera | CameraX 1.5.3 preview, photo/video, front/rear selection, supported flash/zoom, video quality fallback, permission handling and encrypted save status. |
| Black-screen camera | Foreground capture without a Preview use case; preselected photo/video; tap and Volume Down; debounce and swipe/multitouch rejection; accessible capture/exit and optional vibration. The physical display remains on and Android privacy indicators remain visible. |
| Video/audio | Explicit encrypted per-vault audio preference, off by default; microphone permission required only for audio; choice frozen during capture. No fixed duration limit; existing storage byte ceiling applies. |
| Lock/timeout | Revoke input and stop capture on camera foreground loss, screen lock or authority expiry; finalise using the capture-only key where possible; clear camera/Hidden navigation and return to neutral Primary entry. Fresh Hidden authentication is required. |
| Recovery | Authenticated pending journal; exclusive capture leases; strict completed-media validation; complete captures retry import idempotently; incomplete bytes remain retained rather than falsely published or destructively repaired. |
| Evidence integrity | Original timestamps, SHA-256 and provenance protected in encrypted metadata; authenticated date/duration/size/details and integrity verification; distinct original capture identities. Edited/exported copies do not acquire original capture provenance. No claim of recorder identity or legal admissibility. |
| Focused security improvement | App-private no-backup temporary files, per-capture key ownership, verified encrypted staging/import before cleanup, directory durability and cancellation checks. Existing vault keys/formats retained; no V2 biometric lifecycle import. |
| Hidden Browser | Primary-style compact rounded address bar, navigation/tab/download/settings controls, connection status, scrollable compact settings, expanded privacy details, theme/keyboard/inset handling. VPN+Tor stays unavailable. |

There is no diff from baseline in core/browser, core/vpn or hidden_network_security.xml. Working Tor/VPN implementations and fail-closed request policy were not rewritten.

## Automated evidence

CI656 attempt2: [workflow run](https://github.com/traynor1987/Private-gallery-/actions/runs/39947347679). Downloaded artifact digest independently matched GitHub: `900d2e5e4bd3da0028e529e104cedb892766d5cece8ba9efc6902116d1aeeded`. Counts were independently read from XML; summary and process logs are in [evidence-ci656](evidence-ci656/summary.json).

- 333 JVM tests passed; app/instrumentation compilation, debug lint and APK assembly passed.
- 69 focused Android tests and 292 full Android tests passed, zero failures/errors/skips.
- 10 real CameraX emulator cases passed: Primary/Hidden normal and black photos, normal audio video, black silent video beyond 10 seconds, recording revocation and Hidden authority expiry.
- 7 vault capture access/import/recovery tests passed, including encryption, original metadata, distinct captures, fresh authority, failed-auth cleanup and truncated media.
- 2 camera UI tests passed: tap/volume/repeat controls, gesture rejection, foreground-loss locking and neutral route without Hidden reopening.
- 4 browser viewport/theme tests passed: 392×840 light, 840×392 dark, 840×900 dark, 900×840 light, including settings/tabs/keyboard interaction. All four exact-viewport screenshots were visually inspected; no control clipping was observed. These are emulator layouts, not physical Fold results.
- Separate-process camera and browser seed/force-stop/verify fixtures passed; each phase reports OK (1 test). The camera fixture confirms different PIDs and both vault destinations, retained incomplete data and plaintext cleanup.
- Host checks passed: 13 input-policy, 18 encrypted-journal, 373 Hidden foundation plus session/gallery/record/durability and 20/100/500 MiB streaming, 53 browser/diagnostics policy, 12,167 MP4 container and 3 JavaScript helper checks; no-secret and diff checks passed.
- Real native Tor bootstrap/ordinary and disposable onion requests passed in the successful preflight/full test run. This does not verify the owner's live VPN profile or physical Fold routing.

Coverage limits: actual device permission-dialog denial, front/rear switching and flash/zoom behaviour, power-button handling on the Fold, gesture navigation insets, minute/hour-scale recording and device thermal/storage exhaustion still require physical acceptance. Low storage is tested as journal admission refusal; authority revocation and Activity pause are automated proxies for lock, and 12-second emulator video proves only removal of an artificial 10-second cutoff. Live paid AI provider calls and the owner's WireGuard configuration were not exercised.

## Independent security review

A separate reviewer inspected capture ownership, authentication, finalisation and recovery, reproduced a live capture/recovery collision before its fix, and reran host checks. Corrections made:

1. Exclusive live/finalising leases exclude recovery, and reserve ownership before directory publication.
2. Completed video requires strict container/sample validation.
3. Permanent access revocation and original authentication/key identity prevent renewed-session authority reuse.
4. Failed authenticated delivery closes tickets and wipes/releases ownership.
5. Camera entry is synchronous before async binding; foreground loss stops/locks during startup too.
6. Audio load/save gates entry and capture uses a frozen consent snapshot.
7. Primary import is cancellation-aware; verified payload/metadata directories are synced before retiring recovery data.
8. Different captures remain distinct originals; retries reuse only the same capture identity.
9. Rebinding disables input and capture completion restarts debounce.
10. Completion, ordinary exit and resumed authentication retry pending recovery; requests during recovery queue one fresh authorised rerun.
11. UI regression fixes reject navigation swipes and select neutral setup for the synthetic no-configured-PIN edge while preserving the owner's normal locked entry.

The final source review found no remaining concrete critical or important blocker in the reviewed paths. Source review and passing synthetic tests cannot establish physical-device acceptance.

## Failure history and fixes

- CI650: Kotlin zoom setter JVM signature clash; command renamed.
- CI651/652: five lint API-use errors; public volume callbacks and LocalActivity fixed them without suppressions.
- CI653:61/62 focused tests passed; disposable onion request returned 503 while BBC over Tor worked. Production transport unchanged; later runs passed Tor tests.
- CI654/655: added UI tests exposed swipe-as-tap and synthetic no-PIN neutral-route defects. Both production fixes passed in CI656; assertions retained.
- CI656 attempt1: emulator ZIP download failure before compilation; unchanged retry succeeded through testing.
- CI656 attempt2: all tests above passed, then upgrade fixture expected obsolete 31 while baseline correctly reported 32. Fixture now asserts baseline 32 and candidate 33; encrypted-data preservation assertions were not removed or weakened. CI657 is pending.

## Remaining limits and owner acceptance

CameraX requires private plaintext during recording. Verified encrypted staging removes it; a crash or storage/encryption failure can leave private plaintext until authenticated recovery. File deletion does not promise forensic erasure. Incomplete MP4 data may be unplayable; recovery of every interruption is not promised. Each recording is capped just below the existing 2 GiB vault limit with space reserved for encryption. Device capabilities govern quality, duration and finalisation.

No cloud uploads, off-device evidence backup, recovery destination, automatic face recognition, covert background recording or VPN+Tor routing were added. Off-device encrypted preservation remains a future extension.

Use the [Galaxy Z Fold owner checklist](OWNER_ACCEPTANCE.md) with harmless test media first. Upgrade the original package in place; never uninstall or clear its data. Final APK/source/hash, verified production upgrade evidence and GO/NO-GO will be recorded after CI657. Physical acceptance remains pending until the owner reports it.
