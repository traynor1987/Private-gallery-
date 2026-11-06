# Original-key code61 → code62 upgrade controls — prepared, not activated

These controls prepare a future in-place production upgrade from original signed61 to candidate62. They do not approve signing,
publish a production APK, or establish that an upgrade passed. The production package must
never be uninstalled, reset, or cleared. No signer environment, production install, CI trigger,
or publication was used while preparing these files.

| Identity | Required value |
|---|---|
| Branch | `integration/novaos-1.7-home2` |
| Production package | `uk.co.traynor.privategallery` |
| Original application source | `2da2895e387839ec37d0590dfb713dc1bec99853` |
| Original version/code | `1.7.11.5` / `61` |
| Original artifact/run | `13531400377` / `42269170059` |
| Original successful production job | `128068998830` |
| Original activation commit | `4904c937d3773aa07b117022edbcafcbcf8c5d5a` |
| Original archive SHA-256 | `26d0f86d8ece1b3c22d4b41c32eddca2592e9722c3b7b93f7d420fd42aeaa678` |
| Original archive bytes | `60738267` |
| Original APK name | `NovaOS-1.7.11.5-home2-code61-owner-acceptance.apk` |
| Original APK bytes | `144991916` |
| Original APK SHA-256 | `90083faaeb8ebaa896451d9dc721c4d26ee170135b3a4e1aad126038bcd7ef3e` |
| Original certificate SHA-256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |
| Candidate application source | Freeze at the next verified runtime checkpoint; no signing approval yet |
| Candidate version/code | `1.7.11.6` / `62` |
| Platform | Android 17 / API37; minimum and target37 |

An activation/control commit may differ from the frozen application commit. Preserve reviewed
controls before checking out the frozen application. Never substitute a rebuilt baseline APK.
`verify_upgrade1711_baseline.py` requires exactly the original APK and original evidence ZIP
inside the pinned downloaded artifact. It verifies APK name/size/hash and evidence ZIP name,
112168-byte length and SHA-256
`50fc64664a648aa78c049b9e6b55cb2cbaf4918029df208d0f4c6638a46f0948`.
The frozen application source/version/package/APK digest/original certificate and prior
upgrade/restart provenance come from `release-summary.json` at the nested evidence ZIP root.
The baseline61 receipt retains its historical `original_key_code60_upgrade=PASS` and
`restart=PASS`; candidate62 produces `original_key_code61_upgrade=PASS`. The activation
commit is distinct from the application source. The workflow separately
requires the pinned successful push run, original successful `home2-production` job and artifact metadata, repository, branch, activation
SHA and archive digest through GitHub's authenticated API. APK package/certificate/signature
checks remain separate mandatory steps.

## Required order before original-key access

1. Freeze the application source, complete its build/JVM/host checks and API37 runtime matrix,
   and download the original runtime artifact and run/jobs/artifact metadata using GitHub's
   authenticated API. Require a successful push on this exact branch/source from
   `.github/workflows/fast-development.yml`, with the successful `home2-integration` job.
   The gate requires all six successful steps: build/JVM, reused Home policies/encrypted
   adapter, preserved browser engine/isolated identity, Android integration/privacy tests,
   accounting of every selected original Android report, and actual Backup process-restart evidence. Empty, missing, additional,
   duplicate, failed, errored or skipped cases reject the gate. This matrix is selected
   runtime evidence, not a claim of physical Fold or live-account acceptance.
2. Obtain independent approval of the frozen source, complete reviewed matrix, original
   runtime archive, upgrade fixture, all eight control files, and the future signing workflow.
   Approval must explicitly cover production signing. A development TEST ONLY release or
   pending review cannot satisfy this gate.
3. Capture the fixture, controls, original runtime evidence and approval outside the checkout.
   Preserve the tree layout `scripts/*1711*` and `docs/release1711/UpgradeFixtureTest.kt` beneath
   a temporary controls root. `verify_release1711_gate.py` derives its control root from its
   own location; `PRIVATE_GALLERY_1711_ROOT` selects the frozen application checkout for shell
   operations. Do not expose signing credentials to the capture or compilation steps.
4. Run `verify_release1711_gate.py` against those original files. Obtain a fresh
   `READY_FOR_GATED_SIGNER` receipt; this means readiness only. Download and verify the pinned
   original code61 APK. Compile the reviewed fixture against the exact original code61 source
   with `compile_upgrade1711_fixture.sh`; validate its compilation receipt. No application
   baseline is rebuilt for installation. The two permitted harness transformations remove
   the debug application ID suffix and target the fixture at the production package.
5. Only after steps1–4 pass may the separately reviewed future workflow introduce the existing
   permanent signer. Build/sign the frozen candidate, and sign a disposable copy of the
   already-compiled instrumentation APK. Retain the unsigned/debug-signed original compiled
   bytes. No replacement key or automatic signing on ordinary runtime pushes is authorized.
