# NovaOS main promotion audit

Reviewed on 17 November 2026 against NovaOS branch HEAD 14d9158a1b04826f95e2fae4dbbd4df9fd15d352 and old main 93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10.

Promotion is blocked. The owner requires essential security behavior to be preserved before main changes. This commit-level static audit identifies unresolved A and D items. It does not claim complete behavioral equivalence or Android verification of the new changes.

## Remote archive

Branch archive/private-gallery-phase2-2026-10-01 resolves remotely to 93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10. The GitHub create-branch API succeeded and git ls-remote independently returned the same SHA. The first attempted update-ref call could not create a missing ref and made no change.

## Application source provenance

| Build | Application source | Signing run and controls | Runtime evidence |
|---|---|---|---|
| 83 | c22bad35c3c934b8d95cf91e95b0b893cacd8e2c on feature/novaos-sanctuary-integration | sanctuary-production.yml run 30, ID 43752227973, controls commit a67bcbb933625d4837f6bde53bfb1f1c74ae04cc | fast-development.yml run 43743571164 |
| 84 retained candidate | de8716aad373509c48cd820dd3d63aac4af13d6e on feature/novaos-code81-core-apps | sanctuary-production.yml run 33, ID 44093018185, controls commit 0dc538493d41989c75df3c6d8242947b2feab470 | fast-development.yml run 435, ID 44079639233, 286 passed with zero failures, errors or skips |

Both production workflows preserve reviewed controls before explicitly checking out FROZEN_SOURCE and checking HEAD. Application assembleRelease runs after that checkout. Neither production application build checks out main, cherry-picks main, or overlays main application source. The application sources can contain inherited history, which is different from pulling current main during compilation.

The builds have additional declared inputs. Gradle resolves dependencies from Google Maven, Maven Central, a restricted JitPack group and a pinned Guardian Project Maven location. A generated asset task bundles the public Tesseract model, verified by pinned SHA-256 and size. The reviewed upgrade instrumentation is compiled separately against a frozen baseline source with declared transformations. Build 83 also builds its older upgrade baseline in a separate clone. Build 84 uses the authentic published Build 83 APK and archives the Build 83 source only for compiling its test fixture. These fixture and dependency inputs must remain recorded in production evidence.

The retained Build 84 APK SHA-256 is 784ca47cc7aa26a26bc1c655860f1037b765cdf096f392ce94952068d19e804c.
The production certificate SHA-256 is 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.
The authentic Build 83 APK SHA-256 is 5613a7cb6dff7b19a4206bf752ce22bf7b4891e36ce8ca1df9f2cd0ffab1946f.

Run 33 passed the populated upgrade and post-restart instrumentation. The overall run failed at publication. Its evidence applies to the retained APK only. This checkpoint changes source and requires new Android verification before it can become a release candidate.

## Commit dispositions

A identifies essential behavior or coverage requiring adaptation.
B identifies a specific equivalent behavior demonstrated in modern source or tests. Each row limits the claim.
C identifies historical material or a superseded implementation proposal retained in the archive.
D identifies unresolved architectural or test equivalence. Every D remains a promotion blocker until resolved.

