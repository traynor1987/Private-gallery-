# Original-key code58 → code59 upgrade controls — prepared, not activated

These controls prepare a future in-place production upgrade. They do not approve signing,
publish a production APK, or establish that an upgrade passed. The production package must
never be uninstalled, reset, or cleared. No signer environment, production install, CI trigger,
or publication was used while preparing these files.

| Identity | Required value |
|---|---|
| Branch | `integration/novaos-1.7-home2` |
| Production package | `uk.co.traynor.privategallery` |
| Original application source | `ce743678cca903f41a376e5227eb381f9fbb9574` |
| Original version/code | `1.7.11.2` / `58` |
| Original artifact/run | `13481647850` / `42171366749` |
| Original successful production job | `127739753358` |
| Original activation commit | `a7666cf9b44d8f193f252921e62ee0c9b33b0ecd` |
| Original archive SHA-256 | `5279a40b6a1ca1242af9647cf04d98e9ede3f497ce9991b70fe9f8e6986f5dce` |
| Original archive bytes | `60614078` |
| Original APK name | `NovaOS-1.7.11.2-home2-code58-owner-acceptance.apk` |
| Original APK bytes | `144697004` |
| Original APK SHA-256 | `d28e0de4a3aa6082922e20940f73e9e5f80d4ae9487dac0761338e6550abc59b` |
| Original certificate SHA-256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |
| Candidate application source | Freeze at the next verified runtime checkpoint; no signing approval yet |
| Candidate version/code | `1.7.11.3` / `59` |
| Platform | Android 17 / API37; minimum and target37 |

An activation/control commit may differ from the frozen application commit. Preserve reviewed
controls before checking out the frozen application. Never substitute a rebuilt baseline APK.
`verify_upgrade1711_baseline.py` requires exactly the original APK and original evidence ZIP
inside the pinned downloaded artifact. It verifies APK name/size/hash and evidence ZIP name,
103867-byte length and SHA-256
`bb455929217f870a1630b8ea7469d8c1990010c31fada2a433d61ffe6c2026f1`.
The frozen application source/version/package/APK digest/original certificate and prior
upgrade/restart provenance come from `release-summary.json` at the nested evidence ZIP root.
The baseline58 receipt retains its historical `original_key_code57_upgrade=PASS` and
`restart=PASS`; candidate59 produces `original_key_code58_upgrade=PASS`. The activation
commit is distinct from the application source. The workflow separately
requires the pinned successful push run, original successful `home2-production` job and artifact metadata, repository, branch, activation
SHA and archive digest through GitHub's authenticated API. APK package/certificate/signature
checks remain separate mandatory steps.

## Required order before original-key access

1. Freeze the application source, complete its build/JVM/host checks and API37 runtime matrix,
   and download the original runtime artifact and run/jobs/artifact metadata using GitHub's
   authenticated API. Require a successful push on this exact branch/source from
   `.github/workflows/fast-development.yml`, with the successful `home2-integration` job.
   The gate requires all five successful steps: build/JVM, reused Home policies/encrypted
   adapter, preserved browser engine/isolated identity, Android integration/privacy tests,
   and accounting of every selected original Android report. Empty, missing, additional,
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
   original code58 APK. Compile the reviewed fixture against the exact original code58 source
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
   initially absent, run `verify_synthetic_upgrade1711.sh`: install the exact original58 APK,
   install reviewed original-key instrumentation, seed synthetic state, `adb install -r` the
   candidate59 APK, verify, force-stop, and verify again. Never uninstall/clear seeded data.
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
is never release evidence. The marker requires `source`, `baseline_source`, `baseline_code=58`,
`candidate_code=59`, positive `test_run` and `test_artifact`, and exact `review_sha256`,
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
`2e6d4ab486eaf81899c4a4b6679d303990c4cca97b29f4364c3eef0fbb719b5d`.
Current eight-file canonical controls-map digest is
`2246a0d8daf326cabe85bb3a6ebfb6c4e061aa79300b0f125e9358692dc446b4`.

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
| `PRIVATE_GALLERY_1711_BASELINE_APK` | Extracted pinned original58 APK |
| `PRIVATE_GALLERY_1711_CANDIDATE_APK` | Candidate APK; defaults to `app/build/outputs/apk/release/app-release.apk` |
| `PRIVATE_GALLERY_1711_SIGNED_INSTRUMENTATION_APK` | Separately original-key-signed compiled instrumentation copy |
| `PRIVATE_GALLERY_1711_EVIDENCE_DIR` | Fresh upgrade evidence directory; defaults to `app/build/production-evidence/from58` |