6. Verify candidate package/version/minimum/target, non-debuggable status, one signer,
   APKv2 signature and the original certificate with `verify_production_candidate1711.sh`.
   The upgrade runner compares the signed instrumentation payload against the compiled APK;
   only standard JAR signature entries may differ. Both original and candidate application
   APKs receive the same identity/signature checks before installation.
7. On a fresh disposable API37 emulator with SELinux enforcing and the production package
   initially absent, run `verify_synthetic_upgrade1711.sh`: install the exact original61 APK,
   install reviewed original-key instrumentation, seed synthetic state, `adb install -r` the
   candidate62 APK, verify, force-stop, and verify again. Never uninstall/clear seeded data.
   Preserve original output, hashes, compilation receipt and certificates. A signed artifact
   is not delivery-ready until these checks pass. Physical owner acceptance remains separate.

## Gate inputs and exact approval lines

Run the gate before checking out the frozen application, using preserved files:

```bash
python3 scripts/verify_release1711_gate.py \
  --marker marker.json --review SECURITY_REVIEW.md \
  --run github-run.json --jobs github-jobs.json --artifact github-artifact.json \
  --runtime-archive original-runtime.zip --matrix reviewed-runtime-cases.json \
  --output gate.json
```

The workflow must fetch these API responses and archive itself. Locally fabricated metadata
is never release evidence. The marker requires `source`, `baseline_source`, `baseline_code=61`,
`candidate_code=62`, positive `test_run` and `test_artifact`, and exact `review_sha256`,
`fixture_sha256`, `matrix_sha256`, `runtime_archive_sha256`, and `controls_sha256`.
`controls_sha256` is the filename→hash map returned by `controls()` in the gate module.
The matrix is a nonempty class→test-method list mapping. Review must contain these complete
lines, with actual matching values, without implying approval through another document:

```text
Source: <frozen application SHA>
Decision: APPROVED
Production signing: APPROVED
Upgrade fixture SHA-256: <fixture digest>
Upgrade controls SHA-256: <canonical controls-map digest>
Runtime matrix SHA-256: <matrix digest>
Runtime archive SHA-256: <original artifact archive digest>
```

No such approval document or signer receipt is created by this preparation. Recompute hashes
after every control change. Current prepared fixture SHA-256 is
`537cccc991eeaba3c6e89d684048f81f89d8f4f9d8823dfeba2b4ff1028e51bb`.
Current eight-file canonical controls-map digest is
`7b537ca0902efa097263d1c1d324cf5185242fbbbc90df1195a30dc52ca8519c`.

The same existing runtime job executes eight real force-stop phases before its unchanged
main XML-producing suite: both Spaces at encrypted staging, uncommitted settings promotion,
durable committed promotion and verified destination. Each phase retains original prepare
and new-process verification transcripts plus one source-bound receipt. The gate requires
all seventeen original files, distinct old/new PIDs, successful host force-stop/old-PID absence,
expected deliberate process-crash output and successful native verification. These files are
inside the exact runtime archive whose SHA-256 is independently approved. They are not synthetic
XML cases. The main matrix contains 252 cases across 54 classes, preserving all original 196.

## Shell interfaces after preserving controls

All shell entry points require the exact integration branch, repository, push context, and
`FROZEN_SOURCE` matching checkout HEAD. Compilation additionally rejects an exposed signer
environment. The upgrade runner requires `PRODUCTION_UPGRADE=1`.

| Environment variable | Purpose |
|---|---|
| `PRIVATE_GALLERY_1711_ROOT` | Frozen application checkout; defaults to control script parent |
| `PRIVATE_GALLERY_1711_UPGRADE_FIXTURE` | Captured reviewed fixture path |
| `PRIVATE_GALLERY_1711_UPGRADE_FIXTURE_SHA256` | Captured reviewed fixture digest |
| `PRIVATE_GALLERY_1711_COMPILED_DIR` | Fresh output below `RUNNER_TEMP`; default `upgrade1711-compiled` |
| `PRIVATE_GALLERY_1711_GATE_RECEIPT` | Exact-source reviewed gate receipt |
| `PRIVATE_GALLERY_1711_BASELINE_APK` | Extracted pinned original61 APK |
| `PRIVATE_GALLERY_1711_CANDIDATE_APK` | Candidate APK; defaults to `app/build/outputs/apk/release/app-release.apk` |
| `PRIVATE_GALLERY_1711_SIGNED_INSTRUMENTATION_APK` | Separately original-key-signed compiled instrumentation copy |
| `PRIVATE_GALLERY_1711_EVIDENCE_DIR` | Fresh upgrade evidence directory; defaults to `app/build/production-evidence/from61` |

`ANDROID_HOME/build-tools/36.0.0` supplies verification tools. The runner and candidate
verifier read no signing credentials and perform no signing. Explicit debuggable checks use
`if ...; then exit1; fi`; negated `grep` under `set -e` is insufficient.

## Preserved assertions and local preparation evidence

