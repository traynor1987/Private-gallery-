# Private Gallery 1.7.6.2 recovery

Status: IMPLEMENTATION IN PROGRESS; NOT RELEASE-READY. Branch starts at 74ccd09c8ce69e8c2f4167a7ad73aa9c074e3082, preserving 1.7.6.1 signed candidate and subsequent guard/evidence. Owner acceptance of 1.7.6.1 remains unconfirmed. Prior handoff records code 41 as installed and certificate SHA-256 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17; neither phone state nor signing material verified in this environment. Full 1.7.6.1 run's Tor natural-timeout failure remains unresolved; do not carry the earlier risk exception forward without review of this release's networking changes.

Checkpoint: cross-vault Primary PIN presentation switched to existing keypad in HiddenRestoreDialog and PrimaryCopyDialog; original independent authenticate calls and PIN buffer clearing retained. Browser upload picker uses explicit theme surface, adaptive grid and primary action. 161 Python host tests passed (including a source-level presentation regression), with pre-existing ResourceWarnings. Android compilation, instrumentation, usability and security verification NOT performed; no claim these changes are accepted. No metadata/version/signing changes.

Picker investigation: openVaultPicker queries on each open; filters COMPLETE, MIME and vaultOnly. vaultOnly is an intentional enforced egress restriction preserved by HiddenRestoreAccess, so do not remove it. Transferred-media missing cause remains unproved; investigate eligible unrestricted transferred items and lifecycle/query races with integration tests.

Browser investigation: Hidden uses named AndroidX WebView multi-profile; HiddenNavigationClient intercepts network, only admits GET and uses authenticated transport. Turning on JS alone does not deliver forms/uploads and may compromise fail-closed networking. Design and verify safe VPN routing, requests, mode switching and isolation before enabling. Existing Tor JS-off and fail-closed behavior must remain intact.

Remaining: comprehensive app-owned dialog audit and theme design; picker cause and fix; secure full Primary/Hidden parity and VPN/Tor profiles; Samsung editor workflow and cleanup; Fold/accessibility; focused Android 17 and full affected auth/network security tests, independent review; original-key upgrade and signing only after all gates. Preserve 1.7.6.1 evidence; no owner data actions, force push, 1.7.7 or 1.8.

## Extension-based upload filtering and keypad theme

Browser upload accept filtering previously recognized MIME types/wildcards but not HTML extension tokens such as .jpg; this demonstrably excludes compatible JPEGs when a website supplies those tokens. Added a bounded known-media extension mapping, retaining exact/wildcard matching and vaultOnly and COMPLETE checks; unknown extensions remain rejected. This is a verified compatibility defect, not proof that it was the owner's specific missing image. Host Java behavioral checks passed for extension, case, MIME, wildcard and rejection; Kotlin integration tests added but Android/JVM Gradle execution remains pending. Existing source re-queries each opening; transfer-specific ownership, refresh and visibility still need actual integration tests.

The shared Samsung-style keypad now uses the selected Private Gallery colour scheme, including error and key surfaces; visual device testing pending. Host Python suite: 160 pass and 1 error in the existing risk-policy scope test, because protected app inputs have changed. This is expected invalidation of the old 1.7.6.1 approval, not a pass; do not weaken it. Prior original Tor failure remains unresolved. No security approval or Android runtime result claimed.

## Upload request lifetime

The picker previously accepted an asynchronous result whenever any picker was open, allowing an older request to overwrite a newly opened one. A monotonically changing generation now rejects old callbacks, captures the request's accept types, and a new website chooser request cancels and clears the previous one. This fixes a verified source-level race; Android lifecycle/reopen integration and actual owner case remain unverified. The encrypted index and vault-only checks were not changed.

## Samsung handoff, date picker theme and development CI — 27 October

Recovered remote 22dae1e6f439afbae8de56baba3f4ab2249590e6. Fast run 40483729996 failed before Android compilation: historical 1.7.6.1 scope fixture compared its frozen hashes against the 1.7.6.2 HEAD. The 1.7.6.1 admission verifier is unchanged; its fixture tests now run against immutable 33e9669 source and an additional negative check verifies changed live app input is rejected. Fast checkout retains history so fixture is available. Existing historical failure and no approval carry-forward remain intact. 18 policy tests PASS locally. Original fast run remains FAILED and has not compiled the source.

Samsung editing handoff now targets com.sec.android.gallery3d via ACTION_EDIT and read-only content URI; missing or failing resolver activates guided MediaStore export and explicit re-import. Exported copy opens Gallery using a package-targeted ACTION_VIEW, with manual fallback if unavailable. This avoids listing unrelated receivers; third-party editor choice is not yet implemented. Instrumentation coverage added; real Samsung support, Gallery URI access, output selection and cleanup remain unverified on device. No automatic editor-result callback promised.

