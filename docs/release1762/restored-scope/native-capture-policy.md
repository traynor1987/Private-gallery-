# Native Hidden capture and theme preservation

Source implementation checkpoint, 28 October 2026. Android execution is pending; this is not a release or signing approval.

The prior Main-hosted Hidden browser followed Hidden's explicit screenshot opt-in while always excluding Recents. The initial native child instead forced capture off and independently read the app theme from cross-process SharedPreferences. That ignored a completed owner control and could observe an older theme after an asynchronous preference write.

## Authoritative policy

`HiddenVpnBrowserBroker` now appends capture permission and encoded selected app theme to authenticated CLAIM/CHECK replies. Its optional constructor defaults are capture off and system theme. Production Runtime supplies Main's in-memory Hidden-selected/unlocked/Hidden-opt-in predicate and selected app theme. The capture predicate never resolves the Primary screenshot opt-in. No intent value or child-side preference read can enable capture.

Worker-thread CHECK replies do not apply their older presentation state. They coalesce a Main-thread re-check; a fresh pre-draw authority check synchronously refreshes capture policy before clearing any secure flag. The existing child security diagnostic remains the same JavaScript/pinned-network pair. A separate same-capability presentation diagnostic reports only secure/curtain/theme facts, never browsing data or credentials.

## Native window handling

`HiddenBrowserCaptureGuard` reuses the existing `PrivacyWindowTracker` with actual Activity/Dialog/Popup ownership checks. Startup is secure and opaque. Recents screenshots are disabled throughout. Pause conceals the Activity and secondary windows, suppresses their accessibility content and secures all windows before returning. Focus loss secures immediately; owned Dialog handoffs retain foreground eligibility, and IME focus is handled by the same owned-window tracker rather than treating every Activity focus loss as background.

Only resumed, owned-focus, live-authority content with a matching committed composition epoch and subsequent pre-draw can uncover. Hidden opt-in can then clear the Activity's secure flag; an explicitly secure secondary credential window remains secure. Failure to apply protection terminates the native lifetime. Closing retains curtains through native process death instead of exposing a still-live secondary window during asynchronous cleanup. Existing synchronous authority revocation/process termination is unchanged.

## Added checks (unexecuted Android tests)

`ui.HiddenBrowserCapturePolicy1762Test`:

- `defaultOffAndAuthenticatedHiddenOptInRequireFreshOwnedFrame`
- `pauseCoversActivityAndOwnedDialogAndResumeNeedsAnotherFrame`
- `hiddenPolicyCannotInheritPrimaryOptInOrLockedHiddenCredentials`
- `parentSelectedThemeIsAuthoritativeAndIndependentOfHiddenCaptureOptIn`
- `hiddenOptInNeverClearsAnExplicitSecureCredentialWindow`
- `actualImeOverOwnedPromptDoesNotMasqueradeAsBackground`

These use actual Android windows and fresh draw dispatch. The IME case requires and observes a real keyboard window, and restores its settings. No network or credential-store bypass is added to production.

`core.browser.hidden.HiddenNativePresentation1762Test.nativeChildUsesParentThemeAndHiddenCapturePolicyWithoutCrossProcessPreferenceRead` uses the real owned-VPN fixture and unchanged native child. It checks default-off, explicit Hidden opt-in, dark→light authoritative theme updates, opt-in disable, and revocation while opt-in is active. It does not claim success before instrumentation executes.

Local dependency-path, tracked-secret and whitespace checks pass. No Android SDK/emulator is available locally. Physical Samsung/Fold screenshot, Recents, IME and window-transition acceptance remain required.
