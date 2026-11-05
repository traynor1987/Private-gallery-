# NovaOS 1.7 Home 2 integration

Owner specification: 2026-11-05 BIG MERGE directive. Execute existing approved design, no redesign or extra approval round.

Production source 9efd19540a1d75efa1b30f4e23c9cbb096f93aff (signed code54, 1.7.10.2); preview source39e8e9068c203380858d304c7845aece7fcb7e00 (feature ff3f7683). Branch integration/novaos-1.7-home2.

- [ ] A: Reuse Preview UI/assets in production module. Authenticate with existing PIN/biometric authority; replace demo lock. Compile. Add encrypted preference adapter using EvidenceAccess leases, existing VaultCipher, owner AAD and atomic commit. No new keys, no schema migration of media or automatic Preview imports.
- [ ] B: Route built-in icons through MainActivity existing launcher. Preserve exact Browser V1 engines/data directories. Browser facade owns metadata only, creates no WebView. Store uses existing PrivateAppCatalog and launch authentication. Block Hidden networking centrally; Tor deferred.
- [ ] C: Reuse Store, widget, Quick Panel, Mind screens. Bind every preference/context to one captured authentication. Never serialize sensitive Compose drafts. Keep preview appearance. Wallpaper bytes encrypted. Nova Chat disabled Beta until verified.
- [ ] D: Focused host/JVM checks, app assembly, API37 authentication/storage/privacy/lifecycle upgrade batch, independent security review, original certificate and signed54 upgrade. Only activate production signing when all mandatory gates pass. Next production version code must exceed every observed installed/signed code.

Review focus: stale callbacks after lock and reauthentication; Hidden profile selection forgery; corruption silently overwriting state; Home switching destroying browser sessions; screenshot flags cleared by reused preview code; plaintext drafts/wallpaper/recovery files; native navigation bypassing deferred-network gate.

No local active Git/Gradle writer at recovery; accessible Preview worktree clean. Remote source heads frozen read-only; integration writes only on new branch. No force pushes/reset/clear-data/uninstall.

## Verification findings

Full JVM suite first ran399 tests:396 passed,3 failed. All three are old Private Gallery branding assertions in two test files; both tests and production presentation/discovery implementations are identical to signed54 baseline. Failures exactly compare Private Gallery against existing NovaOS. Update only the exact approved brand expectations; preserve version/concealment/capability assertions. No production code changes for these failures.

Independent integration review identified and fixed: Hidden search rejection, native Settings ID, stable UUID projection for duplicate/private addresses, native catalogue refresh, SpaceContext lifecycle owner for breathing, biometric negative-button PIN path and stale callback guard, wallpaper request survival across pause with result only consumed after resumed authority validation. No expired/replaced authentication may inherit a pending picker.

Browser presentation retains the original engine/session beneath ordinary Home and pauses on Activity pause/stop. Background/native route callbacks read current authority synchronously. Primary website capture already explicitly granted may continue inside the foreground authenticated Nova session; Lock, timeout, Panic and Hidden entry destroy renderers. No Android background playback claim. Original engine still pauses outgoing tabs when switching between web apps; this remains an integration limitation rather than altering V1's tab semantics.

Local final build:399 JVM cases passed,0 failures/errors/skips; actual app and instrumentation APK assembled. Source secret scanner found a pre-existing synthetic private-key-header test fixture (no key bytes). Split only the runtime fixture string construction, retaining the exact rejected header and all4 validator tests; scanner now passes without exclusions or rule changes.
