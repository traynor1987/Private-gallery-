# Private Gallery 1.7.8 recovery

Status: UNINTEGRATED FOUNDATION CHECKPOINT / FEATURE INCOMPLETE / NOT APPROVED FOR SIGNING.

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
