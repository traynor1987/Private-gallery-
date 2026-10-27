# Android17 signed candidate owner acceptance

Use the complete [Samsung editing/settings checklist](../OWNER_ACCEPTANCE.md) together with these checks.


- [ ] Confirm actual device Android17/API37 or later before installation; no physical API read was performed by CI.
- [ ] Existing Primary/Hidden PINs and encrypted photos/videos remain accessible after in-place upgrade and restart.
- [ ] On both inner and cover displays, play a representative real video with audio; check continuous playback, seeking and playback speed.
- [ ] Trigger Panic during playback: audio/video stop, no sensitive frame, locked entry; fresh unlock does not automatically resume the revoked player.
- [ ] Native date/time selection, Save, Cancel and Restore Original Date behave correctly without changing photo pixels, separately in each vault.
- [ ] Test Samsung Gallery/Object Eraser fallback and verified encrypted reimport using synthetic/disposable copies first.
- [ ] Check Primary WireGuard, Hidden Tor and Hidden VPN on the actual phone; current patch carries prior verified browser evidence and does not claim fresh live Tor testing.

Candidate exact source33e9669daeb3bf5a3d2cb0aae4d3480fe4b538c2; APK SHA256c7da24aba6f4c8d5f82d4fad8cd034cd65647812e627ac70fde16b61cef3867f. Historical full/Tor timeout remains FAILED under the approved risk-based carry-forward decision. Owner physical acceptance is pending.
