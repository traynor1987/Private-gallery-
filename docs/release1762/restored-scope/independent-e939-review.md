# Independent whole-implementation source review — e939

Reviewed source: `e939f9e93c992ea0e1fdd21d458351546fa4c11c` on `release/1.7.6.2-ui-browser-polish`, compared with the original recovery source `5b6be83154b70d1add7420d34f89398f705c00c8`.

**Disposition: NOT APPROVED FOR SIGNING.** No confirmed Critical source finding. Three confirmed Important website/device-result boundary findings are recorded below; subsequent source-remediation notes are explicitly separate from this frozen review. Both production activation constants remain false. This source review is not Android execution, a routing penetration test, physical Fold/Samsung acceptance, certificate verification of a newly built APK, or final scope acceptance.

## Important finding IR-E939-01 — a delayed Android result can grant a replacement website request

Locations in the reviewed source: `ui/BrowserV2Home.kt:340`–361, 444–453 and 904–922; supporting `ui/PrivacyPermissionContract.kt` and `core/browser/v2/BrowserPermissionRequest.kt`.

The camera/microphone Activity Result callback obtains `ui.pendingPermission` when Android returns, instead of the immutable website request which the user approved before launching Android. The location result callback similarly reads the current `(origin, callback)` pair. The listeners explicitly allow a new website request B to replace A while denying A. `PrivacyPermissionContract` binds only the app's privacy-window token; it does not carry website request identity. The wrapper restricts camera/microphone resources and completes once, but B is an uncompleted, distinct wrapper and can still receive the grant.

A valid adverse ordering is:

1. Website request A is displayed; the owner selects Allow this time, launching Android permission flow A.
2. Another website/frame request B arrives before Android's result. The listener denies A and stores B as the current pending request.
3. Android grants A's requested application permission. The callback reads B, grants B without B receiving its own explicit app confirmation, then clears B's pending UI. Location has the same ownership substitution problem.

This is a concrete missing identity fence in the source, not an observed device exploit. It existed before the modal extraction, but remains in an active affected permission path and must be corrected before release approval.

Bind each launch to an immutable request/origin callback capability and keep a separate in-flight reservation until that launch completes. A returned result must grant or deny only that exact owner while it remains current/eligible; it must never grant or clear a replacement request. Replacement, cancellation, navigation, background and launch failure must invalidate the corresponding owner without reassigning a late result. Immediate-already-granted paths should likewise check the captured request is still the current UI owner before clearing mutable pending state.

Required regression: force A→B between app confirmation and Android result for camera/microphone and location. Returning A must not grant B or clear B's new confirmation. Also cover A cancellation, navigation/background retirement, launcher failure and a later independently approved B. This need not grant real camera/location access merely to test ownership; the deterministic regression must exercise the actual production completion/launch ownership helper or callback wiring.

## Reviewed routing and process boundaries

- The native child binds to an exact app-owned VPN `Network` before non-startup WebView feature/profile initialization. It requires dedicated data/cache roots, suffix, profile and proxy APIs rather than falling back to Primary's provider directories. Process admission rejects restoring/rebinding a previously used child process.
- VPN HTTP/CONNECT traffic uses a session-authenticated loopback proxy whose sockets and DNS use the captured Network. Request bodies are streamed transparently. Proxy credentials are removed before cleartext origin forwarding, retries/direct alternatives are absent, and TLS errors cancel in the WebView.
- Tor's local service owns an application-UID IPv4/IPv6 default-route and DNS sink which forwards no TUN packet. Only authenticated numeric public-destination relay TCP can obtain `protect(Socket)`, after local descriptor creation and before remote connect. Origin names travel through private Unix SOCKS DOMAIN requests, not Android origin DNS. No app-level allowBypass or direct-origin fallback was found.
- Independently fetched upstream `guardianproject/tor-android` TorService.java at `0340ba1609cb73e663e202a316d083803e399db4`. Its private numeric port parser dispatches virtually through `getInfo`; the narrow isolated-mode adapter avoids that parser's Unix-path exception while UNIX_READY uses raw superclass metadata and checks real Unix-only listeners/circuit readiness. It does not fabricate circuit state or alter the native binaries.
- Foreground startup now reserves/releases ownership within a failure cleanup path. Tor connection configuration is delivered before superclass native initialization. Bound-only, nonexported services and owner-Binder death do not establish a presently reachable unauthenticated restart/direct-network path in source.
- Mode admission waits for preceding browser/Tor processes and sink retirement. Old child process binding is never reset to default during exit. Sink teardown closes protected relay traffic before releasing the blocking route and treats unknown process inventory as unknown.

