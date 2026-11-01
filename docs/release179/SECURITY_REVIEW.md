# Evidence Locker 1.7.9 — final consolidated source and runtime review

1 November 2026. **Specification verdict: APPROVED for the reviewed source and gated-build scope. Security/implementation-quality verdict: APPROVED for that same scope. Open source findings: 0 Critical, 0 Important.** The exact candidate is `19e3275ef17746e780d64e6a684d94bab2cabdc3`, version 1.7.9/code51, package `uk.co.traynor.privategallery`, Android API37 only.

This is the evidence continuation and final disposition of the same consolidated independent review, not a new broad source review. Approval authorizes the reviewed source to enter the existing gated production-build process. It does **not** assert that production namespace replay, signing, archived50→51 upgrade/restart, final APK acceptance, publication or physical-device acceptance has occurred. Those mandatory gates remain explicit below. No release-control activation delta is approved implicitly by this report.

## Review continuity and finding dispositions

The binding requirements remain the Evidence Locker design, its plan's global constraints, `CONTRACTS.md` and `PROGRESS.md` rulings. The complete original change-impact/security inspection is preserved in `reviews/SECURITY179_CONSOLIDATED.md`; its historical CHANGES REQUIRED verdict and concrete reproductions remain intact. `reviews/SECURITY179_FIX_WAVE.md` records the independently accepted complete correction. The final evidence now establishes compilation and actual Android execution of those corrections.

| Finding | Final disposition |
| --- | --- |
| SEC179-1 — default source filename in AI text | CLOSED. The AI projection omits the filename-bearing name field. The accepted production `EvidenceAi.kt` still hashes to `75024884ea7efbaaabe2e80e3a21d5178fe146a0af8d5deba91dbb8ba2efec84`. The exact 153-case XML includes the passing default-filename preview/transmission regression, with selected OCR/annotation retention and one-shot confirmation assertions. Local evidence naming remains allowed; preview consent does not override the no-filename AI contract. |
| SEC179-2 — public plaintext before authenticated EOF | CLOSED. Fully authenticated private ciphertext preparation precedes public-provider acquisition, and its retained sealed snapshot supplies plaintext without rereading mutable originals. Accepted production `EvidenceArchive.kt` still hashes to `1c30e00a38fe183c68e6f070f9c7ab90da615cbaf35f26e34307c997ca3c40d9`. All six added archive/SAF regressions pass, covering late tag/hash/post-reader failure, all-source completion, immutable snapshots/live policy, actual legacy Primary late GCM failure, zero early provider acquisition and blocked-provider cancellation. |

The accepted scoped runtime corrections and follow-up closures are retained: `RUNTIME179_FIX1` with `RUNTIME179_PEERCLOSE_FIX1`; `RUNTIME179_FIX2` with `RUNTIME179_REAUTH_FIX1`; `RUNTIME179_FIX3` with `RUNTIME179_RUNNER_FIX1`; `RUNTIME179_FIX4`; and `RUNTIME179_FIX5`. Thus handler termination is not relabelled as genuine peer close, stale-picker rejection is not a vacuous dismissed-owner pass, and artifact-collection failure cannot hide original instrumentation status. The final source includes the accepted Main publication envelope and narrowly corrected UI synchronization; the two consolidated production protections above remain intact.

The clock design/implementation and lifecycle findings are closed by `ROUTE179_CLOCK_FIX1` and `ROUTE179_CLOCK_LIFECYCLE_FIX1`. `MINIMAL179_STDIN` accepts the final two noninteractive stdin-isolation arguments without weakening any case, source/clock binding or failure barrier. This candidate's actual evidence resolves their previously pending Android execution requirements. None of the older failed/partial artifacts is promoted or relabelled.

