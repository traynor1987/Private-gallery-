# Private Gallery 1.7.6.2 Fold acceptance

Install the supplied original-certificate code43 APK as an update over your existing Private Gallery. Do not uninstall or clear app data. Android's installer must offer an update for uk.co.traynor.privategallery. Physical acceptance has not been performed by development CI.

- Unlock Primary and Hidden with their existing independent credentials; check existing images, videos and collections after closing/reopening the app.
- Switch app light/dark themes. Check Choose from Vault, delete/copy/move, collections, security/settings and editor dialogs on cover/inner screens, landscape and large text; confirm buttons and keyboard remain accessible.
- Test wrong/correct destination PIN and cancellation during a cross-vault copy/move. Confirm only verified moved sources leave the source gallery and the destination item is readable.
- Open a website upload picker before and after moving an eligible image into Primary. It should show the new item on reopening, require selection/confirmation, and cancel cleanly. Vault-only restricted edits intentionally remain excluded from website uploads.
- Open Samsung editor for a disposable test image. If direct private-URI editing is unsupported, use the guided Gallery export, edit/save, return and manually select the result. Save as New first; check the originating vault and original before authorising public-copy cleanup. Samsung caches/cloud copies are outside app cleanup.
- Check Primary browsing, themed website prompts, Hidden session history/bookmarks, lock and mode change. Hidden VPN/Tor keep the existing secure GET-only restrictions; interactive POST/JavaScript compatibility is deferred.
- Check Panic Exit and Hide Content, then restart and confirm both vaults remain accessible.

The production pipeline must prove original-key synthetic upgrades from both documented possible baselines (code41 and42). Those fixtures do not inspect the owner's physical phone or actual encrypted files; the checks above complete owner acceptance.
