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

## Resume checkpoint — 23 October 2026

Recovered exact remote/local HEAD `b0aef18c044d8fd3c7cabb0a2e27f1ee603c2621`; clean fresh clone, no surviving app implementation processes. GitHub API lists no newer branch runs: #660/39973944354 and #661/39978808692 failed the previously documented camera paragraph overflow assertions; #662/39981196586 at `a98a602684f7eddd2fb0df12a0b59a251446536b` passed compilation, JVM/lint/assembly and 27 of 28 focused Android tests, with zero skips. Its only failure is the new checker negative control `textBoundsCheckRejectsClippingAndTruncationButAcceptsVisibleText`: unwrapped width overflow was not rejected. Full suite, signing and upgrades remained gated. Diagnostic ZIP 12514018551 independently matches SHA-256 `ac7b1227670f0094dbbb14c09938a33fe992a424ca2e35e710c12a38e7a91d44`.

Narrow test-only correction: compare rendered lines against the actual unclipped semantics layout-node size as well as TextLayoutResult.size; unconstrained text layout is not the drawing boundary. Retain all negative controls, maxLines/ellipsis checks and strict line extent assertions. Print both sizes for negative controls to validate the boundary diagnosis in CI. No production app code or transport changes. Runtime result pending; not an acceptance APK.

### Run #663: boundary hypothesis disproved

Run `40002699996` at source `11db99cc4b0bfbf6526844044517987f7b0b1fc8` again passed compilation, JVM/lint/assembly and 27/28 focused tests, with the same width negative-control failure. Node-size comparison alone is insufficient. Compose's plain-String text semantics rebuilds a MultiParagraph directly using the constrained width, bypassing the softWrap=false width handling used by TextMeasurer. The checker now remeasures using all original TextLayoutInput values before comparing actual line extents with the node. This retains every strict negative-control assertion and does not alter application code. Require fresh CI to prove it. Artifact 12522738724 digest: `e2831d8d37869df75bc1adcb0e60b6be84bf514954372106d846677b6a8fed34`.

### Run #664: UI green; live onion probe blocks release

Run `40004576248`, attempt 1, source `e8ac3ce03b35be0c99200eadc7d0e1bd97f28653`: compilation, JVM tests/lint/assembly and all **28 focused UI tests passed with zero skips**, including every camera negative control and viewport. Fresh screenshot/metrics evidence is archived. The following 70-test preflight passed 69 tests and failed only `HiddenTorBootstrapTest.packagedTorBootstrapsInPrivateProcessAndStopsWhenReleased`: Tor reached 100% bootstrap; BBC returned actual upstream HTTP200 with 256 bytes; the disposable onion fixture returned local503/transport after the connection interval. No HTTP403 was observed. This is consistent with transient fixture/Tor reachability, not proof of a production regression or a passing onion route.

Diagnostics ZIP 12524751835 was downloaded and independently matched `05b3c26f392c8c1c1b9851179bcf03adf07139e32c2ba624f42fc6f4de54f531`. A single unchanged failed-job rerun was requested after reading the real failure and logcat. App transport and strict assertions remain unchanged. Full regression, process-restart fixtures, signing and upgrades are still gated; **NO-GO pending successful release checks**.

### Run #664 attempt 2: live Tor and restart fixtures green; Favourites singular label fix

Source `e8ac3ce03b35be0c99200eadc7d0e1bd97f28653`, job `120599928324`: 333 JVM tests, focused 28/28 UI tests, 70/70 preflight tests (including actual BBC and controlled onion over Tor), and both browser/camera independent-process seed/verify fixtures passed. The full unfiltered Android suite finished **315/316 passed, zero skips**. Only `ProfessionalUiTest.favouriteUsesGenericCollection`, line216, failed: it requires the displayed count `1 item`, while FavouriteHome still formats all counts as `items`. The collection name assertion immediately before it passed.

Smallest production correction: FavouriteHome now uses the same singular/plural rule already used by collection covers. No callback, media, authentication, storage, camera or network behavior changes. Keep the existing failing regression assertion unchanged. Fresh full CI, signing and both upgrades are still mandatory after this app change. **NO-GO until those pass.**

Attempt2 diagnostics artifact12527490458 was downloaded and independently matches SHA-256 `43e2962913369a20c1df407668f1d2319629150a376b7eb2bbc1bdd3af8ad29e`. The local workspace was pruned during the run; a clean clone was recovered from verified GitHub checkpoint `c613e1e9cdf922010234e429d304c990fd9835c9`. No committed work was lost.

## Final verification continuation — run #665

Recovered and independently verified branch HEAD `c4bc6d501ea8e03ac1e86896ecbc581837f886ad`; no newer runs were listed. Run `40012930568` (#665), source `3d5dd83258a81d65bd6b04a789a77abb7521997b`, passed host gates, compilation, 333 JVM tests/lint/assembly, 28 focused UI tests, 70 preflight tests and separate-process browser/camera fixtures. Full regression:315/316 passing, zero skips. Favourites singular-label regression now passes. Sole failure: `PrimaryCopyDialogTest.sourceChoiceFreshPinSelectionCopyAndReturnStayInHidden`, line92, timed out after30000ms waiting for `Verified 1 of 1`. Wrong-PIN rejection, fresh correct PIN, source listing and selection had already passed. The same unchanged copy test passed run#664 attempt2; there is no production copy/transport/storage diff in1.7.1.

Artifact12527594938 was downloaded; its ZIP independently matches `26366509fb7501dac60b7987924d2c08fc18e939b11b90af2b5c49d87be83660`. Existing logcat does not reveal final UI state, so no production cause is claimed. Narrow diagnostic follow-up preserves the semantics tree and synthetic screenshot before teardown on completion failure, and runs PrimaryCopyDialogTest in the focused gate for early feedback. Timeout and all assertions remain unchanged. No app fix, transport change or relaxed release gate. Full fresh regression and both upgrades/signing still required; no signed acceptance APK yet.
