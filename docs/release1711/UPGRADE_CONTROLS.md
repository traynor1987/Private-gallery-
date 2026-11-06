# Original-key code54 → code56 upgrade controls — prepared, not activated

These controls prepare a future in-place production upgrade. They do not approve signing,
publish a production APK, or establish that an upgrade passed. The production package must
never be uninstalled, reset, or cleared. No signer environment, production install, CI trigger,
or publication was used while preparing these files.

| Identity | Required value |
|---|---|
| Branch | `integration/novaos-1.7-home2` |
| Production package | `uk.co.traynor.privategallery` |
| Original application source | `9efd19540a1d75efa1b30f4e23c9cbb096f93aff` |
| Original version/code | `1.7.10.2` / `54` |
| Original artifact/run | `13108872409` / `41369126405` |
| Original activation commit | `7f2c73fcaa32189b39829e829bcaecf7918ff50e` |
| Original archive SHA-256 | `7e141abd9ba9c1e9e440dd0e08bf6cb6bfd869808f0d5bec226ff43fcb858574` |
| Original APK name | `NovaOS-1.7.10.2-code54-owner-acceptance.apk` |
| Original APK bytes | `138323890` |
| Original APK SHA-256 | `3e9497d87c074d6d49bfd42a2b44cc5f2cd46ec0b7476d35d030e75b9cd35ce2` |
| Original certificate SHA-256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |
| Candidate application source | Freeze at the next verified runtime checkpoint; no signing approval yet |
| Candidate version/code | `1.7.11` / `56` |
| Platform | Android 17 / API37; minimum and target37 |

An activation/control commit may differ from the frozen application commit. Preserve reviewed
controls before checking out the frozen application. Never substitute a rebuilt baseline APK.
`verify_upgrade1711_baseline.py` verifies the exact archive, source receipt, APK name, size and
hash. APK package/certificate/signature checks are separate mandatory steps.

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
   original code54 APK. Compile the reviewed fixture against the exact original code54 source
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
   initially absent, run `verify_synthetic_upgrade1711.sh`: install the exact original54 APK,
   install reviewed original-key instrumentation, seed synthetic state, `adb install -r` the
   candidate56 APK, verify, force-stop, and verify again. Never uninstall/clear seeded data.
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
is never release evidence. The marker requires `source`, `baseline_source`, `baseline_code=54`,
`candidate_code=56`, positive `test_run` and `test_artifact`, and exact `review_sha256`,
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
`6b665eacb36a0e5721db66c621332b2a5f3f2b49f15cd3138c539313696a778d`.
Current eight-file canonical controls-map digest is
`81a0dfb220376809960e3cca6b61f913eee3999766f915445f9779fd2d5f25ea`.

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
| `PRIVATE_GALLERY_1711_BASELINE_APK` | Extracted pinned original54 APK |
| `PRIVATE_GALLERY_1711_CANDIDATE_APK` | Candidate APK; defaults to `app/build/outputs/apk/release/app-release.apk` |
| `PRIVATE_GALLERY_1711_SIGNED_INSTRUMENTATION_APK` | Separately original-key-signed compiled instrumentation copy |
| `PRIVATE_GALLERY_1711_EVIDENCE_DIR` | Fresh upgrade evidence directory; defaults to `app/build/production-evidence/from54` |

`ANDROID_HOME/build-tools/36.0.0` supplies verification tools. The runner and candidate
verifier read no signing credentials and perform no signing. Explicit debuggable checks use
`if ...; then exit1; fi`; negated `grep` under `set -e` is insufficient.

## Preserved assertions and local preparation evidence

The fixture retains every original native assertion and operation from
`docs/release17102/UpgradeFixtureTest.kt`, except the required54→56 identity guard and comments.
A host regression reconstructs and compares the entire original fixture text after removing
the additive login-cookie block. Primary and independent Hidden PINs/keys, encrypted media,
video, Recently Deleted metadata/retention, collections/favourites, media dates/sort, camera
options/audio consent, VPN configuration, synthetic encrypted AI credential/consent, Panic
preferences, legacy Home settings, all eleven Private App fields/order/style and all three
owner catalogues, encrypted browser tabs/history/bookmarks/preferences, and independent
Evidence originals/OCR corrections/incidents/timeline/analyses/audit remain checked before
and after restart. Explicit candidate style/sort writes retain their exact existing receipts.

The only filesystem exclusions remain the proof file itself and the exact two AndroidX runtime
files `profileinstaller_profileWrittenFor_lastUpdateTime.dat` and `profileInstalled`; no vault
subpath or WebView directory is newly excluded. The existing persistent synthetic cookie check
is retained. An additive `HttpOnly; Secure; SameSite=Lax; Max-Age=604800` synthetic login cookie
is set and flushed on original54 and verified on56 and after process restart. This uses no
live credential or network; it establishes persistence only, not live website compatibility
or encryption of provider-managed profile storage.

Local checks completed during preparation:

- Eight host control tests passed, including actual-script injected-tool rejection of
  debuggable baseline/candidate APKs before any install/sign; provenance and runtime tampering;
  complete original-fixture preservation; and signed-instrumentation payload integrity.
- `bash -n` passed for all four new shell files. Python control imports/compilation passed.
- The actual downloaded original artifact passed the new pinned-archive validator, yielding
  the exact138323890-byte APK/hash above. Read-only `apksigner verify` confirmed one signer,
  APKv2 verification and the original certificate.
- Actual original-source Android fixture compilation, independent final signing approval,
  production signing,54→56 emulator upgrade/restart, delivery and physical owner acceptance
  remain **PENDING**. No local synthetic test output is substituted for these gates.
