# Private Gallery 1.7.1 recovery

## Verified source and identity

- Baseline: `23b4b1681736e7c9b6635a9c16184abfcc95b507` on `release/1.7-encrypted-camera`, independently read from GitHub and recovered into a fresh clean clone after workspace maintenance. No newer baseline changes or surviving uncommitted source were found.
- Development branch: `release/1.7.1-professional-ui`, initially created at that exact baseline. No force push or main merge.
- Signed 1.7 code 33 source: `18789f0f595abdfcd54e931671a82c23289af1ea`; subsequent baseline changes were documentation/evidence only.
- Code 33 APK SHA-256: `65f2f2de0ef437357e924c38048162cfd415819e946569b456167bb70cf192b1`.
- Production package: `uk.co.traynor.privategallery`.
- Original certificate SHA-256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.
- Target: 1.7.1, code 34. Original signer only; fresh code 33→34 synthetic upgrades required.

The 1.7 original-key synthetic upgrades passed. Full 1.7 physical acceptance was not supplied. The owner reconfirmed working Tor Only and VPN Only in the 1.7.1 brief. Prior live Tor CI failures remain a known test limitation; no recorded critical data-integrity blocker was found. Transport code and test assertions must not be weakened to obtain a passing run.

## Preservation boundaries

No encryption, storage or database migrations; no key replacement, real-media operations, uninstall/clear-data on owner devices, or main merge. Prior releases and V2 recovery branches are preserved. No face recognition or architecture rewrite. Camera black-surface/lifecycle/controller and browser transport source remain unchanged.

## Implemented checkpoint

Shared compact spacing, typography, cards and feedback; responsive settings categories; collection cover cards; explicit thumbnail loading/failure states and revision-scoped sensitive callback ownership. AI editor has authenticated source cards, bounded memory-only thumbnail selection, larger previews and result controls. Camera presentation and browser chrome are refined. Focused tests and screenshot capture are added. Design and implementation plan are under `docs/superpowers`.

Implementation remote checkpoint `f83ba99c69816aa9cb7bdf0f28afa5483dce1dc3` was independently verified through GitHub API and `git ls-remote`; its tree equals local checkpoint `97070e3f8500b82eb65b38f328652eb20d423adc`. GitHub Android run 39973395391 passed host checks but failed Kotlin compilation at the nested camera layout height reference. The narrow fix captures the outer height before entering the nested layout DSL; a fresh CI run must verify it.

## Review and validation status

Independent source review found a replacement-source provenance issue: the editor could replace Photo 1 while viewer save callbacks retained the original parent ID. The source-aware callback fix is implemented and independently reviewed; tests cover ordinary, remote and AI saves with the actual replacement parent. No new camera lifecycle, transport or collection membership issue was found. Camera large-font screenshots and AI post-consent source replacement/visible-keyboard checks are added; execution is pending.

Local checks passed: 7 release-gate tests; 13 camera-input and 18 journal checks; 53 browser policy checks; 12,167 MP4 checks; 3 browser-video tests; 373 Hidden foundation checks plus synthetic isolation/collections/revocation/record checks and authenticated 20/100/500 MiB stream, tamper, low-storage and buffer-wiping checks. Diff whitespace and staged no-secret scan passed for the initial implementation.

Android compilation/instrumentation/screenshots, original signing and code 33→34 upgrades are pending. The local Gradle bootstrap cannot reach its download host; GitHub CI supplies Android tooling. Do not reuse 1.7 test evidence for changed 1.7.1 app code. No physical Fold acceptance or signed 1.7.1 APK is claimed yet.

## Exact continuation

1. Run the reviewed source-aware AI save callbacks and additional focused UI tests in CI.
2. Commit/publish coherent fixes, require remote/local tree equality and independently verify every remote ref.
3. Resolve compilation/test failures from fresh GitHub CI without weakening security/transport checks.
4. Inspect captured screenshots and record limitations honestly.
5. Require original certificate, production package/code 34 and both synthetic vault upgrades before delivering one acceptance APK.
6. Update this document with final source/run/artifact hashes and physical acceptance status.

## Owner Galaxy Z Fold acceptance

Only the owner can complete these checks on both displays, portrait/landscape, light/dark, keyboard open and Android gesture navigation:

- [ ] AI Edit displays selected image thumbnails.
- [ ] Combine Photos displays both source images.
- [ ] Tapping either thumbnail allows replacement.
- [ ] Custom prompt remains editable and controls generation.
- [ ] Generated image preview works.
- [ ] Primary and Hidden media grids look polished.
- [ ] Collections show appropriate thumbnails.
- [ ] Favourites work.
- [ ] Camera controls are clear and compact.
- [ ] Black-Screen Camera behaviour is unchanged.
- [ ] Hidden Browser matches Primary visual quality.
- [ ] Tor Only still works.
- [ ] VPN Only still works.
- [ ] Settings are organised and functional.
- [ ] No clipping on either Fold display.
- [ ] Existing encrypted media remains intact.
- [ ] Signed APK upgrades without data loss.

