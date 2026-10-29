# Private Gallery 1.7.8 recovery

Status: PUBLIC PWA DISCOVERY / ENCRYPTED ICON SPRINT VERIFIED (HOST + COMPILE/JVM + FOUR API37 TESTS) / RELEASE INCOMPLETE / NOT APPROVED FOR SIGNING.

Authoritative starting HEAD: 64d4c213fe3b3a92ecd946e334e8a918851636c9 on release/1.7.7-recently-deleted.
Frozen 1.7.7 application: 25aae6d59f79c455e47acc73a5c99f9c8f15242a.
1.7.7 code46 APK SHA256: 696e80071497501d8da6cd8dd3e2c9728a63b0d4abc9e88c4ab46338fef97a2e.
Original certificate SHA256: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.
1.7.7 physical owner acceptance remains NOT_PERFORMED. Its upgrade/signing evidence is preserved unchanged.

Created release/1.7.8-private-apps remotely through GitHub connector at the exact starting HEAD. Local git HTTPS push lacks credentials; use connector Git Data API with expected-parent fast-forward updates, then independently fetch/compare. Never force push.
Fetched 84 remote branches: maximum code46, only on 1.7.7 branch. Code47 is provisional; recheck before metadata assignment. No application version change yet.

Baseline host suite: python3 -m unittest discover -s scripts/tests -p 'test_*.py': 201 PASS. Existing Python fixture ResourceWarnings observed. These are not Android runtime results.
Local ./gradlew --version fails obtaining uncached Gradle9.5.0 with java.net.SocketException: Network is unreachable. No Android SDK/adb installed. Existing focused1762 workflow provides remote compilation/runtime gates; do not mistake local host checks for Android verification.

Independent design review (not implementation approval): Hidden has unique owned storage roots, random profiles, hard revocation/process kill and whole-root cleanup. Reusing profiles/skipping cleanup would retain plaintext Chromium credentials. Keep established ephemeral Hidden login policy explicitly visible unless a separately verified encrypted snapshot subsystem is implemented. Primary currently shares default-profile cookies across tabs; do not claim per-app isolation/clear/retention. Hidden per-session tabs also share a profile. Definitions can be encrypted and persistent independently of cookies.

Additional scope dependency: HiddenRepository.importMedia rejects all non-image/video MIME; its thumbnail builder assumes image/video. HiddenVpnBrowserBroker also restricts downloads. Documents need an actual encrypted document storage/retrieval lifecycle, not only a MIME allowlist change.

No app implementation, new Android run, signer access or acceptance claim at this checkpoint.

## Foundation checkpoint (not a completed feature)

Added PrivateAppPolicy, bounded immutable PrivateAppCatalog and HiddenPrivateAppStore using existing HiddenRecordStore/HiddenSession authority gates. Added PrimaryPrivateAppStore using existing VaultCipher and supplied generation/commit fences; this Kotlin source and its JUnit test are DRAFT / NOT COMPILED. No existing activity/browser/vault source or version metadata changed.

Executed bash scripts/check_private_apps178.sh: three Java host programs PASS (URL/scope/name validation, catalogue serialisation/reorder/remove/duplicates, encrypted Hidden persistence/mode separation/stale-authority/revoked-commit refusal). Baseline201 Python host tests PASS. These do NOT establish Android compatibility, traffic isolation or release readiness.

Independent foundation review found root-URL scope rejection, trailing-dot onion metadata classification, malformed base64 acceptance, unwiped temporary encode buffer, and missing Primary parent-directory fsync. Source corrections applied; Java regression assertions pass. Primary parent fsync remains source-reviewed only. Trailing-dot issue was NOT a demonstrated transport leak: existing Hidden transport already rejects that host form.

Required remaining work: full Primary/Hidden UI and encrypted catalogue integration; PWA discovery/metadata/icon transport with bounded decode; per-app session/authentication controls with honest shared-profile limitations; documents and allowed screenshots; download/upload app ownership, cancellation/recovery; focused Android synthetic PWA/storage/transport/lifecycle tests; independent whole-change security review; exact-source freeze; original-key46→47 upgrade verification including Recently Deleted; signing and artifact verification. All milestones after foundation remain incomplete. No new GitHub workflow launched. No signing secrets accessed. No APK produced.

