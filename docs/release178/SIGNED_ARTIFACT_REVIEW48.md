# Independent signed code48 artifact review

Decision: APPROVED for the exact signed artifact identified below. No remaining Critical or Important finding identified. Owner physical acceptance remains REQUIRED and has not been performed. The canonical source review is unchanged.

## Artifact identity

Independently fetched GitHub API run 41156213785, attempt 1: success, push on release/1.7.8-private-apps, workflow .github/workflows/acceptance1762.yml, control 1613339778a8c29bafe699af87b40504152977b3. Acceptance178 job 124403493016 and all required stages succeeded, including frozen runtime review before signer access, immutable baseline/certificate/private-key checks, lintRelease/build, production identity/distribution/debug-service checks, in-place upgrade/restart, single-APK delivery and signing-material cleanup.

Artifact 13025351799 is private-gallery-1.7.8-code48-owner-acceptance. Independently downloaded to security-signed48.zip. Raw ZIP size 51,792,985 bytes and SHA256 dac16fa3c5ef0a5ba5fc8d0c8b6640c0e293e3c0407cf1379f3c9717942af416 match the independent API digest. Archive has unique member names and exactly one APK.

The APK is 105,503,318 bytes, SHA256 c9f43de796f0af15b95df54aabba686a11b4b6b6bb4eb08cf00169cccf7b7ec5. Independently executed the reviewed APK verifier on these actual bytes: the embedded v2 signer certificate/public key match; RSA PKCS1v1.5 SHA512 signed-data signature verifies; the APK content digest recomputed over all 103 chunks verifies. DER certificate SHA256 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17 matches the original production identity. Independent compiled binary-manifest parsing confirms package uk.co.traynor.privategallery, version 1.7.8/code48, minSdk 37/targetSdk 37, non-debuggable and CiAccessibilityService178 absent.

## Source, controls and runtime binding

Frozen application source 41b6d065a3fbef2f711198c9c778f7100e620c8f matches production source-sha.txt, release summary, candidate upgrade source and approved runtime source. Production release-control178-sha.txt matches 1613339778a8c29bafe699af87b40504152977b3. The same APK digest appears in the artifact release summary, production APK record and upgrade candidate record.

Archived source review SHA256 edbd535e02c83ec773968d9826b804b5d613a141f0e0f5e16e4a10ac7067e800 matches the independently issued report. Approval retains the exact 41 Android/33 JVM successful runtime source/artifact binding and 0 Critical/0 Important decision. Reviewed control hashes match artifact copies: upgrade fixture 33687d6f0e2665c0b5f783a5af6da5fbfe4ea7b1f84625a782f64b57c5eb70cc; scope validator dd572092bfee11e69c3ae5ac06b704f00eda6c9e4b0a4facbf2f72fd71b42b43; case manifest c7c9365eba76ab651c3403147bb16f1085596648335c26acc5bc903009e38472. Earlier failed lint run remains historical evidence; the mandatory lintRelease task and build succeeded in this original production retry.

## Actual original-key upgrade and restart

Baseline records identify the actual archived delivered code47 APK SHA256 21066bd7153520c688c5a6b6034fca549b33df391762e64a5070eecfeb342d3d, source 04b91ae01c64323aac02975672d6046dfba62e30, original certificate 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Candidate records identify the exact code48 APK above with the same certificate. All three independent fixture logs contain OK (1 test): original 47 seed, code48 verify, and code48 verify after force-stop/restart. Upgrade result records PASS.

Reviewed production runner installs the archived baseline, seeds it, installs the exact candidate using `adb install -r`, verifies, force-stops and verifies with restartCheck=true. Its production branch forbids an existing package and contains no uninstall/data-clear path; uninstall operations exist only in the separate nonproduction branch. API step success and actual fixture/identity records agree with that execution. These checks exercise existing media/trash/ciphertext, PINs, browser cookies/settings, camera/AI/privacy/presentation state and real owner-separated old-format private-app catalogues, plus candidate style command persistence and restart. No data-value or ciphertext check was relaxed for delivery.

The signed-upgrade condition in the source review is satisfied for this exact artifact. Physical-device usability, Fold behavior on the owner's hardware, and owner acceptance remain outside this emulator evidence and are explicitly OWNER_REQUIRED.
