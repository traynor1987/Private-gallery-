# Hidden navigation/layout acceptance correction

Owner physical test of CI588: Hidden opens, PIN setup works and adding media works. Full production acceptance was not granted. The owner reported missing full navigation and space wasted by the separate TEST ONLY banner.

This correction reuses ProtectedAppShell with the same five V1 destinations. While Hidden is unlocked, Vault and Favourite continue using only HiddenRepository, HiddenSession and encrypted Hidden storage. Gallery, Browser and Settings are explicit exits: synchronously revoke Hidden and dismiss its content first, then request the existing V1 destination. MainActivity independently checks actual Primary session authority/key before accepting that request. The ordinary Gallery is the existing device-media Gallery; it does not combine Primary and Hidden records. Once outside Hidden, Vault/Favourite are Primary again, subject to existing Primary authentication. Reopening Hidden always requires its own PIN. Hidden secrets/media/URIs are not handed to the browser, settings or Primary repository.

Browser uses the unchanged existing WireGuard gating and network implementation; this change does not repair or claim live VPN service success. Settings is the existing application/Primary settings after leaving Hidden, not a new Hidden settings store. No Primary-to-Hidden transfer is added.

The extra TEST ONLY/Return to Primary row is removed. The existing Vault header's single-line subtitle carries Hidden · TEST ONLY, including collection/trash views. Primary defaults retain their original subtitle. Shared Scaffold owns system-bar/navigation insets without an additional outer safeDrawing padding. The existing Lock Vault button, Back and lifecycle revocation still exit Hidden. Fullscreen media viewing retains FLAG_SECURE and existing player disposal.

VPN + Tor is explicitly deferred. There is no Tor tab, Tor transport or Tor anonymity claim in1.5.

Focused tests cover all restored tabs, same-row bottom navigation, unobtrusive label, Gallery/Browser/Settings exits, removal of Hidden content and fresh PIN on reopening; the existing persistence, credential, revocation, video-release, picker and Primary-isolation regressions remain required. Actual Galaxy Fold folded/unfolded layouts, gesture/three-button insets, real VPN state and owner acceptance must be tested with the new APK. The package stays uk.co.traynor.privategallery.hiddenprototype. Use disposable media only. V2/main/PR59 remain untouched.


## Verified test candidate

Application source **c5d65136f63c723ee6da6552c2137d02ef9d21a1** passed [CI589](https://github.com/traynor1987/Private-gallery-/actions/runs/39047438504): JVM tests, lint, both APK builds and **161 Android tests,0failures/errors/skips**. The downloaded ZIP digest, actual JUnit XML, APK manifest and v2 signature/content digest were independently inspected. See evidence/ci589-summary.txt and ci589-apk-inspection.json. Subsequent evidence commits change documentation only.

[Download corrected TEST ONLY APK and reports](https://github.com/traynor1987/Private-gallery-/actions/runs/39047438504/artifacts/12106504127). Extract outputs/apk/debug/app-debug.apk. APK SHA256:f67a6b2c09893e80dca253958e5252c5c001f9f05f33733d8f6f8fc8da5c30d3. Package remains uk.co.traynor.privategallery.hiddenprototype; Android Debug certificate SHA256:bf4433e34148c820231857254f4a499d7a135ebd0aa8e6dbbba8eec8e95f4244. This certificate differs from CI588, so an in-place update of that older test app will be rejected. Never uninstall production V1. Preserve any test app containing media you need; a fresh install is appropriate only when its contents are confirmed disposable.

Independent review found no material issue. Its requested real Activity guard coverage is now passing. The corrected APK has not yet passed owner Fold acceptance. No production release, real-media approval, installation or migration is claimed. Follow PHYSICAL_TEST_CHECKLIST.md for the next owner test.