Native metadata date/time pickers now use light/dark picker themes based on the selected Compose palette, keeping original secure-window policy. Visual and Android verification pending. App-wide modal design remains incomplete. No release, signer, original-key upgrade or security clearance claimed.

## Shared dialog surfaces and Fold scrolling

A common app-theme-aware bounded dialog surface is used by the browser Vault picker and AI reference picker; the latter now uses adaptive grid sizing. The Hidden restore dialog uses bounded width and scrollable content when choosing a transfer, while its PIN stays full screen. Added coverage that Samsung editing intents reject non-content URIs. These changes are source-level and have not yet been Android compiled or visually verified. Fast development run 40537962646 applies only to prior source 79a60ffd5f47f51d2370ecc489e40f7428270836, not this later UI diff.

The Hidden transport blocker is specific: WebResourceRequest interception does not expose POST request bodies, and the existing renderer disables WebView network loads while its separately authorised native transport only supports GET. Interactive forms/uploads need a supported, independently isolated routing design with fail-closed enforcement and security tests; do not turn on JavaScript or bypass the native transport as a shortcut. Other modal and browser work remains open.

## Focused authentication and transfer test updates

Updated existing Primary-copy UI test to enter a wrong PIN and then the correct PIN through the numeric keypad, retaining the authentication and successful encrypted-copy assertions. Updated Hidden-restore test for the new keypad and scrollable choices. Added a synthetic Hidden-to-Primary test that verifies a fresh Primary query returns an eligible PNG under extension-based filtering, uses a distinct identity, and leaves the Hidden original present. These tests are added but NOT yet executed on Android; they do not establish the cause of the owner's specific report. Exact Android UI, transfer and isolation/security tests remain required.

Fast workflow 40537962646 for 79a60ffd compiled debug and Android-test Kotlin successfully, but was cancelled during the subsequent JVM/lint/build step by a newer checkpoint push. It is not a passed workflow. The newer fast run 40538531117 tests f59c0a9abd0fdcfc679f51c1575e48e1478d4f0e; do not attribute it to later source. Its result must be inspected. Avoid further CI cancellation or duplicate runs.

AndroidX ProxyController documentation says proxy overrides affect all WebViews in the process and proxy changes are not guaranteed active until the callback. Primary and Hidden currently instantiate WebViews in the same app process. Therefore applying a proxy override only for Hidden would also affect Primary, and cannot by itself establish independent routing or fail-closed mode switching. A possible future design requires an isolated browser process, a transport pinned to the owned VPN, closed-by-default startup/switching and tests for proxy bypass, DNS, service workers and request revocation. This is an investigation direction, not an implemented or verified design. Reference: https://developer.android.com/reference/androidx/webkit/ProxyController .

## Focused Android 17 verification preparation

Fast run 40538531117 on f59c0a9abd0fdcfc679f51c1575e48e1478d4f0e succeeded, including Kotlin test compilation, JVM tests, lint and debug/test APK builds. It did not execute Android instrumentation and does not cover later changes. Prepared a single marker-triggered focused Android 17 emulator workflow covering existing keypad, cross-vault UI/transfer, upload-policy, Samsung intent/manual-export and Hidden profile/SOCKS/navigation tests. The verification helper rejects missing classes/methods and failed/skipped results; exact runtime result and SHA must be verified before claiming pass. This is a development check, not a complete release security, Fold, VPN POST, production signer or upgrade gate.


## Focused Android 17 result

Run 40539768861 on exact source e7b43a5c669b1e1570dbc4d8b79fc21bf9b6930c SUCCESS. Emulator Android 17 executed 36 focused synthetic instrumentation cases; verifier found required classes and four named transfer methods, zero failures/errors/skips. APK is a debug test build; this is not signed release or owner acceptance. Artifact 12751120964 ZIP SHA-256 4b59d71aa44cee964b91677aede46119a9efea49c34e99e472e5de8d6203db6d. Checks include keypad, copy/restore UI and transfer, upload policy, Samsung intent and export helpers, Hidden profile isolation and existing SOCKS/navigation controls. They do not establish actual Samsung compatibility, full visual/modal coverage, complete security review, live Tor, new VPN interactive browsing, original-key upgrade or production signing. Historical Tor full-suite failure remains failed. Parallel fast run 40539768423 is separate; inspect its final result independently. Remaining 1.7.6.2 development remains open.

