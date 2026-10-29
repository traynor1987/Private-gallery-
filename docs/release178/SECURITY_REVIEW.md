# Private Gallery 1.7.8 consolidated security review

**Disposition: NOT_APPROVED — exact-source runtime evidence pending.** This is the consolidated current-source report. It supersedes the provisional findings in SECURITY_REVIEW_DRAFT.md, but does not approve signing, delivery, upgrade or physical owner acceptance. No unresolved blocking source defect was identified after the corrections below; the outstanding release-evidence gate remains blocking.

## Reviewed source and scope

- Candidate: `795bbe9617a49b53192b9db76a448f35d344c1d8`.
- Candidate tree: `e8a1e2f65b1ee5c6f01b3f703fcfc70a37555eb3`.
- Frozen 1.7.7 application: `25aae6d59f79c455e47acc73a5c99f9c8f15242a`.
- 1.7.8 control-parent comparison: `64d4c213fe3b3a92ecd946e334e8a918851636c9`, avoiding misattribution of intervening 1.7.7 release-control/evidence changes.
- Current grouped run: `40931786331`; final artifact and result review pending.

The reviewer inspected the complete affected catalogue/PWA/browser architecture, then the custom-icon, session-control, document, screenshot, transfer and upgrade deltas. Forty-one production files differ from the control parent. Relevant existing authority, cryptography, proxy, profile-retirement and upload-descriptor dependencies were inspected. Review did not restart or approve unrelated historical changes. Corrections after the initial freeze were reviewed as specific deltas, including the large-PDF boundary fix and the six Android-failure corrections. No production source was edited by this reviewer.

Reviewed release-control digests:

| Control | SHA-256 |
|---|---|
| `scripts/private_apps178_cases.json` — 36 unique cases | `e40ccba6ea6459ff16ffb568e675bbdef2b23c70aa1367475e3a93ab0cbca775` |
| `scripts/verify_final_scope178.py` | `6bfbb5898f9a1eeafb1a8067b352dbae3a818e290d2cfda18b6b671159987b21` |
| `scripts/upgrade178/UpgradeFixtureTest.kt` | `be6b1de3301dc3de26e0fc02888e09a59e7f806069f2f12ac8d2bc3b477bfb69` |
| `scripts/verify_upgrade178_baseline.py` | `7e93c9b75d93e2cb7cd7477d4eebc0d2419016fe3a39b275604fef6cfe8b5445` |
| `scripts/verify_synthetic_upgrade178.sh` | `b96b4e4cdcab16a5e2ee148f0dc5c68534415b54f4eaf36c06e3d3267fd6452c` |

## Evidence examined and current blocker

The reviewer independently parsed archived checkpoint XML: storage 5 tests; UI 7 JVM/2 Android; PWA 14 JVM/4 Android; controls 18 JVM/8 Android, all reporting zero failures/errors/skips. Those results establish only their recorded checkpoint sources.

The failed candidate `483f281fbf6cd9b347dde0cf724b5bb843c082d7`, run `40928770951`, was independently inspected from its extracted artifact. Its source file matched that candidate. XML contains **27 passing JVM tests and 35 Android cases: 29 passing, 6 failing, zero errors/skips**. The parent independently downloaded and verified original artifact archive SHA-256 `b9b907da2ec8f7192eeb1c322dc634c8d786a0870b4ce2bc6e32d3a489b99488`. Durable text evidence is in `docs/release178/final-candidate-evidence/483f281`; text upload normalized line endings, so the original artifact digest must not be asserted as a hash of the normalized files.

The six failures were icon geometry, successful screenshot capture, the native VPN download-consent label, the native Tor initial script-probe heading, and the two real Hidden PIN-gated Private App launches. The reviewer examined the corrective source and relevant original logs. **Reviewed corrections are not passing runtime evidence.** Earlier successful compilation or individual passing cases do not approve the new candidate. The current run has reported compilation/JVM progress, but no final current-source artifact has yet been independently examined here.

Blocking gate: obtain and independently validate the successful exact-source artifact, all 36 expected Android cases with no failures/errors/skips, current JVM results, original exit status, actual VPN/Tor packet/DNS/revocation evidence and source identity. Then update this report's evidence and disposition. Until then the machine decision below remains NOT_APPROVED.

## Corrected findings

All entries below are **resolved in reviewed source, pending final exact-source runtime confirmation** unless explicitly described as historical evidence.

