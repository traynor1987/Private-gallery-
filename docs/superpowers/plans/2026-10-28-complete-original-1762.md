# Complete original Private Gallery 1.7.6.2 Implementation Plan

Goal: Finish the reinstated owner specification on the existing release branch, then deliver an original-key code44 acceptance APK.
Architecture: Preserve existing encrypted vaults, credentials and production identity. Replace GET interception for interactive VPN browsing only through a supported isolated browser transport, with explicit revocable authority and no direct fallback; retain strict Tor routing and JavaScript-off defaults.
Tech stack: Android17/API37, Kotlin/Java, Compose, Android WebView, official WireGuard, existing Tor binaries, GitHub Actions.
Spec: Owner directive of 28 October 2026 in this task; it supersedes the historical deferral and code43 release-complete statements.

## Global constraints
- Displayed version1.7.6.2; versionCode44 explicitly authorised by final owner instruction.
- Existing branch release/1.7.6.2-ui-browser-polish; no replacement release branch.
- Package uk.co.traynor.privategallery; production certificate94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.
- Never uninstall, clear owner data, generate signing keys, alter encryption/PIN storage, bypass Vault-only, or expose Hidden files in Primary.
- Do not sign until restored scope, relevant Android/security tests and independent review pass. Physical Fold acceptance remains owner-performed.

## Tasks and verification
- [x] Recover branch HEAD5b6be831 and read recovery, security review, final handoff. Check GitHub queued/running jobs. No jobs observed at recovery; external unpushed sessions cannot be enumerated.
- [ ] Inspect previous code43 artifact against recorded digest/certificate; retain as baseline, never redeliver it as final.
- [ ] Update app/build.gradle.kts, scripts/release_configuration.sh and strict upgrade fixture for44. Test default baseline43 and explicit41/42, reject other paths. Preserve historical43 evidence.
- [ ] Reopen recovery/handoff and remove obsolete deferral; gate signing to new exact-source evidence, code44 and41/42/43 upgrade proofs. No signer access for old frozen source.
- [ ] Audit every modal/popup/sheet/overlay with source inventory and per-entry verification status. Fix uncovered theme, keyboard, scrolling, focus, Fold/large-text defects; run affected Compose tests.
- [ ] Trace transferred-item eligibility across both vaults, encryption/index/COMPLETE/vaultOnly/MIME/thumbnail/query lifetimes. Reproduce any remaining defect before fixing. Test copy/move both ways and explicit egress confirmation/restriction explanations.
- [ ] Audit cross-vault numeric-keypad use and wrong/correct/cancel/lock/expiry cases in both directions. Preserve independent PINs and Hidden PIN requirement.
- [ ] Complete Samsung editor discovery/direct/fallback/reimport/verified replacement/cleanup. Test intent narrowing, lifetime and encrypted import; retain explicit physical Samsung acceptance.
- [ ] Design supported VPN interactive process/transport against official Android APIs. Verify WebView process socket/DNS routing, request bodies, service workers, redirects and cancellation; do not merely enable JavaScript.
- [ ] Implement shared polished Primary/Hidden UI components while keeping independent browser data. Test tabs/settings/history/bookmarks/downloads/uploads/dialogs/desktop mode and error/loading states.
- [ ] Implement VPN transport and strict Tor switching with fresh storage and revocation. Test POST/body/multipart/JS/forms/redirects, DNS isolation, route loss, no fallback and mode-transition races on Android17. Historical Tor timeout failure remains open and transport changes invalidate its previous exclusion.
- [ ] Fold cover/inner portrait/landscape/light/dark/large-text/keyboard/scroll verification, including transition acceptance.
- [ ] Independent complete-source security review. Resolve every Critical/Important finding before signer access.
- [ ] Freeze exact SHA, run mandatory relevant gates, verify original-key41/42/43→44 in-place upgrades with independent credentials/ciphertext/restart, sign original certificate, verify package/signature/digest and publish one final acceptance APK.

## Review focus
Process-bound sockets and DNS may behave differently in Chromium subprocesses; verify actual WebView traffic, not only native HTTP. Revoked IPC/upload completion must not gain new authority. VPN→Tor transitions must leave no pending route or reusable storage. Restricted copies must remain visible as restrictions without becoming uploadable. Editor results must return to the exact originating vault after lock/cancellation.

## Execution environment evidence
Local clone succeeds, but direct Git push lacks credentials. Connected GitHub API advertises push permission and blob creation succeeds; use API with expected HEAD leases. Local Gradle cannot download gradle9.5.0 (Network is unreachable); no Android SDK/adb or /dev/kvm observed. Existing GitHub CI is the available Android execution path; validate that write/trigger path before relying on it. No new Android result or signing is claimed.

## Tor native parity completion

The Tor GET interceptor still cannot submit native form/upload bodies. This is a restored-scope gap, not closed by UI parity alone. Keep existing Tor binaries. Implement an app-owned full-route/DNS-sink VPN network for a fresh Tor browser process; route HTTP/CONNECT through native Tor SOCKS unresolved names. Configure Tor's supported outbound Socks5Proxy to a separate session-authenticated protected numeric relay bridge so missed browser/UDP/DNS paths cannot reach the physical network. Before activation verify the exact packaged Tor version, guard/directory proxy coverage, permission cancellation, v4/v6/DNS sink, native forms/uploads, route revocation, fresh profiles, mode-switch ordering, and existing panic/quick-lock behavior. This contained routing work is required within1.7.6.2/code44.