| Main-only commit | Category | Disposition |
|---|---|---|
| `b376b1536f3cff1e7ad7f311b78a645d68b813f0` | C | docs: freeze Phase 0 security admission contract and audit delta. Historical admission contract. Preserve in the archive. |
| `3ad95b3588404cc0de21705ded75a0ca4efcfaa9` | A | Require owner recovery confirmation without rotating existing envelopes. Recovery confirmation state and restart handling are absent from modern RecoveryVaultKeyStore. |
| `4f543590db5fb9aadc4c4eb6da998bd59e49ba66` | B | Exclude all Android backup domains and verify compiled backup policy. Modern manifest disables Android backup and data_extraction_rules excludes all nine domains for cloud and device transfer. |
| `c9e303a343ffd547c21d3316a5eeb13744cb2208` | D | Harden Browser diagnostic serialization with closed privacy vocabulary. Modern diagnostics have evolved. Closed-vocabulary equivalence and private-field redaction need dedicated comparison. |
| `9b60e69f1ff46420fa9b3d49df28297405581bd9` | A | Freeze synthetic legacy corpus and guard verified Primary storage promotions. Staged-index authenticated read-back was absent. Adapted in this checkpoint. Remaining payload fault boundaries require review. |
| `ca06215f46e1ac6eded088b2c98926928576f265` | C | Freeze review-only future crypto and independent Hidden credential candidates. Review-only future format candidates are historical design material. Importing them would change the installed format. |
| `1f6ab352671b05f4456e104405a80bae510509cf` | C | Clarify frozen slot cross-field and synthetic-header constraints. Clarifications to the archived proposed format are retained as history. |
| `41556399b9f686a89d8a15b100825462b31ad3ed` | C | Add offline nonproduction WebView profile and process evidence harness. Historical offline evidence harness does not define the modern production source. |
| `d73523a97b74f348ef0bac0a430ca52f442ad5e0` | C | Record read-only default diagnostic and editor privacy routing review. Historical diagnostic review record. Current diagnostic equivalence is tracked separately. |
| `59f36aeaad56573f48f58101822c2cce5165aa26` | C | Revise future candidate alias bootstrap and authenticated GCM usage budget. Review-only candidate bootstrap and GCM budget specification. Preserve without adopting a new format. |
| `ac444151b40300cd7a34d40ce7c636ba240448de` | A | Bind Primary repositories and durable promotions to revocable capabilities. Primary repositories still accept a raw key and permissive default authorization. Opaque session binding needs adaptation. |
| `f902b6378ee806de9dece83d12a4b36f8054b244` | A | Revoke viewer and editor resources and reject stale AI publication. Viewer/editor and asynchronous result ownership must be proven against the current Primary session. |
| `e5bf0ba6bc7530ebb34e84c108ef730ae8b4b287` | A | Gate Phase 0 CI and define manual physical acceptance. Retain required security regression coverage. Historical branch-specific CI admission must be adapted for modern main. |
| `1aec3a06abef2f60f0df0570b767118527a70c9a` | A | Serialize archive restore ownership and preserve authenticated interrupted roots. Modern restoreBackup lacks main’s complete serialized transaction and ownership-safe rollback. |
| `bbe26fb2f003bd6b4ef8d7399df5c04e469d4c21` | C | Record verified isolated Browser evidence APK packaging and JVM results. Historical evidence packaging and test record. Retain as history. |
| `fed394754fa82ec24aeeef8cb2ab6a20b9c1ed50` | A | feat(security): bind Primary work and cleanup to revocable session epochs. Modern ProtectedSessionState retains a raw key. Primary session capabilities need equivalent revocation semantics. |
| `9c32220c3b609c7a066f375686137397ff3f810c` | A | Own payload decryption resources and guard legacy ciphertext reconciliation. Payload resource ownership and reconciliation must be preserved beyond per-loop cancellation checks. |
| `a8549159af3584b6f7ccb02966836ae19681d5ca` | C | Record final payload resource and reconciliation regression evidence. Historical evidence record for payload changes, whose security behavior is tracked under 9c32220c. |
| `a14dbb39338aeca4b5a983f1c9e46088606dce85` | A | Close recovery recreation and credential rollback interruption gaps. Recovery reconstruction and credential rollback must not replace existing authenticated envelopes. |
| `8c59f5f37caa4d4296b7862610f145fff3af88f1` | C | Record resumed full local Phase 0 verification and exact CI boundaries. Historical local verification evidence cannot validate a new candidate. |
| `dd99426abc480b912bf93e8e894fb13538740048` | A | Exercise configuration retention with an admitted Primary epoch. Configuration restoration test must cover the modern Primary session and pending picker flows. |
| `16db824b95f7420dfa451aca804d664dbc35f5b7` | A | Verify optimized release backup XML through compiled resource paths. Keep compiled-resource backup exclusion verification alongside modern backup-source checks. |
| `c0ce4013e65118843c4c69a9c868d99dc340cace` | D | Await actual native reverse scrolling and toolbar reveal in Browser regression. Earlier native scroll/toolbar regression must be compared with current browser tests before retirement. |
| `fb223993ad69ba3dbaac57b2c5462daffc7f5550` | C | docs(security): record exact Phase 0 owner upgrade and unresolved admission gates. Historical owner-upgrade and admission report. Preserve as history. |
| `1cf38b6e23cfd2964ac82c415e305149b489b59b` | A | test(security): prove confirmed Primary backup and clean-state authenticated restore at interruption boundaries. Interrupted authenticated backup and restore boundaries require modern regression coverage. |
| `dafa877cf52158259d7882cb885ec3b376f5547c` | C | docs(security): close supported Phase 0 backup and synthetic restore gates; retain bounded owner acceptance blockers. Historical backup closeout evidence. Modern restore correctness remains an A item. |
| `3e492116a6a4add53f8320b59049b341f19e2b53` | C | docs(security): record final owner evidence and admit Phase 0 for Phase 1 review. Historical Phase 0 acceptance and admission record. |
| `0a582c6458e2899dde3b1b7be57eba3f4f1c2b20` | C | Merge pull request #56: close Private Gallery 2.0 Phase 0 admission. Merge history for the Phase 0 changes already inventoried individually. |
| `c60ac008b5631fe3c2d43dddb1ba7e0e8d068753` | A | Bind Primary capability scopes and typed cache identity to immutable epochs. Opaque epochs, scopes and typed cache identity are absent from modern Primary adapters. |
| `570bc845897273de792e395eb2eada4fad177a4b` | A | Bind Primary media adapters, network requests and credential boundaries to original sessions. Network, media, credential and browser-upload operations need binding to their originating Primary session. |
| `cd363624318bb6b03926bbb3ddbf4c2c7a92f2ae` | A | fix(security): attenuate Primary work and close revocation gaps. Capability attenuation and stale-operation rejection need modern integration. |
| `3ac11c67f7271268d7395261e5bab8a1834a4bc0` | A | test(security): cover integrated Phase 1 lifetimes and record acceptance gates. Integrated Primary lifetime tests and Activity boundaries need adaptation. |
| `77c95d14339309ba5ec1dbab6e4c6db82276bb3f` | C | docs(security): record verified Phase 1 CI and exact changed-file inventory. Historical changed-file inventory and exact-candidate evidence. |
| `3d0edcd3cf563204beb33698486948bdaa809924` | A | fix(security): fail closed on uncertain Primary inventory and serialize setup admission. Fail closed on partial Primary credentials and uncertain inventory. Inventory reader adapted for missing-index protection. Setup admission remains open. |
| `935c2a1f638c621e7e4e6e28f0a3658367e362ea` | C | docs(security): close Phase 1 acceptance with verified replacement and owner PASS. Historical physical acceptance record cannot establish acceptance of modern changes. |
| `f34db23445bd16fd45ae100474c19a2de8669695` | C | Merge pull request #57 from traynor1987/phase1/primary-scoped-security. Merge history for Phase 1 changes inventoried individually. |
| `347eda34db21d693b51ccbdcb890a23a291cdd9b` | C | docs(security): record admitted Phase 2 boundaries and implementation plan. Historical Phase 2 admission and implementation plan. |
| `5005dc559deabe41bbc4eb1be183ba0e1c49261e` | D | feat(security): implement reviewed independent-domain format primitives. Old F1/slot format differs from installed hidden-v1. Do not transplant its encoding without migration analysis. |
| `1c12942ceaeea4897f567800fb69ee9f0ec3dbcc` | D | feat(security): add independent scoped sessions and transient discovery policy. Modern HiddenSession has scoped operations. Whole-path discovery and concealed-container equivalence remains unproved. |
| `8246bb376349e322dfc465f1f0f060643d31ea4e` | B | fix(security): require explicit sleep-inclusive clocks for domain policies. Modern HiddenSession receives elapsedRealtime explicitly and checks elapsed time. Current HiddenTimeoutChecks and revocation checks pass. |
| `7115ce49398b7dcdd1b8e475474358d995366d67` | D | test(security): prove authentication tokens cannot be copied or fabricated. Modern Hidden authority identity tests pass, but equivalence across all old Secondary APIs remains unproved. |
| `50d42a9d6c141b002268fe438380607923d28caf` | A | ci(security): admit Phase 2 checks and record validated core checkpoint. Phase 2 regression coverage must be mapped into the future production workflow without dropping security tests. |
| `280bd49d137183d97f4626318be9435db89b7677` | D | feat(security): persist independent domain setup and concealed authentication. Modern HiddenCredentialStore uses a different PIN-only Keystore format. Setup, collision and recovery equivalence need Android tests. |
| `1f35ebd50dad81aed9861d094ff5b663f4ed905e` | D | fix(security): recover Phase 2 secret ownership and retire obsolete credentials. Secondary ownership and credential retirement differ. Safe recovery equivalence needs investigation. |
| `ec0c8183cbb86b9c73cafcb76441c167c3e34f02` | D | test(security): cover Phase 2 collisions and stale adapter boundaries. Hidden authority checks pass locally, but the older collision/stale-adapter test cases are not yet mapped completely. |
| `e78bccb1e3ac9e17a7c8dfa0a84456c512b0bf5f` | D | fix: close Phase 2 maintenance and lifecycle revocation gaps. Concealed lifecycle and maintenance fixes need comparison with the modern HiddenBrowser and HiddenSession integration. |
| `dd98acab2beeef00b51e0b4255e6841c4dcb3323` | C | docs: close Phase 2 acceptance gates after owner PASS. Historical owner acceptance and Phase 2 closeout record. |
| `93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10` | C | Merge PR #58: Phase 2 independent concealed container foundation. Merge history for the archived Phase 2 implementation inventoried above. |

