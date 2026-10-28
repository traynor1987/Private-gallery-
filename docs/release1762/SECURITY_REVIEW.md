# OWNER SCOPE RESTORED — RELEASE UNFINISHED (28 October 2026)

The owner withdrew the historical deferral and reinstated the complete original 1.7.6.2 specification. The previous code43 APK is intermediate, not final. The final owner instruction explicitly authorises versionCode44; versionName remains1.7.6.2. Prior completion/GO statements and transport-test exclusions below are historical and do not approve the restored scope. No new final candidate, full transport security verification or physical Fold acceptance is claimed. Signing must remain blocked until new exact-source restored-scope gates pass.

See docs/superpowers/plans/2026-10-28-complete-original-1762.md for recovery and remaining work.

Interim restored-scope UI/editor/picker review found an Important nested-scroll editor chooser crash and missing Primary preview-selection checks. Both corrected and scoped re-review found no remaining Critical/Important in that narrow diff, by source inspection only. See restored-scope/ui-editor-picker-review.md and ui-editor-picker-rereview.md. New VPN implementation has not received full independent review or runtime verification. Its activation remains explicitly gated off pending those results; no final release approval.

---

# 1.7.6.2 source security review and test impact

Independent read-only review by a fresh-context reviewer covered base 74ccd09..local068a764 (remote equivalent eb03956) and surrounding authentication, browser/egress, profile and external-edit lifecycle code. It ran no tests and is not a physical-device or live-network attestation.

Important finding: an asynchronous upload preparation A could finish after cancellation/new chooser B and clear B's callback and selection. Activity generation checks prevented a demonstrated plaintext bypass. Fixed in local a2172b1 / remote5482d183 by capturing both request object and picker generation before preparation and rejecting a stale completion before any UI/callback/cleanup mutation. Obsolete-operation cleanup remains with Activity preparation, avoiding deletion of B's files. Added delayed A-cancel-B regression. Its runtime result must pass before signing.

Minor finding: direct session callback tests do not cover the entire new WebView JS bridge. Real confirm/prompt tests were already added; real alert and before-unload explicit Stay/Leave checks are now added. Lock/background, tab close, network revocation and stale reply checks retain deny/cancel defaults. Pending runtime evidence remains required.

The reviewer found no Critical source defect. Confirmed boundaries: independent cross-vault access/authentication objects retained; Vault-only/COMPLETE/MIME filtering retained and repository remains egress authority; Hidden library remains bounded runtime memory and is cleared; Samsung handoff remains explicit package-targeted EDIT/read-only with manual fallback. Review deliberately did not attest physical Samsung/IME/Fold, real DNS/VPN/Tor runtime, exact-HEAD tests, signer or upgrade execution.

## Relevant mandatory verification

Full PrimaryCopyAccessTest and HiddenRestoreAccessTest (independent auth, revocation, encrypted transfer, verified replacement, restrictions), HiddenCredentialStoreTest and HiddenCredentialSeparationTest, external editor lifecycle/provider/export suites, profile isolation, local SOCKS transport, navigation transport/redirect rendering, session library close, new JS request lifetime, shared dialog/large-text/window cases and actual encrypted move-to-picker. JVM, lint, host policy/encryption/stream checks remain gates. Original-key upgrades from documented possible baselines code41 and code42 must verify independent credentials, original ciphertext hashes, media/settings and restart.

## Impact rulings and preserved evidence

HiddenRuntimeFailureTest starts real Tor before installing a local stalled-SOCKS fixture; it contains the historical natural-timeout-vs-transport-lost failure. It was inadvertently selected in the first grouped runner, but that run40682448892 failed compilation before instrumentation, so it did not execute. Removed that selector before the next run: no 1.7.6.2 changes to Hidden transport, Tor startup/native binaries, timeout/DNS/routing or connection policy; local SOCKS/navigation/fail-closed/profile suites and library-close/revocation checks cover this release's changes. This is an impact-based exclusion authorised by the owner, not an assertion change or a historical pass. Historical failure remains unresolved. No unrelated full camera/AI/media regression or live onion timeout retries are required by this diff.

No key derivation, credential persistence, encrypted formats or original production signing identity changed. User's actual phone state and data remain untouched. No physical acceptance claimed.

## Prepared original-key signing workflow review

The same independent reviewer separately examined the newly prepared acceptance1762 workflow and the strict code43 upgrade fixture allowlist. No Critical/Important source issue was established. The workflow pins a frozen source, requires successful exact-source focused CI plus artifact checksum/source/exit/XML evidence before signer access, checks the original certificate, then requires separate clean-emulator41→43 and42→43 upgrades and restart tied to the identical candidate digest before publication. JKS removal is unconditional. Actual key availability, GitHub artifact/API behaviour and upgrade execution remain unclaimed until this pipeline passes. The reviewer initially could not find frozen40507b34 locally; it was subsequently fetched and app/scripts were byte-identical to the reviewed tree. The final candidate source is234d2d5c688a3f598408eebca24275757a40b8e8, adding only the documented wrapping layout, website test fixtures and dialog semantic tag since that source; final Android checks remain pending. Later workflow checks also require SELinux Enforcing and the complete selected method set, without weakening assertions.


## Final verification and incremental review

Frozen234d2d5c688a3f598408eebca24275757a40b8e8 passed117 Android17 cases plus165 host/JVM/lint gates (run40685615288). Independent incremental review of the later FlowRow, semantic tag and local-response test fixture found no actionable Critical/Important issue. The original upload-race finding is fixed and its delayed callback regression passed. Minor test-clarity suggestion deferred: explicitly assert the pathname after Stay; the subsequent interaction with the original-only DOM button already provides additional evidence that the old page remains. This is not a production behavior defect.

Productionrun40687888327 passed original-certificate verification, release identity/build, both original-key41/42→43 upgrades with byte-preserved ciphertext/independent credentials/settings and restart, exact candidate digest checks and signing-material cleanup. APK SHA256b4b6887666bffda25723ac9355c0c99cbd7e9154b6162aa8a27e17dd203e280e. Certificate SHA25694f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. No unresolved Critical/Important finding for modified boundaries established. GO for owner acceptance; physical Fold/Samsung and the owner's actual data remain untested by development. Historical Tor failure and all impact exclusions remain as documented.
