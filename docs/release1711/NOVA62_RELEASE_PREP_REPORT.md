# NovaOS code62 release preparation — implemented, runtime pending

Prepared from reviewed/pushed `8470eb501709c7a75ba3ef6c025f9378d055301c` on `integration/novaos-1.7-home2`. A Backup (`f6e1b82d`), B Arcade (`69b44c8c`) and C Calculator (`8470eb50`) were reviewed before this sole-writer delta. Root confirmed signed1.7.11.5/code61 remained latest at20:34 UTC. Candidate is1.7.11.6/code62, production package `uk.co.traynor.privategallery`, min/target/compile API37, unchanged permanent certificate. The separately installed development build remains `.hiddenprototype` / `-TEST-ONLY`.

No workflow, push, signer, replacement key, production install, uninstall, data clear, signing marker activation or production approval was performed by this preparation. Final branch review, original API37 runtime evidence and original-key61→62 upgrade remain mandatory.

## Implementation and final feature repairs

**Wallpaper I1:** `StoredWallpaperImage` uses Android BitmapFactory bounds and actual bounded decoding. Portable wallpaper bytes must be supported PNG/JPEG/WebP, ≤8MiB and already normalized within2048×2048; malformed/oversize images reject during validation, before staging or changing settings ciphertext. Decoder resource exhaustion fails closed. The Home wallpaper loader also uses bounded sampling for legacy/local data and treats invalid images as unavailable. No custom image codec or browser/Vault changes were introduced. Host regression reproduced malformed acceptance before repair; native fixture supplies a genuinely compressed16384×16384 PNG and checks original ciphertext plus later valid wallpaper usability in both Spaces.

**Automatic scheduling A6 and checkbox M1:** due checks alone did not fulfill the owner requirement. The explicit, disabled-by-default Primary setup now requires an actual persisted SAF tree grant and acknowledgement that the generated recovery key was saved. Folder, recovery material and selected restrictions are stored only in the current Primary encrypted `nova_backup_automatic_v1` namespace. That namespace is not portable; import attempts reject and supported-settings replacement retains existing local configuration. Android necessarily owns its persisted SAF grant, but no URI/key is placed in job extras, nonsecret due preferences or a portable archive.

The existing JobScheduler job supplies daily/weekly constrained due signals; eligible unlocked foreground Primary Home executes automatically through the existing BackupCoordinator and real SAF write/reopen/authentication path. It checks charging/Wi-Fi before work and retires active IO on constraint loss. Lock, Panic, background, owner retirement or disposal cancels the child capability without retaining Hidden authority or extending authentication. Locked/unavailable sessions need unlock. One global inflight operation and a persisted15-minute minimum attempt interval prevent hot retries/incomplete-file storms. Fresh timestamp/UUID filenames avoid replacing previous backups. Failed/retired documents are never promoted to Verified; existing history records actual outcomes. Foreground eligibility and incomplete-destination behavior are stated in UI. Saved controls hydrate correctly and selections survive the system picker.

No automatic execution is added for Hidden. Primary UI contains no Hidden disclosure. The explicit unsupported Evidence Locker category remains excluded under the owner's permitted unsupported-category exception; no audit-lineage bypass is introduced. Arcade formatting M2 remains deferred because a style-only rewrite would enlarge this security/release delta without changing behavior.

## Selected native evidence prepared

These fixtures compile but have **not run** in this preparation. They use synthetic isolated roots/credentials and the development package only.

