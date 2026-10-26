# Private Gallery1.7.6.1 — Android17-only regression handoff

**NO-GO for signing/owner installation.** Owner-approved Android17/API37 minimum and CI policy are implemented. All four supported-platform focused Tor checks pass on the tested candidate. Release is now blocked by10Android17 broad UI/player failures; no signed acceptanceAPK exists. Stop furtherCI per owner's instruction.

## Verified source and scope

Starting owner checkpoint955df99ca69d8ab72f454dcca2c86605fcda308d/treef1a575b6bf534103ca7a3a9fbd54c9e4167d3632. Same release/1.7.6.1-editing-settings-polish branch throughout; no new release branch/forcepush/reset/mainmerge/1.7.7/1.8. Final tested source `719cec4be24d909c6dbae3aaa0b1842af6e6e925`, tree `76aa3dcd82bea27b801bcc915a2f97cba0db75d7`. Subsequent handoff commit is documentation/evidence only; preserve tested source identity separately from branch HEAD.

App minSdk37/targetSdk37, existingcompile37.1, version1.7.6.1/code42 and uk.co.traynor.privategallery preserved. No productionTor/browser/media implementation, encryption format, credentials or owner media changed. HistoricalAPI36failure evidence remains unchanged and failed, not fixed. DroppingAPI36support is explicit ownerpolicy, not a corrected failure.

Installed runnerSDK metadata independently verified: AndroidVersion.ApiLevel37.1→major37/minor1, Platform.Version17, nonpreview, rawmetadataSHA25695f5afa020739d89c1fb08c7f8ecfb5da55e81f5c6834bbedb306bf7153b19b4. EmulatorgetpropSDK37/release17 and fingerprint recorded. FinaldebugAPK badging confirms minSDK37/target37. Owner's physicalFoldAndroid17 remains user-reported; no physicaldevice attached/read or physicalacceptance claimed.

## Immutable acceptance run and audit

