# Risk-based release verification policy

Version numbers describe intent; affected security boundaries determine the gate. Never mark a deferred or failed test passed. Tests and their assertions remain in the repository.

| Category | Default release verification |
| --- | --- |
| Major (2.0,3.0) | Complete Android17 regression, crypto/auth/isolation/network/media checks, independent review, original-key upgrade/signing |
| Feature (1.7.6,1.7.7,1.8) | New functionality, dependencies and affected boundaries; comprehensive security for critical changes; full lane for uncertain/high risk |
| Patch (1.7.6.1,1.7.6.2) | Independently approved impact manifest, fresh changed-component/dependency/security/Android checks, package/certificate verification and original-key upgrade/restart |
| Emergency hotfix | Explicit independent emergency risk decision; affected security boundaries and signing/upgrade remain required; no automatic exception |

Encryption, credentials/authentication/revocation, browser/session lifecycle, Tor, SOCKS/DNS/proxy/VPN, networking dependencies/configuration, storage formats/isolation, package/signing identity and platform/toolchain changes escalate scope. Unknown impact defaults to full. A patch number never authorises narrowing. Reviewed carry-forward is an explicit exception requiring exact previously tested production/test/build identities and per-case provenance; any changed protected input invalidates it.

## Three permanent lanes

- **Fast development:** compilation, JVM, static/lint and host security checks; no emulator or signing. A push which intentionally starts patch/full admission must not duplicate the fast build.
- **Patch acceptance:** reviewed scope manifest, exact named Android17 cases (no failures/errors/skips), required process isolation, debug41→42/restart, then original certificate/package/code/minimum verification, production build and original-key41→42/restart before acceptance upload. Candidate/source/APK hashes and executed/deferred/carried/failed categories are archived.
- **Full release/security:** existing complete suites and live Tor controls, including the unchanged natural stalled-CONNECT assertion. Required for major/high-risk/unknown-impact changes. Manual and monthly scheduled comprehensive checks preserve failures; scheduled workflow execution requires GitHub's default branch to contain this configuration. A release-branch-only schedule is not represented as active. Non-release periodic runs do not publish a signed candidate.

All lanes cache build dependencies and preserve artifacts. Admission is marker-triggered, source frozen and independently reviewed; do not rerun identical source to seek green. Fail compilation and focused gates before broader testing. No replacement production keys; no owner uninstall/data clear.

## 1.7.6.1 reviewed application

The initial impact review recognises MainActivity shared lifecycle/presentation and target37 changes relative to installed code41. It does NOT claim those boundaries are unchanged from1.7.6. The exception is based on the exact candidate already tested at52ef76cf5f69fd271a712424f7db6038b02d5d5d:435 individual PASS records, including current platform/shared changes, plus one unresolved FAILED natural-timeout classification. Raw full verdict remains435/436 FAILED.

Fresh scope:152 exact methods/29classes,354JVM, host/static security, browser-profile process restart, API37/software-decoder/SELinux receipts and BOTH debug/original-key upgrades. Unchanged camera, AI, backup, large-media and browser-home results may be carried per case. Fresh deterministic revocation/local SOCKS tests are not described as fresh live-Tor verification. Live bootstrap/revocation and successful natural-timeout observations are carried explicitly; the later natural-timeout failure remains FAILED, not carried as a pass.

Independent decision and exact input/test pins are in `../release1761/risk-based/approved-scope.json` and `../release1761/risk-based/SECURITY_REVIEW.md`. All further application, instrumentation, dependency, SDK or signing/upgrade input changes invalidate this specific exception and require renewed review/full verification. Policy implementation itself receives an actual-diff review before admission.
