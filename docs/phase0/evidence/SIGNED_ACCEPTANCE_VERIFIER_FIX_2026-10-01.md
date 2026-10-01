# Phase 0 signed acceptance verifier correction — 1 October 2026

Owner-dispatched [Signed Device Acceptance Build 36839887566](https://github.com/traynor1987/Private-gallery-/actions/runs/36839887566), job110296230084, used `phase0/security-admission` at reviewed `dd99426abc480b912bf93e8e894fb13538740048`. It failed after Gradle, not during compilation/signing/package construction.

## Actual failure and root cause

- Permanent signer restoration, source backup policy and all27 fixture hashes passed.
- `testDebugUnitTest lintDebug assembleDebugAndroidTest assembleRelease` completed **BUILD SUCCESSFUL in5m11s**,115 executed tasks. The actual release APK existed (30,388,844 bytes); retired-runtime/model inspection passed.
- Packaged policy validation then failed: `APK: packaged data_extraction_rules missing`. Signature report/hash/source identity/upload steps did not run; there is no downloadable signed acceptance artifact and no signature-continuity claim.
- The verifier assumed the original ZIP path `res/xml*/data_extraction_rules.xml`. Release AAPT2 optimizes file paths. A real SDK36 compile/link/optimize reproduction produced resource `xml/data_extraction_rules` ID0x7f010000 mapped to `res/4j.xml`; its manifest still referenced the correct resource ID. The original verifier rejected this complete, safe APK with the same message.

[Official AAPT2 documentation](https://developer.android.com/tools/aapt2) documents `optimize --shorten-resource-paths`. Source filename matching cannot verify an optimized APK's effective resource.

## Correction and executed regression evidence

Only the verifier and its tests change; app source, crypto, manifest, exclusion XML, signer requirements, release publication and owner data do not change. The verifier resolves the reviewed XML resource through the compiled resource table, checks every configuration's actual file, validates unique ZIP membership and decodes each binary XML tree. Missing/ambiguous/unresolvable/non-XML values fail closed. All existing nine-domain/two-mode exclusions and manifest checks remain required.

Tests use real SDK36 aapt2 compilation/linking/optimization, not mocked dump output. Four new tests cover safe shortened paths, unsafe shortened domain/manifest, and unsafe qualified XML overrides with and without optimization. Actual pre-fix RED:14 tests,3 behavioral failures,0 errors (after fixing local ZIP-extraction executable permissions). After correction: **14 Python tests PASS**,0 failures/errors/skips. The optimized synthetic APK also passes the CLI. Future reference vectors:5 PASS; source verifier/no-secret scan and git diff whitespace checks PASS.

The normal full Android push/PR CI executes the expanded14 tests plus existing normal/evidence JVM/lint/APK and139 emulator cases. Its terminal result must be inspected on the correction's exact SHA; previous dd99426 green runs do not admit a new SHA. Current results are linked in draftPR56. No signed workflow is dispatched automatically by this correction.

## Integration follow-up: native scroll completion

At verifier-fix `16db824b95f7420dfa451aca804d664dbc35f5b7`, [push36841365191](https://github.com/traynor1987/Private-gallery-/actions/runs/36841365191) passed all139 Android cases. [PR36841373328](https://github.com/traynor1987/Private-gallery-/actions/runs/36841373328) passed all build/policy gates but failed exactly one case: `BrowserPolishTest.realPageScrollHidesThenRevealsChromeWithoutRecreatingWebView`, line124, immediate toolbar-display assertion after native swipeDown.138 cases passed,0 skipped. The failed result is retained rather than ignored or relabeled green.

The fixture already awaited asynchronous Chromium scrolling after swipeUp but asserted synchronously after swipeDown. A test-only follow-up adds bounded5s waits for actual reverse page movement and the existing visible-toolbar assertion. Hide, reveal, same-WebView and same-parent assertions remain; no retries of the gesture, forced visibility, skips, production Browser change or longer global timeout are added. A genuinely missing reveal still times out/fails. The follow-up must pass complete CI on its own SHA before it is admitted; a timing explanation alone is not PASS.

## Next boundary and unchanged admission decision

After green CI on the correction SHA, the owner must manually start a **new** Signed Device Acceptance Build using `phase0/security-admission`. Rerunning the old failed run tests the old verifier/old SHA. Match run HEAD/build-sha.txt to the newly reviewed SHA; require packaged exclusions, signature/hash checks and artifact upload to pass. Verify permanent signer independently before any installation. Use PHYSICAL_ACCEPTANCE.md's clean test-device and independent backup/restore order.

This correction establishes optimized binary-policy inspection only. It does not resolve B02/API36.1 cross-platform extraction, OEM behavior, physical durability/biometrics/owner-safe upgrade, Browser provider/profile/process/residue measurements or independent future crypto review. No Phase1, production Hidden functionality, migration, merge or publication occurs.

PHASE 0 RESULT: NO-GO FOR PHASE 1
