# Black Screen audio overlay and supported Android pinning

Owner specification: optional App Mode Black Screen and native Settings Pin NovaOS; Android17/API37,
Samsung Galaxy Z Fold, original package uk.co.traynor.privategallery, existing integration branch.
Baseline signed1.7.11.4/code60 sourcef2727d75d142c2a733c53861e1a0f74440e0b6b5,
delivery HEADebc3b24492c4673214a02cda37054a7439fd4697. Source and remote match; tree initially clean.
Use the existing isolated project checkout; no replacement branches, reset, force-push or source-branch edits.

## A Black Screen

Use an Activity-owned native opaque BLACK View above the existing NovaOS content, without replacing,
detaching, navigating or pausing its WebView. Offer only from eligible installed-app immersive menu,
not standalone Browser/native apps, unavailable loading/admission/error contexts or revoked owners.
Bind activation to the existing authenticated session and exact app/tab identity. Native control is
presentation only; it does not own authentication, browser/network policy, playback or storage.

Save and restore exact per-window brightness, actual status/navigation visibility and immersive
behavior. Use supported WindowInsetsController APIs and lowest supported window brightness.
A View-scoped keepScreenOn request exists only while this explicit foreground overlay is visible;
no service, unrestricted wake lock, global brightness setting, permission or polling is added.

Consume all window touches above content, hide underlying accessibility descendants temporarily,
and expose a labelled accessibility click to Wake NovaOS. Double tap and Android Back/Escape wake
without webpage navigation. MainActivity's existing three-finger Panic dispatch remains outermost.
Restore previous accessibility/focus only when still authorized. Restore brightness/bars even on
cancellation, pause, lost window focus, disposal, actual lock, timeout or Panic. Never persist an
activation across recreation, another app, a Space switch or a later authentication session.
Rotation/Fold config changes naturally resize the matching-parent cover; recreation dismisses safely.
Incoming calls and actual Android screen-off follow existing production pause/lock/audio policies.
No claim of true display-off, zero display energy, guaranteed third-party background playback or
bypassed platform controls. Pure-black pixels do not eliminate all display power consumption.

## B Pin NovaOS

Add a neutral native Privacy & Security settings section using public Activity/ActivityManager,
DevicePolicyManager, KeyguardManager and Settings intents only. Public lockTaskModeState distinguishes
NONE, PINNED, LOCKED and unknown; NONE does not prove the global pinning switch is disabled.
The SDK does not expose a supported global switch or pinning-specific Settings action. Do not copy
@hide LOCK_TO_APP_ENABLED/raw keys. Offer ACTION_SECURITY_SETTINGS (ACTION_SETTINGS fallback) and
honest guidance to enable App pinning if needed; never claim an unknown switch state.

Only an explicit current authenticated foreground Settings action calls startLockTask. Block locked
keyguard, revoked/changed Space, paused Activity, unreadable policy and device-policy allowlisting
(isLockTaskPermitted true), because that path can enter managed kiosk without normal confirmation.
Calling startLockTask does not prove success: refresh actual state on resume/focus, no polling,
automatic re-request, saved pinned flag or silent automation. Preserve Android exit gestures;
never exit/manage LOCKED mode. Samsung Modes/Routines may launch normally, not silently pin.
No new permissions, accessibility service, device admin or manifest privileges.

## Approach and constraints

Native overlay chosen over a separate Dialog or replacement web page: it covers the whole current
window without browser lifecycle churn and retains the existing Panic dispatch. Pinning uses
supported native flow; hidden setting probes and device-owner provisioning are ruled out by scope.
Preserve all encryption, Primary/Hidden separation, concealed entry/Hidden PIN-only, BrowserV1,
Store/data, Home appearance, Evidence AI, status indicator and existing Android system controls.
Do not reopen VPN/Tor. Independent feature commits; one combined focused API37 regression batch,
independent security review and original-key60-to-next upgrade/restart before signed owner delivery.
Physical Fold/YouTubeMusic/account/call acceptance stays explicit; synthetic audio is not live-service proof.
