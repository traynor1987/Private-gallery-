# NovaOS 1.7.11 Home 2.0 independent source and signing review

Source: 0e7f7c07608a6b439a4471e613531a758028c849
Decision: APPROVED
Production signing: APPROVED
Upgrade fixture SHA-256: 6b665eacb36a0e5721db66c621332b2a5f3f2b49f15cd3138c539313696a778d
Upgrade controls SHA-256: 81a0dfb220376809960e3cca6b61f913eee3999766f915445f9779fd2d5f25ea
Runtime matrix SHA-256: 6544f450036015ce3359edd8e06c9a253c883daf01da7bb518bc1e086b35a368
Runtime archive SHA-256: 7329a7a3dee3589ded9cf7b00aadf2bc90589482e54c72b2d58c814e571b6cf9
Signing workflow SHA-256: 8290adbdad12f35d2c022cf6308342d93f1ca6068550852c1aae04fff9984245

Review date: 2026-11-06 UTC. Reviewer: independent integration/security review agent.
Frozen Git tree: `eb0dc6d332584b5955d57f42f98ce1b10d53fce7`.

This approves use of the existing permanent production key to build the exact source above
as package `uk.co.traynor.privategallery`, version `1.7.11`, code `56`, through the reviewed
`home2-production` workflow only. It approves progressing to the fresh enforcing API37
original-key code54-to-code56 upgrade experiment. Publication remains conditional on every
reviewed identity, signature, compiled-fixture, upgrade and restart gate passing. This review
does not assert that production signing or that upgrade has already occurred.

## Original evidence independently checked

The authenticated GitHub run metadata identifies push run `42141848244` on
`integration/novaos-1.7-home2`, from the exact source above, with successful conclusion and
workflow `.github/workflows/fast-development.yml`. The reviewer independently re-fetched
jobs and artifacts through the connected GitHub API. Job `127643806118`, `home2-integration`,
completed successfully, including all five required build/JVM, host/encrypted-adapter,
browser-preservation/isolated-identity, API37-runtime and original-report-validation steps.

Original artifact `13467919132`, `novaos-home2-integration-verification`, is unexpired and
bound to that run and source. Its authenticated artifact digest matches the independently
computed complete ZIP digest above. ZIP integrity and unique member names were checked.
The source receipt matches the frozen source. The actual original Android XML contains
exactly the reviewed 57 cases across 16 classes, each once, with zero failures, errors or
skips. The checked-in matrix bytes match the source-generated matrix and the pinned digest.
The archive also contains 399 JVM test cases with zero failures, errors or skips.

The archived build identity is the separate development installation
`uk.co.traynor.privategallery.hiddenprototype`, `1.7.11-TEST-ONLY`, code56, minimum/target
API37, label `NovaOS TEST ONLY`, signed with an Android Debug certificate. It is runtime
evidence for the integration; it is not an original-key production upgrade artifact.

The selected passing runtime evidence includes:

- Real MainActivity/Hidden Home Quick Panel and popup three-finger input through system
  injection, synchronous drawer concealment, shared callback deduplication/restoration,
  both Space authorities revoked, neutral lock and fresh Primary PIN entry. Popup gesture
  tails do not invoke the child action.
- Real encrypted Private Apps Store installation, ciphertext checks, Add to Home, exact UUID
  launch, My Apps and uninstall. The final selector corrections preserve all assertions.
- User-gesture-started HTML audio retained in the same attached WebView across Home, app
  and standalone Browser switches, then paused on background; native Mind MediaSession
  controls cannot restart playback after pause or authority retirement.
- Inactive/stale native focus, popup, permission, geolocation and file callbacks are denied;
  replacing a renderer does not transfer a pending interaction to its successor. Mixed
  unknown website resources cannot expand camera/microphone grants.
- Owner-scoped encrypted Home state, retired capabilities/editors, authenticated corruption
  handling, wallpaper, Hidden credentials, independent entry/lock authority, encrypted
  evidence persistence, camera capture and Panic capture recovery.

## Source assessment

No unresolved Critical or Important findings remain in the reviewed integration scope.
Production application sources are unchanged from reviewed source
`23ee083987c39f60adbba827766f4d7c3f5e1612`; later commits add delivery controls and test-selector
corrections. The frozen working application, controls, workflow, fixture and matrix were
checked against HEAD without differences before this review document was written.

The pinned V1-preservation verifier passes against original production source
`9efd19540a1d75efa1b30f4e23c9cbb096f93aff`. Existing constructor/settings, security policies,
crypto/vault/browser stores and default browser tab operations remain preserved. Additive
Private Apps lifecycle entry points share the existing bounded renderer registry; native
callback liveness/identity guards are deliberately tightened. No replacement browser engine,
provider bypass, autoplay bypass, Preview credential/cookie import or media-schema rewrite
is introduced. Provider-managed browser profile storage remains an existing platform-managed
boundary; this review does not claim that it has become vault-encrypted.

Home uses captured Primary/Hidden authority and owner-authenticated encrypted persistence.
Hidden Home web/VPN/Tor and remote-analysis networking remain unavailable in this candidate.
Nova Chat Lite/Favourites integration and Home backup remain disabled as displayed by the UI.
The existing Primary browser and Private Apps paths remain the supported web entry points.

## Signing and delivery controls

The eight-file canonical controls digest, fixture and complete signing-workflow hash above
are approved together. Ten independent host regressions passed: eight upgrade/provenance
control checks and two tests executing the actual workflow staging body. Required order is:

1. Re-fetch original GitHub run/jobs/artifact evidence, verify exact hashes, full runtime
   matrix and explicit review approval before any signing environment is introduced.
2. Preserve reviewed controls; check out the exact frozen source. Verify the pinned archived
   original code54 APK rather than rebuilding an application baseline. Compile the reviewed
   fixture against the actual original source with only the two reviewed package transforms,
   before exposing signer credentials; retain and revalidate its compilation receipt.
3. Verify availability of the existing private key and original certificate, then build/sign
   the frozen candidate and a disposable copy of the already-compiled instrumentation.
   Verify unchanged instrumentation payload and original certificate separately.
4. On a fresh disposable enforcing API37 emulator with production package initially absent,
   install the exact original54 APK, seed synthetic data, install56 with `adb install -r`,
   verify, force-stop and verify again. Never uninstall or clear seeded application data.
5. Publish only after all those gates succeed. Deliver the single production APK separately;
   evidence contains receipts, hashes, manifests, reports and reviewed controls, with no
   instrumentation APK, nested archive or signing material. Failure uploads remain selected
   diagnostics. Signing material is removed after signing and on every workflow outcome.

The required original certificate SHA-256 is
`94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.
The original54 application SHA-256 is
`3e9497d87c074d6d49bfd42a2b44cc5f2cd46ec0b7476d35d030e75b9cd35ce2`.
Changing the frozen source, fixture, controls, matrix, runtime archive or signing workflow
invalidates this approval for the changed inputs and requires renewed review.

## Acceptance limits

Physical Galaxy Fold testing and live WhatsApp/website accounts were NOT PERFORMED by this
review or the synthetic CI matrix and remain owner acceptance work. No claim is made that
the selected matrix exhausts every website, device posture, browser feature or external
provider behavior. The original-key production upgrade and restart are still PENDING and
must pass before publication. Existing production installations must be upgraded in place;
the owner must not uninstall NovaOS or clear its data.