The integrated conclusions retained from the consolidated inspection cover encrypted owner/domain catalogues; Primary/Hidden authority, credential-lock revocation and publication; unchanged original-byte references, integrity/hash and historical lineage/VaultOnly; offline OCR/native cleanup; incidents, owner decisions, timelines and private search; exact one-shot AI consent/credential handling; owned VPN/DNS and private Unix Tor without direct fallback; authenticated bounded GCM/ZIP/PDF opening and explicit plaintext export; SAF authority, original-document protection and cancellation; workflow/lifecycle privacy and compatibility. The final actual cases supply the previously missing execution evidence. No additional concrete Critical/Important defect was established in this evidence continuation.

## Immutable actual evidence

| Identity | Verified value |
| --- | --- |
| Source | `19e3275ef17746e780d64e6a684d94bab2cabdc3` |
| Workflow/run | `.github/workflows/fast-development.yml`, push on `release/1.7.9-evidence-locker`, run `41295673256`, attempt 1 |
| Job | `124815899187`, `evidence-locker-checkpoint`, completed/success |
| Original artifact | `13079244254`, `evidence179-checkpoint`, 3,546,864 bytes |
| Original ZIP SHA-256 | `ed1582ae487f84d63ec98da7dfec50f78c63d0a2d82883145c718f7b0efabfc7` |
| Exact manifest SHA-256 | `f2a622f5fa919e29d5465d0e8bac1dacdd1a4bbb504371b939658f3a671ee2f6` |

The coordinator fetched the original API responses and complete job log. This reviewer independently inspected their saved bytes in `/workspace/scratch/4181ccd09b2e/final179-runtime`: `api-run.json`, `api-jobs.json`, `api-artifacts.json`, `original-job.log`, `original.zip` and immutable `original/evidence179`. The API records bind the successful run, exact source and artifact digest; all ten required build/evidence stages succeeded. The optional dependency-cache save was skipped, as were unrelated jobs; no mandatory Android case was skipped. The original log records strict four-route PASS at `2026-11-01T10:26:30.1202049Z`.

Independent read-only verification established:

- The ZIP size/hash matches the API identity, its entries have unique safe paths, and all **2,370 file entries** match the existing extraction byte for byte.
- `verify_static` accepts the exact manifest and **153 unique passing cases, zero errors/failures/skips/duplicates or missing/extra cases**, with original exit status zero. These comprise 149 nonroute cases and all four route cases. Relevant counts include 21 AI, 28 archive, 31 SAF, 14 OCR and 8 credential cases, plus the Hidden UI/authentication, lock/revocation, original-reference, privacy and other mandatory classes.
- Final/focused gate bindings, pinned OCR model and packaged native report, actual ZIP alignment, release API37 compile metadata and API37 emulator stability evidence all pass. The actual run compiled the integrated app and all instrumentation before executing the tests.
- Every one of the original route proof's **2,159 raw/source hash bindings** matches the immutable extraction or current corresponding source. Original route claims identify source19e, 153 cases and success.
- Read-only `load_case` checks pass for `vpn_revoke`, `vpn_loss`, `tor_revoke`, `tor_death`: **six actual same-counter calibration samples** with required raw identity/protocol/bracket checks. The independent lifecycle verifier accepts all **eight actual Kotlin controls**: competing samples, cancellation before/after claim, timeout before read, completion/deadline race, late stale request, throwing cleanup and cancelled caller.
- The four source-bound `check_case` calls, endpoint observations, actual host phase statuses, strict original packet-policy checks and firewall checks pass. The VPN-loss contract remains `unavailable_after_path_loss`; subsequent host drain is cleanup and is not claimed as remote peer closure. Other required close observations retain the independent causal bounds.
- All **3,062 SLL2 records** pair consistently with the original hash-bound full rendering. The truthful capture witness is index/name `1/lo`, `2/eth0`, `4/enP46808s1`, `5/pg1762`. Fresh actual tcpdump decoding of the unchanged PCAP matches the complete original rendering after removing only the host-dependent interface token/padding. All timestamps, directions, decoded packet content and hex/continuation bytes remain compared. The local extracted binary required `-Z root` because it is not system-installed; this local read-only decode is not claimed as execution of the production namespace path or unchanged CLI.