No additional confirmed Critical/Important source bypass was found in these inspected paths. Their Android/Chromium/kernel behavior remains an execution requirement, not established by the source statements.

## Revocation, authentication and storage assessment

The formerly reported non-proxied VPN revocation gap is source-resolved in this checkpoint. HiddenSession's authority-bound one-shot signal runs before revocation returns. Its broker callback only marks atomic state, signals the known live claimed process and queues preallocated cleanup; it does not acquire the broker/session monitor in reverse order. A final CLAIM fence handles publication racing with revocation. Owner-close/background/revoked authority hard-terminate; explicit still-current Done alone uses a bounded clearing grace, retaining its revocation signal until actual death so a later lock wins. This is immediate termination signaling plus a required prompt-death runtime assertion, not a claim that already in-flight physical packets can be recalled.

Primary and Hidden authentication epochs and original stopped-at timestamps fence delayed presentation events. Pending RETURN survives timer cancellation and is evaluated before foreground restoration. New authentication cannot inherit old upload/transfer/browser authority. No encryption algorithm, PIN storage format, production certificate or vault key derivation change was found in the reviewed delta. Cross-vault destination copy/move operations remain caller-owned and require existing independent credential/access classes.

Dedicated WebView roots have durable markers and parent/child kernel lock ownership. Deletion does not follow nested symlinks, persists data/cache removal before retiring the marker, and does not infer Primary's provider directory names. Cleanup scheduling must return success before admission. Ordinary process death can be handled offline by the persisted job; Android force-stop suspending jobs until restart is a real documented limitation. Abrupt revocation correctly relies on owned-root removal rather than falsely requiring an asynchronous clear acknowledgement.

The paged picker replaces the silent 500-item truncation with immutable, bounded metadata pages. Query generation/random identity, session and membership fences protect previews; fresh restrictions remain checked before issuing a grant and again during provider use. The provider is nonexported, same-UID and opaque-capability based. Decryption uses a reliable pipe with current authority checks and no new public plaintext file. Native upload selection and egress confirmation remain distinct. The encrypted library keeps per-mode authenticated records, wrong-key failure does not overwrite existing records, and all operations use the captured authority. Saved navigation does not automatically restore cookies, tabs or sessions.

## Modal and Samsung editor changes

Reviewed the production call-site delta and extracted modal callbacks. The new components receive explicit decisions rather than performing storage/network operations themselves. Save/replace/export and website resource/external-app confirmations remain owner-owned; no new implicit egress was found in those extractions. The active Primary permission path has the specific ownership defect above despite unchanged resource filtering. Layout tests of permission surfaces alone cannot prove that asynchronous ownership.

Editor discovery queries EDIT-compatible exported activities, excludes the app itself and unrelated VIEW/SEND receivers, checks declared permissions, deduplicates explicit components and prioritises Samsung Gallery. Main revalidates selection before launch. Handoff/export/import work retains originating-vault and immutable request authority checks, existing egress restrictions, verified encrypted imports, default Save as New and separate Replace confirmation. App-owned leases and public-copy cleanup remain distinguished; no claim of erasing Samsung/cloud caches or guaranteed Samsung callbacks is made. Actual OEM editing/AI/save/return behavior remains physical acceptance.

## Checks independently executed in this review

| Check | Result |
|---|---|
| Exact local HEAD | Matched e939 above |
| HiddenRevocationSignalChecks, compiled against reviewed production HiddenSession | PASS: synchronous boundary, stale epoch, race/failure and lock-inversion stress |
| `test_final_scope1762.py` | PASS: 5 tests |
| `check_hidden_browser.sh` | PASS: 53 policy checks and >500/1001-item snapshot paging checks |
| Exact upstream TorService parser/lifecycle source | Independently retrieved and inspected |
| Android instrumentation / real packet capture / signed upgrade | Not executed or claimed by this reviewer |

No unrelated camera/AI/large-media suite was rerun for this source review.

## Required disposition before approval

