# Physical owner acceptance — Copy/Move and independent Gallery

Use only the new TEST ONLY APK on a disposable device or emulator, or an existing test installation whose signing certificate matches. Do not uninstall a test installation containing valuable media to bypass a signing mismatch. Do not install or replace production V1. All deletion tests must use disposable synthetic media.

1. Phone → Primary: Copy a photo and video; reopen/decrypt both; originals remain. Move different disposable media; approve Android's deletion prompt; verify encrypted copies and removal of those originals. Repeat with deletion denied/cancelled: copies remain and the app reports originals retained.
2. Hidden active: repeat Phone Copy/Move to Hidden using the independent Hidden PIN. Verify the selected destination, original retention/deletion outcomes, larger media playback and seek. Confirm Primary collections/favourites are unchanged.
3. Hidden → Gallery → Primary: wrong PIN exposes no Primary media. Cancel returns to Hidden and does not accept a late biometric result. Authenticate correctly, browse Primary, then select Vault and Favourites: both still use Hidden. Explicit Return to Primary still requires Primary authentication.
4. Select Primary media in Gallery: Copy to Hidden retains Primary. Move to Hidden requires confirmation; each verified Hidden copy corresponds to a Primary original in Recently Deleted. Restore one such original to prove recovery. Repeat duplicate selections; inspect counts and both collections/favourites.
5. During transfer, switch screens, background/lock the app, and interrupt/restart it. No automatic source deletion may occur on restart. Verify completed copies and remaining originals. Repeat with low storage using disposable data.
6. Exercise Android deletion approval, denial, Back, and Activity recreation while its prompt is open. Verify outcomes after returning/unlocking. Android owns an already-launched deletion prompt; do not edit the selected source in another app while approving it.
7. Check screenshots on/off, existing browser playback, VPN connectivity and fail-closed behavior. Restart and verify encrypted persistence.

Record the exact APK SHA-256 and pass/fail for each check. Physical acceptance remains incomplete until the owner confirms the corrections. Only then may a new production candidate be built, with its own signing/package/version/upgrade verification and a separate explicit authorization to install it.
