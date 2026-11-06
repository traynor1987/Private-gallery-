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
