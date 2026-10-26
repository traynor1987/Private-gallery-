# Android 17 regression repair implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox syntax for tracking.

**Goal:** Resolve the ten identified API37 failures, then retain all mandatory release/signing gates.

**Architecture:** Preserve production playback, cryptography and Tor. Compare one unchanged failing playback case on the default Goldfish path with the unchanged affected/adjacent suite on the platform software codec using an emulator feature switch. Correct only the native picker instrumentation's proven input-animation race using public animation-synchronised input injection.

**Tech Stack:** Kotlin/Compose/Media3, Android37.1 SDK, API37 Google ATD, Python evidence verification, GitHub Actions.

**Spec:** Owner's final Android17 regression repair instruction; preserved baseline docs/release1761/android17-regression-red/HANDOFF.md.

## Global Constraints

- Existing branch only; no force push/reset/new production signer/data clearing on owner device.
- min/target37, code42/name1.7.6.1 and package/original certificate unchanged.
- Preserve all historical failures; no timeout increases or assertion waivers.
- SELinux Enforcing throughout. No production codec override or plaintext fallback.
- Focused verification precedes full regression. All mandatory Tor/security/full/upgrade gates precede acceptance upload.

## Review Focus

- Default codec failure must retain raw XML and correlated permission/input errors, not be relabelled a pass.
- Software configuration must use actual platform codec and same APK/source/fixture; it does not prove physical hardware decoding.
- Picker test must inject real input after native animation synchronisation; preserve both FLAG_SECURE states.
- Date and time Save/Cancel must preserve original media hash and invoke adjustment only on confirmation.
- Playback/Panic must retain first-frame/progression, key revocation and surface removal assertions.

### Task 1: Source-bound focused evidence and correction

Files: new `.github/workflows/repair1761.yml`, `scripts/run_regression1761_focused.sh`, `scripts/verify_regression1761_focused.py`, host verifier tests; modify only `ui/MediaDetails174Test.kt`.

- [x] Recover GitHub56b3902e473412bba5c4729963ef81ee3b57c67f/tree0b368d4764ee6e80b7dfa84939840469aecb4b3e; verify parent tested719cec4be24d909c6dbae3aaa0b1842af6e6e925.
- [x] Digest/CRC-check original12692732398; inspect percase logcats. All9 failures show Goldfish→memfd denial→EPERM→queueCSD failure; picker tap explicitly dropped at opacity0.348877.
- [ ] Add strict host verifier tests (source/API/APK/enforcing/default negative/positive named cases); observe RED then implement verifier and observe GREEN.
- [ ] Use public UiAutomation.injectInputEvent(sync=true), which waits for WindowManager animations, for native dialog buttons. Retain real input, screenshot policy checks, add date/time Save/Cancel assertions in existing case.
- [ ] One marker-triggered workflow: compile/JVM/lint; default unchanged playback negative control; fresh AVD `-feature -HardwareDecoder`; all affected cases and related player/crypto/continuity/metadata cases. Same source/APK. Archive failures unconditionally. No signing/full workflow here.
- [ ] Independent actual-diff/security review before launch; correct important findings.
- [ ] Commit/verify remote once; run one focused workflow. Audit raw source-bound evidence independently.

### Task 2: Release admission, conditional on focused success

Files: existing acceptance1761 workflow and recovery/evidence docs.

- [ ] If focused controls prove correction, independently review the narrowly scoped emulator configuration update for all acceptance AVDs; no app source changes implied.
- [ ] Freeze source; single full admission retaining354JVM/101early/201broad/76security/436full, named4Tor objectives and original-key41→42/restart.
- [ ] Audit raw reports and signing/certificate/package/version/APK hash; deliver one APK only after all gates pass.
- [ ] If failure unexplained/repeated: stop NO-GO with exact evidence; no speculative rerun.
