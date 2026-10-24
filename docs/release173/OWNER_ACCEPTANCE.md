# Private Gallery1.7.3 — Galaxy Z Fold acceptance

Recommended acceptance build:1.7.3/code36, packageuk.co.traynor.privategallery. Install as an update over installed1.7.2/code35. Do not uninstall or clear app data. Automated/emulator checks passed; physical acceptance is not yet declared.

- [ ] Full-screen numeric keypad fits cover/inner screens, portrait/landscape and Fold open/close; digits remain masked and no soft keyboard appears.
- [ ] Existing Primary PIN works. Existing variable-length credentials retain the Unlock confirmation because their length was not persisted.
- [ ] Existing Hidden PIN works through the established discreet entry, without first entering Primary PIN.
- [ ] Enabled Primary fingerprint prompts automatically and opens Primary only.
- [ ] Fingerprint cancellation leaves PIN usable without repeated prompting; temporary lockout/unavailable/enrolment changes keep secure PIN fallback.
- [ ] Hidden never offers or accepts biometric unlock; cancel the neutral Primary biometric prompt to use the discreet PIN route.
- [ ] Incorrect PIN clears entered digits and preserves existing attempt protection; no double-PIN sequence.
- [ ] After Hidden lock/device lock/process restart, entry is neutral and reveals no prior Hidden content or state.
- [ ] Existing Primary and Hidden encrypted photos/videos, collections, favourites and settings remain accessible.
- [ ] Normal Phone Gallery offers all6orders; Date Added — Newest First is the initial default.
- [ ] Download/import an old photo today: it appears first by Date Added while original Date Taken remains separate.
- [ ] Copy/move into the other vault: destination addition time is new; known capture time is preserved.
- [ ] Date Taken, both filename directions and stable duplicate-date ordering work; missing legacy metadata uses documented fallback.
- [ ] Primary and Hidden sort preferences are independent and survive lock/restart; Phone Gallery preference persists.
- [ ] Filtering, selections and collection removal keep the active ordering; scrolling remains usable with the owner's collection size.
- [ ] Normal/Black-Screen/Reading View cameras, video audio/lock stopping, AI Edit/Seedream, browser downloads, Primary WireGuard and Hidden Tor Only/VPN Only still work.
- [ ] Blue vault launcher icon and compact search remain correct; signed update preserved existing data.

VPN+Tor remains unavailable. No face recognition or visual indexing was added. Report any failure with vault context, Fold screen/orientation and exact action; do not send private PINs or media. Production GO requires owner confirmation of these physical checks.
