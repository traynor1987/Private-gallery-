# Galaxy Z Fold — Private Gallery1.7.5/code38

Pending owner physical testing. Install only the final verified original-key APK in place; never uninstall/clear data. No physical acceptance is inferred from CI.

- [ ] Existing Primary/Hidden credentials and encrypted photo/video media remain accessible after upgrade.
- [ ] Primary fingerprint works; Hidden still requires its independent PIN only.
- [ ] Pinch zoom, tap-centred double-tap zoom, pan while zoomed, fit-scale paging and swipe-down close work on cover and inner screens.
- [ ] Sorting/filter order and counter stay correct when paging; adjacent images load smoothly.
- [ ] Full-screen controls can be hidden/revealed; Details/Favourite/Collection/privacy export work in correct vault.
- [ ] Video double-tap middle-left/right seeks10seconds (clamped at ends), native timeline scrubs, play/pause,0.5/1/1.5/2× speeds, aspect/rotation/buffering/error controls work.
- [ ] Returning to a video within the same open viewer resumes; closing/locking/recreating clears session-only positions.
- [ ] Settings > Security > Privacy Shield explains opt-in Panic Exit; three-finger touch and optional Quick Lock work without accidental ordinary photo activation.
- [ ] Panic during Primary/Hidden photo/video viewing hides content immediately, stops audio, returns neutral locked entry and never reopens Hidden automatically.
- [ ] Panic during normal/black-screen/Reading View camera safely stops recording; unlock originating vault and verify retained/recovered capture.
- [ ] App switching, notification shade, gesture navigation, Recents and Fold open/close do not flash protected content. Screenshot preferences remain effective when focused.
- [ ] Background/lock/recreation requires correct independent reauthentication. No metadata in lock-screen notifications.
- [ ] Primary WireGuard and Hidden Tor Only/VPN Only still work; VPN+Tor remains unavailable.
- [ ] Camera, AI Edit/Combine Photos, metadata editing/GPS-removing export, storage/sorting/collections remain correct.

Limits: resume is memory-only within the open viewer; previews/rendering are bounded; clean export remains JPEG/non-animated PNG as in1.7.4. A metadata membership operation explicitly requested before Panic may finish afterward; Panic does not delete/rollback media and does not guarantee protection against coercion. Physical checks remain pending until owner reports results.