| Finding | Correction and reviewed boundary |
|---|---|
| Hidden pending downloads retained a global URL across app/tab changes. | Selection captures initiating WebView, document and navigation revision; stale consent is rejected and selection clears on navigation/switch. Active-download ownership and cancellation remain with the original browser/session. No cross-vault plaintext leak was demonstrated by the initial defect. |
| Primary background-tab download callbacks inherited the current tab's fence. | BrowserV2Session admits only the active, live foreground tab; the UI captures its document and owned Network. Retired/background callbacks are rejected. |
| New Primary generic download used ambient URLConnection. | The captured owned VPN Network pins sockets and DNS, public-address filtering applies, redirects/cache/retries are disabled, and a watcher cancels lost authority. The UI explicitly requires the Private Gallery VPN. This correction does not change the separate historical long-press image helper or grant it a new transport guarantee. |
| Profile clearing returned before asynchronous cookie deletion completed. | Full WebStorage clearing has a process-wide generation barrier and durable pending marker. Networking and fresh renderer creation remain denied until completion and durable publication. Normal supported clear-on-lock uses the same barrier. Startup also clears when the persisted clear-on-lock preference is enabled, covering death before marker persistence. Storage failure does not interrupt session/key revocation; failed completion remains gated. Unsupported providers show an update/retry limitation instead of claiming completed clearing. |
| Primary document validating wrapper was not closed. | The bounded wrapper now closes around encrypted staging, wiping its plaintext accumulation buffer on success/failure. Hidden already closed it. |
| Document phone restore used the image/Pictures destination. | Primary and Hidden document export use MediaStore Downloads. Hidden recovery recognizes its document journal tag and preserves prior media journal compatibility. Explicit egress restrictions remain enforced. |
| Hidden accepted 32 MiB PDFs but its display path rejected documents above the old 16 MiB media limit. | A document-specific bounded, authority-checked read precedes image fallback and verifies full size/SHA-256. A structurally valid 17 MiB PDF regression exercises native rendering, lock refusal and ciphertext tamper. That case passed in the otherwise failed 483 candidate; the final candidate must retain and pass it. |
| Sampled custom-icon scaling produced 31 px instead of the expected 32 px short axis. | Final geometry derives from original oriented dimensions and rounds once. Original byte/pixel limits, maximum 64 px output and 6 KiB PNG storage remain enforced. Portrait and odd-size regression added; original assertion retained. |
| Screenshot success fixture invoked PixelCopy before a window buffer existed. | Original logcat independently shows HWUI “Surface doesn't have any previously queued frames”. The fixture now waits for a committed frame after WebView visual readiness. Production secure/focus/authority/attachment/bounds checks are unchanged; diagnostics preserve only non-content failure causes. No drawing fallback or privacy-policy relaxation was added. |
| Hidden reauthentication inspected WebView.url from an IO thread. | Original logcat independently confirms the wrong-thread WebView call. Presentation-authority checks now run on Main before/after IO credential verification. Hidden retains original view and navigation-revision fencing, including same-URL reloads; parent verifier still checks the live broker/session key. New thread assertions accompany the existing wrong/cancel/correct and one-use tests. |
| Existing native tests waited for the old GET-media label and a shorter Tor timeout. | Consent assertions now use the actual GET-file label. The initial Tor heading wait is bounded at 65 seconds, matching the existing 60-second native load deadline plus scheduling grace. Exact heading, JS-off, transport, origin and revocation assertions remain; no fallback or retry was introduced. |

## Security properties and limits

**Encrypted catalogue and authority.** Definitions are bounded and contain no login credential API. Primary handles capture key identity, authentication epoch and transfer/presentation generations; operation key copies are wiped and commits use the revocation gate. Hidden retains the original HiddenSession authority and broker gate, with keys in the parent. Separate authenticated records distinguish Primary, Hidden VPN and Hidden Tor. Corruption is refused; writes stage ciphertext before atomic promotion. Saved IDs are resolved again against the original owner before launch.

**PWA/metadata requests.** Discovery is public metadata only: same-origin manifest/start/scope/icon resolution, supported schemes and hosts, no site cookies or origin Authorization, no redirects and no ambient cache. Owned VPN DNS/sockets and public-address checks are retained. Hidden uses the existing authenticated local proxy; Tor origin resolution stays on its private Unix SOCKS route. Responses, dimensions, request time and overall operation time are bounded. Metadata editor dismissal suppresses publication but does not immediately interrupt a blocking request; its deadline and revocation cancellation still apply. Manifest scope is visible metadata, not a browser sandbox; no installation, offline/push or Chrome-equivalent guarantee is made.