Fast development run 40539768423 also SUCCESS on e7b43a5c669b1e1570dbc4d8b79fc21bf9b6930c: compilation, unit tests, lint and debug/test APK builds. It ran in parallel with the focused workflow because the checkpoint included scripts; retain both run records, avoid duplicate future triggers. Subsequent documentation-only commits do not constitute newly tested application source.

## Hidden browser session-only bookmarks and history

Added a bounded, in-memory library owned by each Hidden runtime, with history and bookmarks available in Hidden Browser settings. Items are never written to disk or Primary storage; the runtime clears both on disconnect, lock/close or mode restart. Navigation remains subject to existing HiddenConnectionPolicy, transport and authorisation; JavaScript/network configuration and Tor binaries are unchanged. History is session-only and records main-frame finished URL callbacks (which can include error pages); this is not persistent Primary browser parity. Host Java checks cover URL restrictions, bounded history, deduplication, independent instances, removal and clearing; 162 Python host tests pass. Android compilation, UI and actual lock/mode-switch instrumentation for this addition remain pending. VPN POST/forms/uploads and full browser parity are not implemented; do not claim otherwise.

## Collection dialog layout

Collection selection and Vault-media selection now use bounded lazy lists for large collections, with filename truncation and a disabled Add action until media is selected. Collection-management text scrolls on smaller displays. Two synthetic Compose tests cover scrolling and selection under dark/light themes. Host Python 162 tests pass; Android compilation and runtime checks for these changes are pending. This is not a completed audit or visual verification of all modal surfaces.

## Added targeted tests and revocation UI guard

Prepared focused Android tests for bounded Hidden session history/bookmarks and collection dialog scrolling. The focused runner now names both new test classes. Hidden browser library panels are hidden when disconnected or locked and their transient UI snapshots are cleared; lifecycle/lock and mode-switch behaviour still requires runtime instrumentation. Do not claim this is complete persistence or interactive VPN support. Source remains unverified by Android CI until a fresh grouped run.


## Grouped Android 17 checkpoint — session library and collection dialogs

Focused run 40591081592 SUCCESS on source a65df1428c98787d551f6f5812cae0406691a6ea: APKs compiled; 40 Android instrumentation cases passed with no failure/error/skip. This includes two session-library tests, two large collection-dialog tests, and existing authentication/transfer/editor/Hidden transport checks. Artifact 12773908302 ZIP SHA-256 7b3e5f37b170775a5a17dabd9eef2ea6489bcf0c767d268adea02406c79ddc91. Host Java library checks and 162 Python checks passed. Android device was an emulator, not the owner's Fold; test APK is debug only. Session library tests exercise in-memory isolation/clear, not whole-runtime lock/mode-switch instrumentation. Browser POST, uploads, persistent bookmarks, full modal audit, physical Samsung and Fold checks, mandatory final security review, production signing and original-key upgrade remain outstanding. Do not treat this checkpoint as complete or accept earlier 1.7.6.1 risk approval for new browser changes.


## Browser upload picker focused interaction

Added a synthetic WebView instrumentation case for a .png accept filter: the Vault picker shows an eligible item, Continue is disabled until selection, and no plaintext upload is prepared before explicit confirmation. The test cancels before egress. It has not yet run and does not establish the cause of the owner's particular missing item. Existing restrictions remain unchanged. A subsequent focused marker run should verify the exact test and preserve its result.


## Browser picker interaction Android result

Run 40592039926 SUCCESS on source e81979e061c449b1cd6ef597d0f6e64a29df5ed1: exactly one named synthetic Android 17 WebView test passed with zero failures/skips. It exercised an extension-based .png chooser, disabled Continue until selection, and cancellation without plaintext preparation. Artifact 12773848179 ZIP SHA-256 4c680bd90b4f1df0131c55ebf997c8c17d69e60431496591377f699ae7c8cbce. This does not prove the owner's specific transferred file was eligible or that the actual root cause is fixed; it does not test Hidden-browser upload, physical Fold, or production signing.

## Owner-approved scope adjustment and modal work

The owner has deferred secure interactive Hidden VPN browsing (POST bodies, JavaScript-dependent pages, forms/uploads and complete browser compatibility) to a future feature release. See docs/release1762/HIDDEN_BROWSER_ROADMAP.md. Existing Hidden GET-only transport and Tor restrictions are unchanged; no VPN compatibility or full parity claim is made.

