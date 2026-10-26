# Private Gallery1.7.6.1 — owner-approved Android17 personal support policy

Owner James explicitly approved on26October2026: sole intended user, physical SamsungGalaxyZFold reported runningAndroid17; Android16support is no longer required. Minimum Android17/API37. This changes supported scope, not historical test verdicts.

Starting955df99ca69d8ab72f454dcca2c86605fcda308d/treef1a575b6bf534103ca7a3a9fbd54c9e4167d3632. Same release/1.7.6.1-editing-settings-polish branch. Exact installedbaseline1.7.6/code41; candidate1.7.6.1/code42. Packageuk.co.traynor.privategallery; originalcert94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. No encryptedformats/keys/credentials/vaultmedia/browsers changed.

## SDK/platform verification

Google Android17 SDK docs and uses-sdk mapping identifyAndroid17/API37:
https://developer.android.com/about/versions/17/setup-sdk
https://developer.android.com/guide/topics/manifest/uses-sdk-element

App minimum/target37, existing compile37minor1 retained. Target37 keeps target at the declared minimum; Android17 target behavior must pass complete regression. Buildtools36.0.0 remain existing signing/packaging tooling, not the declared runtimeAPI. CI verifies actual installed platforms/android-37.1/source.properties majorAPI37/nonpreview and SHA256 of metadata. Each emulator-stage checks getprop SDK37/release17 plus fingerprint, rather than relying on its label. APK metadata checks minimum37 and originalpackage/code/signature. There is no physically attached Fold/adb locally: owner-providedAndroid17 is not independently read from physicaldevice. Physical API/installation and Samsung/Fold behavior remain owneracceptance items; do not claim emulator receipts prove physicalacceptance.

## Historical evidence

All API36 failed/successful receipts remain intact, especially final-bounded/runtime/api36: live/revocationpassed but naturalfailurecategoryassertionfailed beforepeer/reuse postconditions. This is NOT fixed, reclassifiedgreen or erased. It is outside newly authorised supportscope. Historicalonioncause also remains unknown; no new productionTorcorrection is claimed.

## Mandatory Android17 gates

One immutable admission marker after host verification and independent actual-diff review. No separate speculative diagnostic. Source/SDK/APK-bound gates:
-354JVM;101early (prior99+two runtime controls),201broadUI,76security (prior74+two controls),436unfiltered (prior434+twocontrols),0fail/error/skip.
-RealTorbootstrap, exact upstream controlledonionreadiness and freshnavigation, with existingbudgets and independentfixturechecks.
-NaturalstalledSOCKSCONNECTtimeout cleanup and sessionrevocation: actualruntime/WebView callbacks, peerclosure, emptytabs, rejectedreuse/noextrasocket; originalassertions retained. Fixedcategory/postcondition evidence correction remains test-only.
-Subscribed privateTorcontrol observer must terminate; earlyverifier checks namedcontrols/count/source/API/release/observer before fullsuite. SuccessfulAPI37historicalcontrols do not substitute for finalsource checks.
-Failclosed SOCKS/DNS/noalternatefallback, vault/browser isolation, authentication, revocation, editing/Panic/HideContent, camera/video, AI/Combine, transfer/media preservation all retained.
-Debug41→42/restart and original-key production41→42/restart with synthetic independentPINs/encryptedmedia/settings/VPN retained. No ownerdata used or cleared.

Only supportedplatformAPI37 is required by1761-specific workflows/evidenceverifier; sharedolderreleaseverifier defaults remain bothSDKs. Signing only after mandatorycandidatechecks; upload only after productionupgrade/restart and cert/package/checksum checks. AnyAPI37mandatoryfailure retainsNO-GO. No duplicateworkflow for unchangedsource. Physical owneracceptance remains pending even after automatedGO.

## Independent review

Whole-diff read-only reviewer approved one frozen acceptance run after fixing pre-full sequencing: earlynamedcontrols and observertermination now verified before fullregression. No productionTor changes; originalsigner/package/upgradegates preserved. Host105Python/53policy/13readiness/20SOCKS/16redaction checks planned/fresh results recorded in recovery. SDK/device runtime checks await CI; no success claims before receipts.