Caller requirement: Primary current/commit closures MUST capture immutable session/key/generation authority, not only an unlocked boolean. This must be verified at eventual integration. Icon Base64 validation is not bitmap validation: renderer must bound dimensions and reject malformed image content.

## Limited-budget storage sprint — compilation pending

Starting remote HEAD independently recovered as dd9a25977bea5cdaee1df82f80d2f7e7181f7cb7. No new branch or version change.

Correction to preceding handoff: pushing the foundation automatically triggered existing fast-development run40906416716, completed SUCCESS. Earlier wording that no workflow ran / Primary remained uncompiled was stale. This sprint does not reuse that result for changed source.

Implemented shared PrivateAppStore contract with immutable PRIMARY/HIDDEN_VPN/HIDDEN_TOR ownership. Existing Primary/Hidden encrypted formats are preserved. PrimaryPrivateAppAccess captures authentication identity, validates under the caller-supplied revocation/commit gate, copies keys only for each operation, and wipes those copies even after failure. Hidden retains its existing HiddenSession authority. No current-vault fallback or cookie/credential API is introduced.

Added shared Primary/Hidden CRUD/restart JVM coverage, Primary stale-handle lock/unlock rejection, revoked update preservation, corruption refusal, additive first-use behaviour preserving old browser files, Hidden authenticated ciphertext mode-swap refusal and corruption preservation. Three Java host programs pass after changes; new Kotlin/JVM suite pending remote execution.

The existing fast-development workflow now selects one narrow private-apps-checkpoint job only on this branch; its historical broad job is excluded here. One grouped invocation compiles app/test sources and runs only core.privateapps JVM tests. No emulator, full regression, signing or APK publication. Historical release workflow files and1.7.7 evidence untouched.

Budget scope: finish and verify catalogue storage first. UI/browser binding is not implemented in this sprint candidate. Exact next task: capture Primary access from MainActivity's immutable sessionKey identity plus phoneTransferGeneration under phoneTransferGate; expose Hidden handle through its existing authenticated broker; then build shared Private Apps Library/add/edit/remove UI using these handles. Callback publication and launch must recheck originating authority, not current selection. Do not claim runtime isolation or UI completion from catalogue tests.

## Verified storage sprint handoff

Tested source: d391681820126b4108669b48f56bb4cc32b92bef.
Grouped run40909283419 / job123572509730 SUCCESS. Existing broad fast job was SKIPPED. Android17 compile SDK installed; application and unit-test compilation plus `:app:testDebugUnitTest --tests 'uk.co.traynor.privategallery.core.privateapps.*'` passed. JUnit XML independently downloaded and parsed:5 tests,0 failures,0 errors,0 skipped. The three Java host programs also passed in CI and locally. No emulator/runtime test or release gate was run.
Evidence artifact12912148430, SHA256 adb692a0ef020c81cf323b3b7902520017285335348f4f7f2a474908811f351b, independently downloaded/hash-checked and bound to tested source via archived source-sha.txt. Durable JUnit XML and summary are in docs/release178/storage-sprint-evidence. See docs/release178/STORAGE_SPRINT.md for scope and next task.

Independent source review found no concrete Important bugs in the sprint diff. Important future integration condition: caller authentication/revocation MUST use the supplied gate. Concurrent real Activity/Binder revocation and callback publication remain unverified. Per-store ownership is tested, but no production UI consumer is connected yet; this is not complete Private Apps functionality.

Previous1.7.7 branch remains64d4c213fe3b3a92ecd946e334e8a918851636c9. Comparison against that SHA confirms unchanged app/build.gradle.kts, V1_7_7_RECOVERY.md, docs/release177, .github/workflows/acceptance1762.yml and scripts/upgrade. Physical1.7.7 acceptance remains pending. No version change, signing, app-data clearing, replacement key, or release APK occurred.

