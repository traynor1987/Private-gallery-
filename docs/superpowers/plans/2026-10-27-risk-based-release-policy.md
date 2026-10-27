# Risk-based release implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox syntax.

**Goal:** Permanent fail-closed impact-based CI and independently approved1.7.6.1 patch acceptance/signing.
**Architecture:** Preserve existing full lane and all tests. Freeze independently reviewed152-case manifest and prior435PASS/1FAILED evidence; reject changed protected inputs. New patch runner emits exact source/APK/executed/deferred/carried records and requires both upgrade paths before upload.
**Tech Stack:** Python standard library, Bash, GitHub Actions, AndroidAPI37/SDK37.1, existing original signer.
**Spec:** docs/release-policy/POLICY.md and docs/release1761/risk-based/SECURITY_REVIEW.md.

## Global Constraints
- Existingreleasebranch only; no production/test changes, replacement keys, force push or owner-data operations.
- Unknown/new critical impact requires full; current exception pins exact52 app/test/build/upgrade inputs.
- Preserve436FAILED and natural-timeoutFAILED; no global disable/assertion changes.
-152fresh/354JVM/process isolation/debug+production upgrades mandatory; physical acceptance pending.

## Review Focus
- Missing/substituted/skipped/duplicate fresh methods fail.
- Stale app/test/SDK/upgrade input or altered policy scope fails.
- Carried evidence remains per-case; failed run never accepted as green.
- Signing and upload require all approved patch gates and original certificate.
- No duplicate automatic fast build on acceptance markers; periodic full remains configured honestly.

### Tasks
- [x] Recover4c080ffc/tree f855162f and exact baseline/full evidence; review complete production diff.
- [x] Obtain independent conditional criteria approval and freeze152 manifest.
- [x] Add negative host tests, then policy/evidence verifier; RED→GREEN and complete host suite.
- [x] Add fast and patch lanes; refine full manual/monthly non-release operation without weakening release gates.
- [x] Independently review actual diff and scope/provenance pins; freeze coherent candidate and verify GitHub.
- [x] Execute one patch admission; audit source/APK/native/process/upgrade evidence.
- [x] Original-key sign/upgrade only after approved gates; audit certificate/package/hash and deliver one acceptance APK if green.
- [ ] Otherwise preserve exact independently reviewed NO-GO blocker; no speculative full run.

Outcome: ONEpatch40433064873 passed152Android/354JVM/process/debug+original-keyupgrade/restart; originalcert/package/APKhash verified; independent GOforowneracceptance. Physicalacceptancepending. Laterhost-onlyguard160PASS is distinct from Android-tested33 source.