The fixture retains every original native assertion and operation from
`docs/release17102/UpgradeFixtureTest.kt`, with the current61→62 identity guard and comments.
The complete fixture is also compared against the original signed61 activation fixture at
`4904c937d3773aa07b117022edbcafcbcf8c5d5a`, SHA-256
`54a13a0216d40fa397c6ea0f4fc9ce9ad90cbae3d22aa0f65abeca88700759f6`.
Only the enumerated identity/comment substitutions and the delimited portable-app addition are permitted. The stale fixture in frozen app source
`2da2895e387839ec37d0590dfb713dc1bec99853` is not the authority for this comparison.
A host regression reconstructs and compares the entire original fixture text after removing
the additive login-cookie and Home2 state blocks. Primary and independent Hidden PINs/keys, encrypted media,
video, Recently Deleted metadata/retention, collections/favourites, media dates/sort, camera
options/audio consent, VPN configuration, synthetic encrypted AI credential/consent, Panic
preferences, legacy Home settings, all eleven Private App fields/order/style and all three
owner catalogues, encrypted browser tabs/history/bookmarks/preferences, and independent
Evidence originals/OCR corrections/incidents/timeline/analyses/audit remain checked before
and after restart. Explicit candidate style/sort writes retain their exact existing receipts.

The additive Home2 fixture creates real code61 `SpaceContext` encrypted state separately for
Primary and Hidden: complete serialized Home layout/pages/dock/folders/drawer order, columns
and icon size, different committed owner themes, encrypted wallpaper bytes/reference/transforms,
a stacked note/clock widget with exact note text and active index, clock/reduced-motion/lock
preferences, and independent journal and mood records with original IDs/timestamps. Candidate
verification checks the original complete serialized records and read-only decoded widget/Mind
values, wallpaper plaintext bytes and owner isolation before and after restart. It reads the
raw encrypted Home state rather than constructing the candidate UI model, whose deliberate
first-open native-theme migration has separate focused runtime coverage. The retained encrypted
Vault-picker and encrypted-viewer wallpaper entry points have separate selected Android
runtime cases; this fixture preserves pre-existing owner wallpaper and layout state. The candidate
release preserves the owner-selected black-screen overlay, Android screen pinning, consented
Evidence AI reports and actual Home status indicator. Additive baseline-ABI seeds cover strict
2048/Sudoku/Snake saves (including Sudoku completion credit) and Calculator settings/history/memory.
The fixture writes only existing code61 SpaceContext APIs; candidate codec checks use reflection. Every new Home2
ciphertext is included in the existing full-file checks; missing records, empty fallback reads,
altered text/layout/theme or wallpaper bytes fail preservation.

The only filesystem exclusions remain the proof file itself and the exact two AndroidX runtime
files `profileinstaller_profileWrittenFor_lastUpdateTime.dat` and `profileInstalled`; no vault
subpath or WebView directory is newly excluded. The existing persistent synthetic cookie check
is retained. An additive `HttpOnly; Secure; SameSite=Lax; Max-Age=604800` synthetic login cookie
is set and flushed on original61 and verified on62 and after process restart. This uses no
live credential or network; it establishes persistence only, not live website compatibility
or encryption of provider-managed profile storage.

Local preparation checks and exact commands are recorded in `NOVA62_RELEASE_PREP_REPORT.md`.
The retained ten upgrade-control and three delivery-staging regressions check nested evidence,
identity tampering, original ABI transformation, every historical fixture operation, fail-closed
signer/runtime gates, debuggable APK rejection, instrumentation payload integrity and actual
workflow staging. New narrow tests reject preservation allowlist expansion and fabricated or
incomplete restart-phase accounting. These host fixtures are test inputs, never release evidence.

Candidate application/instrumentation compilation is separate from original-source upgrade
fixture compilation. No candidate API37 execution, original-source instrumentation compile,
independent final-source signing approval, production signing or in-place61→62 upgrade is
claimed by preparation. No workflow, signer or publication was activated. Historical releases,
source archives and the signed61 baseline bytes remain immutable.

## Published evidence excludes test packages

The original-key signed instrumentation exists only on the disposable runner. Published evidence includes the compilation receipt, baseline Gradle input, package/manifest reports and compilation log, plus the existing instrumentation signature/hash and payload-comparison receipts. Neither compiled nor signed test APK is copied into the evidence archive. Staging and the final ZIP reject installable packages, nested archives and signing files. The authorized NovaOS application APK is a separate release asset. The staging regressions execute the actual workflow body against synthetic inputs; all thirteen staging/upgrade-control tests pass. These controls still do not grant signing approval without successful frozen-source runtime evidence.

### Original60 authoritative wallpaper seed

The signed61 activation fixture already uses the authoritative WallpaperStore.apply seed.
Original60 deliberately preserves the separately committed wallpaper when a retained Home
layout saves. The upgrade fixture therefore seeds with WallpaperStore.apply, keeps its returned
opaque image reference/revision, and only then saves the Home layout. Direct blob creation plus
a stale layout mutation does not establish an applied wallpaper and is not a valid seed.
The fixture preserves every original assertion/exclusion and adds exact applied-record equality
for each owner. Full encrypted-file snapshots are still taken after seeding. The fixture remains
compiled against actual original61, and its new hash requires renewed independent approval.