The reviewer used the read-only validator functions and a fresh decoder, not a mutating full route CLI against the original extraction. No unchanged host test suite or Android execution was rerun. No original evidence was rewritten.

| Bound evidence | SHA-256 |
| --- | --- |
| Original route proof | `b1f826bb93e41958da2d441adecd9cc59b04260068e6a513365ac426131ac929` |
| OCR distribution | `367a13bc325b905fdc36053fb8129f4bf70e8a785122a7d020b33e85032ac8f2` |
| APK native alignment | `e3bd3b1c58aeeb624342481bfc57abea4606500b33bece786b606f4b8f69292d` |
| Final gates | `3ca481d0413e0f9046d02e09e2fee0ae6fef7904d2c0e17f6a76ceb9ed869511` |
| Focused gates | `ccc3c23268084a8772c08b5378b796826c2e196ef84276f2bdee5e86e01736fe` |
| Android SDK receipt | `26996353f2706c3b58ef78282f48d66ed8925c0ac6eb4846e4003c771f21f9e8` |
| Emulator health | `10e570f4374bc0b95f2935098029b854aba52fdc6cb7f4199ca5263f639e01b5` |
| Canonical original raw-route hash map | `6e7a94d242d250dcf4773cde7c4582a32161d7754e0377b505a6dce26bcebeb0` |

The last value is independently computed from the verified original canonical hash map; it is **not** a claim that the pending production replay has already produced an equivalent map.

## Mandatory conditions on gated-build authorization

1. **Before secrets/signing:** independently review the exact control-activation/frozen-source delta and bind this report, source, manifest, original run/artifact and control hashes in the final scope approval. Accepted replay controls are identified by coordinator commit `a2ffbbe5349b942f2876b32c0345666b71fa4c18`; scoped source acceptance is `reviews/RELEASE179_PACKET_REPLAY.md`. The helper still matches its accepted hash `de37d0600025775e586fac01bbeec769fc56648ffee13edda9a0a8f4fb4e43cd`. This report does not authorize unreviewed control changes or application substitution.
2. **Actual production pre-secret namespace replay remains PENDING.** The existing release step must create its separate offline namespace, restore only the original witnessed down/unaddressed dummy-interface lookup, obtain byte-identical fresh decoding and execute the unchanged frozen full route-validator CLI against a separate replay tree. All original raw/source/proof hashes and canonical comparisons must pass; original extraction must remain immutable. Any privilege, environment, interface, decoder, frozen-CLI or comparison failure must stop before secret access. Source/host acceptance and the local normalized comparison do not waive this gate.
3. **Original-key build and actual upgrade/restart remain PENDING.** Preserve original certificate `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`, archived code50 baseline source `4820e176dcb60a3aa2f1bfd7b36e2de19fafee11` and pinned original APK. Required baseline/fixture checks must precede signing access as designed. After building/signing the exact source, verify production package/version/API, signing certificate/v2 signature, packaged model/native distribution/alignment, then execute the actual archived50 seed → candidate51 `install -r` → verify → force-stop/restart → verify flow **before publication**. Never uninstall, clear data or replace keys to obtain a pass.
4. **Final binary and delivery remain PENDING.** Independently verify the resulting single production APK identity/digest and actual upgrade receipts before curation/publication. Emulator tests do not establish arbitrary-traffic absence or physical-device behavior. Fold/owner physical acceptance remains **OWNER_REQUIRED**.

These are staged release conditions, not hidden open source defects. The completed actual emulator evidence satisfies the consolidated source/runtime review requirement; later stages must independently enforce their own fail-closed gates. This reviewer performed no Git operation, CI invocation, secret/signing access, source edit or subagent delegation. Only this canonical report was written during the final evidence continuation.

## Final machine-readable decision
```json
{
  "source_sha": "19e3275ef17746e780d64e6a684d94bab2cabdc3",
  "decision": "APPROVED",
  "critical_open": 0,
  "important_open": 0
}
```
