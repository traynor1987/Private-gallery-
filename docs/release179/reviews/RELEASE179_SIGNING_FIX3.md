# Evidence179 release controls — verification-range correction round 3

1 November 2026. **Specification verdict: PASS for the scoped correction and retry bindings. Security/implementation-quality verdict: PASS for this scope. New findings: 0 Critical, 0 Important, 0 Minor.** The reviewed correction may enter one further attempt of the existing production acceptance workflow. Actual corrected production instrumentation verification and the full original50→51 upgrade/restart remain mandatory; neither is claimed complete here.

## Corrected diagnosis and original evidence

The earlier inference that `Verified using v2 ...: false` established an absent v2 signature block was unsupported. That result can mean v2 verification was skipped because a newer scheme already covered the requested API range. Round2's command regression proved the explicit signer option and preserved guards, but did not characterize this real verifier behavior. This report expressly corrects that inference; it does not silently reinterpret the prior receipts as successful v2 verification.

The coordinator identifies second production run `41299753877`, job `124828087675`, failure artifact `13080923102`. Independently checked the supplied original ZIP at `/workspace/scratch/4181ccd09b2e/production179-retry-failure/original.zip`: 76,469 bytes, SHA-256 `2d3669734960c5d21bdd73123242a85128c4c7b8a57559f51b64ae41b36c6bb0`. Its paths are unique/safe and all 35 extracted files match the archive byte for byte.

The archived control hash identifies the actual round2 runner `233246421b783cb3901dde3efb96a16ee84f5d71beaa0e8891f3e8c34fc64f9f`, including explicit v2 signing. The retained signature receipt again reports v2false/v3true, one original signer. Instrumentation-hash continuation, seed, verify, restart and final upgrade-result receipts are absent. The mandatory v2-result check still precedes both application installs. **Both production attempts were scheme-verification admission aborts before install/seed.** Neither receipt establishes the presence or absence of the unretained instrumentation APK's v2 block, and neither establishes an executed application upgrade failure. The failed APK is intentionally absent from filtered diagnostics, so it has not been independently reverified locally.

Actual first-production namespace/frozen-CLI replay, compilation and application-signature successes remain documented in `RELEASE179_SIGNING_FIX2.md`. The next attempt must execute the unchanged gates again; these earlier successes do not bypass them.

## Actual implementation and cryptographic characterization

Read the retained actual `ApkVerifier` bytecode and the worker's complete round3 report/supplement. In the local implementation, offsets158–172 bypass `V2SchemeVerifier.verify` when the effective minimum is at least28 and a newer scheme was found; minimum24 instead reaches the v2 verifier at175–191. The v3 verifier remains invoked for its supported maximum range. The signer-engine bytecode separately generates enabled v2 and v3 blocks. Thus explicit v2 signing and lower-minimum verification serve distinct purposes.

The coordinator supplied an already-signed public AOSP diagnostic APK from `platform/tools/apksig/src/test/resources/com/android/apksig/golden-unaligned-v2v3-out.apk`. Independently checked its 8,631 bytes, SHA-256 `dd651a2f60754b10e352298d107f8f45395e30f08203671eb2befbdfbf216479`, and Git blob SHA1 `c18c445a43685c42f292a5a592eb47a15cbb31be`, matching the supplied official-file identity. Read and hash-checked both retained actual local apksigner outputs:

| Verification range | v2 verified | v3 verified | Signer |
| --- | --- | --- | --- |
| Minimum37, default maximum | false | true | One public AOSP signer |
| Minimum24, default maximum | true | true | The same public AOSP signer |

The coordinator recorded zero exits for both actual commands. Output SHA-256 values are respectively `9580277c857547142b58620434e23bf4a82a7de38790b7128f2e2af2ccdf9132` and `804b8ff696fcbbf2facc4c81a080733c1e32c267f41336175d048591f7dd0a3a`. This is genuine cryptographic characterization beyond a mock: identical pre-existing signed bytes exhibit the skip and broader verification. The sample's certificate `fb5dbd3c669af9fc236c6991e6387b7f11ff0590997f22d0f5c74ff40e04fca8` is not the authorized production signer. The worker also records rejection of its successful dual-scheme receipt by the unchanged actual production certificate assertions. The sample is diagnostic only and is not incorporated into a baseline, candidate, fixture or release approval.

Inspected the public-certificate Java probe and its recorded actual output, plus `processV3Configs` bytecode. For the verified original RSA4096 public key, the local engine selects `RSA_PKCS1_V1_5_WITH_SHA512` with algorithm minimum24; the last/sole signer's maximum is Integer.MAX_VALUE and its minimum is derived from the selected signature algorithms. This supports no28–36 gap for that local unrotated single-signer construction. The original50 minimum24 verification receipt independently reports a valid original v2 signer, but that v3false APK is not itself the dual-scheme reproducer.

