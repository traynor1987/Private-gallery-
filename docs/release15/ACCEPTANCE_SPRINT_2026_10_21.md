# Private Gallery 1.5 acceptance fix sprint — 21 October 2026

Recovered clean workspace and independently verified GitHub branch head
`c246b502a80603ee9afe501280efd276aad004eb`. No active implementation/build process
was found. The recovered application/test sources already match successful CI595
(`13f98aa10c66ed806416088932b484af28e2287a`); subsequent changes were documentation.

## Retained corrections

- Original five destinations, isolated Hidden Vault/Favourite, device Gallery,
  existing WireGuard browser and original Settings.
- Shared full-screen PIN presentation with independent Hidden credentials.
- Hidden background timeout choices and retained session across tab navigation;
  explicit lock/screen-off revocation; Return to normal Vault.
- Existing screenshot consent applies to Hidden Activity media; Hidden PIN remains
  SecureOn. No new screenshot preference or encryption/storage changes.
- CI595 removed the oversized media Dialog and uses the working Activity viewport.
  That fixes the identified constraint mismatch, but Samsung physical acceptance
  is still required; earlier CI594 failed owner testing.

## This sprint

The shared PIN card had no scroll container. Its setup/error/confirmation/actions
could exceed the viewport when the keyboard is open or the window is short.
LockSurface now consumes actual safe drawing/IME insets and scrolls its content,
retaining the existing centered card design on larger windows. This also benefits
the shared Primary PIN/recovery presentation without changing authentication.
No device-specific height or bottom offset is used in production.

PinViewportTest constrains the shared Hidden setup page to a short viewport and
requires fields, submit and cancellation to be reachable by scrolling. Removing
the scroll container makes performScrollTo fail because there is no scroll parent.
Local Android execution was attempted before implementation but was blocked by
the unavailable Gradle distribution/network. A local Android red/green result is
not claimed; final CI must compile and execute this regression.

The existing Android workflow additionally checks the actual APK application ID,
TEST ONLY version marker, Android apksigner verification/certificate and SHA-256,
and includes those diagnostics with the APK. It does not publish a release.

## Verification and review

Local baseline: 373 foundation assertions plus synthetic isolation/import/
favourites/collections, revocation, encrypted records/corruption/recovery,
durability and background timeout checks passed. Node browser tests 3/3 passed.
Secret scan, WireGuard dependency checks and diff whitespace checks passed.
Final changed-source CI is pending at this checkpoint.

Independent read-only review of the recovered source found no material production
issue in navigation isolation, lifecycle authority, timeout, or screenshot policy.
It confirmed the Fold/keyboard physical acceptance gap. Follow-up review found
missing Compose inset imports; those were added before push. The reviewer found
the scroll/inset order appropriate and no credential or screenshot policy change.

## Installation and physical acceptance

The debug application ID is `uk.co.traynor.privategallery.hiddenprototype`, separate
from production V1. Ephemeral GitHub debug signing may differ between runs: an
in-place update of an older test install is not guaranteed. Do not uninstall an
app containing needed media. This sprint does not alter real owner media.

Check folded/unfolded Hidden Vault and Favourite navigation icons and labels above
the gesture bar; PIN setup/unlock/retry/cancel with keyboard; screenshot on/off and
locked PIN; all timeout options/background return; tab retention; manual/screen-off
lock; Return to Primary; synthetic photo/video import, playback and favourites.
Production approval remains withheld pending the owner's physical test.
