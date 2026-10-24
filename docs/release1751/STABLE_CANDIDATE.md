# 1.7.5.1 Android16+ stable acceptance candidate

The owner authorised minimum Android16(API36); the Fold runs Android17. The existing four acceptance repairs and all encryption/authentication/media code remain preserved. Android12 failures remain unresolved on an unsupported platform. This is a candidate for full release verification, not a signed APK or owner acceptance.

## Verified focused source

- Source: `d96da3bbeb5caeb4270f671b2b8bc22123208faa`, tree `a9cee83501a193e2e299984c3ea901e36fc80373`.
- App tree: `446ff0591d53e205eab14a1ef3b53deb16dbb5d3`; production main: `f6d126869d436274ef238a866990d3334f318af0`.
- Focused run17: `40134231802`; artifact `12576632293`; SHA256 `6a194fac0ca73669a9c10a0ad085de8efaabb84520b80d484201ca41624baad4`.
- Raw XML: JVM349/349, Android16/API36 47/47, Android17/API37 47/47; all zero failures/skips. Both platforms ran identical47 cases. Source/SDK/fingerprint/display metadata and archive CRC/SHA were independently verified.
- Machine evidence: [RUN_17_EVIDENCE.json](RUN_17_EVIDENCE.json). Run16 and runs12–15 remain historical evidence. Only the precisely diagnosed Android17 Espresso initialization issue is resolved by run17; Android12 is not relabelled passing.

The candidate commit adds only this documentation/evidence and the release marker. App/build sources, test assertions, scripts, workflows and Gradle configuration must remain byte-identical to the focused source. The full workflow records its own exact tested/signed commit SHA. Android17 compatibility evidence is from the identical tested app/configuration inputs at the focused SHA.

## Mandatory full acceptance gates

One `acceptance1751.yml` run must complete JVM/lint, all existing security/browser/camera/media/UI checks, fresh broad focused and unfiltered Android16 regression, synthetic debug38→39 upgrade/restart, original permanent signer challenge, production package/minimum/certificate/hash validation, and synthetic original-key production38→39 upgrade/restart. Success-only artifact upload remains mandatory. No skipped or failed required test can satisfy a gate.

Original installed38 source is `69bfade789df4a49d911091bb8cf8205c88a3a39`; candidate package `uk.co.traynor.privategallery`, version1.7.5.1/code39, minimum36. Original certificate SHA256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. Baseline build/minimum and encrypted data formats are preserved. No replacement key, owner-device uninstall, data clear or real media operation is authorised.

CI uses coherent checkpoints, one writer, reviewed test-only fixes, scoped dependency/build-cache reuse, fresh platform XML and actual-outcome checks. Recovery-only commits skip CI. The focused workflow and marker-triggered full workflow do not duplicate each other on the candidate push. No new feature programme or main merge.

Full results, signing and APK SHA remain pending. Actual Samsung Recents pixels, biometric hardware, both Fold screens, folding and the real in-place update require [owner physical acceptance](OWNER_ACCEPTANCE.md).