| Area | Concrete native checks |
|---|---|
| Real SAF transport | Separate-UID DocumentsProvider, actual DocumentsUI CreateDocument/OpenDocumentTree results, grant acknowledgement/persistence/revocation, actual PFD writes and reopened readback, denied/failed/truncated/tampered destinations, close errors, blocked IO cancellation and descriptor closure. |
| Real authority/lifecycle | MainActivity Backup operation with actual Lock, three-finger Panic and background retirement in both Spaces; stale/duplicate/cancelled picker results; ActivityScenario recreation at picker/key/preview phases; no stale preview. |
| Automatic execution | Actual encrypted arm/reopen/exclusion, provider verification and unique files, failed-provider bounded retry, blocked scheduled IO cancellation, no Hidden arm/run, real MainActivity foreground Home executing after actual JobScheduler due signal without a manual Backup button. Portable restore retains encrypted schedule credentials; malicious schedule namespace import preserves all ciphertext. |
| Scheduler | Actual JobScheduler persisted daily/weekly constraints and no secret extras/ClipData; force-run while real Main is locked changes due status only and creates no backup ciphertext. |
| Restore usability | Actual Main settings replacement locks; unlock reloads Home/Mind/widgets, old model cannot write, later edits/reopen retain restored values. Injected directory-sync/storage failure preserves exact prior ciphertext. |
| Apps and concealed entry | Actual strict Arcade/Calculator data roundtrips through independently keyed archive restores in both Spaces. Real Main Calculator gesture is default-off, then presents the independent Hidden PIN flow; Primary PIN cannot admit Hidden; real Panic retires both Spaces and stale callbacks. Original C model/throttle tests remain included. |
| Resize and visuals | Actual `wm size` narrow/expanded Backup reachability and recreation; synthetic Primary Backup, Sudoku cover/expanded, Calculator standard/scientific cover/expanded images under additional-test-output `nova62-visuals`. Existing screenshot paths remain unchanged. |
| Wallpaper | Actual Android decoder malformed/huge-dimension rejection, both-Space ciphertext preservation, bounded legacy rendering and valid normalized image acceptance. |

The exact main matrix is **253 cases /54classes**, with all **196original cases /38classes** retained as a strict subset, no original assertions removed. `verify_home2_runtime.py` still requires original XML exactly once, with no missing/extra/duplicate/failed/errored/skipped case.

Eight additional host-driven phases deliberately remain outside normal XML: Primary and Hidden at encrypted archive staging, uncommitted settings promotion, durable committed promotion and verified destination. The host observes the actual PID at a native boundary, force-stops it, verifies old-PID absence, then starts a distinct process. Native verification compares exact old/new ciphertext decisions, original media/metadata, interrupted-vs-verified history, cleanup and destination authentication. It is not context recreation or a mocked process restart. Original sixteen transcripts plus one source-bound receipt are retained separately in `home2-evidence/backup-restart62`.

The same existing `home2-integration` job runs these phases before its unchanged main runner; a required validation step and the exact-source signing gate revalidate the17files, eight identities, old/new PIDs, deliberate process crash, successful verification and archive identity. No broad workflow/job framework was added. Existing Home host checks now also run focused Arcade, Calculator and automatic scheduling scripts on the final frozen source.

## Preservation and upgrade controls

The V1 gate accepts only the reviewed six Vault files/status/hash pairs and the single reviewed Hidden broker authentication-error change, SHA-256 `38cf1b02530da82dc1aeb54ea562316d8d4108542eaa0eddc983819cbee13ead`. It checks the complete committed and working-tree delta and rejects additional/deleted/renamed/untracked/ignored files and altered hashes. Crypto pins, browser factory/ordinary-tab/session semantic checks and original data-directory rules remain. No broad folder allowance exists.

