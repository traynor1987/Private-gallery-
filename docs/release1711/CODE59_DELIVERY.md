# Code59 — verified navigation, live status and first-launch delivery

NovaOS 1.7.11.3 / versionCode59; Nova Home2.0; Android17/API37 only.
Package: uk.co.traynor.privategallery.
Branch: integration/novaos-1.7-home2.
Frozen application source: ab659cf3578d08cdc0deb643f887e561abb14c63.
Frozen tree: 408c9e0d7b2d0980c45b94c43e91b4933e0bad89.
Signing activation: d62d8fc3c1fb921db52c63a1364de3edad102c13.
Upgrade baseline: actual original-key code58, sourcece743678cca903f41a376e5227eb381f9fbb9574.

[Download signed owner-acceptance APK](https://github.com/traynor1987/Private-gallery-/releases/download/novaos-1.7.11.3-home2-code59/NovaOS-1.7.11.3-home2-code59-owner-acceptance.apk)

APK144778924bytes; SHA-256:
6db4f5853c8bbcdc379c0887002620d4f75cbd3c86dc98c2e46707b833d94796

Original production certificate SHA-256:
94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17

## Exact fixes

- Shared NovaOS-owned Recents/Home/Back row on Home, supported native roots/workflows and
  Browser. Existing Android system/gesture/IME insets remain. Eligible installed App Mode
  alone adds overflow to the right of Back; its floating top-right overlay is removed.
- Secure bottom sheet above the row reuses Back, Forward, Refresh/Stop loading, Nova Home,
  Open in Browser, App Settings, Exit App, Switch App, Screenshot to Vault, Browser Tools,
  Lock Space and Panic Exit. Disabled actions reject invocation; Back dismisses the sheet
  first. App changes, backgrounding and revoked authority retire stale callbacks. Opening
  or dismissing the sheet preserves WebView identity, session and user-started audio.
- Compact themed battery percentage/fill, charger/charging, saver and connectivity indicator
  uses supported Android callbacks/broadcasts. Wi-Fi association differs from validated
  internet; supported mobile technology is shown only when reported, restricted details
  omitted. Airplane/disconnected states are actual device states. No polling, location,
  SSID/SIM disclosure or guessed signal. Optional VPN badge is omitted. Original root insets
  suppress custom chrome while the Android status bar is visible. Quick and committed
  pause/resume paths now restore exactly one subscription and fresh readings.

### First-launch regression was not already fixed in code58

Fixing commit: d0babe10bf1e96e395033865030ca35362c89fba.
Later status lifecycle correction: ab659cf3578d08cdc0deb643f887e561abb14c63.

The controller formerly discarded an unready saved-app launch before remembering its identity;
presentation policy also exposed browser chrome on blocked/failed App Mode. Authenticated
admission metadata now preserves the intended saved UUID and app-owned privacy/loading/error
UI while the existing real network gate is unavailable. Re-admission checks the original
session, unchanged saved definition and current presentation. Explicit OS VPN consent handoff
retains only admission metadata; old transfer/controller authority stays retired. Home,
ordinary background, lock and concealment cancel pending admission. Another unlock cannot
revive it. No artificial delay, new browser engine or weaker network gate is introduced.

Relevant passing API37 cases include:

- firstColdLaunchStaysInIntendedAppWhileRequiredVpnUnavailableAndRetryKeepsUuid
- explicitOsVpnConsentHandoffRetainsOnlyAdmissionMetadataWhileOldControllerRetires
- changedSavedDefinitionRejectsBlockedLaunchToHomeInsteadOfOrdinaryBrowser
- pendingColdLaunchIsCancelledByHomeBackgroundAndActualLock
- userStartedAudioStaysAttachedAndPlayingAcrossHomeAppAndStandaloneBrowserUntilBackground

Tests cover blocked first launch/retry, explicit VPN-not-required admission, warm/Home reopening,
same UUID/WebView, simulated OS handoff and revoked authority. Live connected-VPN handshake
and Android consent UI on the owner's Fold remain physical acceptance; they are not claimed
verified by these simulated boundaries.

## Preserved and verified

Browser V1 preservation script PASS: same constructor, configuration, stores and ordinary
tab operations; existing session source hash remains
8f4c8e68023c5a73a648f8cf7c4e3328d13538de3b276b0220686f6798861e7b.
Synthetic browser history/settings and persistent login cookie survive upgrade/restart.
Browser V1 remains independently launchable; saved apps reuse existing Private Apps engines.

Selected authentication/session, concealed Hidden-entry, Primary/Hidden isolation, Panic,
Gallery/Camera/Evidence and encrypted Home/wallpaper cases pass. Separate Hidden PIN policy
and prohibition on Hidden fingerprint remain. Code57 native-theme/mobile-scope/media-menu
fixes and code58 secure wallpaper entry points remain included. Approved Home appearance,
Vaults, Gallery editing, Store and data formats are preserved. No crypto keys/schema rewrite,
plaintext Preview import, Home redesign, VPN/Tor repair or future runtime was added.

Runtime42209939762/job127865786721/artifact13499660758:130unique Android cases across29classes
and406unique JVM cases, zero failures/errors/skips. Original archive68233934bytes SHA256
194292602306328a64b67c1ba1e173448ac9bd3f6657f37f566ae6dd45c6398f.
Parent and independent reviewer verified original provenance, source, matrix, reports, isolated
test APK and SystemUI/API37/tools22 environment. Independent exact-source signing approval
and renewed fixture approval are recorded in SECURITY_REVIEW.md.

Production42214765335/job127881954559 SUCCEEDED. Actual original signed58 -> signed59
in-place installation, synthetic encrypted-data/configuration preservation, force-stop and
verification after restart PASS on a fresh enforcing API37 emulator. The experiment started
with package absent, then retained all seeded data without uninstall or clear. Original58
fixture compilation and evidence gates preceded signer access.

First production run42212278039 failed during original58 fixture seeding before59 installation;
no APK was published. The corrected fixture uses original58's authoritative wallpaper Apply
API, retaining all original assertions/exclusions and adding exact committed-record equality.
Original failure evidence and correction are preserved in CODE59_PROGRESS.md. The application
source and successful130-case runtime were unchanged by this fixture-only correction.

Production artifact13502360091:60654499bytes; SHA256
c4ffbed29381c36f349d43e1a52c46acd778687d3cc5899e4a9d48bb64afbc6e.
Nested evidence108169bytes; SHA256
06ce826e58021da23aac3c66a83a111008038b040c53ee6540b082cabda47c64.

Parent downloaded the actual production archive and checked authenticated digest/size, CRC,
safe exact delivery members, actual APK with aapt/apksigner, one original RSA4096/v2 signer,
nondebuggable package/version/API37, exact frozen source and candidate hash receipts, all
three upgrade phase successes, reviewed fixture hash, and GitHub APK/evidence/summary/checksum
asset hashes. Evidence contains no installable package, nested archive or signing material.

## Limits and Galaxy Z Fold acceptance

This signed prerelease is for owner acceptance. Physical acceptance remains NOT_PERFORMED.
Live Fold posture/rotation, Samsung biometric hardware, real radio/VPN transitions and personal
third-party accounts need device checks. Restricted radio details and VPN badge are omitted.
Background-audio tests verify continuity inside NovaOS; Android-level background playback and
third-party login/playback restrictions remain. Deferred Hidden VPN/Tor work stays deferred.

Install over existing NovaOS; do not uninstall or clear encrypted application data.

- Confirm existing Primary/Hidden media, Evidence, layouts/wallpapers and Browser V1 logins.
  Use concealed entry plus separate Hidden PIN; check lock, timeout and three-finger Panic.
- Cold-open YouTube Music before returning Home. Confirm intended App Mode immediately,
  including required-network unavailable/retry; reopen and confirm the same session.
- Play media, open/dismiss the bottom menu, use Back/Home/Recents and reopen. Check no
  reload/audio interruption, no floating menu, and native apps have only three controls.
- Connect/disconnect charger, try Wi-Fi/mobile/airplane transitions and visible Android status
  bar. Check actual values, permitted technology labels and no duplicate indicators.
- Fold/unfold and rotate with the row, sheet and status indicator visible; check bounds and
  accessibility. Keep separate Primary/Hidden data and wallpaper previews private.
