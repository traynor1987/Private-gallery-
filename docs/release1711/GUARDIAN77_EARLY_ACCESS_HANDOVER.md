# Guardian Code 77 production release — blocked checkpoint 2026-11-11

Application/test source: `6dabbda337363477d4516d9108aef61189ca1755`.
Version 1.7.38.4, versionCode77, production package uk.co.traynor.privategallery.
No Code77 production signer activation, original-key candidate build or retained-data upgrade has run.

## Latest exact results

- Run42918431295 / source80e9d765e291d14c6cf7bc2f7633a086daf79a25: 262/263 broad cases passed. Failure WallpaperUiTest.gallery_apply_refreshes_retained_home_on_return_and_stale_save_preserves_it: immediate Home visibility assertion before asynchronous IO apply/close completed.
- Source2ac3ff8b63f1982e6107373d7839b131d7bb7214 added bounded observation of the actual completion callback/editor dismissal, preserving all original assertions. Independently reviewed.
- Run42928029177: Wallpaper12/12, Guardian compatibility2/2, encrypted store10/10, Inbox2/2, Evidence learning2/2 PASS. Then NovaWebScroll67Test line171 failed: following genuine keyboard show and dismissal, swipeUp did not increase scrollY within5000ms. Root cause unconfirmed. Artifact13815658193 SHA256604abd94f1a4e25f94c742fd1aa39bb9cec239e0253a01c15e8c4703207bf486.
- Source6dabbda337363477d4516d9108aef61189ca1755 adds bounded synthetic touch/viewport diagnostics to that test, delegating touch unchanged to super; no production change, weakened assertion, retry or increased timeout. Workflow runs wallpaper then browser before broader checks. Independently reviewed.
- Latest run42931202049 / job130230089203 FAILED earlier: WallpaperUiTest.primary_home_vault_picker_cancel_apply_and_reopen and hidden_home_vault_picker_cancel_apply_and_reopen. selectVaultWallpaper timed out at line82 with grid=true, readyTiles=1, loadError=false. Both actual application DecorViews reported focused=false; dialog visible type2. Browser diagnostic did NOT execute. Root cause unconfirmed. Artifact13817391283 SHA256f377b1fdf1c40d0582a72d8a6640b8284090de36fbcfbdfa6f0b960ce1191b9d.

Do not mark the full regression gate passed or use earlier partial results as complete evidence. Do not launch unchanged full reruns to seek green. Next investigation must capture actual foreground window/input-dispatch state at the focus timeout and reproduce the post-keyboard swipe with diagnostics. Distinguish test scheduling from a real product failure before changing app code.

## Physical owner acceptance — retained

Main WhatsApp capture/encrypted Inbox physically confirmed. Main/Friends probe identified both with text, zero unresolved events. Main retained count3 before and3 after an observed Friends notification. This closes archive isolation for that physical test; do not ask owner to repeat it.

## Preserved application scope

Approved contact cards unchanged. Conversation detail full-screen, chronological incoming-only notification-derived history, per-message Not Upsetting/Upsetting/Clear labels stored encrypted and fed to local examples. Authentication, FLAG_SECURE and independent holds remain required. Re-analysis closes old previews before refreshing, including cautious Uncertain holds. Evidence result publication uses Main.immediate with existing authority guard.
Automatic real WhatsApp dismissal, cloud analysis and automatic Panic disabled. Protect All/evening schedule deferred. Samsung notification previews remain a separate system exposure.

## Original baseline and controls

Original76 sourcec818c670baa4b980708dc2effb1ab2ab0331ade1, signed run42819991907/job129842766718/artifact13767667010.
Archive SHA2567529b9dd3ef07db0b959e29d657c046bde0efc11b3b79a27f9be19ab3aa98757, bytes62192742.
APK SHA2567fd1e4211e6277a8a8bfeaa20ef8ee180a89a57b38e435bb30b5a69fbcada171, bytes148976200.
Certificate SHA25694f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.
The archive and nested provenance were validated locally; apksigner independently verified one original signer, v2 true. Actual production package/version76/min-target37 verified. SDK tools matched official repository checksum/size.

Pending release-control patch is saved beside this file as GUARDIAN77_PENDING_CONTROLS.patch. Apply only to an isolated controls worktree; do not activate its marker. Local worktree /workspace/scratch/bdd7369164b7/guardian77-release-controls has controls retargeted6dabbda/current failedrun, reviewPENDING, historical marker intentionally incompatible. Independent review found no control bypass. Four current host rejection tests pass. Fixture transform validated against actual c818 build config. New archive assertion correctly uses noBackupFilesDir.
Historical test_upgrade1711_controls.py asserts oldCode64/65; it fails10/errors2 unchanged on historical and adapted controls. Do not present it as passing current verification.

Only after exact-source complete runtime passes: bind real artifactID/digest, fixture/workflow/control/matrix hashes and independent approval in a fresh marker/review. Then original-key signing, original76->77 seeded encrypted-data upgrade/restart, signature/package/checksum verification and established production artifact delivery. Never reuse historical Code76 approval.
