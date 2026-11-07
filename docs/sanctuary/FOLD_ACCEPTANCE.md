# Nova Sanctuary 1.0 · Galaxy Z Fold acceptance

Record the candidate SHA, APK SHA-256, Android API, One UI build and whether each check passed. Use a separate, isolated test build first. A production upgrade check uses the archived original-key Code 65 APK on a disposable emulator and does not clear its seeded data.

| Check | Expected result |
| --- | --- |
| Folded and unfolded layout | Mode tiles, countdown, approved launcher, settings and overlay controls fit at normal and enlarged text sizes. |
| Mode presets | Gaming starts at 120 minutes, Learning at 60, Self Care at 90, Work at 240, and Panic at 720. Edited durations and separate allowlists survive reopening. |
| Android grant setup | Usage Access, overlay and notification state are truthful. A denied permission leaves the countdown available and Android protection paused. |
| Block WhatsApp | Leave WhatsApp off the allowlist. Open it from launcher and Recents. The overlay covers its content without closing the app. Check the cover display and unfolded display, then switch between them during a block. |
| Approved launch targets | From the blocking overlay, open NovaOS, an approved entertainment app and each installed approved work app. Return to WhatsApp and verify the overlay returns. |
| NovaOS launcher | Approved internal apps open. Unapproved apps are rejected from the drawer, search, Recents, and Store launch action. Recovery and lock routes remain reachable. |
| Multi-window, lock screen and system UI | Check split screen, recents, Home, keyguard, permissions, app info and phone. No overlay prevents Android controls or an emergency call. Do not place a test call to emergency services. |
| Process and power | Close NovaOS normally. Check the timer and monitor. Use the emulator tests for ordinary process death. On the Fold, test reboot, battery restrictions, notification disable, Usage Access revocation, overlay revocation, Active apps Stop and Android Force stop. Protection status must match what is actually running, and the timer must not gain extra time. |
| Panic check-in | Show the check-in near expiry. A response alone leaves the deadline intact. A selected extension changes the deadline and is reflected in the session. |
| Early exit | Overlay has no end button. In NovaOS → Home settings → Sanctuary, incorrect input fails and the exact phrase opens a second confirmation. Cancelling keeps the session. Confirming stops it. |
| Trusted contact | The Phone shortcut opens the dialer. The UI does not promise WhatsApp Favourites isolation. |
| Space isolation and data | Verify Primary and Hidden unlock separately, existing Vault entries and app sessions remain, and no Hidden metadata appears in overlay, notifications or Primary Sanctuary. Check Panic Exit still locks the Vault as expected. |

Physical sign-off must identify the tested APK and explicitly cover the real WhatsApp block and approved-app switching. A successful emulator run alone does not satisfy the Fold release gate.

## Optional DND and screen pinning

- In a disposable or reversible session, set Android DND exceptions for a trusted test contact and alarms before activating Sanctuary's optional DND. Verify the contact and alarm still come through, then end Sanctuary and compare the original Android settings and independently active rules.
- Repeat with Notification Policy Access denied, and again while revoking access during the session. The local timer and Guardian remain available according to their separate grants.
- Change DND manually during a Sanctuary session and verify that ending Sanctuary leaves your deliberate Android changes in place. Verify Android Settings can disable DND after a Force stop, even if Android deferred or cancelled scheduled cleanup.
- Confirm Work starts with the pinning offer off. In another mode with the offer enabled, tap the existing Android pinning request, inspect the system confirmation, unpin with the system gesture, and verify the same session and deadline continue. Check an approved external app with pinning off.
