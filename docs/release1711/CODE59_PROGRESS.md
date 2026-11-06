# Code59 navigation, live status and first-launch presentation

Branch integration/novaos-1.7-home2; candidate NovaOS1.7.11.3/code59, Android17/API37.
Signed upgrade baseline58: ce743678cca903f41a376e5227eb381f9fbb9574 (see CODE58_DELIVERY.md).
Status component checkpoint: bb5ed23e8e9e55d023ba0505c6755c38c216f234.

Source implementation complete; runtime/signing/upgrade/publication remain pending.
Local full app + instrumentation compilation and406JVM cases passed, zero failures/errors/skips.
Host Home/encrypted adapter/native-theme/concealed-entry, PrivateApps8groups, V1 preservation,
32status checks, navigation3/source admission3, and13release/staging checks passed.
Current focused matrix:130cases/29classes, including real Main cold/warm/required-blocked/retry,
Home/background/lock/conceal, immutable UUID/WebView, OS consent metadata stop/resume and
old-controller retirement, menu stale/disabled callbacks and genuine started HTML audio.
Status simulation covers battery/charging/saver and radio/validation transitions; actual Android
bar visibility/lifecycle, compact font bounds and battery fill pixels are instrumented.

Code58 was not already fixed: controller launch required ready networking before remembering
saved identity, while presentation policy showed browser chrome for blocked/failed App Mode.
New captured admission metadata keeps app-owned privacy/loading/error UI, opens only through
the unchanged production session API after real readiness and saved-definition revalidation.
An explicit Android VPN consent handoff may retain admission metadata, never old transfer
leases. Lock/conceal/ordinary background cancel; another unlock cannot revive it. No artificial
readiness delay, browser-engine rewrite, new VPN route or weaker fail-closed gate is introduced.

Independent review found/fixed stale menu app identity, stale availability, failed-admission
fallback, paused retry, and immediate lock/conceal retirement. No remaining Critical/Important
source findings; original exact-source API37 evidence is still required before signer approval.

Broad archival host discovery was also inspected: unrelated existing TLS fixture-count,
legacy no-emulator fast-lane expectation, old1.7.7 version expectation, and document-fixture
method-field cases fail. They were not changed or weakened for this focused UI release.

Physical Galaxy Z Fold, live VPN handshake/Android consent UI, real mobile radio transitions,
Samsung fingerprint and third-party accounts/playback require owner acceptance. Simulated
readiness/status and emulator tests are not claimed as physical network or Fold verification.

## Runtime1 retained evidence

Exact source d0babe10bf1e96e395033865030ca35362c89fba, run42199798600/job127831977600,
artifact13493769315 SHA256ef8e42799799cd64f3e8c8774e5a26b6a81e606be99182553b2a8f987bd21e8f.
Original XML130cases:129pass,0skip, onefailure in actualAndroidStatusBarAndCoveredSurfaceStopSubscriptions
at setBarsVisible(true), waiting for platform status bar visibility. All first-launch, menu,
retention/audio, navigation and security cases passed. Original ZIP/CRC and reports retained.
The selected google_atd image removes SystemUI (Android official ATD documentation), which
cannot exercise a real status bar show transition. Google official image metadata confirms
system-images;android-37.0;google_apis;x86_64 exists. Change only this existing job to that
full API37 image and require com.android.systemui before tests. No app code, cases, assertions,
timeouts or shipping gates changed. Repeat complete exact-source130-case batch.
Reference: https://developer.android.com/studio/test/managed-devices#whats-removed

## Runtime2 retained evidence

Source a1ea6ff961e12d984f424c5f1c6b7bd73ee5ba1c, run42201641601/job127838096719,
artifact13494957675 size418373 SHA25619f6b2ada7ed34729ea8189b286cea79b651410520b0d2c638ee1840056cd09b.
SystemUI guard passed, but instrumentation aborted after12/130 when Android surfaceflinger's
RegionSampling thread hit mapper.ranchu assertion !hasReadColorBufferDma and restarted the
Android system. Original crash stack/ZIP/CRC verified; incomplete report is nonqualifying.
Existing repository production and older focused lanes already pin SDK command tools22.0
and validate AVD targetandroid-37.0 to avoid old-tool Major.Minor target misparsing and this
graphics failure. Port that same tool pin into home2-integration and require actual22.0,
correct target, API37, SystemUI and stable surfaceflinger before the unchanged130 tests.
No product, cases, assertions, timeouts, GPU flags or signing controls changed.
Primary reproduction: https://github.com/actions/runner-images/issues/14484

Runtime3 b8379d3d8dc76b932a7080108982f49c2e558366/run42203253768 is superseded
before qualification: the emulator runner executes each inline script line in a separate
shell, so a multi-line preflight cannot retain variables/loops there. Move health checks
into scripts/home2_emulator_preflight.sh invoked as one command. Three host tests execute
the actual script and reject wrong API, missing SystemUI, wrong AVD target, surfaceflinger
restart and DMA crash signatures; healthy path records all15 observations. CI runs those
checks too. No application, Android cases, assertions or timeouts changed.

## Runtime4 and bounded follow-up

