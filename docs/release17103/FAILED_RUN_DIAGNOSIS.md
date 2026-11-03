# Code55 failed-run diagnosis and focused correction

Authoritative recovered source:17d9512fc4613c24f401ad7cba8f359310c77d88, unchanged branch release/1.7.10-private-home. No newer remote source was present at recovery. Version remains1.7.10.3/code55; last signed54 remains the upgrade baseline.

Run41657787553/job125993078723 compiled the app and Android tests and passed host/JVM and fixture preparation. Artifact13240894402 was downloaded completely and SHA256 independently verified:4f133eaea415a48cd1707be118d61e29f620db99f0a7a4ef3a6b51c13d74effb. Its raw XML proves15/15 no-fixture cases and3/3 original VPN/Tor route cases passed. Eight new continuity cases failed; none is counted as passing or security approved.

## Confirmed native action defect

All three actual-Main fixtures failed at Private Apps before exercising suspension. The archived baseline AX tree places the Private Apps content description on an icon without ACTION_CLICK16; its parent owns the visible enabled click action. The new fixture clicked the label directly. Production creates exactly that IconButton/Icon hierarchy. Existing passing native fixtures climb to the clickable ancestor. The correction is confined to test interaction: resolve the real ancestor rather than removing accessibility or owner assertions.

## Foreign-origin/state observation failure

All five dedicated child fixtures reached a real HTTPS synthetic document and valid begin request, then timed out waiting for visible FOREIGN_REJECTED. The archived server evidence has five valid begin events and no callback or probe event from those failed fixtures. The assertion is a website status observation, not a production SecurityException. Original artifacts contain no failure-specific DOM/AX/screenshot trace, so they cannot prove whether the negative click missed, the handler failed, or AX stayed stale. No harmless-failure classification or confirmed production-policy defect is asserted.

The actual served JavaScript was executed in a Node VM with mocked fetch responses for both site origins: wrong origin and wrong state reject without callback, cancellation stays unauthenticated, and a valid provider-origin state proceeds to the distinct protected website-session response. Existing endpoint tests retain TLS, host, preauth-cookie, one-use state, cross-site cookie isolation and POST-only replay assertions. This host check does not substitute for Android WebView execution.

Both new interaction helpers omitted the AX cache clearing and stable same-window/viewport-clipped HTML touch used by the already-passing native fixture. Corrections adopt those existing mechanisms. Protected content is not uncovered and production click/security behavior is unchanged. Additional synthetic-only negative-result observation reports just origin/state rejection classification, never actual origin/state/token values. It neither consumes pending state nor creates a session. The Android test still requires FOREIGN_REJECTED and zero callback, now separately requiring the handler's server observation. Failure diagnostics contain bounded geometry/actions and whitelisted synthetic status labels; secure-window capture remains subject to Android redaction.

## Verification and release status

No production lifecycle/authentication/network/storage code is changed in this correction. Reuse of the18 passing cases is allowed only after machine-checking unchanged production/debug/build inputs and those passed test sources/helpers against the pinned original source/archive. Original raw evidence remains preserved, clearly labeled as prior-source-compatible rather than new exact-source execution. The grouped rerun targets all eight failed cases and requires new full packet evidence, all eight continuity receipts and12 distinct transition scenarios. No failed continuity result is reused.

Mandatory remaining gates: corrected Android compilation and grouped8 execution; full callback/session/ownership/expiry/Lock/Panic/death and fresh packet results; independent final security approval; frozen-source original-key54→55 install-r/restart; certificate/package/version/APK hashes; one signed acceptance APK. No signing marker is activated and no repaired real Google transaction is claimed. Existing Beta disk-profile persistence disclosure remains unchanged.
