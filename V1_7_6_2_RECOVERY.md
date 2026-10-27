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
