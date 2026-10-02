# Independent initial-registration and recovery closure review

Date: 2026-10-02. Reviewer: independent Phase3 readiness reviewer.

**IC-02: CLOSED AT DESIGN LEVEL. Scoped affected-path implementation readiness:
GO. Remaining Critical findings:0. Remaining Important findings:0. PHASE 3
RESULT: NO-GO.** This permits implementation under the existing authorization;
it establishes no production, Android, signed-candidate, migration, merge or
owner acceptance. All IC01–10 production cases remain PENDING.

## Inspected baseline and scope

Reviewed the local normative supplement and its explicit conflict edits against
committed checkpoint `6c8773dff5ecfd9f4ced29da0beb5b91220df402`. These are local
documentation changes, not a new reviewed source checkpoint. Original approved
design head was `b4a34a9e2784f8b8497c46be8ac30d56b751c28e`; unchanged main
baseline was `93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10`.

The supplement was read first and matched the requested SHA256 exactly:
`8e822004bdba28d893711d5a545ce3106921402062a95fca91b62a9f9a7ce99e`.
This review covers initial canonical creation, selected-key failure closure,
restricted credential accounting, bounded unselected Primary material,
CURRENT-state4/5/6 paired restoration, proof continuation and P1 pointer staging.
In-flight application changes, codec correctness and production test results
are outside this review. No application source was inspected in this closure
pass, and no application or normative file was changed by the reviewer.

## Original Important finding

Exclusive initial canonical CREATE_NEW can leave a0-byte or partially written
key64 file before the required58/82-byte registration is durable. The former
strict grammar admitted incomplete q stages but rejected these canonicals.
Checking every credential/proof usage child could consequently reject an intact
old selected slot/A2 after an unrelated failed credential-generation attempt,
stranding otherwise recoverable held Primary ciphertext.

The integrated supported windows also included unrelated partial P1 attempt
owners blocking the catalog writes needed for restoration; source-index
retirement committed while CURRENT catalog remained state4; state6 restart
needing a fresh conflict-checked intent binding; and a P1 selector stage with no
explicit admitted physical path. The proposal and original evidence remain in
`.superpowers/sdd/2026-10-02-phase3-implementation/initial-canonical-review.md`.
Pending implementation alone was not treated as a design finding.

## Closure assessment

