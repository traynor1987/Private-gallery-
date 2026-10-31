# Task7b release controls — independent initial scoped review

Date: 2026-10-31. This is a source/control review, not final application, runtime, signing, upgrade, delivery, or physical acceptance approval.

**Spec compliance verdict: CHANGES REQUIRED. Security/code quality verdict: CHANGES REQUIRED. Open findings: 0 Critical, 1 Important.** The upgrade runner rejects its required clean-emulator state, so the current controls cannot complete the specified actual original50-to51 upgrade. No signing or activation is authorized by this report.

## Scope and immutable context

Read `work/task-7b-release-brief.md` first, then its release report and review package, plus the referenced Task7 brief/signing reconnaissance. Reviewed the additive acceptance179 job, preserved-control/source/runtime validators, baseline-compatible compilation/provenance helper, production upgrade runner/receipt validator, fixture additions and public artifact curation. Direct interfaces inspected were the frozen route CLI, final-gates/artifact layout, production identity/signer helpers, baseline archive verifier, release configuration and the reflected evidence-store/catalogue signatures. This was not another application-wide review.

The coordinator supplied frozen application source `ddd0e3cdba8a67b34e806298822766139d3f46be` and in-progress Android run `41237030300`: application/all instrumentation compilation and native alignment had passed, while actual runtime remained unverified. That status was not upgraded or independently queried here. The route validator itself was not edited by Task7b and must remain the exact frozen validator.

Inactive state independently observed: `.github/workflows/acceptance1762.yml:467` still contains forty zeroes for acceptance179 `FROZEN_SOURCE`; `.acceptance179-candidate` is absent. No Git commands, CI operations, secrets access, signing, device operations, installs, uninstall/data clear, or source edits were performed in this review. The only repository write is this report. The previously passing 24 host methods were not rerun.

| Reviewed input | SHA256 |
|---|---|
| `work/task-7b-release-brief.md` | `4f7376cf467244d6b0b07420e4ca5e1f1f66a0be8babb967bf2b46a56cd23614` |
| `work/task-7b-release-report.md` | `f1247ef52f86cb23bf52ba0a746a33679f6abb248becc1261444bc306810f5de` |
| `work/task-7b-review-package.diff` | `494cde8770eb23b7319dd9fce21f65100a62f9e309156bd54d24ed0842960bf5` |
| `.github/workflows/acceptance1762.yml` | `7a196496917cf0edb53b6e2c89379ad2e3688ca3ba602360c9b7bef9ccfb658f` |
| `scripts/verify_synthetic_upgrade179.sh` | `ce8b74c3a71813d322a38776d546482c2adda6121af024cb31d980c152cc83d5` |

## Findings

### 1. Important — absent-package admission aborts the required original-key upgrade

**Location:** `scripts/verify_synthetic_upgrade179.sh:19` (interaction with `set -euo pipefail` at line 2 and the absence check at line 20). Called after original signer access and candidate signing by `.github/workflows/acceptance1762.yml:613-634`.

`installed_package=$(adb shell pm path "$fixture_package" | tr -d '\r')` is an unguarded assignment. Android's `pm path` returns status 1 and no package output when the package is absent. With modern adb forwarding shell exit status and this script's `pipefail`, the assignment returns 1; `errexit` terminates the script before the next line can accept the empty result. If the package is present, the next line rejects it. Consequently the intended clean API37 emulator cannot reach baseline install/seed at lines 65-79. This is a release-blocking liveness defect rather than an unsafe-success bypass: downstream upload remains blocked, but the original signer has already been accessed and a candidate built before this guaranteed admission failure.

**Independent evidence:** the current AOSP `PackageManagerShellCommand.displayPackageFilePath` returns 0 only when package/application info exists and returns 1 otherwise; `runPath` returns that result. Inspected [AOSP source](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/pm/PackageManagerShellCommand.java), observed blob `aa235c2258ac152037b0bc33edf860de20624a7a`, methods around source lines 729-760. A read-only local Bash probe of the exact assignment/guard with an `adb` function returning 1 and empty output terminated with `returncode=1`, empty stdout/stderr, before an immediately following continuation marker. This probe established shell control flow only; it is not an Android upgrade test.

**Required correction:** query package presence through a command whose successful empty result means absence, then reject any exact installed production-package match; require the query itself to succeed so transport/package-manager errors still fail closed. Alternatively explicitly distinguish documented absent-package status from other errors without blanket `|| true`. Add a narrow admission regression covering absent, present, and query/transport failure without signing or installing. Keep the no-uninstall/no-data-clear contract intact. Re-review the corrected runner/control hashes before activation; an actual original50 seed, exact signed51 `install -r`, verify and force-stop/restart verify are still required afterward.

The existing `scripts/tests/test_upgrade179_runner.py:21-30` cases reject before adb, so they do not exercise this newly changed valid-admission path. Their reported passing state does not address this finding.

## Other reviewed boundaries

- The acceptance179 job remains additive and constrained to the exact repository/push/branch/marker. Before secret-bearing steps it binds reviewed control bytes and source, API-fetched successful original workflow/job/named stages, exact nonexpired artifact identity/digest and downloaded ZIP hash, then runs independent runtime/source review validation. This review found no concrete bypass of those checks.
- Runtime revalidation parses exact unique manifest/XML identities, original exit/source gates and bound SDK/health/model/alignment receipts. It invokes the actual frozen route CLI against archived raw observations. Original CI proof and independently rewritten proof are kept separately, their normalized raw/control hashes are compared, and the original proof is restored for its original byte digest. The current-checkout capture path check rejects relocation; there is no path rewriting or validator monkeypatch to force a pass.
- The original code50 archive/APK source, byte length and hashes are pinned. Actual aapt/apksigner checks are wired before original signer access. Baseline application rebuilding/replacement signing is not part of the path. Instrumentation compilation happens once from exact baseline source before secret access, applies the two declared package transformations, and records original/transformed fixture/build plus APK/package/manifest/compiler hashes. The later runner validates this compilation and resigns a disposable copy with the original certificate.
- The reviewed fixture keeps the original legacy assertion bodies and exact numeric/ciphertext handling; new reflective first-use writes occur after legacy checks and capture separate owner catalogue/file hashes for restart. Added metadata, OCR raw/correction, incident/timeline/search and advisory source-reference assertions match the directly inspected candidate interfaces. Source inspection and the reported host ABI check do not establish real baseline Android compilation or original-key upgrade success.
- The runner contains no uninstall/data-clear operation and orders archived baseline install/seed, exact candidate `adb install -r`, verify, force-stop and restart verify. Upgrade receipts are useful only in conjunction with that actual trusted workflow execution; synthetic host receipts or standalone receipt-validator success must never be represented as an executed upgrade.
- Delivery is success-dependent and stages one candidate APK beside an explicit public receipt allowlist. Failure upload is restricted to the curated evidence subtree, and original signing material cleanup is unconditional. Baseline/test APKs, unfiltered route fixture trees, private keys/configuration and Tor cookie paths are outside the delivery allowlist. Physical acceptance remains owner-required.

## Remaining gates and disposition

Resolve finding 1 and obtain a scoped correction review. Keep `FROZEN_SOURCE` zero and the activation marker absent meanwhile. Actual exact-source Android17 focused/raw-route gates and consolidated security approval remain prerequisites for activation; pre-sign baseline instrumentation compilation, original certificate verification, production identity/distribution/alignment checks, actual original50-to51 upgrade/restart and final single-APK verification remain required at their configured stages. No actual gate is satisfied by this report or by synthetic host tests. There is no final approval machine block in this report.
