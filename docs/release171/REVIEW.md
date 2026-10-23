# 1.7.1 independent review

Reviewed on 23 October 2026. These are source and emulator screenshot reviews, not physical-owner acceptance.

## Scope and findings

- AI source replacement originally retained the viewer's previous parent ID when saving. This was corrected with source-aware ordinary, remote and AI callbacks. Focused tests cover all three routes, consent after replacement, the exact custom prompt, late thumbnail callbacks, and protected result previews.
- Result fullscreen preview reuses its owned bitmap and clears on background, discard and regeneration. It does not create a public cache or send an additional provider request.
- Camera changes are presentation only. Capture lifecycle/lock handling, the intentionally black capture surface and core camera implementation remain unchanged from the verified baseline.
- Encryption, vault storage, database compatibility, browser routing and transport sources remain unchanged. The unavailable combined VPN + Tor option stays disabled.
- Collection cards/counts change presentation only. The media loader rejects disposed or superseded callbacks without recycling Primary cache-owned bitmaps.
- Browser screenshot fixtures now resize actual emulator windows before activity launch, including dialogs, and restore size/density after teardown.
- Camera text checking initially compared paragraph constraint width with a shrink-wrapped text node. Captured metrics and screenshots demonstrated the false positive. The corrected checker examines rendered line bounds, height, omitted lines and ellipsis. Negative controls deliberately render each kind of clipping. No labels or classes are exempted; runtime verification is still required.

## Fresh visual review

Run 39978808692, source `47003864f48526ffa29b003cfcc2796b704dfdc9`:

- Primary dark browser fallback has readable contrast.
- Primary settings match the requested physical emulator windows. Hidden dialogs fit cover/inner sizes, with title and Done accessible at landscape font scale 1.3.
- Combine shows both actual synthetic source images, prompt and Generate on cover portrait without a redundant large canvas.
- Camera entry labels fit in the captured cover and inner layouts.
- No blocking visual issue was found. Short landscape screens require scrolling; the Hidden Browser central connection-settings action can initially be below the viewport, while its toolbar settings control remains accessible.

The neutral palette's 16 measured text/surface pairs per theme have minimum ratios 6.52:1 dark and 5.40:1 light. This is not a measurement of every rendered state. Automated callbacks and screenshots do not establish physical screen-reader, gesture, hardware-camera or Fold hinge behavior.

## Release-path review

The pinned baseline is 1.7/code33 source `18789f0f595abdfcd54e931671a82c23289af1ea`; candidate is 1.7.1/code34. Production verification requires package `uk.co.traynor.privategallery`, the original certificate SHA-256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`, a non-debuggable APK and recorded source/APK hashes.

Synthetic upgrades seed distinct Primary/Hidden PINs, reject cross-vault PINs, compare decrypted photo/video and encrypted-file hashes, and preserve collections/membership, favourites, theme/screenshots, Hidden timeout, AI settings/consent and stored VPN configuration. Installation uses `adb install -r` and verifies again after restart. No data is cleared after fixture seeding. Candidate upload is gated on the signed production-package upgrade passing.

No blocking release-path gap was found. The fixture rebuilds the pinned baseline and operates on synthetic data in a disposable emulator; owner-media preservation and live transport operation still require owner acceptance. Consult `V1_7_1_RECOVERY.md` for actual completed run and artifact evidence rather than treating this review as execution evidence.

## Fresh continuation review — 23 October 2026

A separate reviewer examined `04341a8dccdb1f9cb54626fb5d5472568e9930a9` against baseline `23b4b1681736e7c9b6635a9c16184abfcc95b507`. No concrete production-source release blocker or optional polish finding requiring an implementation cycle was found. Source-aware saves, authenticated source replacement, consent reset, exact prompt, bounded previews and cancellation/disposal boundaries were checked. Core crypto/storage/camera/transport directories have no diff. The black camera subtree and controller calls remain intact; responsive large-font controls were visually inspected. Original-certificate/code34 gates and immediate/restart synthetic upgrades are retained.

Runtime release approval remains withheld pending successful camera checker negative controls, fresh full regression, original-key signing and both upgrade checks. Physical Fold accessibility, camera hardware, live Tor/VPN and owner-media preservation remain owner acceptance items.


### Owner launcher branding follow-up

Independent focused review of owner artwork crop/export, adaptive/round/monochrome resources and dedicated acceptance gate found no additional blocking issue. Local branding checks and four evidence-gate negative tests passed. Corrected the launch probe to exported LauncherActivity and require full Android workflow to equal its original content except the explicit focused-harness path exclusions. PNGs and focused summary/source are archived. Kotlin compilation, real Android resource rendering, original signing and both synthetic upgrade gates remain CI requirements, not review claims. Physical One UI masking/cache refresh and existing owner media remain owner acceptance.


Focused evidence follow-up: #666 full suite315/316 has only the controlled onion local-transport failure; the same strict test passed preflight. Independent review accepts a narrowly scoped unchanged-test rerun with equivalent controlled fixture, immutable failed-artifact provenance, exact315passes/one namedfailure/zero skips or errors, fresh strict Tor success and fresh launcher/upgrades. Retain #666 as FAILED and report the isolated retest separately. This does not rule out suite-order or network variability; a repeat failure blocks release. No production transport or assertion changes.


### Repeated onion timeout: fixture-readiness correction

Independent review of separate Tor client/cache readiness, exact-body SOCKS-only retrieval before URL export and retained cleanup diagnostics found no blockers. Three focused tests and whitespace check pass. This fixes an established fixture readiness gap; it does not yet attribute the Android timeout to that gap. Fresh normal full Android CI is mandatory, retaining all assertions, transport isolation, signer and upgrade checks. The earlier isolated-retest evidence path is historical and is not the final release gate for this continuation.


### #667 complete test evidence and post-test shell repair

Independent review found no blockers in moving the multiline post-test block into one external Bash script. Two shell tests pass, including a negative control reproducing split-if syntax failure. All333JVM/31focused/70preflight/317full cases actually passed. The final acceptance reuse gate pins that exact artifact and requires identical complete app/build/test/resource tree, rejects any failed/skipped/error case and nonallowlisted source changes, and retains fresh launcher/upgrades/original signer. Four gate tests and whitespace check pass. The workflow #667 remains accurately reported FAILED after tests; remaining upgrade/signing stages must pass before handoff.