NEXT: implement authenticated owner-bound catalogue adapters at MainActivity/Hidden broker entry, then shared Private Apps Library/add/edit/remove/launch UI; use existing browser contexts. Preserve stale-handle rejection and verify callbacks across lock/re-unlock/vault switches. PWA/session/document features remain later work under the original1.7.8 specification.

All earlier pending-compilation statements above are historical and superseded by this verified sprint result. Final documentation/evidence commit does not alter tested application source and must not trigger another compilation run.

## Final17% UI/integration sprint — candidate awaiting grouped checks

Baseline5257521930d23e64096113a0a4653a8b3c8c8dba verified against remote. Versions and branch unchanged.

Added MainActivity factory capturing Primary key identity, authentication epoch, phone-transfer generation and browser presentation generation. It refuses locked, Hidden-selected, hidden-content, wrong-route and background contexts. Existing PrimaryPrivateAppAccess owns key copies and fenced commits. Primary launch uses existing BrowserV2Session and refuses unavailable authorised networking.

Added bounded PRIVATE_APPS command to the existing authenticated Hidden broker. Commands are single-entry add/edit, remove or read, validated in parent and committed through HiddenSession plus live-broker gate. Child receives bounded encoded catalogue only; keys stay in parent. Hidden launch uses its existing native WebView/profile and selected immutable VPN/Tor transport, with no new network client or policy changes.

Added shared themed PrivateAppsLibrary with adaptive grid, safe letter placeholders, empty state, URL/name add/edit, confirmed removal and Open. Browser entry remains available for return. UI clears state and closes on authority loss; no saved-instance catalogue state. Opening the library cancels outstanding upload selections. Request buffers are wiped. Icons remain placeholders; no remote icon fetch/decode.

Four Java host check programs pass locally including command validation and broker-revoked-before-commit preservation. Added controller JVM tests for stale launch and revoked publication. Added actual MainActivity factory and Compose CRUD/launch/revocation instrumentation tests for the disposable test package. One grouped existing-workflow job will compile/JVM-test and run only these two instrumentation classes on API37. Results pending; do not infer runtime proof yet.

Not a release: no PWA discovery, dedicated app chrome, per-app session controls, documents or new download capability. Runtime real VPN/Tor traffic and native Hidden broker instrumentation remain later gates. Full Fold/light-theme physical acceptance remains pending.1.7.7 evidence and signing controls remain untouched.

## Verified UI/integration sprint handoff

Tested implementation: c9bc6eaa881b09b862196496d2ebf769a8e2a423, descended from5257521930d23e64096113a0a4653a8b3c8c8dba. Final evidence-only commit is discoverable as HEAD of release/1.7.8-private-apps; application/scripts/workflow must remain identical to tested implementation.

Single grouped run40912786141 / job123584924803 SUCCESS; historical broad job SKIPPED. Application, JVM and Android test APK compilation passed. Seven targeted JVM tests and two API37 instrumentation tests passed with zero failures/errors/skips, independently confirmed from downloaded XML. Four Java host programs passed locally and in CI. Artifact12915211952 SHA25612b4fad24f8efb6331777a4320d9d915fb1b6e907ae7a614c882826ad0fedfcc was independently downloaded/hash-checked and source-bound. Durable XML, source SHA and result manifest: docs/release178/ui-sprint-evidence.

Completed: authenticated Primary Activity catalogue factory; bounded authenticated Hidden broker catalogue commands; immutable owner-bound controller; shared responsive themed library with safe placeholder tiles, empty state, add/edit/remove confirmation and launch; browser entries for return; stale-authority UI cleanup and fenced Hidden writes. Saved websites use existing authorised Primary or selected Hidden VPN/Tor browser paths. No additional WebView or network stack. Independent source review identified and resolved two issues before testing: stored requireAuthentication entries now fail closed until fresh-auth flow exists, and nested edit/remove dialogs explicitly use SecureOn.