`ANDROID_HOME/build-tools/36.0.0` supplies verification tools. The runner and candidate
verifier read no signing credentials and perform no signing. Explicit debuggable checks use
`if ...; then exit1; fi`; negated `grep` under `set -e` is insufficient.

## Preserved assertions and local preparation evidence

The fixture retains every original native assertion and operation from
`docs/release17102/UpgradeFixtureTest.kt`, except the required58→59 identity guard and comments.
A host regression reconstructs and compares the entire original fixture text after removing
the additive login-cookie and Home2 state blocks. Primary and independent Hidden PINs/keys, encrypted media,
video, Recently Deleted metadata/retention, collections/favourites, media dates/sort, camera
options/audio consent, VPN configuration, synthetic encrypted AI credential/consent, Panic
preferences, legacy Home settings, all eleven Private App fields/order/style and all three
owner catalogues, encrypted browser tabs/history/bookmarks/preferences, and independent
Evidence originals/OCR corrections/incidents/timeline/analyses/audit remain checked before
and after restart. Explicit candidate style/sort writes retain their exact existing receipts.

The additive Home2 fixture creates real code58 `SpaceContext` encrypted state separately for
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
release changes app navigation, the App Mode bottom sheet, first-launch presentation and live battery/connectivity display. Every new Home2
ciphertext is included in the existing full-file checks; missing records, empty fallback reads,
altered text/layout/theme or wallpaper bytes fail preservation.

The only filesystem exclusions remain the proof file itself and the exact two AndroidX runtime
files `profileinstaller_profileWrittenFor_lastUpdateTime.dat` and `profileInstalled`; no vault
subpath or WebView directory is newly excluded. The existing persistent synthetic cookie check
is retained. An additive `HttpOnly; Secure; SameSite=Lax; Max-Age=604800` synthetic login cookie
is set and flushed on original58 and verified on59 and after process restart. This uses no
live credential or network; it establishes persistence only, not live website compatibility
or encryption of provider-managed profile storage.

Local checks completed during preparation:

- All thirteen host upgrade-control/staging regressions passed. They include nested evidence and
  identity tampering, exact original-source ABI transformation, complete original-fixture
  preservation including an exact signed58 fixture comparison, fail-closed signer/runtime gates, real-script debuggable APK rejection before
  any install, signed-instrumentation payload integrity, and actual workflow staging execution.
- `bash -n` passed for the four shell controls; all four Python controls and workflow inline
  Python parsed successfully. The existing workflow still has eight jobs and unchanged
  release-marker/branch interfaces; no workflow was added.
- The actual signed58 provenance is pinned above; synthetic archive tampering tests verify the
  required source/version/certificate and historical57-upgrade receipt. This preparation did not
  download or rerun validation on the historical artifact.
- Original-source Android fixture compilation, independent exact-source approval, candidate
  API37 runtime evidence, production signing and 58→59 emulator upgrade/restart are not claimed
  by these local checks. No Gradle build, CI, signer access, install or publication was used
  while adapting the controls. Historical release/source archives remain immutable in Git.

## Published evidence excludes test packages

The original-key signed instrumentation exists only on the disposable runner. Published evidence includes the compilation receipt, baseline Gradle input, package/manifest reports and compilation log, plus the existing instrumentation signature/hash and payload-comparison receipts. Neither compiled nor signed test APK is copied into the evidence archive. Staging and the final ZIP reject installable packages, nested archives and signing files. The authorized NovaOS application APK is a separate release asset. The staging regressions execute the actual workflow body against synthetic inputs; all thirteen staging/upgrade-control tests pass. These controls still do not grant signing approval without successful frozen-source runtime evidence.

### Original58 authoritative wallpaper seed

Original58 deliberately preserves the separately committed wallpaper when a retained Home
layout saves. The upgrade fixture therefore seeds with WallpaperStore.apply, keeps its returned
opaque image reference/revision, and only then saves the Home layout. Direct blob creation plus
a stale layout mutation does not establish an applied wallpaper and is not a valid seed.
The fixture preserves every original assertion/exclusion and adds exact applied-record equality
for each owner. Full encrypted-file snapshots are still taken after seeding. The fixture remains
compiled against actual original58, and its new hash requires renewed independent approval.