**Custom icons and launch UI.** Primary explicitly selects a photo; Hidden uses the current authenticated vault picker/preview without creating an upload grant. Source URIs are not persisted in catalogue icons. Size/pixel bounds precede sampled decode, orientation is handled, normalized PNGs remain encrypted, and stale editor results are discarded. Browser tabs retain existing transport and profile owners. Open-tab launch-time metadata can outlive a catalogue rename/removal until reopened; no credential isolation follows from app chrome.

**Authentication and sessions.** One accepted PIN result launches only one unchanged saved entry under the original authority. Newer opens supersede older tickets. This guards opening a saved entry, not direct navigation to its website; the UI says so. Primary tabs/apps share one profile and profile-wide retention/clearing. Hidden tabs share the current ephemeral profile, which is retired on closure/revocation; encrypted definitions do not retain Hidden login state. There is no persistent Hidden login or per-app cookie-clear claim.

**Documents, transfers and screenshots.** PDF/text/CSV storage is encrypted and bounded at 32/2/2 MiB. Text and CSV are rendered literally; PDF uses bounded native page rendering over an in-memory read-only descriptor. No document HTML/formula execution or public plaintext preview file was found. Trash, restore and explicit public export use existing owner/egress checks. Generic downloads require complete supported GET responses; cancellation and explicit retry are supported, not range resumption or POST/blob replay. Pending upload selections bind view/origin/generation and cancel on navigation/switch; already delivered website bytes cannot be recalled. Hidden grants/readers remain broker/session-bound and are revoked with that authority.

Screenshot capture requires confirmation, the existing capture policy, current ownership and successful PixelCopy of the visible WebView rectangle. FLAG_SECURE is never cleared. Video/embedded/native surfaces and JavaScript-disabled documents are conservatively refused; Tor JavaScript is never enabled to obtain a screenshot. Source buffers are wiped; Hidden passes a bounded PNG pipe to parent-held encrypted staging with policy/revocation checks. The runtime helper suite exercises pixels/refusals/wiping; actual Hidden screenshot IPC and new document MIME use of existing upload descriptors have source/compositional coverage rather than separate full end-to-end cases. Do not describe them as bespoke end-to-end test results.

**Presentation acceptance.** The exact manifest includes synthetic cover/inner widths, themes and larger text. These do not establish physical folding, OEM WebView behavior or owner acceptance. `OWNER_ACCEPTANCE.md` states the operational limitations and leaves physical Samsung Galaxy Z Fold checks pending.

## Upgrade and signing controls

The proposed acceptance178 path was reviewed while disabled at an all-zero frozen source, without an activation marker. It requires exact-source independent approval and reviewed report/control hashes, API-verified workflow/run/step/artifact identity and archive digest, exact no-skip test cases, and independently rerun VPN/Tor validators before signer access. It does not reuse 1.7.7 approval.

The new upgrade path requires the **exact archived original signed 1.7.7 APK**, not a rebuilt application substitute: run `40899438953`, artifact `12908558722`, archive SHA-256 `16873a3b24abcb311d21ca11e93e1af1707be42946a817770e4b9d629e790869`, APK SHA-256 `696e80071497501d8da6cd8dd3e2c9728a63b0d4abc9e88c4ab46338fef97a2e`. These pins match preserved 1.7.7 recovery/evidence. The parent reports independent archive download/hash verification and successful baseline extraction validation; the reviewer checked the validator/runner and historical pins. Original certificate is `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.

The code46→47 fixture preserves both vaults' keys/media, collections/favourites, browser records/settings, encrypted bytes and Recently Deleted identity/timestamps/retention, including restart. Only compatible instrumentation is rebuilt; the application baseline is pinned. Signed candidate identity and the same APK digest must pass the original-key in-place upgrade/restart gate before delivery. This review has not observed that upgrade execution and makes no claim it passed. Future authorization for a controlled signing/upgrade stage must remain distinct from verified APK delivery and physical owner acceptance.

## Final machine-readable decision

```json
{
  "source_sha": "795bbe9617a49b53192b9db76a448f35d344c1d8",
  "decision": "NOT_APPROVED",
  "critical_open": 0,
  "important_open": 1
}
```

The one open important item is the missing independently validated final exact-source runtime gate, not an assertion that a corrected source defect remains unresolved. Do not activate signing from this report or turn pending evidence into PASS assertions.

## Operational handover appended by release coordinator

Owner requested stop. Run40931786331 completed FAILURE with3 Android failures (PixelCopy status3, UI-authority callback thread assertion, native VPN website touch target). No final approval is issued. Current artifact12924006012 requires independent download and inspection. The NOT_APPROVED decision above remains in force.