Resolve IR-E939-01 and execute its affected ownership regressions. Require exact-source Android17 results for all mandatory selected methods, actual VPN/Tor body/DNS/non-proxy observations, continuous UDP across Hidden-only lock with WireGuard retained, network/process loss, profile/offline cleanup, main lifecycle and repeated mode switching, transfers and external editor cleanup. Explicitly distinguish routed UDP, blocked behavior and missing observation. Passing compilation or a host model cannot substitute for those results.

Then enable only the verified production modes, execute the real Main entry/lifecycle/mode-switch suites without bypassing activation, freeze the exact final source and perform another final independent review against its results. The signing workflow is currently intentionally blocked: frozen source is historical code43, restored-scope approval files are absent, and both mode flags are false. Before key access, exact-source/full-scope approval, zero open Critical/Important findings, original package/certificate identity and approved code44 must be verified. Original-key41/42/43→44 in-place encrypted media/credential/settings/restart evidence and final APK digest/signature are still mandatory. Physical Fold/Samsung acceptance remains owner-required.

## Important finding IR-E939-02 — device-picker result can send A's selected files to B

Locations in the reviewed source: `ui/BrowserV2Home.kt:293`–317 and its onShowFileChooser listener; `core/browser/v2/SecureWebViewFactory.kt:220`–230.

Both GetContent and GetMultipleContents completion handlers call the current mutable `ui.pendingFileResult` and then clear it. launchDevicePicker captures neither that callback nor an immutable chooser generation. A second file chooser B cancels A and replaces the pending callback. If Android's result for the already launched picker A arrives later, the current B callback receives A's selected content URIs even though B never received its own selection/egress confirmation. This is a website file-egress ownership boundary, not just an inaccurate status label.

The SecureWebViewFactory wrapper is once-only per callback; it does not bind Android launch A to callback A. Cancelling A completes A with null, while B remains fresh and can accept the wrongly routed URIs. The vault-picker generation protects the separate encrypted-vault query/preparation path, not these device-picker launchers. No device exploit was executed in this review; the source admits the concrete A→B ordering.

Use immutable per-launch single/multiple picker ownership with a retained Activity Result registry reservation. Invalidation may clear references and reject the old callback, but must retain the OS launch marker until its actual result is consumed so it cannot be reassigned to B across recomposition/configuration. Deliver only to the exact still-current chooser owner, with tab/origin/presentation/authentication/policy fences and existing accept/mode/max-four restrictions preserved. A late result must neither invoke B nor clear B's new confirmation state. Add deterministic actual-owner-helper/callback regressions for A→B, cancel, navigation/background, rotation/configuration, launch failure, single versus multiple launcher separation and a later explicitly approved B.

## Read-only screenshot preservation assessment

Baseline `5b6be831` MainActivity1907–1919 selected the independent Hidden screenshot preference and cleared FLAG_SECURE only while resumed, uncovered, owning focus and outside a permission-window handoff; Recents screenshots remained disabled. The reviewed Main2029–2040 retains that policy. The setting at Main3943–3951 says it allows screenshots while using Private Gallery and declares no browser exception. HiddenPreferences defaults the opt-in to false.

The native Hidden activity at183 instead sets FLAG_SECURE unconditionally, has no screenshot preference/Recents API handling, and only terminates onStop. That is stricter and does not establish a current content leak, but silently ignores the completed Hidden opt-in for both native modes. Full feature-preservation claims should not hide this policy mismatch.

Prefer preserving the authenticated Hidden opt-in with its false default. Any implementation must remain secure throughout startup and permission/focus/pause transitions: always disable Recents snapshots, protect and cover Activity plus owned secondary windows/accessibility before backgrounding, and restore capture only for live authenticated foreground content with owned focus/frame admission. The existing PrivacyWindowTracker accepts Activity and can cover Compose secondary windows; blindly treating an owned Dialog or IME focus handoff as another app would break normal flows. Retrieve the policy through the authorised parent/broker's Hidden-specific policy, not Primary's generic screenshot selection or an untrusted launch extra. Cross-process SharedPreferences apply timing must not let an old enabled value defeat a newly disabled setting.

If always-secure native browsing is an intentional exception, it must be explicitly described and accepted as that exception rather than represented as preserved app-wide behavior. Required checks include default off, Hidden opt-in, Primary opt-in not enabling Hidden, visible dialog/IME, Recents/pause/screen-off/revoke and stale auth. No production code was changed by this assessment.