Evidence boundaries: Android UI test uses encrypted Primary fixture storage and launch callback, not live internet. Activity test exercises the actual factory with synthetic authenticated state and same-key lock/unlock epoch revocation, not production PIN entry. Host/JVM coverage includes separate owners, encrypted restart, corruption, bounded commands and revoked commits. No new real Hidden Binder lifecycle, VPN/Tor traffic, DNS leak, upload/download or Fold physical evidence is claimed. Source review is not a substitute for those gates.

Remaining: PWA/manifest discovery, website/custom icons, UI reorder/favourites, dedicated app chrome, per-open authentication, honest enforceable session controls, download/document/screenshot and upload lifecycle integration, full Hidden runtime isolation/routing checks, light-theme/Fold/large-text physical validation, complete security review and original-key upgrade/signing gates. Primary tabs share their existing cookie profile; Hidden tabs share the current ephemeral Hidden profile. Catalogue persistence does not retain Hidden cookies or provide per-app cookie isolation. Existing Hidden cleanup policy remains unchanged.

Exact next task: add focused synthetic native Hidden broker instrumentation exercising authenticated library CRUD, selected VPN/Tor launch and lock/revocation through real broker/process boundaries; verify no cross-mode catalogue publication or transport fallback. Then continue bounded PWA/icon discovery and remaining Private Apps controls. Do not repeat the completed catalogue foundation or this successful compilation/UI batch without a relevant change.

Preservation checked: remote release/1.7.7-recently-deleted remains64d4c213fe3b3a92ecd946e334e8a918851636c9; app/build.gradle.kts, V1_7_7_RECOVERY.md, docs/release177, .github/workflows/acceptance1762.yml and scripts/upgrade are unchanged against that release. Version remains1.7.7/code46 pending later authorised release metadata assignment. Physical1.7.7 acceptance remains pending. No production signing, replacement keys, uninstall, application-data clearing or release APK publication occurred.

Earlier candidate/pending-result statements are historical and superseded by this handoff. Only documentation/evidence is added after the tested source; no second workflow is needed.

## PWA/icon sprint — candidate awaiting grouped checks

Recovered authoritative203f4fcde8f8b8cb29ba539802a871d2c6b0e42a. Added explicit Discover website in the library editor, public bounded HTML/manifest parsing, supported name/short_name/start/scope/display/icon metadata and same-origin URL validation. Compatible manifest offers Add as Private Web App; fallback offers Add as Private Website. Existing launch remains unchanged. No JavaScript enabling, installation or shortcut creation.

Primary metadata uses its current owned WireGuard Network with pinned socket factory/DNS and existing public-address filtering. Hidden metadata uses its existing authenticated proxy, including Tor remote DNS. No cookies, origin auth, redirects, cross-origin resources, disk cache or direct fallback. Requests are bounded by response size, call and operation deadlines, plus100ms revocation cancellation. Authenticated Hidden IPC accepts one bounded URL and returns a bounded one-entry catalogue. This is public metadata discovery; login-protected metadata and redirected sites may fall back to website entries.

Raster icon retrieval uses manifest icon then favicon fallback, maximum128KiB encoded/1024px source dimensions, normalized to at most64px and6KiB PNG. Icons are stored inline in existing encrypted catalogue records, including Hidden; UI decodes only bounded PNGs and recycles bitmaps on disposal. No plaintext icon cache. Custom icon picker not implemented.

Four host check programs PASS locally; new parser/transport JVM and synthetic PWA/light-theme UI/icon Android tests await the single grouped workflow. Independent source review found no blocking issue. Limitation: editor dismissal discards results but does not immediately cancel the blocking transport; it may continue until completion or the45-second operation deadline. Vault/broker revocation is actively checked and cancels calls. Real Hidden broker/transport runtime verification remains outstanding.

Not complete: dedicated app control surface, custom icons, reorder/favourites UI, per-open authentication/session controls, PWA advanced capabilities, media lifecycle and full release gates. Existing browser controls remain available; manifest display mode is stored but does not claim standalone/Chrome-equivalent support. UI states no push/offline guarantee/service-worker installation. Next after this checkpoint: real Hidden discovery/launch/revocation tests through authenticated process boundaries, then dedicated app controls/custom vault icons.1.7.7/version/signing evidence untouched.

