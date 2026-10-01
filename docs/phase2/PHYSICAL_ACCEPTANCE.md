# Phase 2 owner physical acceptance

Status: **aggregate owner-reported PASS**, 2 October 2026 (Europe/London).

Owner confirms the exact signed candidate `e78bccb1e3ac9e17a7c8dfa0a84456c512b0bf5f`, Signed Device Acceptance Build #54, was installed and reports **"Green and it works."** Existing app/data remained in use; no uninstall/data clear is authorized or reported. This closes the requested focused physical acceptance as an aggregate report only. The checklist below is the requested scope, not a record of individual observations. No individual device, biometric, screenshot or backup observation is invented.

Accepted production candidate: `e78bccb1e3ac9e17a7c8dfa0a84456c512b0bf5f`.

- Exact-head push #474: [run 36937435472](https://github.com/traynor1987/Private-gallery-/actions/runs/36937435472), SUCCESS.
- Exact-head PR #475: [run 36937439593](https://github.com/traynor1987/Private-gallery-/actions/runs/36937439593), SUCCESS.
- Both passed the independent-domain and complete JVM tasks, lint, debug/AndroidTest packaging, frozen legacy corpus/asset and F1 vector checks, backup mutations/package policy, no-secret scan, WireGuard-only audit, Node tests, isolated Phase0 evidence JVM/lint/package tasks, and API36 instrumentation. Full Android suite: 172 tests PASS; separate targeted Primary slot admission suite: 10 tests PASS. The latter is not Phase0 Browser instrumentation.
- Reconciled local JVM evidence: 501 tests across 111 suites, zero failures/errors/skips. CI success is independently verified from completed exact-head runs and their job logs.
- Signed Device Acceptance Build #54: [run 36940495096](https://github.com/traynor1987/Private-gallery-/actions/runs/36940495096), SUCCESS.
- [Acceptance artifact 11200525476](https://github.com/traynor1987/Private-gallery-/actions/runs/36940495096/artifacts/11200525476); ZIP SHA256 `f097c1e8f9565aacf845642f13e928408c1f35229a49835c68c899c7d7e35031`.
- APK SHA256 `59150ea612111b4451f62ccf2450e76eb6031ed8cdbeb2ed47b903f9ca11321f`. Downloaded bytes and recorded checksum agree; `build-sha.txt` equals the accepted candidate.
- SDK36 apksigner cryptographic verification PASS: one RSA4096 signer; permanent certificate SHA256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`, matching independently verified owner-accepted Phase1 build #53. Recorded signature and actual verification agree.
- Package `uk.co.traynor.privategallery`, versionName `1.0.27`, versionCode `28`: same package/certificate and no version downgrade, suitable for in-place installation. Packaged backup exclusions and absence of retired runtime/model weights PASS.
- Owner physical acceptance: **aggregate owner-reported PASS**, reported 2 October 2026 (Europe/London). Owner confirms installing this exact candidate / Build #54 and reports **"Green and it works."** Existing app/data remained in use. No uninstall/data clear is authorized or reported. No individual test observation, hardware result or secret is inferred.
- Final recorded whole-candidate/scoped source reviews identify no remaining Critical/Important finding; no unresolved PR review threads. Primary crypto formats, immutable legacy fixtures and app version configuration are unchanged from the starting main.

**PHASE 2 RESULT: GO FOR PHASE 3 REVIEW**

All 42 matrix rows and 50 acceptance exit gates are closed on the accepted production candidate using their mapped automated/source evidence and the explicitly authorized aggregate owner PASS. This permits final Phase2 integration and verification only. Phase3 implementation, Hidden media migration/transfer and public release remain unauthorized. The merge SHA and terminal post-merge main CI are recorded in [PR #58](https://github.com/traynor1987/Private-gallery-/pull/58) after integration; GO is not a claim that an in-progress main run passed.

## Installation

Install IN PLACE over the existing Private Gallery using the permanent signer. Do not uninstall, clear data, migrate/re-encrypt Primary, deliberately corrupt owner data or alter Android biometric enrollment for testing. Do not share any PIN/recovery material. Use no sensitive or owner media in the new space; Phase 2 has no media import or transfer.

## Primary and ordinary presentation

- Existing Primary PIN and optional existing Primary biometric still unlock normally. Check a few existing images and a video, collections/Favourite/Recently Deleted, Browser and existing relevant AI/VPN/update settings without changing owner data.
- Before discovery, inspect Vault/Gallery, navigation/More/Settings, search/collections/trash, storage/backup/recovery/update/diagnostic screens, editor/import/AI/Create Image where normally used. No new space entry, status/count/storage breakdown/recovery notice, transfer action or notification is visible. Ordinary Hide Content remains a separate presentation setting.

## Discovery and independent authentication

- Wrong or incomplete About interaction reveals no new space.
- Complete the familiar sequence within 30 seconds: About Installed/details five times, Private Gallery/version interaction once, Installed/details four times. It reaches a neutral authentication flow only, with no contents/counts.
- If first setup is offered on this device, create a different 12–64 digit PIN and save its generated independent recovery key privately. Confirm possession by reentry. Do not send the key to the assistant. Setup should reach the minimal ready shell; Primary media must not appear.
- If a space already exists, use its independent PIN. Wrong PIN is rejected; Primary PIN does not authorize this space. Do not deliberately exhaust retries. The minimal shell has independent security/recovery controls and no media/import/transfer/browser/camera functionality.
- If the device supports the optional independent biometric slot, enable it from the authenticated shell, then check the normal eligible biometric challenge after discovery. PIN fallback remains inside that flow. Device-enrolled biometrics identify an enrolled biometric, not a unique person. After cold restart/security change or Every time policy, expect the independent PIN. Unavailable hardware must fail closed, never downgrade.

## Lock, lifecycle and privacy

- Lock and return: new-space controls/content disappear and Primary remains normal. Reentry requires the discovery sequence and independent authentication again.
- Background or screen-off from the shell with its default immediate policy: authority closes. Returning/reentry must not preserve unauthorized ready content. If safely checking a grace option, expiry must require reauthentication.
- During authentication/recovery/shell, screenshots are blocked and Recents shows no revealing preview where Android supports it. Returning to Primary must not flash protected frames. Protection deliberately stays latched for the Activity window after discovery.
- Restart/recreation starts without new-space authority or visible advertisement. Confirm Primary still unlocks and remains usable. Do not perform destructive reboot/enrollment changes merely to test invalidation; ordinary process restart is enough for this checklist.

## Owner response

Record PASS/FAIL and concise observations for the exact verified candidate. Include any unavailable optional hardware behavior; never include secrets, filenames or sensitive screenshots. Failures remain NO-GO until resolved and a new exact candidate is validated. No Phase 3 implementation is authorized by acceptance. After PASS, standing authorization permits final review, safe Phase 2 merge and green post-merge main verification only.