Existing eight release controls and the existing production job now prepare signed61→candidate62. Pinned baseline source `2da2895e387839ec37d0590dfb713dc1bec99853`, activation `4904c937d3773aa07b117022edbcafcbcf8c5d5a`, run42269170059/job128068998830/artifact13531400377 and all signed61 byte/hash identities are unchanged historical evidence. Exact values remain in `UPGRADE_CONTROLS.md`. The immutable local archive and APK were read-only verified, including actual APKv2/one signer/original certificate `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.

The upgrade fixture preserves all signed61 and older17102 assertions/operations and the same exact ciphertext exclusions. Additive Arcade saves include persisted Sudoku completion credit; Calculator uses its canonical0.1+0.2→0.3 DEG history and memory. Seeds use only existing code61 SpaceContext APIs; candidate-only schema checks use reflection during verification. The real fixture must still compile against actual code61 source before signer access. The installed baseline must remain the archived original APK, never a rebuilt substitute. Existing API provenance, exact runtime/review/hash approval, signer ordering, candidate identity, payload comparison, in-place `adb install -r`, force-stop/reverify and forbidden evidence-package gates remain intact. Old code61 marker/review are unmodified and cannot authorize62.

## Local verification performed

Tools: JDK17.0.21+9, Gradle9.5.0, existing API37 SDK/cache. Local shell used `JAVA_HOME=/tmp/nova59-tools/jdk-17.0.21+9`, its `bin` in PATH and `GRADLE_USER_HOME=/tmp/nova59-tools/cache` for host checks. Gradle commands use `-Pkotlin.incremental=false --no-daemon --console=plain`.

| Check | Result / evidence |
|---|---|
| `:app:testDebugUnitTest --tests '*backup*' --tests '*PortablePreferenceSchemaTest' :app:assembleDebug :app:assembleDebugAndroidTest` | PASS;29JVM cases/5classes, zero failures/errors; APK/instrumentation build2m30. `/tmp/nova62-prep-build4.log`. |
| Final `:app:assembleDebug :app:assembleDebugAndroidTest` after folder-ticket repair and Main auto-run addition | PASS2m1. `/tmp/nova62-prep-final-build.log`. Final assertion-only `:app:assembleDebugAndroidTest` also PASS1m4 (`/tmp/nova62-prep-last-native-build.log`). |
| `python3 -m unittest scripts.tests.test_upgrade1711_controls scripts.tests.test_home2_delivery_payload scripts.tests.test_backup_restart62_controls scripts.tests.test_preservation62_controls scripts.tests.test_home2_runtime_capture` | PASS16tests, including many tampering subcases. `/tmp/nova62-final-controls.log`. |
| `bash scripts/check_home2_host.sh` | PASS existing policies, actual encrypted adapter/atomic restore/corruption/revocation/lease checks, app codecs, wallpaper regression and wiring. `/tmp/nova62-final-home.log`. Host graphics dependency uses JDK ImageIO; actual Android decoder proof remains native. |
| `bash scripts/check_arcade.sh`; `bash scripts/check_calculator_host.sh`; `bash scripts/check_backup_schedule_host.sh` | PASS126 /89 /12behavioral checks and Calculator entry wiring. `/tmp/nova62-final-{arcade,calculator,schedule}.log`. |
| `python3 scripts/verify_v1_preservation1711.py` and dedicated negative controls | PASS exact allowances/browser invariants; negatives reject committed/working/untracked/ignored/extra/deleted/renamed/wrong-status/wrong-hash mutations. `/tmp/nova62-final-preservation.log`. |
| Shell/Python/YAML/inline Python syntax; workflow comparison; `git diff --check` | PASS; all other workflow jobs identical to8470eb50. Existing production workflow still eight jobs. |
| Debug APK `aapt dump badging` | PASS `.hiddenprototype`,1.7.11.6-TEST-ONLY/code62, compile/min/target37, debuggable as expected. `/tmp/nova62-final-debug-package.txt`. |
| Immutable signed61 archive, nested evidence and APK readback; `apksigner verify --verbose --print-certs` | PASS exact archive/provenance/payload and actual permanent certificate/APKv2/one signer. `/tmp/nova62-final-baseline-signature.txt`. No installation. |

The wallpaper malformed-import regression failed against the prior implementation before repair (`/tmp/nova62-wallpaper-red.log`) and passed afterward. Automatic policy began with missing-production-API red coverage and passes twelve actual-policy cases. A local unittest invocation initially exposed a sibling-fixture import assumption; it was repaired and the full16check set rerun successfully. Initial host invocation lacked local javac in PATH; the correctly configured commands above passed. Neither issue is represented as Android execution.

## Remaining required gates and physical limits

Root's same final reviewer reads immutable feature641cd9c6→8470eb50 and this complete release delta, then original runtime evidence for the final exact source. This overlap does not omit files or manufacture signing approval. After review, root may launch one consolidated runtime job and inspect the actual synthetic screenshots. Any mandatory failure must be repaired and affected checks rerun; case removal or synthetic evidence cannot substitute.

Still pending:253native cases and8real restart phases; actual provider/device timing; original code61-ABI instrumentation compilation; exact-source original-artifact review/signing approval; original-key61→62 in-place upgrade and repeat verification; final production signing/release. Physical Samsung Fold posture/IME/window behavior, real storage exhaustion, removable/cloud-provider peculiarities, long-duration scheduling/charging/Wi-Fi and owner live-account/recovery-key acceptance remain separate physical checks. Injected storage faults, emulator resize and synthetic accounts are not those physical proofs.

Prepared review identities (recompute after any relevant change):

- Upgrade fixture SHA-256: `537cccc991eeaba3c6e89d684048f81f89d8f4f9d8823dfeba2b4ff1028e51bb`.
- Runtime matrix SHA-256: `4f733582e53494420844b1d29f801df8fe2d2ecf08ae81f48fb4515ded812f48`.
- Signing workflow SHA-256: `f77c63d849672db7ee8e9f0773b1da2a8899d266f5ce741880b06e4e84813dbf`.
- Eight-file controls-map SHA-256: `7b537ca0902efa097263d1c1d324cf5185242fbbbc90df1195a30dc52ca8519c`.


## Final Part2 repair round1 — prepared after7fd1d68a

Independent review `/tmp/nova62-final-prep-review.md` identified three Important findings. P2-I1 is repaired by deriving the independently cancellable Primary child from the originating EvidenceAccess lease factory and retirement boundary. Key/current/commit delegate through the parent lease; no copied key becomes a new authority. Foreground state and child detachment/retirement change together inside that same short boundary. Child close never closes the origin; provider/archive IO stays outside the monitor.

The three new JVM regressions use actual EvidenceAccess/lease code: retirement before commit prevents promotion, an already-linearized commit holds the exact origin monitor and blocks retirement until publication finishes, and child cancellation closes its resource/lease while the origin remains usable. Before repair, the old independent-monitor derivation reproduced the ordering failure (3tests/1failure; `/tmp/nova62-r1-authority-race-red.log`). An earlier compile-only missing-API failure is not used as the behavioral proof. One additional native case intercepts the actual automatic export's final Verified-history commit immediately before acquiring the origin boundary, retires the origin, then asserts unchanged history ciphertext, no lastBackup/Verified entry, the interrupted structured status and preserved original media. Native execution remains pending.

P2-I2 now checks stored `Unverified` and rendered `Interrupted / unverified` in staging recovery, retaining no-success, ciphertext/media preservation, cleanup and new-process assertions. P2-I3 compares the entire raw restored Arcade preference map for exact preservation, then separately expects Sudoku's intentional paused reopen. No production game behavior, old runtime cases or restart validator was changed.

The matrix now contains253cases/54classes, all196original cases preserved, plus the same eight separate process-restart phases. The matrix hash above is updated; fixture/workflow/eight-control hashes are unchanged. Focused green verification and final narrow-review status are recorded in the ledger. No runtime, signer or publication activation occurs in this repair.


Round1 final verification: `:app:testDebugUnitTest --tests '*EvidenceChildAuthority62Test' :app:assembleDebug :app:assembleDebugAndroidTest -Pkotlin.incremental=false --no-daemon --console=plain` **PASS1m11**, actual authority JVM **3/0**, no skipped cases; `/tmp/nova62-r1-authority-green.log`. The asynchronous child resource/lease cleanup assertion uses a bounded wait and reads erased key bytes under the origin monitor; it does not assume `close()` performs synchronous cleanup. Both ordering tests passed. Exact preservation,253/54matrix with all196original cases, prepared hashes and whitespace checks pass. No broad suite was repeated; native execution remains pending narrow independent review and root CI.

Metadata recovery: the prior-session Git backing directory vanished externally after verification. Root restored self-contained Git metadata without changing working files, resetting or checking out source. Recovery checkpoint `e537c4622e9c7712c673e3aa0601e61b10055db5` has the exact frozen7fd1d68a tree `d90f87bc458402f43a8eae95035a278203b70627`; all2871tracked source paths were hash-verified unchanged by root. This repair's eleven files also match their preserved successful-build SHA-256 snapshot. The final repair commit therefore descends from the tree-identical recovery checkpoint; no rebuild is necessary solely for metadata recovery. Independent scoped repair review and actual runtime/signing gates remain pending.