| Supported window | Exact closure evidence | Assessment |
| --- | --- | --- |
| Initial canonical create/write/sync/reopen failure | `INITIAL_REGISTRATION_AND_RECOVERY.md:10–19` binds exclusive creation to the original live operation, attempt, inode/parents/root and planned state. Full file+parent sync and exact reopen precede the private one-shot allowance; failure, interruption, revocation or disposal ends authority for that key. | Original uninterrupted creation may finish its original write; failed or restarted creation cannot be adopted, filled or resumed. Full uncertain bytes do not reconstruct a permit; retry needs a fresh domain and gives no refund. No unsupported atomic-NOREPLACE or hardlink primitive is assumed. |
| Short canonical isolation | Supplement:21–54; `STORAGE_FORMAT.md:133–169`; `PRIMARY_TRANSFER_FORMAT.md:25–42`; `HOLD_RECOVERY.md:78–86` | Explicit regular key64 bounds are P1/credential0..57 and media/proof0..81. The actual required key always denies when short/missing/malformed/inconsistent. OTHER intact selected keys can freshly charge over admissible unrelated shorts. Shorts authenticate nothing and grant no parsing, copy, ownership, query, encryption, repair or discard authority. |
| Schema1 and credential recovery | Supplement:29–34,52–54; `HOLD_RECOVERY.md:58–127` | The unrelated credential58 exception is explicit only for schema2/restricted A2 admission; ordinary schema1 creation/admission and frozen bytes stay unchanged. Restricted service checks all credential/proof usage children but charges only valid EXISTING selected slot/A2 canonicals. No new canonical, generation, anchor, repair, discard or opaque-media traversal is authorized. |
| Unrelated failed P1 attempt/generation | Supplement:58–110; `PRIMARY_TRANSFER_FORMAT.md:99–106` | CURRENT selector/bootstrap/descriptor/catalog and ALL required selected references remain authenticated. Only unselected material supplying no required current reference gets the exact nonauthority quarantine classification. Owner<=699, optional reservation0..26, files<=16 with purposes1/5/9, optional selection0..26, generation/bootstrap/catalog/descriptor and evidence caps are explicit. Unknown, unsafe, unreadable, replaced or excess material denies. Ordinary malformed-ownership writes/backup blockade stays in force. |
| Capacity and identity throughout restoration | Supplement:41–45,68–102; `HOLD_RECOVERY.md:89–127` | Complete Primary transfer-tree8192/depth6 and existing attempt16/q16 limits still apply; all quarantine and every new restoration entry count. Quarantine is pinned/frozen; only the transaction's own exact new files/counter replacements are authorized. A2 retains LC-01's honest projection exception and E+R+N/proof16 reservation equations. Fresh complete ordinary admission is still required later. Quota denial retains held/ordinary ciphertext; no infinite recovery or automatic reclamation is claimed. |
| Source absent with CURRENT state4 | Supplement:114–127; `RECEIPT_RETENTION.md:47–56`; spec restart table | Fresh paired restoration authenticates exact selected intent/projection/journal, held payload/root/tags/length/SHA and CURRENT authenticated index. Source ID must already be absent with expected post-move relationships and no conflicting reuse. Explicit4→5 recognizes committed retirement, then5→6→7 restores. It changes no source-index membership during activation, needs no new Hidden media verification and grants no unlink, cleanup or receipt authority. A still-ordinary source denies this activation. |
| State6 after interrupted catalog/index writes | Supplement:129–158; `RECEIPT_RETENTION.md:54–67`; `PRIMARY_TRANSFER_FORMAT.md:192–224` | NEW paired authentication/proof binds CURRENT state/intent. Exact M1 already committed permits6→7 without another index promotion; absent source requires fresh current-merge validation and durable revision-incrementing same-state6 re-proposal first. Conflicts preserve the hold; later unrelated writes survive. No direct4→7 or historical whole-index rollback is introduced. |
| Private proof state/revision and one-shot consumption | Supplement:129–156; `HOLD_RECOVERY.md:35–40,147–166` | One challenge reserves one private restoration transaction. Only its own synced, authenticated legal selected catalog changes advance expected state/revision. Every paired gate checks the current expected tuple, original operation/epoch, independent selected credential hashes and deadline. Consume before promotion, or before already-restored handoff; afterward only exact-intent completion remains. Failure supplies no retry allowance. Restart/revocation destroys continuation; disk intent is not persisted authority. |
| P1 physical selector staging and uncertain winner | Supplement:162–190; `PRIMARY_TRANSFER_FORMAT.md:14–21`; `LOCK_ORDER.md:41–46` | The sole optional stage is original fresh pinned `attempts/<attempt32>/selection`,0..26 bytes; complete Selector26 targets only `vault/transfer-v1/selected`. Original creator/operation, exact bytes, authenticated target and expected predecessor are required. File AND attempt-directory sync/reopen precede the short paired rename gate; BOTH source and destination parents sync afterward. Canonical selected closure authenticates on reopen before success/destruction. Uncertainty never authorizes blind rollback/delete; restart cannot promote/adopt/discard a found stage. |

The owner bound is consistent with frozen S2:172-byte F1 overhead plus527-byte
maximum owner body equals699. The quarantine limits do not substitute for R,
context, ownership or AEAD checks on selected references. A full pending P1
zero-hash58 retains PF-01's private original-writer semantics; IC-02 does not
turn that pending state into established service or change S1/legacy formats.

The narrowed restoration path writes only the chosen hold's required
catalog/intent/legacy-index ownership state. It cannot authenticate unselected
records for cleanup, mount old generations, release receipt dependencies,
produce a Hidden receipt, grant Hidden media access or unlink payloads.
`LOCK_ORDER.md:13–49,52–81` retains the total order, short paired mutation gates,
outside-authority fsync/readback and deferred cleanup barriers. The supplement
adds no reverse acquisition, domain key bridge or fresh restricted Hidden key.

## Verification and remaining gates

