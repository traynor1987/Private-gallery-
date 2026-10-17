# Hidden navigation/layout acceptance correction

Owner physical test of CI588: Hidden opens, PIN setup works and adding media works. Full production acceptance was not granted. The owner reported missing full navigation and space wasted by the separate TEST ONLY banner.

This correction reuses ProtectedAppShell with the same five V1 destinations. While Hidden is unlocked, Vault and Favourite continue using only HiddenRepository, HiddenSession and encrypted Hidden storage. Gallery, Browser and Settings are explicit exits: synchronously revoke Hidden and dismiss its content first, then request the existing V1 destination. MainActivity independently checks actual Primary session authority/key before accepting that request. The ordinary Gallery is the existing device-media Gallery; it does not combine Primary and Hidden records. Once outside Hidden, Vault/Favourite are Primary again, subject to existing Primary authentication. Reopening Hidden always requires its own PIN. Hidden secrets/media/URIs are not handed to the browser, settings or Primary repository.

Browser uses the unchanged existing WireGuard gating and network implementation; this change does not repair or claim live VPN service success. Settings is the existing application/Primary settings after leaving Hidden, not a new Hidden settings store. No Primary-to-Hidden transfer is added.

The extra TEST ONLY/Return to Primary row is removed. The existing Vault header's single-line subtitle carries Hidden · TEST ONLY, including collection/trash views. Primary defaults retain their original subtitle. Shared Scaffold owns system-bar/navigation insets without an additional outer safeDrawing padding. The existing Lock Vault button, Back and lifecycle revocation still exit Hidden. Fullscreen media viewing retains FLAG_SECURE and existing player disposal.

VPN + Tor is explicitly deferred. There is no Tor tab, Tor transport or Tor anonymity claim in1.5.

Focused tests cover all restored tabs, same-row bottom navigation, unobtrusive label, Gallery/Browser/Settings exits, removal of Hidden content and fresh PIN on reopening; the existing persistence, credential, revocation, video-release, picker and Primary-isolation regressions remain required. Actual Galaxy Fold folded/unfolded layouts, gesture/three-button insets, real VPN state and owner acceptance must be tested with the new APK. The package stays uk.co.traynor.privategallery.hiddenprototype. Use disposable media only. V2/main/PR59 remain untouched.
