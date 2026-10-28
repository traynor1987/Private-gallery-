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