## Important finding IR-E939-03 — untrusted device URIs can name app-private files

The device picker completion path in the reviewed source passes returned URIs directly to the website callback (bounded count only). SecureWebViewFactory's once-only callback does not validate scheme, provider ownership or whether the picker source can access the returned URI. The current IR01/IR02 working ownership fix, inspected separately, still had the same unvalidated device-URI forwarding when this finding was sent.

Android's official WebChromeClient/FileChooserParams API documentation explicitly requires treating picker results as untrusted: a third-party picker can return URIs pointing to the host application's sensitive files, and WebView imposes no restriction on those selected URIs. WebSettings.allowFileAccess=false is not a replacement for this file-chooser boundary. Primary source: https://developer.android.com/reference/android/webkit/WebChromeClient.FileChooserParams (createIntent/parseResult); independently retrieved during this review.

Source-admitted adverse examples are `file:///data/user/0/uk.co.traynor.privategallery/...` and a URI for this app's own file/upload provider. A hostile picker need not possess the application's private-file privilege if the host blindly forwards the URI for WebView to read as the host. No real private-file exfiltration was performed by this reviewer.

Validate only the external DEVICE result path before invoking its captured callback: reject non-content schemes and providers belonging to the application's UID (including authority aliases), verify legitimate read access/grant provenance rather than relying on ambient broad application permissions, and explain security exclusions. The trusted Vault picker must retain its separate verified preparation and explicit confirmation route, including its app-owned provider URI; a global URI filter must not break or bypass that flow. Negative private-path/own-provider/alias/scheme cases and positive externally granted/revoked-grant cases are required against the actual production validation helper and delivery path.

## Subsequent working ownership-remediation assessment (not an e939 approval)

Read the coherent BrowserExternalResultGate/BrowserPermissionOwners/BrowserV2Home/BrowserV2Session working changes after the frozen review. IR01 and IR02 are materially source-resolved by inspection: retained ViewModel UUID registry keys preserve single-flight/channel reservations across remount/configuration; cancelling once-owner callbacks releases references without reassigning an OS marker; result delivery targets the captured owner and conditionally clears only matching UI; immutable origin/resources/mode/accept values and document/tab/live-view/network fences prevent later requests inheriting a result. Actual source-side active-tab guards ensure the forwarded permission/geolocation/file request belongs to the active live tab instead of merely assuming the active tab at UI arrival. The resource whitelist wrapper remains intact.

The supplied direct production UI/ActivityResultRegistry remount and actual Activity recreation regression sources target the prior gaps meaningfully. They remain Android-unexecuted in this review. IR03 remained open at this checkpoint. A new committed exact-source review and test disposition must supersede this working-tree assessment; it grants no signing approval.

## Read-only operation-quota assessment

The reviewed Runtime.authorized and broker.valid use beginOperation merely to test identity. HiddenSession caps active operations at eight, so eight legitimate operations (or overlapping short probes) can make a ninth liveness probe fail even while authentication is current. Treating that capacity rejection as revocation can close a valid browser. No Android failure was observed; the source counterexample is deterministic.

A synchronized read-only isAuthorityCurrent check of owner/generation/foreground/unlocked-with-expiry is the appropriate separation: it must not clone keys or consume crypto admission slots. Existing actual decrypt/import/promotion operations must retain beginOperation, the quota and commit fences. Required host cases: eight actual operations plus a true read-only authority check, ninth crypto admission still rejected, and lock/background/revoke/expiry/clock rollback/stale reauthentication all returning false as appropriate. Separating identity from capacity grants no new credential or cryptographic authority.


## Further working-tree review reported before grouped verification11

The independent reviewer reported IR01/IR02 materially source-resolved by immutable retained owners and source-tab guards; IR03 materially source-resolved by explicit system OPEN_DOCUMENT, external provider UID/read-grant/MIME/readable-descriptor checks, all-or-nothing validation and a captured-owner post-I/O fence. No further confirmed Important source finding was identified in these fixes. The reviewer also assessed authenticated Hidden-only screenshot opt-in, secure startup/pause and owned-window coverage, always-disabled Recents and fresh Main policy checks as preserving the intended source policy. Read-only authority identity leaves actual crypto admission unchanged. This records the reviewer's message; it is not final exact-SHA approval. All new Android regressions and final review remain mandatory.
