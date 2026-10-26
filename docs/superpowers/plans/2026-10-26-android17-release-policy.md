# Android17 Personal Release Policy Implementation Plan

> **For agentic workers:** Execute natively in this session; independent whole-diff review before CI. Owner explicitly authorises implementation/release gates, so no extra plan-approval loop.

**Goal:** Complete existing1.7.6.1 with owner-approved Android17/API37 minimum and Android17-only release gates.
**Architecture:** Keep package, signer, keys, data and browsers unchanged. Change support/build policy and strict evidence verification, preserving historical API36 failures. One immutable acceptance workflow performs focused gates before full API37 regression and original-key upgrade/signing stages.
**Tech Stack:** Android/Kotlin/Gradle, Python evidence verifiers, GitHub Actions.
**Spec:** Owner's Android17 support-policy request in current conversation.

## Global Constraints
- Same release/1.7.6.1-editing-settings-polish branch; starting955df99ca69d8ab72f454dcca2c86605fcda308d.
- uk.co.traynor.privategallery;1.7.6.1/code42; original cert94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.
- API36 failure remains failed historical evidence, not fixed.
- No new production Tor changes; all API37 Tor checks mandatory. No physical acceptance claim from emulator.

## Review Focus
- Wrong SDK/emulator silently satisfying gates: require installed platform metadata API37 and device getprop SDK37/release17.
- Missing/substituted/skipped security cases: exact counts and named controls in focused/preflight/full evidence.
- Upgrade gate accidentally dropping original signer/data proof: retain synthetic original-key41→42/restart on API37.
- New native tests changing discovery count: early101/preflight76/full436, broad201/JVM354 preserved.
- Target/minimum and Android17 behavior: min/target37, retain compile37.1 and full UI/security coverage. Physical Fold API cannot be read without attached device.

### Task1: strict policy/evidence changes
- [x] Update tests first for minimum37, API37-only acceptance and exact mandatory case names/counts; observe expected RED.
- [x] Set min/target37, release floor37. Add SDK metadata/device identity verification.
- [x] Parameterize shared platform verifier with supported-platform tuples (existing callers defaultboth);1761 explicitlyAPI37only.
- [x] Remove API36 steps from1761 workflows only; preserve original-key gates and original historical branches/artifacts.
- [x] Add runtime failure class to early/security gates and named verifier; retain original live assertions and observer termination proof in early evidence.
- [x] Run host suite/shell/no-secret checks and independent actual-diff review.

### Task2: frozen acceptance
- [x] Commit reviewed source and one acceptance marker; independently verify remoteSHA/tree.
- [ ] SDK metadata/API37 emulator receipt; focused101 with live+runtime controls; broad201/security76/full436/JVM354; debug upgrade/restart.
- [ ] Only after prerequisite gates pass, original-key build/certificate/package/minSDK/checksum and original-key41→42/restart; preserve one acceptanceAPK.
- [x] On any API37 mandatory failure retainNO-GO with exact evidence; no unnecessary duplicate workflow.


Outcome:tested719cec4/run40405808061;JVM354/early101PASS,broad20110FAIL. Security/full/upgrades/signingunreached;NO-GO. See docs/release1761/android17-regression-red/HANDOFF.md.