Disposition totals are 18 A, 2 B, 19 C and 9 D across all 48 commits.

## Targeted adaptations in this checkpoint

EncryptedIndexStore now authenticates and compares staged plaintext before replacing vault-index.enc. It retains current V1 through V7 serialization, AES-GCM associated data, filenames, package identity and keys. A tampered staged authentication tag caused the new regression to fail before the fix. After the adaptation, all nine selected index and migration tests pass.

A missing index now refuses to represent a populated or uncertain Vault as empty. PrimaryStorageInventory is reused from archived main and rejects files, symlinks, inaccessible directories and observed inventory changes. Tests confirm existing payload bytes survive the refusal and a genuinely empty Vault remains readable.

The complete current Hidden foundation host script also runs independently. Its result must not be described as Android Keystore or populated upgrade verification.

The protected-core verifier retains its baseline and exact-file checking. Only the reviewed index and inventory source hashes are admitted for these adaptations. A changed APK must receive fresh mandatory Android tests and a populated upgrade before release.

## Remaining essential work

Primary operation authority, setup admission, recovery confirmation, restore serialization, stale result rejection and payload/resource lifetimes remain unresolved. In current code, AndroidVaultRepository accepts a raw key with authorized defaulting to true. PinVaultKeyStore.create checks the salt preference rather than the full existing-data inventory. RecoveryVaultKeyStore marks an envelope configured after persistence without a separate possession-confirmation lifecycle. restoreBackup can clear credentials in a failure path without the archived main transaction ownership checks. These are concrete source differences that prevent a blanket equivalence finding.