Added bounded scrolling to backup-restore, owner-authentication, change-PIN, privacy-share and remote-AI-consent dialog contents for large text and smaller screens. Added a synthetic dark-theme share-dialog test that checks scrolling and cancellation without media reading. Local 162 Python host checks pass, with pre-existing ResourceWarnings; Android checks for this new diff remain pending. This is not yet a complete visual audit of all app-owned modal surfaces, Fold acceptance, final security review, upgrade or signing.


## Release identity preparation

The owner deferred interactive Hidden VPN transport to the documented roadmap. Branch inventory of 83 repository branches found maximum declared versionCode 42; candidate metadata is now 1.7.6.2/code43/min-target37 with unchanged package. Release configuration selects the documented code41 installed baseline by default and separately supports the original-key code42 acceptance baseline via PRIVATE_GALLERY_UPGRADE_BASELINE=42; unknown baselines fail closed. These are prepared synthetic upgrade paths, not proof of the owner's current installed version or passing upgrade. The historical 1.7.6.1 version test reads immutable candidate 33e9669 and the new 1.7.6.2 config has its own explicit tests; previous release/security guards remain intact. 164 host Python checks PASS. No 1.7.6.2 Android candidate build, signing, upgrade or physical acceptance claimed for these new bytes.


## Additional release-boundary tests prepared

Focused Android selection now includes Fold cover/inner light/dark/landscape layouts, existing external-editor lifetime revocation, settings authentication and three named cross-vault revocation cases. Added a synthetic runtime-close test asserting the session-only Hidden library is cleared. These additions are not yet executed and do not constitute independent review or release approval. Current previous run 40601087057 tests source 61d0b5d only, before these additions and version43 metadata.


## Modal checkpoint test

Focused run 40601087057 passed 42 Android 17 emulator tests on source 61d0b5d0cc21b2e3e5549991c8b4c82ae2da3c41; artifact 12779221620 ZIP SHA-256 6dba9160b8eb9262c3137f43e8c63c8b85b1c042223263c6a7637605be65e6d0. This predates code43 metadata and expanded revocation/Fold test selection and is not a final release result. No signing, original-key upgrade, physical acceptance or complete modal audit claimed.


## Additional modal and picker handling

Added bounded scrolling for backup export, transfer progress, browser find and AI progress, and a lazy bounded Hidden collection selector. Picker now displays a loading state during its asynchronous eligible-media query instead of prematurely saying there are no items; the empty state explains that Vault-only restricted items cannot be uploaded. The existing complete/ownership/MIME/egress filters are unchanged. The synthetic WebView picker test now holds the query to verify the loading state before returning its item. These latest application changes and test are not yet Android-verified. The owner's specific missing item remains unexplained without evidence of its metadata and eligibility. The remaining app-wide visual audit and final release security review remain open.


## Expanded Android 17 verification

Focused run 40601707815 passed 59 Android 17 emulator tests with no skips or failures on exact source 3ff418aefd3bb5926db47e2a1413d4f15d7b02a8, including the added revocation, external-edit lifecycle, Fold layout and Hidden session-library selections. Evidence artifact 12778039222 ZIP SHA-256 638bc149c6622757c040ead3845f5d7965a013519704de2845991792b9c50882. It predates later app modal and picker changes. Marker commit 9cd5659f21aaab0cf1d869bb3fa1b84763434e20 requests a fresh focused run for those changes; verify its outcome before claiming a pass. Full modal audit, the owner's specific picker defect, final security review, production signing and both original-key upgrade paths remain open; no acceptance APK or physical acceptance is claimed.

## Sol recovery and final polish — 28 October

Remote HEAD 906232e verified; clean clone, no competing local writer. Prior run 40602551112 SUCCESS on 9cd5659: 59 Android17 tests, zero failed/skipped; artifact 12779496357 SHA256 bcd3c46b64cfd53c45ce61b1962f594133c3803526a1c5838d56e63ef3a98044. No duplicate of that run launched.

Added shared GalleryAlertDialog to all app-owned alert sites, explicit selected-app palette/shape/border, scrollable body and separate actions. Existing bounded scroll/lazy bodies retain their own scrolling. Bounded AI settings dialogs share GalleryDialogSurface. Primary V2 website JS dialogs now use the themed shell with origin, bounded prompt/message and explicit choice; pending requests cancel on background/network revocation/tab close/change/navigation/renderer loss. Hidden transport/JS/Tor are unchanged. Added real-window large-text theme/layout cases, website-dialog lifetime tests and actual encrypted Hidden-to-Primary move → reopened website-picker test, including restricted-media rejection and cancellation before plaintext egress. These latest changes are pending Android compilation and tests; full release security review/signing/upgrades remain pending.