[40405808061](https://github.com/traynor1987/Private-gallery-/actions/runs/40405808061), job121863128320, FAILED at FullAndroid17regression/debugupgrade stage's first broad201suite. Artifact12692732398,62582735bytes, SHA256 `a1cae7135ee46598578903bb3e7dd678e5085f660391da30e4c3142569c1a087`; independently downloaded/digest/allZIPCRCsverified. Source, SDK, release, named tests, observertermination and testeddebugAPKhash cross-checked. Raw focused/broad nativeXML, filteredsecurityobservations, installedSDK/APK/device receipts, exactfailuremessages and JVMreportdigest inventory are preserved here; AUDIT.json records counts.

| Gate | Verified outcome |
| --- | --- |
| SDK37.1/API37/deviceAndroid17/minimum37 | PASS |
| Host suite |108Python plus exact10CIhostphases PASS;3existingmocklogResourceWarnings retained |
| Compilation/lint/debugassembly | PASS |
| JVM |354pass/0failure/error/skip |
| Earlyfocused |101pass/0failure/error/skip |
| BroadUI |191pass/10failure/0error/skip outof201 |
| Security76 | NOTREACHED |
| Unfiltered436 | NOTREACHED |
| Debug41→42/restart | NOTREACHED |
| Original-key signing/productionupgrade/restart | NOTREACHED |
| Ownerphysicalacceptance | NOTPERFORMED |

Two earlier cause-proven pre-runtime aborts remain separate FAILED records:40404349275 (newSDKreceiptparser incorrectly expected37 instead of official37.1),40405139204 (isolatedstoragefixture inheritedreleaseenv/newhelperdependency). Both corrected through hostRED→GREEN, actualdiffreview and exactrelease-env hostreplay before this run. They executed noAndroidruntime/securitycase and were not unchanged-source retries. See android17-sdk-red and android17-host-red receipts.

## Four mandatory focused Android17 Tor objectives — final candidate PASS

-Realbootstrap, exactupstream200/81byte onionreadiness2795ms, freshonionnavigation881ms (also81bytes), existingHTTPSchecks; controlledfixtureprovisioning/healthgates retained.
-NaturalstalledSOCKSCONNECT: TRANSPORT_TIMEOUT, disconnectedtrue at30067ms; peerClosedtrue/reuseDeniedtrue/extraConnectionfalse/postconditionfailures0; actualruntime/tabs/Torcleanup assertions passed.
-Sessionrevocationduringstall: CLOSED/disconnectedtrue438ms; peerClosedtrue/reuseDeniedtrue/extraConnectionfalse/postconditionfailures0.
-Privatecontrolobserversubscribed and readerAlivefalse. Source/API37/exact101/namedcontrols/termination verifier ran BEFOREfull regression.

Failclosed/SOCKS-only/DNS/noalternatefallback assertions were retained; no Torsecurity waiver. These focused results are not the unrun76security/436full suites or packet-levelphysicalDNSproof. Historicalonioncausation remains unresolved; do not claim itfixed merely because currentpositive/negative cases passed.

## Exact ten Android17 broad blockers

All rawexceptions/stacks are in failures.json and broad-native-0.xml. Eight PrivateVideoPlayer cases timed out waiting for realplayback/ready/interactive progress:

1. PrivateVideoPlayerTest.pauseResumeWithoutStopExposesRecoveryForStoppedNetworkPlayer —15000ms.
2. PrivateVideoPlayerTest.realEncryptedPayloadPlaysThroughProtectedViewer —15000ms.
3. PrivateVideoPlayerTest.protectedPageReuseLoadsFreshBytesAndPlaysAgain —15000ms.
4. PrivateVideoPlayerTest.galleryContentUriPlaybackAndCleanup —15000ms.
5. PrivateVideoPlayerTest.changingLifecycleOwnerDoesNotReleaseRetainedPlayer —15000ms.
6. PrivateVideoPlayerTest.speedSelectionAndInMemoryResumeUseActualPlayer —15000ms.
7. PrivateVideoPlayerTest.protectedMemoryPlaybackAndCleanup —15000ms.
8. PrivateVideoPlayerTest.surfaceDoubleTapsSeekActualMemoryPlayerAtBothEdges —10000ms.
9. PrivacyShield175Test.panicDuringRealPlayerPlaybackStopsImmediatelyAndRevokesSession —realplayer mustprepare/advance BEFOREPanic; observedfocusLosses0/focustrue/coverfalse/epoch0/lifecycleRESUMED/state1/error4003. Events show READ_STARTED,AUTHENTICATED,PLAYER_PREPARING,state2,PLAY_WHEN_READY1,VIDEO_TRACK_SUPPORTED1,state3,PLAYER_ERROR4003,state1. Panicbehavior itself was not reached; do not misclassify as a provenPanicExit defect.
10. MediaDetails174Test.nativeDateAndTimePickersFollowOwnerScreenshotPolicy —EspressoNoMatchingViewException forandroid.widget.TimePicker; capturedhierarchy still containsDatePicker/yearpicker andSECUREdialog flag. Do not label this as a proven screenshot-security leak.

No production/infrastructure/test-harness rootcause for these failures is established. The supportchange also raisedtargetSDK37; causation has NOTbeen proven by this correlation. Do not blame Tor, silently reverttarget, increasewaits or weakenplayer/dialog assertions to obtain green.

## Independent review and release decision

Read-only reviewer independently reconciled source/SDK and rawXML: focused101allpass; broad201/10fail with eightplayer timeouts, onePanicplaybackprecondition4003, oneTimePickermissing. Confirmedtarget/min37causationunproved, remaininggatesunreached. FinaldecisionNO-GO; stopfurtherCI, preservefailedsource/evidence.

Original productioncertificate expectedfrombaseline: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. Candidateproductioncertificate verification and signedAPK SHA256 are unavailable because productionbuild/signing were neverreached. DebugAPKhash in AUDIT.json is not an owneracceptance checksum. No replacementproductionsigner generated. No owneruninstall/data-clear/credential/key/mediaoperation performed.

Next authorised work must first diagnose these Android17video/decoder/readiness and nativeDatePicker→TimePicker failures from preserved artifacts, then choose a cause-specific correction and requiredreview. No newrun or unrelatedfeatures queued. All completedSamsungexternalediting/Panicsettings/HideContent presentation work remains preserved; physicalSamsung/ObjectEraser/Foldacceptance stillpending.
