# Private Gallery 1.7.6.2 code44 — final signed owner-acceptance delivery

29 October2026. **Ready for physical owner acceptance.** This new code44 APK supersedes the incomplete code43 acceptance candidate. Physical Samsung Galaxy Z Fold acceptance has not been performed. No original feature is deferred to another release.

[Download the final signed code44 APK](https://chatgpt.com/library?file_id=libfile_74b87e9b4e4c8191baf548be30bfafde&account_id=personal).

## Frozen identity and exact evidence

| Item | Verified result |
| --- | --- |
| Application source | `1ac9ebf7f5c6e4665ca3416e0e37b78bc52131e9` |
| Application tree | `72d560be635d970cfd42902d00a34dd27b2a8890` |
| Release-control commit | `da372e1b57ac17638986e8efa272d6ab76138ddb` — selects frozen source; later documentation does not change APK bytes |
| Identity | `uk.co.traynor.privategallery`, Private Gallery1.7.6.2, versionCode44, minimum/target API37 |
| Final APK | `Private_Gallery_1.7.6.2_code44_FINAL.apk`,104880682 bytes |
| APK SHA256 | `b9133370af1996dc3f16caa5c8a986392197cb2562a06e78cf1600ff15356702` |
| Original production certificate SHA256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |
| Signature | Android apksigner PASS; downloaded APKv2 RSA4096 signature and every signed content digest independently verified with the original certificate |
| Focused verification | [Run40816475978](https://github.com/traynor1987/Private-gallery-/actions/runs/40816475978), job123258613728, SUCCESS |
| Focused artifact |12872024835; ZIP SHA256 `c65fafbe2a863dce4d9679dc22bdaf2573aed1e562dc5d0a41ed732e5d7b653b` |
| Production signing/upgrades | [Run40818215332](https://github.com/traynor1987/Private-gallery-/actions/runs/40818215332), job123264234281, SUCCESS |
| Final APK/evidence artifact | [12873449331](https://github.com/traynor1987/Private-gallery-/actions/runs/40818215332/artifacts/12873449331); ZIP SHA256 `c08aabf8e3b04ef9fd4874ce8441490aacd3795b0d54b37e974be57b3ec47b1d` |
| Independent review | [FINAL_SECURITY_REVIEW.md](FINAL_SECURITY_REVIEW.md): APPROVED_FOR_SIGNING for exact frozen source;0 Critical/0 Important open; report SHA256 `80de5172c4c02f1636ece784ffc771df5bd06a05a59c8f8318810313d230ce0d` |

## Original requirement inventory and proof

| Original requirement | Completed implementation | Verification and limits |
| --- | --- | --- |
| Every app-owned modal | Shared selected light/dark blue-grey surfaces, rounded corners, typography, spacing, accessible actions/selection, keyboard and bounded scrolling. Includes vault chooser, copy/move/PIN, collections, import/export/delete/transfers, Samsung editor, browser uploads/downloads/script/permissions, camera/AI/privacy/security/backup/errors. System permissions remain system-owned. | [Complete preserved inventory and test mapping](restored-scope/modal-verification-map.md), [current constructor reconciliation](restored-scope/current-modal-reconciliation.md), [native additions](restored-scope/native-child-modal-inventory.md). Source-audited all active families;93 original identifiers plusR94–97 remain traceable. Unchanged passing Main14, modal13, media4, browser10, native22 and editor6 component evidence reused. Mapping explicitly distinguishes untested variants and unreachable legacy dialogs; it does not claim every physical state was exercised. |
| Independent cross-vault authentication | Samsung-style numeric entry for both directions, copy/move, wrong/correct PIN/cancel/expiry/locking. Primary may use authorised biometrics; Hidden always requires its own PIN. Credential and encryption formats preserved. | Reused reviewed PrimaryCopyAccess20, HiddenRestoreAccess24 and related authentication/dialog cases; [vault/editor review and source mapping](restored-scope/complete-vault-editor-review-6c7b.md), final consolidated review. |
| Transferred-media upload visibility | Completed destination ownership/index lifecycle, typed MIME/extension handling,64-item paging beyond500, current-request generations, loading/thumbnail states and explicit exclusions. Authenticated vault-only selection with separate egress confirmation; Hidden cannot appear in Primary. | Final exact-source `HiddenUploadPickerPaging1762Test#actualTransferredLastItemBeyondFiveHundredSurvivesCancelReopenAndTypedPages` PASS; actual encrypted501st transferred-item selection/cancel/reopen/upload. [Picker audit](restored-scope/picker-audit.md). |
| Shared Primary/Hidden browser interface | Shared address/navigation, refresh/stop, tabs/bookmarks/history/downloads/upload/settings, loading/errors, website dialogs, find, desktop/mobile and selected theme; independent encrypted metadata and browser sessions. | [Parity implementation](restored-scope/browser-parity-completion.md), [library metadata](restored-scope/library-metadata-parity.md); unchanged shared UI evidence plus final native live and Main lifecycle cases. |
| Interactive Hidden VPN | Isolated non-exported child process admitted once by current authenticated owner; process bound to owned VPN before WebView. Native JavaScript, DOM/forms/POST/request bodies, workers/WSS, cookies/consent and actual encrypted-file uploads through authenticated HTTP/CONNECT transport. | Final `HiddenVpnLive1762Test#nativeChildPostsFormsUploadsAndRevokesOnOwnedVpn` and NetworkLoss case PASS. Actual packet/body/DNS/STUN/revocation verifier PASS. No direct-network fallback; private TLS remains end-to-end. Seekable encrypted upload FD fails closed after revocation, including held duplicates. [Architecture](restored-scope/vpn-architecture.md). |
| Hidden Tor | Same polished native browser; JavaScript disabled by default, unchanged packaged Tor binaries, private Unix SOCKS origin resolution, full-UID v4/v6/DNS sink and protected numeric relay bridge. | Final `NativeTorIsolation1762Test#packagedTorNativeFormsUploadsDownloadsAndNetworkSink` PASS and Tor server/packet verifier PASS; actual form/multipart/download, cookies, DNS/UDP/v4/v6 sink, Tor death and revoked-FD EIO assertions. [Tor implementation](restored-scope/tor-parity-implementation.md). |
| Independent storage, mode changes and lock | Primary is independent of Hidden; generation-specific WebView directories, separate encrypted mode library, process retirement before reuse, secure VPN↔Tor transitions, stale capabilities refused, roots cleaned. | Both final `HiddenMainBrowserLifecycle1762Test` methods PASS: actual Main entry/independent Home lock policies; VPN→Tor→VPN retires old processes/cookies. NetworkLoss PASS; consolidated audit covers process/UID/epoch leases and profile cleanup. |
| Samsung Gallery editing | Compatible EDIT handlers with Samsung priority; private direct handoff where supported; guided temporary public export/open/edit/return/select fallback; durable encrypted originating-vault receipt; fresh origin authentication and encrypted verified reimport. Save as New default, separately confirmed replacement, idempotent recovery and owned-copy cleanup. | Reused actual editor lifecycle, encrypted reimport/recreation/recovery and UI cases mapped in final review. [Samsung workflow](restored-scope/samsung-implementation.md), [recovery](restored-scope/editor-recovery-completion.md). OEM callbacks, Samsung AI and cloud behavior remain physical acceptance. |
| Fold/accessibility | Bounded/wrapping dialogs, scrollable long lists, reachable actions, shared adaptive browser panels, keyboard handling and retained WebView redraw across configuration changes. | Actual cover/inner/landscape resized native viewports each perform real HTML touch and fresh HTTPS POST in final live case; existing light/dark/large-font/IME component evidence reused. Physical hinge, OEM keyboard and TalkBack remain owner checks. |
| Encryption, privacy and upgrade | Original package/key/certificate and independent credentials retained. Revocation and temporary-file cleanup fenced; original media encryption formats unchanged. | Original-key synthetic41→44,42→44,43→44 each PASS on Android17/API37, SELinux Enforcing; encrypted Primary/Hidden media, collections/settings/VPN and independent credentials verified after install-r and force-stop/restart. No uninstall/data clear. |

## Focused verification and security disposition

Final focused run: host checks, compilation, JVM and lint PASS; exactly six selected existing Android17 methods executed once each, zero failures/errors/skips. They cover transferred-media paging, native Tor, native VPN interactive traffic, owned-network loss and two actual Main lifecycle/mode-switch paths. VPN and Tor packet/body/DNS/fail-closed verifiers PASS. The evidence archive embeds exact frozen source and exit-status0.

Unchanged authentication, transfer, modal and editor evidence was independently mapped from earlier actual passing methods; earlier workflows containing failures remain recorded as failures. Test counts alone are not acceptance. No unrelated full camera/AI/video suites were repeated. Development push triggers exclude this release branch; the focused marker and production acceptance marker are separate, and useful running jobs are not cancelled on later pushes. One signing/upgrades workflow produced the delivered APK.

The consolidated independent review approved the complete final source with zero Critical/Important findings. Its final disposition supersedes its clearly labelled historical pending sections. The later release-control change only supplies exact-source approval and pins signing; it makes no application or security-boundary modification.

## Upgrade proof and install

| Installed baseline | Exact baseline source | Same final candidate |
| --- | --- | --- |
|1.7.6/code41 | `180a7ce7048f56371d1e0566b6333de65fd1aa75` | Original certificate, install-r, seed/verify/restart PASS |
|1.7.6.1/code42 | `33e9669daeb3bf5a3d2cb0aae4d3480fe4b538c2` | Original certificate, install-r, seed/verify/restart PASS |
|1.7.6.2/code43 | `234d2d5c688a3f598408eebca24275757a40b8e8` | Original certificate, install-r, seed/verify/restart PASS |

Install code44 as an update. **Do not uninstall or clear app data.** These are controlled synthetic upgrade proofs, not inspection of the owner's phone or files. The old code43 APK is not this final candidate.

## Short physical Fold acceptance and genuine limits

Follow [OWNER_ACCEPTANCE.md](OWNER_ACCEPTANCE.md). At minimum: update over the installed app; unlock both vaults and reopen existing encrypted media after restart; cover/inner/fold/rotate/light/dark/large-text/keyboard; copy/move both directions and reopen the website chooser; VPN JS/form/upload/download then Tor JS-off and secure switching/lock; Samsung edit→Save as New→encrypted originating-vault reimport and temporary-copy cleanup.

Samsung Gallery/AI support depends on installed OEM editors/services. Automatic callbacks are not promised; the guided export/select-result fallback remains available. App-owned temporary copies are cleaned, and public copies are explicitly identified for separate cleanup. Private Gallery cannot erase Samsung cloud caches. Physical Fold/OEM keyboard/TalkBack and the owner's actual installed data require owner acceptance.

Supported native downloads are explicitly confirmed, reusable complete image/video GET responses. POST-generated, blob, single-use or segmented resources are refused with an explanation rather than replayed unsafely. VPN+Tor combined mode remains unavailable; separately supported VPN and Tor mode switching is verified. No generic website compatibility or physical acceptance claim is made beyond these supported boundaries.