Inspected the explicit edits in S2/P1/V2/A2/R2, matrix IC01–10, G20/G46/G48/G63
and DG05, spec restart rows, implementation plan, report and current readiness
status. IC01–10 cover creation faults, old selected proof isolation, short
grammar/ownership, unrelated P1 quarantine, state4/state6 recovery, private
proof advances/consumption, quota and both-parent pointer durability. These
cases and the product gates are still marked PENDING. DG05 was PENDING when
inspected; this report supplies its independent design review, not production
PASS. Status documents still saying re-review pending require the controller's
separate status update after accepting this report.

No Gradle, JVM, Android, concurrency, IC fault test, owner-data operation or
remote verification ran during this review. The parent reported30 design
reference tests and whitespace checks passing; those do not execute IC01–10 or
prove product durability. Earlier independently run reference tests likewise
do not establish these newly exposed production windows. Only this report was
written. No commit/push, merge, migration, signed build or later-phase work was
performed.

No remaining Critical/Important design ambiguity was found within the supported
windows and stated finite capacity/evidence limits. Production implementation
and independent task/integration review must establish the actual predicates,
physical ordering and fault behavior before product gates can pass.

## Exact inspected document fingerprints

SHA256 hashes below identify the inspected local text; they are not claims that
these files were committed or that subsequently edited status text is identical.

| Inspected file | SHA256 |
| --- | --- |
| `docs/phase3/INITIAL_REGISTRATION_AND_RECOVERY.md` | `8e822004bdba28d893711d5a545ce3106921402062a95fca91b62a9f9a7ce99e` |
| `docs/phase3/STORAGE_FORMAT.md` | `4118d5ebfbfff45f56f49ddd50a4e83484e5db48bbec80db44597e099338dcb1` |
| `docs/phase3/PRIMARY_TRANSFER_FORMAT.md` | `4bf8414211d70f510af2a84c34bc3e2b774738708e1d22dc5443cec50fb8fea4` |
| `docs/phase3/VIDEO_AND_USAGE.md` | `007793de26a1904c2b8ae093c891a5af65fc03ff088e3adb033bd1880e789b17` |
| `docs/phase3/HOLD_RECOVERY.md` | `5d6a35101f6571d9477218b28d2dfbd6e15e57b1013ef3246a081fe67915c92a` |
| `docs/phase3/RECEIPT_RETENTION.md` | `54d3f9bf06f155baa629db9537f4bcf64c36f7d2641ef6e22a3e732df0125551` |
| `docs/phase3/LOCK_ORDER.md` | `72db85bbac2871d2d3c6deb792c7fff523d974ccbc23d4c3fba17a3f2e8e72cf` |
| `docs/phase3/NEGATIVE_TEST_MATRIX.md` | `533bf58a872e7df52a1097afff418806a7db2c878c748d676a494d525fdc8923` |
| `docs/phase3/EXIT_GATES.md` | `5d08548679cd0ba24fdfce446e9a8d2de1a8dee04675067af60bd0dc9822b999` |
| `docs/superpowers/specs/2026-10-02-phase3-hidden-media-transfer.md` | `88dc7dcb1eb19ec383740a48d7d5f2732d95f581464c70a0c2e47421ae9790cd` |
| `docs/superpowers/plans/2026-10-02-phase3-implementation.md` | `f690a46f2898b655077c498f7f421fca8fc062c81a3df1bd048f66c13822a3d5` |
| `PRIVATE_GALLERY_2_0_PHASE_3_REPORT.md` | `65b8873910308460e9001b4c19ed889281f8798ea79cb34eb90e245e0665ce92` |
| `docs/phase3/IMPLEMENTATION_EVIDENCE.md` | `18abe1331e383c9ba48003d301b24baa5ac679ecac465290325d29f3058dee0d` |
| `.superpowers/sdd/2026-10-02-phase3-implementation/global-constraints.md` | `122e7bd50eb52aba8fff782eafe27b571ff6745c3768b430008aea57488e1dd8` |
| `.superpowers/sdd/2026-10-02-phase3-implementation/progress.md` | `0e5aa252afb438b38075941861183341f4189a419474fbd66e802c334614e678` |
