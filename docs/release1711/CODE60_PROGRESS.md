# Code60 evidence report and actual Home status progress

Candidate NovaOS1.7.11.4/code60, package uk.co.traynor.privategallery, Android17/API37.
Existing integration branch integration/novaos-1.7-home2. Do not redesign Home, change encryption,
uninstall/clear owner data, replace Browser V1 or reopen VPN/Tor development.

Actual latest delivered baseline is original-key1.7.11.3/code59, appsource
ab659cf3578d08cdc0deb643f887e561abb14c63, signing activationd62d8fc3c1fb921db52c63a1364de3edad102c13.
Production42214765335/job127881954559/artifact13502360091 passed signed58->59 preservation/restart.
Original archive60654499bytes SHA256c4ffbed29381c36f349d43e1a52c46acd778687d3cc5899e4a9d48bb64afbc6e.
APK144778924bytes SHA2566db4f5853c8bbcdc379c0887002620d4f75cbd3c86dc98c2e46707b833d94796.
Original certificate94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.

## Status diagnostic — original evidence, not release approval

Source817675161fd5e886a90cbbad66b647fe0b15d278 contains unchanged59 application plus actual
Main/Home tests and synthetic diagnostic capture. Run42225645070/job127918319130 failed.
Artifact13505398428:839335bytes SHA256
bc766ec94f2cc6ab784cd8ec7eb54776c977e61019b920fb8c68c8cbfc22d5fa.
Original ZIP/CRC and XML verified; run aborted after67/133 cases, nonqualifying.

- Actual Home did not request immersive status hiding: original root statusBars=true and no
  custom status. This reproduces the integration policy gap. APK disassembly proves both Home
  width branches and actual Android provider are packaged; build omission is ruled out.
- Explicitly hiding the actual bar admitted the real provider. It displayed73% and updated to
  42% charging/Wi-Fi from real BatteryService protected broadcasts. A test assertion incorrectly
  tested list membership for Charging against one combined description; correct token matching
  retains the real charging requirement.
- Actual window resizing crashed when a cached pager child read widgets.clockVisible from a
  retired SpaceContext. Bounded fix resolves clock state in the admitted parent and checks the
  retained child before rendering. No preference reader is made permissive after retirement.
- Existing hidden wallpaper test timed out on a second grid selection after successful Cancel/
  reopen, before the status crash. Root cause unproven; no assertions were removed or weakened.
- Post-Gradle externalFiles pull lost files after app uninstall. AGP additional-test output is
  collected before uninstall; fixture now writes that directory. Success checkpoints require
  actual PNGs with the normal Primary screenshot preference enabled only in the isolated DEBUG
  synthetic fixture, with full preference/device-state restoration.

New Home-owned status-bar policy hides status bars only for live foreground Home, restores
prior window visibility/behavior on disposal, and keeps actual Android visibility suppression.
No status provider permission, network routing or polling change. Physical Samsung-specific
behavior remains owner acceptance; this diagnostic alone does not mark the requirement complete.

## Evidence report implementation

Existing exact-body one-shot consent, authenticated source hashing, credentials, encrypted
Analysis envelope/history and route authority are reused. New strict versioned report validates
selected IDs/literal passages/fields/timestamp claims; derives counts locally; retains source
fingerprints and read-only historical bindings; injects qualified labels and exact disclaimer.
Incomplete/refused responses become Insufficient Evidence with no accepted counts. No real API
calls or private evidence are used during development; all response tests are synthetic.

Native UI adds explicit Behaviour report selection, record/image inclusion review, encrypted
Save, report history/reopen and supporting-original buttons through the existing verified viewer.
Report-only plaintext export requires separate confirmation and document selection. Only report
ID/catalogue digest survive the picker; fresh same-Space authority and all retained contributors
(including removed record references) are revalidated before and during writes. Originals and
unselected records are never copied. No plaintext cache or storage/schema migration is introduced.

Independent review found two Important issues before checkpoint/signing:

1. File capture/creation/modification/import timestamps could multiply repeated-message events.
   Nineteen pure-JVM parser tests produced five real behavioral failures. Corrected distinct-time
   authority belongs to the actual overlapping conversation passages, never file dates or an
   unrelated timestamp citation. Short repeated passages merge despite date prefixes; ambiguous
   references cannot bridge distinct dated events. Historical reports retain app-validated grouping.
   Omitted chronology falls back only to validated canonical literal transcript quotations.
