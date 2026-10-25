# Private Gallery 1.7.6 — Galaxy Z Fold owner acceptance

Install only the final original-key signed acceptance APK over1.7.5.2/code40. Never uninstall or clear app data. Emulator tests do not establish physical acceptance.

- [ ] Signed in-place upgrade preserves both PINs, encrypted media, collections and settings.
- [ ] Primary fingerprint works; Hidden always requires its independent PIN.
- [ ] Long-press, tap, drag, Select All and Deselect All use the displayed vault/filter/collection scope.
- [ ] Date Added/Date Taken inclusive ranges and file-size selection show correct matching counts.
- [ ] All/Photos/Videos/Favourites/Large Files/Recently Added filters preserve sorting.
- [ ] Bulk Copy/Move in both directions shows correct processed and final verified/failed totals.
- [ ] Cancel a synthetic batch: originals without verified completed moves remain.
- [ ] Close/reopen during a synthetic batch, authenticate and resume from Transfer History without duplicate completed operations.
- [ ] History stays invisible in Primary until Hidden authentication and can clear finished records without deleting media.
- [ ] Bulk collection Add/Remove preserves originals and multiple membership; counts remain correct.
- [ ] Change collection cover; Pin/Unpin; restart and verify Primary/Hidden independence.
- [ ] Edit photo → Edit with Samsung Gallery: consent warning appears and original remains encrypted.
- [ ] If Samsung accepts the content URI, use Object Eraser where available and save its result.
- [ ] If direct editing is unsupported, use explicit manual export, edit in Samsung Gallery, then select the edited result.
- [ ] After each external screen, reopen the originating photo and choose Edit with Samsung Gallery again; authenticate first if locked.
- [ ] Preview and Save as New return to the originating vault; Replace Original requires explicit confirmation and retains recovery copy in Recently Deleted.
- [ ] Hidden locking never sends its result into Primary; Panic cancels the pending handoff.
- [ ] App-owned temporary handoff disappears after import/cancel or expiry; separately approve/check public-copy cleanup. Delete document-provider copies manually.
- [ ] Primary WireGuard and Hidden Tor Only/VPN Only browsing and downloads still work.
- [ ] Cameras, Reading View, encrypted video playback, AI Edit/Combine Photos, metadata, screenshots and privacy/Recents controls still work on inner and cover screens.

Samsung integration deliberately does not assume a returned-result contract. Android ACTION_EDIT specifies no output: https://developer.android.com/reference/android/content/Intent#ACTION_EDIT . Runtime resolution is checked; export/edit/explicit result selection is the supported fallback. Samsung-specific handler behaviour and Object Eraser availability require this physical test. Samsung caches, cloud processing and backups cannot be erased or verified by Private Gallery.

Temporary handoff is a read-only random PNG content URI, expires after15minutes, and never exposes encrypted vault paths or filenames. A process death drops the in-memory origin ticket: reselect the original and saved public result after authentication. Existing vault-only egress restrictions remain in force. No VPN+Tor combination is newly enabled.