## Verified PWA/icon sprint handoff

Tested source5e1f83f58c87e2d75a6de1176da0204a5cf6d4da. One existing grouped workflow run40916333151 / job123597429221 SUCCESS; historical broad job SKIPPED. Application and Android test compilation passed. Fourteen targeted JVM tests and four API37 instrumentation tests passed with zero failures/errors/skips, independently parsed from downloaded XML. Four Java host programs passed both locally and in CI. Artifact12916018031 SHA256cd836e25e2a2d9d452624178bdba5274cf46f179c6550b3b300be0ed3c7b82d9 independently verified and source-bound. Durable evidence: docs/release178/pwa-sprint-evidence. Final checkpoint adds documentation/evidence only; app/scripts/workflow remain identical to tested source.

Passed new coverage: manifest name/short_name/start/scope/display/icon resolution; unsafe/private-literal and cross-origin URL refusal; invalid scope/display fallback; bounded resources and buffer wiping; no credential headers or redirect following in a synthetic OkHttp interceptor; pre-request revocation; light-theme synthetic PWA editor discovery, icon preview and encrypted restart persistence; oversized/malformed icon rejection. Previous catalogue and Primary Activity authentication/revocation tests also passed in the grouped batch.

Evidence limitations: synthetic transport tests use an interceptor, not live VPN/Tor traffic. Android discovery test injects local fixture responses, not the real Hidden Binder path. No new DNS-leak, real Hidden launch/revocation, custom icon, app chrome, media, physical Fold, original-key upgrade or release-signing result is claimed. Previous1.7.7 remote SHA64d4c213fe3b3a92ecd946e334e8a918851636c9 and protected version/recovery/release177/upgrade paths remain unchanged; its physical acceptance remains pending.

Completed slice: public PWA discovery and safe encrypted manifest/favicon icons integrated with Primary and Hidden library editors. Unsupported metadata retains website/manual fallback. Tor JavaScript policy and login retention remain unchanged. No public installation, launcher shortcut, plaintext icon cache or final APK.

Exact next task: implement focused synthetic HTTPS fixtures exercising DISCOVER_PRIVATE_APP over the real authenticated Hidden broker in VPN and Tor modes; verify CRUD/launch, mode separation, revocation and transport loss without fallback. Then implement dedicated app controls and vault-only custom icon selection. Also improve editor-cancel request cancellation (current result is discarded but bounded network work can continue), expand PWA capability reporting and icon candidate selection, and complete remaining original1.7.8 gates. Do not repeat completed foundation or passing batches without relevant changes.

This section supersedes the preceding pending-results paragraph. No version bump, signing key access, uninstall or data clearing occurred in this sprint.

## Full-completion implementation batch — pending verification

Recovered93f4c95eba731eee9c146e742bd05f432224d231, no competing agent writer at recovery. Added atomic pin/unpin/reorder/address-visibility commands, library controls, dedicated presentation bound to existing browser tab identity, app navigation/home/settings and quick switching. No new WebView/profile/transport. Added real Hidden child/broker CRUD, owner separation, VPN HTTPS/Tor onion launch, revocation and authenticated restart instrumentation. Existing grouped workflow now provisions existing disposable transport fixtures and runs only affected classes.

Host catalogue checks and five fixture source tests passed locally; shell/YAML checks passed. Android/JVM results pending. Independent source review caught nested dialog scrolling and stale full-record address-setting writes; fixes use one scroll container and atomic parent-side field updates. Runtime verification and consolidated release review still required.

See docs/release178/COMPLETION_TRACKER.md for missing original scope. Custom icons, per-open authentication/session controls, document and screenshot handling, full media lifecycle/privacy gates and original-key upgrade/signing remain incomplete. Version metadata remains1.7.7/code46 until final release assignment; no signer access.1.7.7 evidence remains unchanged. Checkpoints remain recovery safeguards, not release completion.