2. Dialog dismissal cleared parent task before VPN permission routing. The current dialog task
   is now captured before dismissal and passed explicitly through both permission branches.
   New UI case checks task preservation and task-chip changes; runtime green remains required.

Parser tests use the existing real org.json dependency on JVM. Android retains synthetic consent,
report-only export, native view and full encrypted repository/report/original workflow tests,
plus existing legacy AI and security cases. Selected matrix currently172Android/36classes;
The first combined build compiled app/instrumentation and passed430 JVM cases (24 report cases),
with zero failures/errors/skips. Final25th case covers33 merged citations reopening without
relaxing the provider32-reference cap; the final full JVM target is431cases. Source/runtime
are not yet frozen.

## Prepared release gates — not activated

Original59->60 fixture SHA256
72efb7de5d0308898b67b8146e4b9f4ce2ab875464e3ca4f4950ea6d2e35c773.
Eight controls digestfcf4052b4d2a88c7b452010d6c08d5c701bffee875483a0fe4702efe37208008.
Signing workflow SHA25680841a70160f17f3cc7d013f76b1bc6b5731d82570bc196bfb767268711dcbf3.
Fixture comparison pins the actual signed59 ACTIVATION fixture67f8f4… (the corrected secure
WallpaperStore.apply setup), preserving all assertions and ciphertext exclusions.
Thirteen release-control host checks plus the runtime-capture wrapper passed (14 total).
The reviewed fixture compiled against actual appsourceab659 on the host, preserving the only
two authorized package transformations; compile completed2m36. This is ABI compatibility only,
not an upgrade, signer approval or emulator result. Combined
API37 original reports/visible PNG verification, independent exact-source review and signed59->60
preservation/restart remain required before publication. Existing marker/review approve only59;
they must not be treated as approval for60.

## Independently reviewed checkpoint

Evidence report checkpoint df73ad8ab0bf0707e8872c3ab34e549a4494fdd1 is pushed.
Final combined host build passed431JVM cases across106suites, zero failures/errors/skips,
and compiled app plus instrumentation (1m46). The25th parser regression failed before the
stored-only chronology cap correction and passes afterward; provider references remain capped32.
Independent source rereview reports no outstanding Critical/Important findings, checkpoint-ready
only. Full172-case API37 runtime, actual Home PNG review and production signing remain pending.
Runtime matrix SHA256 564061623b8cd878be4955ddadc1092a6daeb17fe88ce7d0b8e04d75c9b88c09.

## First combined runtime — original failed archive retained

Source dbb946485d4f52231e90dd86723ad35e0d5d1647; run42233044111/job127943488970.
Artifact13510099839,6946168bytes, SHA256
925ca3a19eeb2bac642b6d760de44504827d1d6ef8c6e2c8ac9a70fdd1f795a9.
Original archive/CRC/provenance and reports independently verified:431JVM pass,172Android
executed,171pass/1failure/0skips. All report/consent/export/UI, prior wallpaper, privacy,
retained-browser/audio and other selected cases pass. This run is not release approval.

Actual Main/Home now initially hides Android status bars and visibly renders the real status.
Original PNGs show73percent/Wi-Fi in360x800,840x800 and840x360 configurations;42percent plus
charging bolt/Wi-Fi after BatteryService broadcasts;37percent after native Settings/Home return.
Parent and independent reviewer inspected original PNGs: readable, no header control overlap.
Lock restores the Android bar and removes Home/custom row. This is emulator evidence, not
physical Samsung Fold verification.

The sole failure occurs after ActivityScenario.moveToState(STARTED): the Compose test driver
requires a root while a deliberately paused Activity can unregister all roots. The test-only
correction uses the supported fetchSemanticsNodes(atLeastOneRootRequired=false) only for that
paused absence and its diagnostic capture; foreground calls remain strict and an explicit
lifecycle-below-RESUMED assertion is added. All battery/charging/resume/geometry/screenshot/
lock assertions remain. No production change, timeout increase, arbitrary delay or assertion
removal. Independent rereview approves this bounded rerun checkpoint. Fresh exact-source
172-case success remains mandatory before signing.
