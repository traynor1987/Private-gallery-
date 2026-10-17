# Stage 1 — functional TEST ONLY Hidden gallery

Historical checkpoint. Current permanent Hidden integration and its exact-source verification are documented in [PERMANENT_HIDDEN_STATUS.md](PERMANENT_HIDDEN_STATUS.md). Statements below describe only the older checkpoint.

Owner amendment on 17 October supersedes the previous security-first milestone order. Existing V2 backups and the V1.5 credential foundation remain preserved. Stage1 implements functionality without accepting real media.

Implemented: concealed debug-only entry; a separate Hidden gallery; synthetic PNG and bundled synthetic MP4 imports; thumbnails; photo viewer; shared V1 Media3 video player using an explicit in-memory ByteArrayDataSource; favourite filtering; basic named collections; return to Primary; reset; background dismissal and memory-state cleanup.

Safety boundary: debug application ID uk.co.traynor.privategallery.hiddenprototype, separate from the installed production app. Launcher label/version identify TEST ONLY. Both entry and screen check HIDDEN_PROTOTYPE; release defaults false and has no debug fixture assets. Every prototype screen shows TEST ONLY / INSECURE / SYNTHETIC MEDIA. There is no file picker, owner URI input, Primary repository, export, persistence, browser handoff, or disk cache. Runtime media and metadata live only in a bounded in-memory model. Close/background/reset/process death discard prototype state. The immutable bundled debug MP4 is disposable test content, not a gallery storage format.

Imports are deliberately limited to built-in synthetic fixtures. Arbitrary-file import and real-owner media are not accepted. No permanent plaintext format is introduced. Stage2 can replace this disposable model without supporting or migrating Stage1 state. Thumbnail decode and playback use supported Android APIs and the existing V1 player. None of the V2 ownership architecture is imported.

Host verification: existing373 foundation checks plus functional model checks pass. Tests exercise disabled import, separate context isolation, photo/video byte import/read, favourites, collections, reserved names and complete reset. The reserved-name test was observed failing, then passed after normalization/rejection was fixed. Three existing Node browser-helper tests and source distribution/WireGuard checks pass.

Focused independent review found no owner-data or production-guard blocker. It found the collection-name/filter collision, now fixed. Android tests are authored for synthetic photo/view/favourites/collections/reset, actual video decoder readiness, separate debug ID, and background/reopen clearing. Exact prototype source53e5b2a3a0b117bc20a16a0d91176dd99751ad54 passed CI583: JVM tests, lint, debug APK/AndroidTest APK compilation and all137 Android tests, with zero failures/skips. Actual emulator tests passed photo import/viewing/favourites/collections/reset, video import/decoder READY, debug package isolation and background/reopen clearing. Platform: API36/google_atd/x86_64; this is not owner physical-device acceptance. Evidence: evidence/ci583-summary.txt.

Historical foundation evidence: CI581 SUCCESS on944c6b5227e1236eda5c3633c3ce131f1bcbbbfc (131 Android passes). CI582 SUCCESS onf381ffa1a1533ee92e3968de2214575457bc22a5 (foundation UI/credential integration). Those sources and reports remain recoverable; the prototype bypasses the PIN dialog only in the test-only route.

Status: DEVELOPMENT PROTOTYPE, NOT SAFE FOR REAL MEDIA. Stage2 encrypted media/metadata/thumbnails, secure import/failure recovery and complete isolation/security tests remain mandatory. Stage3 physical-device testing, permanent-signing verification, compatible candidate and owner release/install approval remain pending. No install, publication, owner-media migration, Tor, VPS or browser changes are authorized by this prototype checkpoint.

## Verified checkpoint and artifact

Branch: release/1.5-hidden-vault. Tested implementation SHA:53e5b2a3a0b117bc20a16a0d91176dd99751ad54. A later documentation-only evidence commit does not change app/build/test inputs. CI583:https://github.com/traynor1987/Private-gallery-/actions/runs/39026089846. Test-only debug artifact12098619408 contains app-debug.apk and diagnostics; ZIP SHA2560efd6e83ae98a6514d1eff27be4275cf7525e388641ece41622aaca65913014c. This is a debug-signed separate-package prototype, not a permanent-signed production candidate. No installation or publication was performed.

What remains: Stage2 encrypted persistent media/metadata/thumbnails and complete credential/session integration; corruption/interruption/cancellation recovery and no-plaintext persistence verification; then Stage3 device testing, permanent-signing verification and owner acceptance. Imports are still built-in synthetic fixtures only. Real owner media must remain excluded.