The old F1 Secondary format must not replace installed hidden-v1 credentials. Its confidentiality, collision, retirement and revocation properties need targeted mapping to the actual modern Hidden store and Android tests.

## Promotion and release procedure

After resolving all essential and uncertain items, record the final tested NovaOS commit. Prefer a reviewed promotion commit with old main as a parent and the audited modern source tree. That preserves historical ancestry and permits a normal protected update. Any non-fast-forward main update still requires the owner's explicit confirmation of the final audited SHAs and the verified archive.

PR 61 records the historical full-merge attempt. Supersede it with the audited promotion PR when that candidate exists. Do not merge its current conflicting tree.

Keep version 1.7.38.8 / 84 during reconciliation. Choosing 1.7.39 is allowed at final candidate preparation and requires changing embedded metadata, rebuilding and re-verifying the resulting artifact. Never relabel the retained APK.

The publication workflow already supports a repository-scoped GitHub App token from novaos-production-publication. The previous run had no app token and GitHub rejected the tag because it required Workflows write. Owner setup of NOVAOS_RELEASE_APP_ID and NOVAOS_RELEASE_APP_PRIVATE_KEY remains necessary unless another existing authorised scoped credential becomes available. No new publication retry is warranted while that dependency is absent.

Main, release tags and published assets have not been changed by this checkpoint. No production signing, emulator upgrade or release publication is triggered by the archive operation.
