# Professional UI consistency acceptance

Starting main: 4e8baef13b47cca2a8a1340a646d3b8c8b3093a4.

## Audit and scope

Gallery, Vault and Browser already used MediaChrome bottom sheets. Remaining inconsistencies were the lime primary palette, heavy headings, decorative card stripes, ungrouped menu actions, outlined choice buttons without selected semantics, wider Favourite padding, and missing app navigation in Browser. VPN-required feedback was a generic message rather than a stable content state.

The pass uses fixed neutral light/dark Material surfaces, muted slate selection, medium/semibold typography, shared cards, rows and sheet groups. It preserves compact grids and favourite collection storage. Existing Gallery has no sort/type-filter API; this pass does not invent one or filter only a partial paged dataset. Its count explicitly says loaded, rather than claiming the total device library count.

VPN presentation consumes existing state and Android permission/preparation status. It does not grant network access. BrowserVpnGate, controller, engine, WebView configuration, encrypted stores and acquisition remain unchanged. Explicit connect/retry uses the existing controller even when automatic connection is disabled. No timers simulate progress.

## Automated checks

Existing complete Android workflow: no-secret scan, VPN dependency/notices check, unit tests, lint, app/test APK builds, API 36 emulator suite. New coverage exercises Gallery access and menu, Vault media/collections/menu, Settings choices, navigation geometry and VPN transitions in both themes. Test fixture screenshots contain synthetic data only and are archived in acceptance diagnostics for visual review.

Local Gradle cannot bootstrap because the environment cannot reach the distribution host. GitHub Actions is the build/emulator authority. CI cannot establish physical-device acceptance.

## Required physical checks

Run Signed Device Acceptance Build on green main; do not publish a release. Install with the permanent signing identity over the existing installation; do not clear data.

1. In light and dark modes, inspect Gallery, Vault Media, Vault Collections, Jenna/Favourite, Browser and Settings on Fold outer and inner displays. Confirm compact thumbnails, legible counts/search/filters, neutral surfaces and consistent navigation. Repeat with larger text and TalkBack; selected controls and loading states must be announced.
2. Open Gallery and Vault overflow sheets. Scroll all actions, change thumbnail size, dismiss with Back/drag, and check navigation-bar/keyboard insets. Gallery selection, copy/move, Vault sort/filter/search, add-to-collection and new collection must retain their existing results. Use disposable media for move/delete/restore checks and confirm Android deletion consent remains required.
3. With Require VPN enabled and no selected profile, Browser must show Set up VPN within its shell and route to Settings. With auto-connect off and a valid profile, show VPN not connected and connect only after tapping Connect securely.
4. Revoke VPN permission, return to Browser, cancel Android's request, then use Allow VPN connection. Keep the page hidden and networking blocked until the owned connection reports connected. Confirm a connected VPN belonging to another app does not bypass the requirement.
5. With auto-connect enabled and a valid profile, verify real Connecting securely progress and automatic reveal of Browser when connected. Address bar, toolbar and navigation must remain stable. Load a test page after connection.
6. Use an invalid/unreachable test VPN configuration to exercise failure/retry, then restore the normal profile. During connection loss, page content must be covered and new navigation/downloads blocked. Restore the VPN and confirm Browser returns normally; test tabs, back/forward, fullscreen video and app switching for regressions.
7. Lock/unlock with PIN and biometrics, check auto-lock, secure-window protection and existing Vault contents. Confirm favourite collection identity and existing bookmarks/sessions survive. Do not expose private data in acceptance screenshots.