## CI and screenshot checkpoint — 23 October 2026

Remote source `376511ee67d2366f7cb1a50b4d6c18ee9e449f36` was independently verified. Run 39973944354 passed compilation, 333 JVM tests (zero failures/skips), debug lint and assembly. Focused Android results: 49 tests, 45 passed, 4 camera viewport failures reporting text overflow for the entry heading. Full regression, signing and upgrade stages did not run. Diagnostics artifact 12512275577 was downloaded and its ZIP SHA-256 matched GitHub: `bdd7596b2969fde60e7ec18dc21057da68036a2fe96407438ae71d16935e0c1e`.

Follow-up: camera tests retain strict overflow assertions, remove unnecessary fractional viewport upscaling, and save screenshots plus text metrics before asserting. This is a diagnostic hypothesis, not a claimed fix. Browser tests now resize the actual disposable emulator before Activity launch so settings dialogs cannot silently use a larger window; original display settings restore afterward. Hidden landscape includes 1.3 font scale.

Screenshot review prompted two small presentation refinements: Combine setup uses both source cards without a duplicated large canvas, and generated results have a full-screen preview that clears on lock/discard. Collection titles wrap to two lines and singular counts read correctly. Existing provider, encryption, network, camera and collection operations remain intact.

The focused gate now runs the five changed UI classes; the unfiltered full suite still runs every legacy test. Its evidence checker explicitly requires all six UI classes including ProfessionalUiTest, avoiding duplicate legacy authentication/settings execution without reducing coverage. Independent release review and seven host gate tests passed for this ordering.

The neutral palette's 16 measured text/surface pairs per theme have minimum contrast ratios 6.52:1 (dark) and 5.40:1 (light). This does not certify every rendered state or the intentionally black camera surface.

## Fresh viewport evidence — run 39978808692

Remote source `47003864f48526ffa29b003cfcc2796b704dfdc9` was independently verified. Compilation, 333 JVM tests (zero failures/skips), debug lint and assembly passed. Focused tests: 27 total, 23 passed, four camera text-check failures, no skips. Full regression/signing/upgrades did not run. Artifact 12512413755 ZIP SHA-256 independently matched `f293d5cf2fa2cde8cef3491bc1365855c696805cf71995c0bf5915c0bd0039e8`.

The diagnostic hypothesis about density was disproved. Captured metrics show `hasVisualOverflow` reporting paragraph constraint width larger than a shrink-wrapped text node despite every rendered line fitting (for example 150px heading, 808px paragraph constraint, actual line right 148.84px). Camera entry screenshots confirm readable labels. The test correction uses rendered line bounds, line truncation and height, with deliberately clipped negative fixtures; camera production code is unchanged.

Independent visual review found no blocking defect in the fresh browser/settings and AI screenshots. Primary dark fallback contrast is correct with its themed parent. Actual browser windows match the requested dimensions and Hidden dialogs fit with title/Done accessible and scrollable content. Combine shows both source cards plus prompt and Generate on the cover portrait fixture. Short landscape layouts require vertical scrolling; hardware and owner acceptance remain pending.

## Owner-requested safe stop — 23 October 2026

Stopped at the owner's request to conserve remaining usage. Latest tested-source checkpoint is remote `a98a602684f7eddd2fb0df12a0b59a251446536b`, independently verified by GitHub ref API and `git ls-remote`; its tree `142725f23411e9d6d7d5c499c94ea394867b70b3` equals local commit `2e3fc7d3a005c2931615803a25871bfc04405889`. It contains the independently reviewed camera checker correction and negative controls, with no app changes since `47003864f48526ffa29b003cfcc2796b704dfdc9`.

GitHub run **39981196586**, job **120508609887**, is still running independently. Last observed state: Kotlin compilation passed; JVM tests/lint/debug assembly in progress. Do not assume later stages passed. No signed 1.7.1 APK has been delivered, no owner-device operation performed, and no physical acceptance claimed.

Resume from `release/1.7.1-professional-ui` without resetting or overwriting newer commits. First inspect run 39981196586, download and verify artifact digests, inspect the camera negative-control/viewport results and screenshots, then resolve any actual failures. Require fresh full regression, original-key signing, package/code34 validation and both code33→34 synthetic upgrades. Do not reuse prior-version test evidence or weaken transport checks. Finish the signed acceptance APK, independently verify its hash/certificate, update this recovery record and deliver the owner checklist. Independent review findings are preserved in `docs/release171/REVIEW.md`.

The safe-stop checkpoint changes documentation only and uses `[skip ci]` so it does not restart the existing run. Distinguish its final remote documentation SHA from the CI/APK source SHA above.
