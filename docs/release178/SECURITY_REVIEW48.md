# Independent code48 repair review

Decision: APPROVED for the exact source and focused runtime evidence below. Open Critical findings: 0. Open Important findings: 0. Delivery remains conditional on verified original-key signed code47→48 upgrade/restart and signed artifact review; this report does not approve an untested APK.

Reviewed application/control source: 843552f5aa7ee922b01abbcba1ea924f08f696ef. Comparison baseline: 8a92f183ffee8eb4bac2bbf0a9fe78e549fb4aaa. Independently fetched candidate; working-tree tracked application, tests, controls and documentation matched candidate byte-for-byte at review. Existing canonical code47 review and historical evidence are not superseded or modified by this repair review.

## Scope and findings

Reviewed the complete repair delta: catalogue codec/commands/controller, metadata discovery and transport integration, Primary session binding, Hidden activity lifecycle, compact chrome/library/settings/switcher/loading presentation, regression tests, version and release/upgrade controls.

Catalogue v2 appends appStyle/discoveryReport while retaining the old nine-argument constructor. Version 1 decoding consumes no new bytes and supplies true/empty defaults. Merely opening old data does not write it. Atomic commands preserve old fields; Hidden broker delegates bounded requests to the same command validation, without an obsolete action 11 admission ceiling. New values remain encrypted in the original owner-separated stores.

Public metadata discovery retains same-origin URL policy, bounded responses and normalization, no profile cookies or authorization, no redirects, existing TLS validation, captured VPN DNS/socket authority or private Tor SOCKS route, and the existing operation watchdog. HTML base is independently resolved against the original page; every subsequent fetch is also checked against that original origin. Supported display overrides and up to three raster icon candidates are bounded. Optional icon work begins only before 20 seconds and cannot override compatible metadata when skipped. Discovery failure has an explicit website fallback rather than an unsupported installation claim. Automatic discovery uses an editor/URL identity fence; cancelled or replaced editors cannot publish a stale result.

Saved-app reopening selects an existing binding instead of allocating/reloading a tab. Fresh-PIN controller authority and saved-definition equality are still checked before launch/switch. Style changes only update presentation metadata. Removal closes all associated live bindings. Compact controls retain an outside-scope/origin warning, transport failure visibility, browser tools and configured Hidden Quick lock. Existing secure dialog policy, WebViews, permission handling, transport boundaries and Android Back behavior are retained.

Two Important issues identified during review were corrected before this candidate: (1) closing the last Hidden app could bypass owned-download cancellation; matching active/pending/candidate downloads are now retired before view destruction; (2) compact mode hid configured Quick lock; it is now available in the overflow. No remaining Critical/Important source defect was identified. This is an assessment of the reviewed delta, not a claim of an exhaustive proof of the unchanged application.

## Regression coverage and evidence limits

The exact manifest declares 41 Android cases; expected focused catalogue JVM count is 33. New/extended tests cover automatic PWA/website choices, compact controls and Fold/theme wrapping, encrypted style persistence, switcher close, actual Primary WebView identity and HTML draft retention across reopen/style change, Hidden fresh-PIN reopen without a start-page GET, and a real slow download cancelled by closing its app with repository/staging assertions. Existing screenshot, document transfer, upload consent/cancellation, authority/revocation, VPN/Tor/DNS packet, privacy and network-loss assertions remain required.

Independent local checks performed: eight upgrade preparation/baseline tests passed; six final-scope validator tests passed; shell syntax and git diff whitespace checks passed.

Exact candidate workflow run 41152312292, attempt 1, job 124391388498 was independently fetched through GitHub API: completed/success, push on release/1.7.8-private-apps, workflow .github/workflows/fast-development.yml, head 843552f5aa7ee922b01abbcba1ea924f08f696ef. Every required host/compile/JVM/fixture/Android/observation/source/upload step succeeded. Artifact 13024451086, private-apps178-checkpoint, was independently downloaded; raw ZIP SHA256 c113da8e6fcfd857096fc7db4df9081d5f98360892ef2ca6bebaf7594b9be5d7 matched the independent API digest.

Independently parsed artifact XML: exactly 41 selected Android cases and 33 JVM cases, no duplicate identities, failures, errors or skips. Android identities match the exact candidate manifest. Independently reran strict VPN and Tor packet/protocol validators: PASS, including WireGuard-only origin/fixture DNS, actual HTTP/form/multipart bodies, private Tor route/deny-all IPv4/IPv6/UDP/DNS sink facts, private cookies and in-flight revocation. Original final-gates.json records exact 843552f5, decision PASS, original_exit_status 0, owned_vpn/native_tor/dns_and_packet_observation/revocation all PASS; source-sha.txt independently matches. Rendered API 37/target android-37.0 has 15 stable SurfaceFlinger observations at PID 517 and empty preflight/final crash buffers. These are actual original successful gates, not a reinterpretation of an earlier failed run.

The additional 843552f5 delta corrects browser-style address visibility to respect its independent encrypted preference in both Primary/Hidden. Its actual production Primary-composition test covers all four style/address combinations and collapsed/expanded controls without renderer/network authority and passed in this exact 41-case run. Static signature/tag/listener/harness review found no defect. Prior 269a79c5 passing 40/33 evidence remains historical corroboration only; final approval is bound solely to 843552f5 and the exact successful artifact above.

## Original-key upgrade and signing controls

Version 1.7.8/code48; baseline is the actual delivered original-key version 1.7.8/code47 APK, not a rebuild. Baseline application source 04b91ae01c64323aac02975672d6046dfba62e30; signing control 67296943557d6caf55f1ab5840f3c7eaa8b18b84; run 41081513370/artifact 12990222572. Baseline archive SHA256 5ea6025721ad51530249592213ec7d8a8682682e52eba84ba65587e86b68f82e; APK SHA256 21066bd7153520c688c5a6b6034fca549b33df391762e64a5070eecfeb342d3d; certificate SHA256 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Reviewed controls agree on these identities, code47→48, single APK, signed package identity, release debug-service absence and in-place restart proof. Freeze remains zero and signing is not activated.

Reviewed upgrade fixture preserves previous media/trash/hash/nonce/ciphertext/retention, PIN, cookies/browser settings, camera, AI/privacy and presentation assertions. It adds real Primary/HiddenVPN/HiddenTor version 1 private-app definitions, old-field comparisons, candidate-only reflective style commands and reopened persistence. Initial ciphertext equality is checked before deliberate candidate style writes; only those exact three catalogue files receive candidate hashes for restart. All other state retains its existing strict checks. Baseline instrumentation compilation and full signed 47→48 seed/install-r/verify/force-stop/restart remain mandatory and unproven for this candidate. Owner physical acceptance remains required after a verified signed artifact.

Control hashes (SHA256):

- 41-case manifest: c7c9365eba76ab651c3403147bb16f1085596648335c26acc5bc903009e38472
- Final scope validator: dd572092bfee11e69c3ae5ac06b704f00eda6c9e4b0a4facbf2f72fd71b42b43
- Upgrade fixture: 33687d6f0e2665c0b5f783a5af6da5fbfe4ea7b1f84625a782f64b57c5eb70cc
- VPN packet validator: 2e9827e1869a8ed52b22dd5161cd34c72404c6dc6a75e8a8bc9e4ad5d0b49676

## Final machine-readable decision

```json
{"source_sha":"843552f5aa7ee922b01abbcba1ea924f08f696ef","decision":"APPROVED","critical_open":0,"important_open":0}
```
