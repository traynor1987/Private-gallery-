# Android result ownership — IR-E939-01 / IR-E939-02 / IR-E939-03

Implementation stage, not final security approval. The findings apply to e939f9e93c992ea0e1fdd21d458351546fa4c11c. These working changes still require Android compilation and execution, an exact-source independent disposition and final release verification.

## Corrected behavior

Camera/microphone and geolocation Android results now complete the immutable website request that caused the launch. A replacement website request cannot receive an earlier grant, lose its dialog or be cleared by an older cancellation. Actual requesting origins are displayed without paths or user information. Captured request resources are copied, and completion requires the original session, active tab, document revision, URL and owned WebView identity to remain current. Tab changes, navigation, background and disposal cancel exact owners. The existing resource whitelist remains in BrowserPermissionRequest.

Device document-selection results use the same immutable-owner mechanism. Single and multiple contracts have separate stable result channels but one shared device-selection reservation. Cancellation sends null once and releases callback references while retaining the in-flight marker until the actual result. A replacement chooser cannot launch over that marker, receive its URIs or be cleared by it. Mode, accept input, generation and callback are captured; current policy and document/view ownership are rechecked before forwarding. Multiple selection remains bounded to four, single selection to one. Vault-only and blocked policies cannot inherit a stale device result. Existing Vault query/preparation generation checks remain in place.

BrowserV2Session rejects requests from an inactive tab, absent owned view or stopped/blocked session before forwarding to UI. Rejected file requests are consumed with null, preventing a default picker fallback. A legitimate cross-origin frame in the actual active tab remains eligible for explicit origin-specific permission consent.

## Reservation lifetime and configuration changes

BrowserExternalResultGate is the actual generic production owner/once-result implementation. BrowserPermissionOwners retains its markers and stable random ActivityResultRegistry keys only in the Activity ViewModel. Managed registration reconnects a cancelled marker to its old result after UI remount or configuration recreation. It does not generate a replacement key that could leave an orphaned OS request or reassign a result. New process lifetimes get new keys; no browser authority or credentials are saved.

Cancellation clears result/rejection callbacks, including captured UI/WebView/native request references. It deliberately does not free a still-pending OS launch reservation. Only an actual matching result or synchronous launch failure releases that reservation. Device single/multiple results also match their immutable launch channel. Callback execution takes place outside the gate/owner monitors, including reentrant failure handling.

PrivacyPermissionContract still owns its existing Android privacy-window handoff tokens. No PIN storage, encryption, transport, signing material or system-owned permission dialog is changed. Two read-only session predicates expose the existing document revision and existing view identity without instantiating a renderer during result validation.

## Verification

Host production-helper checks PASS locally, including A-to-B replacement for three permission classes, retained cancelled markers, result/cancel once behavior, reentrant launch failure, callback reference release, single/multiple device channel ownership and 200 result/cancellation races. The executed compiler command uses `java -m jdk.compiler/com.sun.tools.javac.Main`; the executable main is `uk.co.traynor.privategallery.core.browser.v2.BrowserPermissionResultGateChecks`.

`BrowserPermissionOwnership1762Test` contains thirteen direct production UI/ActivityResultRegistry cases:

- cameraResultCannotGrantReplacementMicrophoneOrClearItsConsent
- locationResultKeepsExactOriginAndReplacementNeedsItsOwnConsent
- cancelledDeviceAUriCannotReachReplacementBOrEraseItsChoice
- cancelledSingleMarkerBlocksMultipleReplacementUntilActualResultAndCapsFour
- documentNavigationAndBackgroundRevokeCallbacksWhileAndroidMarkerStaysReserved
- stableRetainedKeysConsumeCancelledResultsAcrossUiAndRegistryRecreation
- inactiveTabRequestsAreConsumedWithoutPermissionOrFileFallbackUi
- actualActivityStopCancelsLocationOnceAndLateAndroidGrantCannotReviveIt
- deviceSelectionCapturesAcceptModeAndRechecksTightenedPolicyBeforeDelivery
- retainedCancelledReservationSurvivesActualActivityRecreationWithoutGrantingNewOwner
- privateDeviceResultIsRejectedWithExplanationWithoutReadingPrivateFile
- writeCapableAndFolderRequestsAreConsumedWithoutLaunchingAnyPicker
- cancelledDeviceValidationCannotDeliverLateReadableUri

These use controlled external results and synthetic files; they do not grant camera/location Android permissions or contact a website. Device-positive cases read synthetic files through an actual separately installed test-APK provider and explicit URI grants. They exercise the actual UI callbacks, registry lifecycle and production gate. The registry save/restore case is explicitly distinct from the actual Activity recreation/retained-marker case. None has been executed in this local environment, which lacks an Android SDK. Physical Samsung permission behavior is not claimed.


## Device URI confused-deputy correction (IR-E939-03)

DEVICE selection now launches an explicit exported system or updated-system ACTION_OPEN_DOCUMENT handler. It never falls back to a generic third-party GetContent receiver. The narrow manifest query supports handler discovery, including OEM system handlers. All website MIME alternatives and known extensions remain represented. Only MODE_OPEN/MODE_OPEN_MULTIPLE and API 37 PERMISSION_MODE_READ are supported; saving, folder selection and read/write authority are rejected with an explanation. No persistable grant is taken.

Before any device URI reaches WebView, the production validator requires content scheme, unambiguous authority, a resolved provider with a different UID from Private Gallery, an explicit read URI grant, matching MIME/extension, and a readable descriptor. Own-provider aliases, private file paths, ambiguous cross-user authorities and revoked grants fail closed. Selection validation is all-or-nothing; the trusted Vault picker retains its separate explicit confirmation and legitimate own-provider flow. The URI policy is DEVICE-only.

Validation uses two bounded workers, four queued operations, a five-second coroutine deadline, CancellationSignal where supported, and descriptor closure on cancellation. A non-cooperative provider cannot grow the worker pool. Cancel, replacement, navigation, background or policy tightening prevents late delivery. The UI shows cancellable verification progress and a clear rejection message. Ownership and policy are checked again after blocking work. There is no plaintext cache or persistent grant storage.

BrowserDeviceSelection1762Test adds three unexecuted Android cases: actual external-provider read-grant/descriptor positive followed by revocation negative; private/own-provider and mixed-selection negatives; and explicit system read-only intent with all MIME alternatives and mismatch rejection. The separate test provider uses only synthetic cache files, restricts callers to the target UID, grants read only, and is absent from the release APK. Tests do not establish Samsung DocumentsUI or MediaStore compatibility.

Host checks also execute the actual BrowserDeviceUriPolicy against private-file/data/http schemes, own-provider UID/aliases, ambiguous authority, missing provider and missing read grant, plus a normal external content-provider policy positive. These pass locally. Android source compilation and all sixteen Android cases remain pending CI; no physical Samsung acceptance is claimed.

Reference: Android WebChromeClient.FileChooserParams documentation, https://developer.android.com/reference/android/webkit/WebChromeClient.FileChooserParams (API 37 permission modes and explicit warning that returned chooser URIs are untrusted and WebView does not restrict their access).
