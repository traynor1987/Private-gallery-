# Code60 — Evidence AI reports and actual Home status

NovaOS1.7.11.4 / versionCode60, Nova Home2.0, Android17/API37 only.
Package uk.co.traynor.privategallery. Branch integration/novaos-1.7-home2.

[Download original-key signed owner-acceptance APK](https://github.com/traynor1987/Private-gallery-/releases/download/novaos-1.7.11.4-home2-code60/NovaOS-1.7.11.4-home2-code60-owner-acceptance.apk)

Install over the existing NovaOS installation. Do not uninstall it or clear application data.
This is a signed owner-acceptance prerelease; physical Samsung Galaxy Z Fold acceptance remains separate.

## Frozen sources and identity

- Report feature: df73ad8ab0bf0707e8872c3ab34e549a4494fdd1.
- Actual Home status fix: dbb946485d4f52231e90dd86723ad35e0d5d1647.
- Final verified application source: f2727d75d142c2a733c53861e1a0f74440e0b6b5.
- Frozen source tree: 3c1141d32331fdfb26ba34f50d8200d916f0a6ee.
- Signing activation: 9b21d17c180a319fe93e2d06f18b057b5c9f964c.
- Actual signed59 baseline application source: ab659cf3578d08cdc0deb643f887e561abb14c63.

APK144942764bytes, SHA-256:

```text
d21b9134409f24bd460368b36aea715de82b6e00d98a303f0bdd9c514b3f1f82
```

Original production certificate SHA-256:

```text
94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17
```

Actual downloaded APK verified with aapt/apksigner: original package, version1.7.11.4/code60,
minimum/target API37, nondebuggable, one original RSA4096 signer and APKv2 verification.
No replacement signing key, owner uninstall, data clear or encryption migration is introduced.

## Exact implementation

Evidence Locker adds optional Behaviour report* for explicitly selected records/collections,
with a reviewable record subset and optional images off by default. It reuses the existing
locally extracted metadata/OCR, real authenticated source reader, exact-body one-shot consent,
secure transport authority, encrypted owner credentials and encrypted Analysis history.
Nothing is uploaded merely by opening, preparing or cancelling. Every request requires a fresh
explicit confirmation showing the transmitted text/instructions, selected image previews,
model, route, estimated cost and body digest. No automatic full-vault upload or retry is added.

The native report separates literal selected evidence, contextual inference and AI interpretation.
It provides a qualified potential-pattern or Insufficient Evidence outcome, confidence,
alternatives, context limits and seven local potential-event counts: emotional abuse, verbal
abuse, blackmail/coercion, gaslighting, threats, intimidation and manipulation. AI judgments
and counts have an asterisk. Category overlap and conservative deduplication are explained.
Every counted event requires a literal local transcript anchor and opens its supporting original
through the existing authenticated viewer with a fresh source-fingerprint check.

Counts cannot be inflated by screenshot capture/import dates or unrelated timestamp references.
Only explicit-offset local conversation timestamps inside the exact overlapping citation can
distinguish repeated occurrences; ambiguous or unzoned dates remain uncertain. Short repeated
passages merge conservatively. Saved canonical groupings and all citations survive reopening.
If provider chronology is omitted, the app uses only already validated literal transcript
quotations, with known dates first and explicit context limits. Model-supplied totals, fabricated
references/quotes/time claims, extra fields and app-owned validation metadata are rejected.
Incomplete/refused responses yield Insufficient Evidence without accepted counts.

Reports can be saved encrypted, reopened in AI reports and exported separately as labelled text
only after confirmation. Fresh same-Space authority, catalogue digest and every retained original
contributor are checked before and during export; removed/changed/Vault-only contributors fail
closed. No original media is copied, moved, deleted or edited. No plaintext cache, new key scheme,
source hash/metadata change or Gallery visibility change is introduced. The VPN permission handoff
retains the actual selected task before dismissing the dialog.

All reports prominently include:

> * AI-generated assessment based on the selected evidence. Findings are interpretations, not guaranteed facts, clinical diagnoses or legal determinations. Context may be incomplete, and classifications or counts may be inaccurate.

The missing Home indicator was implemented and packaged in delivered59, but actual Main/Home
diagnostics reproduced a window-policy gap: Android reported its status bar visible and the
custom indicator was suppressed. Home now owns status-bar hiding only while live and resumed,
restores prior visibility/behavior on exit, and retains suppression while the real Android bar
is visible. Battery/network values still come from the existing supported Android callbacks;
no polling, location permission, radio guesses or VPN routing change was added.
The retained pager also stops encrypted preference access after Space retirement; original
clock visibility is resolved in the admitted parent. Home appearance and layout are preserved.

## Verification and preserved features

[Successful runtime42235897971](https://github.com/traynor1987/Private-gallery-/actions/runs/42235897971):
job127953286741, artifact13511693979. Original archive74513773bytes, SHA256
4cd70b3820f133c5bda2edd26a74f86cb1d05e839f2d44040566652d2ac57b2f.
Exactly172 unique Android cases across36classes and431 JVM cases across106suites;
zero failures/errors/skips. Original reports, provenance, selector/matrix, isolated APK identity,
SystemUI/API37/tools22 evidence and actual screenshots were independently checked.
Fourteen release-control/capture host tests and the existing Home/private-app/native-theme/
concealed-entry/browser preservation checks also pass.

Actual Main/Home with the real Android provider initially displays73percent/Wi-Fi without
fixture-forced initial hiding. Protected BatteryService broadcasts update42percent with charging,
then37percent after native Settings/Home return, and61percent with charging after pause/resume.
Showing Android's bar removes the custom duplicate; locking restores Android's bar and removes
Home/custom status. Original compact360x800, wide840x800 and landscape840x360 PNGs were visually
reviewed and show readable status without overlapping header controls. Device battery settings,
window size/density/rotation and screenshot preferences are restored by the isolated DEBUG tests.

Selected tests cover report duplicates/counts/references/incomplete context, fresh consent,
cancellation, source changes and Space retirement, encrypted history and linked original,
report-only export and buffer cleanup; existing concealed Hidden entry/PIN-only authority,
Primary/Hidden storage, Gallery/Camera/Evidence, native theme, secure wallpapers, first-launch
App Mode, Recents/navigation, lock/Panic and retained web-app/audio cases remain passing.
Browser V1 preservation checks retain its constructor/configuration/stores/ordinary tabs and
session source SHA8f4c8e68023c5a73a648f8cf7c4e3328d13538de3b276b0220686f6798861e7b.
No Home redesign, browser replacement, VPN/Tor repair or future runtime was added.

The first combined runtime42233044111 had171/172passes: only the test driver's query after an
explicit Activity pause required a Compose root that could legitimately be absent. The bounded
test-only correction permits absent roots only for that paused query/capture, adds an explicit
below-RESUMED lifecycle assertion and preserves all foreground/battery/charging/geometry/lock
assertions. The fresh complete172-case run above passed. Original failed evidence is retained
and documented in CODE60_PROGRESS.md; it was never used as approval.

## Original-key upgrade and delivery

[Production42238614535](https://github.com/traynor1987/Private-gallery-/actions/runs/42238614535),
job127962769022: SUCCESS. Actual original signed59 -> signed60 in-place install, synthetic
encrypted-data/configuration preservation, force-stop and verification after restart PASS on
a fresh enforcing API37 emulator. All seed/verify/after-restart receipts contain OK (1 test).
The experiment begins with package absent and retains seeded data without uninstall or clear.
Reviewed instrumentation is freshly compiled against actual59 source before signer access;
its identity, original certificate, exact-source runtime/review/fixture/controls/workflow gates,
release lint, OCR distribution and16KiB alignment are verified before publication.

Fixture SHA72efb7de5d0308898b67b8146e4b9f4ce2ab875464e3ca4f4950ea6d2e35c773.
Independent review SHA854cfc29fff3692e5acd3a63d9c1ab993cde50907467dc31d7c4c4b8d44b750f.
Controls map digestfcf4052b4d2a88c7b452010d6c08d5c701bffee875483a0fe4702efe37208008.
Signing workflow SHA80841a70160f17f3cc7d013f76b1bc6b5731d82570bc196bfb767268711dcbf3.

Original production artifact13517195318:60716615bytes, SHA256
1a941bac0c92f845db5599afe16831659a0b0fea4042f3c7311bbd9978290c2a.
Evidence109868bytes, SHA256a62bf205e5578e7c57168096ff6275e495cabc1cca56ab18d2b111bbba37d59d.
Parent downloaded original ZIP and checked authenticated digest/size/CRC, exact delivery members,
actual APK package/signature/API/code, all upgrade/restart phase receipts, immutable source and
candidate hash, delivered review/control/fixture/compilation gates, safe nested evidence members
and all four GitHub asset digests/sizes. Evidence contains no APK, signing material or nested archive.

Independent post-publication review separately fetched authenticated run/jobs/artifact/logs and
release/tag metadata, inspected the actual original APK/evidence, reran aapt/apksigner and16KiB
alignment, checked all four asset digests and each upgrade phase/control binding. No remaining
material issue was found.

## Limits and Galaxy Z Fold acceptance

Physical Galaxy Z Fold acceptance: NOT PERFORMED. Automated resize/rotation checks simulate
compact/wide/landscape dimensions; they do not prove physical hinge behavior or Samsung hardware.
Live OpenAI/provider/model behavior was not exercised: HTTP tests use synthetic responses and no
private evidence. Classifications and counts remain fallible interpretations. Requests are bounded
to32 selected records/8 optional images, with local text anchors required for counted flags;
truncation or insufficient context cannot establish a proven pattern. Existing fail-closed transport
policy remains; this work does not validate or enable deferred Hidden VPN/Tor functionality.
Restricted mobile technology/signal details are omitted rather than guessed; real radio/VPN transitions,
live accounts, Samsung fingerprint hardware and third-party playback restrictions need owner checks.

- Install over code59 and confirm existing media, Hidden PIN-only concealed entry, collections,
  encrypted Home/wallpaper settings, Evidence and Browser V1 login/history.
- On Home, confirm live percentage, charger bolt, Wi-Fi/permitted mobile/disconnected state;
  fold/unfold, rotate, open native Settings and return Home, pause/resume and swipe the real bar.
- In Evidence Locker, choose a small collection/subset, prepare and cancel once; then explicitly
  approve a request, inspect starred counts/disclaimer and each supporting original, save/reopen
  encrypted history and separately confirm report-only export. Check lock and three-finger Panic.
