# Native failure investigation — d5c246

Observed Android17 run40755407244 tested `d5c246a6c1a366e71cee47751dd9062cec23b199`: 255/273 passed,18 failed. This report owns five native failures and the related cold-boundary false pass. Artifact ZIP SHA256 `4701aa33b739a55ad86f49fe9c90f318e1eaa6a6b368f9acdb39631a96137bef`,449 entries, extracted `/tmp/focused-d5c246`; full failures `/tmp/focused-d5c246-failures.txt`. No Android SDK/emulator exists locally. Changes below are candidates requiring exact-source CI, not passing runtime evidence.

## Stalled Unix revocation

`HiddenRuntimeFailureTest.revokedNativeAuthorityClosesStalledUnixPeerAndDeniesReuse` fails at line72, the 3-second peer EOF future. The handshake successfully reached the deliberately stalled CONNECT response read; the successful fresh Unix DOMAIN/payload test and natural30-second timeout control passed. `HiddenVpnProxy` tracks the Tor Duplex before connection and its100ms watcher revokes/ closes all tracked sockets. The Duplex formerly called only LocalSocket.close. Android's LocalSocketImpl close calls Os.close directly; a concurrent blocking native read can retain the Unix socket until that read exits. Source reference: https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/net/LocalSocketImpl.java (close and shutdownInput/Output).

Candidate source correction: shutdown input/output before descriptor close, waking the negotiation/read and producing EOF to the peer. A closed marker checked before and after connect prevents a revoke-before-lazy-descriptor-creation race from leaving a newly connected socket admitted. Existing exact3-second revocation/EOF/no-response/no-reuse test remains unchanged, as do production30-second timeout and DOMAIN-only resolution. No fallback added. Actual Linux/Android proof remains pending.

## Unauthorized Activity restart loop and VPN cascade

The full log shows an unauthenticated `HiddenVpnBrowserActivity` launch at19:50:56.011 without extras, immediately before the VPN live test. PIDs7864→7880→7896→7912→7949→7965→7981→7997→8013 repeatedly die/restart for the same top Activity. VPN live subsequently fails at broker launch with `Previous browser process is still alive`19:50:59.711. This is not evidence of a failed real VPN tunnel.

Cold failure reaches terminate(BACKGROUND), whose hard branch killed the process without finishing its Activity record. Candidate correction calls finish before hard process kill. Parent synchronous revocation still kills immediately: a restored stale Activity is rejected and finishes before its own kill, breaking subsequent recreation. No graceful delay, authority reuse, network rebind or curtain relaxation is introduced.

`HiddenVpnBoundary1762Test.noCapabilityLaunchCannotBindPrimaryOrRetainChild` previously sampled PID absence after100ms and passed before cold launch finished. It now requires both process absence and absence of child baseActivity OR topActivity records, continuously for1second within its unchanged10-second deadline. This catches delayed cold startup and the observed restart loop; Primary PID/Network assertions remain. This strengthened test has not executed yet.

## Explicit authorized Done

Library maximum-size bookmarks/history reached the actual claimed child successfully. Per-test log shows History dialog destroyed19:49:38.639, then no child termination until finally kills PID7503 at19:49:48.687 after the10-second ACK assertion fails. The test performed consecutive identically labelled Done clicks. Accessibility action acceptance precedes Compose dialog/root removal, allowing the second click to target the departing History action.

Test correction waits for browser Settings in the active root and removal of the maximum history entry before clicking browser Done. Existing requirements remain: supported profile clear ACK, process death, both explicitly owned storage roots removed, credentials still unlocked, then revocation. Production profile cleanup and deadlines are unchanged. The stale-root explanation is source/log-supported inference; exact execution must establish the ACK.

## Web virtual actions: form and paging chooser

Onion backend `acceptance-diagnostics/onion-fixture/interactive-events.jsonl` records `/script-probe`, `/interactive`, then approximately40 `/evidence` GETs, with ZERO POST. The Tor test accepted its `Submit Tor form` accessibility action but failed real form-body assertion. Paging similarly accepted `Choose Hidden upload`, then could never find chooser Cancel. Both helpers climbed from exact web text to the first clickable ancestor and treated ACTION_CLICK acceptance as activation. A WebView/container action can be accepted without activating the intended HTML control. No failure hierarchy was archived, so this exact locator diagnosis is an inference, not proven source defect.

Test candidates tap the screen bounds of the exact visible/enabled accessibility node for web form/file controls, using ordinary injected touchscreen DOWN/UP. They retain every actual server/body, multipart original hash, no-JavaScript, no-egress-before-confirmation, >500-item reachability, restricted/MIME exclusion and cancel/reopen assertion. They do not evaluate JavaScript or invoke application callbacks. Native Compose actions retain semantic clicks. No timeout is increased. Exact CI must confirm the interaction hypothesis and all downstream assertions; absent proof these two cases remain unresolved.

## Checks and disposition

Local `bash scripts/check_hidden_browser.sh`: PASS,53 browser/diagnostics policy checks plus paged picker >500 final-item/stable pages/exclusions/metadata bounds. `git diff --check`: PASS. Android/Kotlin compilation, real Unix revocation, ordinary Done ACK/root deletion, cold reject/task retirement, Tor HTTP bodies and actual owned VPN/UDP/DNS/process retirement remain pending. Run the full mandatory native suites and source/scope evidence gates; no CF waiver, skipped native proof or passing release claim is permitted.

Both native activation flags remain false. Version1.7.6.2/code44, original production identity/certificate, PIN/key storage, Tor binary and capture policy are unchanged. Fresh independent review of this delta and exact-source Android execution are required before any completion, activation or signing claim.