Source1034dd6a8fe6fea8705353986336d49bd52cc6d9, run42203689872/job127845239913,
artifact13495857907 size1301368 SHA256578bb7c9219851aa08df3f20d05e208e5ef976a481aff0504421f1535a438f3c.
Verified actual tools22.0, AVDtargetandroid-37.0, SystemUI,15stable surfaceflinger samples,
empty crash preflight, and complete130 originals:128pass2fail0skip/error. The tool pin
resolved the emulator abort; every first-launch/navigation/menu/audio/security case passed.

Actual status-bar transition now occurred, exposing a real suppression defect: original root
statusBars changed visible but child-dispatched Compose visibility remained stale/consumed.
Bounded product correction: additive root pre-draw/attach observer reads original window
insets without replacing listeners or polling; native visible event retires subscription
synchronously, unknown insets suppress chrome, and transition waits one draw for composition.
Original six status test cases/assertions/timeouts are unchanged;32host checks remain green.

Catalogue UUID test's repeated fake native launches keep Home in the same Activity; actual
Gboard IME show/hide transitions move controls. Fixture helper now explicitly hides/clears
previous search IME, waits actual hidden state, clicks Apps exactly once, waits displayed,
then retains every original UUID/all-records-visible/exact-launch assertion. No case or
required result is removed, skipped or retried. Independent bounded review found no
Important issues. Full local app/instrumentation build and406JVM passed after these changes;
the same complete130-case full-SystemUI batch remains mandatory before signing.

## Runtime5 lifecycle correction

Source ae4773fdd84a37c89f5cb5540d2d55bb7f09313a, run42206787672/job127855354427,
artifact13497997053 size1296638 SHA256b46499c18dbe9447468ef8f9f3c02effa70facc65b88a49a0d0055334895b715.
Original ZIP/CRC and complete130 cases verified:129pass1failure0skip/error. Both previous
statusbar suppression and repeated catalogue drawer cases now pass, along with all first-launch,
navigation, retained audio, authentication, concealment and encrypted storage cases.

Remaining failure is status subscription re-admission after an81ms pause/resume. The pause
lifecycle callback closes synchronously, but both state writes can coalesce before Compose
commits an effect; the unchanged root-insets observer then suppresses an identical hidden-bar
event. Resume now reconciles the remembered session directly using latest enabled state and
actual original root insets. Pause/lock/visible-system-bar also clear snapshots synchronously.
The same lifecycle test retains its assertions/timeouts and now repeats three rapid cycles
without an intervening Compose idle frame. No test selection or release gate is relaxed.
Observer restarts now detach only their own listener: the remembered session remains the
sole final-disposal owner, preventing a paused-effect cleanup from closing a newly resumed
subscription or erasing its initial reading. The test checks exact opens/closes after idle,
three rapid cycles, a fully committed paused cycle, and a fresh42% reading. Independent
bounded source review found no Important issues; signing is still not approved.

## Runtime6 passed; production1 seed correction

Exact application sourceab659cf3578d08cdc0deb643f887e561abb14c63, run42209939762,
job127865786721, artifact13499660758:68233934bytes, original ZIP SHA256
194292602306328a64b67c1ba1e173448ac9bd3f6657f37f566ae6dd45c6398f.
Parent and independent reviewer verified authenticated provenance, CRC/safe members, frozen
source, exactly130Android/29classes and406JVM unique passes with zero failures/errors/skips.
Actual test APK identity/hash/signer and fullSystemUI/tools22/API37 receipts also pass.
Original-key signing was independently approved with the then-reviewed fixture.

First production run42212278039/job127873637498 at activation5ee4c8f0b982b765655fa547c4d4042dfd5b3b99
built and validated the original-key candidate, but stopped during seed on original58 BEFORE
installing59. No APK was published. Original diagnostic artifact13499402896:102397bytes,
SHA2566cb4af078993686dc0b810810c99f569697c2686e665b1a4183a44ed52d2f8a7.
Seed failed with JSONObject No value for image at home2State line483. Code58 correctly
rejects stale Home wallpaper changes: writing a blob then model.change cannot replace its
authoritative applied record. This is fixture setup, not a candidate migration/data-loss result.

Correction only to the review-bound upgrade fixture: seed through original58 WallpaperStore.apply,
retain its returned opaque ID and revision, then save the layout. All original verification
assertions and ciphertext exclusions remain; exact dedicated wallpaper-record preservation
is additionally asserted for both Spaces before/after upgrade and restart. The complete signed58
fixture-comparison host test permits only this explicit seed replacement and additive assertion.
It failed against the old setup and passes after the correction; all13focused release controls
pass. Original58 compilation and renewed independent fixture signing approval are required.
Application/runtime source, controls, workflow and130-case matrix are unchanged.
Corrected fixture SHA25667f8f46ef2381da7920f5a28b65f964137087ae068934fbae7f22bb827a299a1
compiled against actual original58 in2m32s. Independent review verified original failure,
complete historical comparison, exact compiled inputs and unchanged other hashes; renewed
original-key signing approval recorded in SECURITY_REVIEW.md. Original runtime gate and
all13release controls pass again. A fresh production upgrade remains required before publication.