The local apksig characterization implementation hashes to `5e5c33e9c713fb599c29ac2ba591d8b1bdfd1ffbef80772eaf1788314f9ee378`; it is not claimed byte-identical to CI build-tools36. These observations explain and justify the bounded correction, not a guaranteed future CI result. Actual production verifier failure, including any signature-range incompatibility, must still fail closed. No new key, signing operation, APK modification or device install was used by these supplied characterization probes; this reviewer inspected their bytes/results without rerunning them.

## Exact source change and retained boundaries

The complete production delta is the instrumentation verification line:

```bash
"$sdk/apksigner" verify --min-sdk-version 24 --verbose --print-certs "$instrumentation_apk"
```

Removing its single ` --min-sdk-version 24` sequence from current runner bytes exactly recovers the archived round2 hash. The explicit `--v2-signing-enabled true` signer option, original-key/environment references, private disposable instrumentation copy and immutable compilation provenance are untouched. No maximum verification API is lowered and v3 is not disabled. This broadens signature verification to the v2-supported range; it does not edit the APK manifest or authorize running the app on API24.

The original94f2 certificate, mandatory v2true, one-signer and tool-exit checks remain byte-identical. So do source/fixture checks, baseline/candidate package and min/target37 assertions, actual emulator SDK37 admission, clean production-package admission and all install/seed/install-r/verify/restart gates. No app, Android test, compiled fixture, workflow, version or API support change is introduced. The public diagnostic sample cannot satisfy the retained production identity check.

The updated two focused host methods execute the actual signing/verification block with a mocked external tool, now capture/assert the exact verifier argv and still assert the complete explicit-v2/original-key signer argv. The five negative cases retain rejection of v2false, foreign certificate, multiple signers, sign failure and verify failure before continuation. The reported RED rejects the old omitted-range invocation; GREEN passes both methods, and Bash syntax passes. I inspected these tests and recorded results without rerunning passing suites. They verify command/gate behavior and remain correctly separated from the actual cryptographic characterization and pending CI execution.

## Approval, retry and remaining gates

Compared current approval structurally against the second production artifact's `FINAL_SCOPE_APPROVAL.json`. The only change is the runner control hash. All other12 control hashes remain unchanged; all13 match current bytes. Frozen source `19e3275ef17746e780d64e6a684d94bab2cabdc3`, actual153-case manifest/evidence, original artifact/run, eight evidence hashes and canonical review hash `4ea5a1368fe4b3f7e6328b5471a1a85f4947c014f42437fdcc67cc04b02ea4c4` remain unchanged and applicable.

The marker is exactly the same source first line followed by `retry: explicit-v2-cryptographic-verification; previous-run:41299753877`. The workflow hash remains the accepted activated value. Root's stated sequence is a control/test/report/approval/review commit using literal `[skip ci]`, then a marker-only commit for the existing acceptance workflow. The marker-only push does not match the fast-development source-path filter. No new workflow or153-case Android rerun is introduced by this review; no commit or workflow launch is claimed to have occurred.

The retry must pass actual pre-secret namespace/frozen-CLI replay and scope checks, archived50/compiled-fixture admission, original-key production and instrumentation verification, then actual original50 seed → candidate51 `install -r` → verify → force-stop/restart → verify. Exact final APK/upgrade receipt validation and independent binary acceptance remain required before publication. Uninstall, data clear, replacement keys, skipping a signature assertion or synthetic upgrade receipts remain prohibited. Physical Fold acceptance stays `OWNER_REQUIRED`.

| Reviewed input | SHA-256 |
| --- | --- |
| `scripts/verify_synthetic_upgrade179.sh` | `4068d99d566dff66980fa66755a244e22836018bd9eb1e8ce67af1a929b0f67a` |
| `scripts/tests/test_upgrade179_runner.py` | `c0189a5c442a7ec1932ea8f074aa24a62fab9927823bcd0cf8dd1b3f0b341ed3` |
| `docs/release179/work/task-7b-release-report.md` | `7666fa7191fada450060169f159a09c1349bb6060f0187d9a62e1a9cd453f4e3` |
| `docs/release179/FINAL_SCOPE_APPROVAL.json` | `a78b6868a296d6f5e575b3b392e2842cf0bbe09dfe358fd1d39bf8b4dfe4c027` |
| Retry `.acceptance179-candidate` | `0862fbc2f80763169f73c3fc0aa05a769aa909fd637b3fc1ddaf2c7a0c92f94d` |

Only this report was written. No source edits, Git/CI operations, test reruns, secrets/signing access, device operations or subagents were used. Original evidence and earlier reports remain unmodified; this scoped report supplies the corrected interpretation and new acceptance limits.
