# Independent fresh Primary58 closure review

Review date: 2026-10-02.

**PF-01: CLOSED at design level. No remaining Critical or Important finding
identified in the reviewed fresh Primary58 closure.**

**PHASE 3 DESIGN RESULT: GO FOR IMPLEMENTATION. PHASE 3 RESULT: NO-GO.**
The fresh Primary58 implementation path may proceed within the authorized
scope. No production, Android durability, signed-build or owner PASS is claimed.

## Original finding and reviewed state

Important PF-01 identified an incompatibility between P1's inherited58-byte
ledger digest of completed ciphertext and the new requirement to install and
reopen a canonical reservation before its first GCM encryption. U82 explicitly
had a pending zero-hash state; P1 U1 did not. A synced q-stage alone could not
satisfy the installed canonical prerequisite. The initial detailed finding
remains in the implementation plan's `primary-fresh-ledger-review.md`.

This independent read-only re-review inspected only the current normative/doc
closure against `6c8773dff5ecfd9f4ced29da0beb5b91220df402`: P1's new fresh58
section, V2/S2 cross-references, P3-PF01..06 and G20. These are local changes
relative to that committed baseline. Unrelated in-flight application source,
pure codecs and actual durable-ledger implementation were not reviewed.

## Closure evidence

| Requirement | Exact reviewed closure | Disposition |
| --- | --- | --- |
| Exact P1-only pending58 state | P1 lines41–45 specifies unchanged offsets/schema1: encryptions1, exact planned blocks, queries0 and32 zero hash bytes. Completed state has a nonzero immutable complete-ciphertext SHA. Existing S1 credential behavior, actual-key identity and legacy Primary encrypted formats remain unchanged. | CLOSED |
| Correct planned charge and durable exclusive registration | P1 lines47–54 defines10+ceil(bodyBytes/16)+1 for padded156-byte AAD, excludes the tag, validates purpose/body/physical/per-key limits, reserves ordinary capacity, exclusively creates canonical state and syncs/reopens the same exact object before GCM. Collision or failure never overwrites an existing key or grants new credit. | CLOSED |
| Private live one-shot and bootstrap-owner circularity | P1 lines56–64 binds original operation/epoch, live attempt/namespace, full header/context/key, planned charge and pinned identities. Its allowance is consumed before invocation and never restored. Owner encryption derives permission from the original scoped writer and newly pinned reserved attempt, not the yet-unencrypted owner. Existing interrupted attempts cannot become fresh. | CLOSED |
| Physical original-output completion | P1 lines66–71 denies pending query/copy/select/established service. Only the same still-live successful writer persists and syncs exact producer output, reopens the same pinned file and verifies physical length/EOF/context/digest. Found/replaced bytes and uncharged decrypt cannot substitute. | CLOSED |
| One-way installed completion and separate query | P1 lines73–79 changes only zero hash to nonzero digest while preserving1/exact blocks/0. Completion uses owned q-stage sync/reopen, atomic replacement, parent sync and exact installed reopen. No downgrade/reset or query/selection follows uncertainty. Subsequent full authentication is separately freshly charged, not implied by completion. | CLOSED |
| Restart, revocation and failure closure | P1 lines81–89 destroys permit/finalizer on restart/revocation/disposal. Pending/inconsistent state is quarantine; complete-looking ciphertext cannot finalize/resume/reconstruct/refund it. A genuinely installed completed state can be freshly inspected and charged after an exception; no blind rollback or filename-driven cleanup occurs. | CLOSED |
| Cross-contract and executable gate mapping | V2 lines30–36 and S2's P1 cross-reference retain installed reservation, exact original-output completion and no pending restart/query authority. P3-PF01..06 and G20 require the associated failures and authority boundaries; all product execution stays PENDING. | CLOSED |

## Independent consistency assessment

The58-byte pending state solves the hash-before-encryption problem without a
second durable format or stage-only allowance. Its zero hash is not a claim to
authenticate future bytes. The private original-writer permit supplies exactly
one fresh invocation after its planned charge is durably installed; arbitrary
disk pending state never supplies that permission. Consume-before-invocation
and fresh attempt/key on retry prevent a provider failure from becoming another
encryption under the original derived key.

The planned GHASH formula agrees with the inherited whole-record framing:
ceil(156/16)=10 AAD blocks, ceil(ciphertext body/16) data blocks and one length
block. The tag is excluded. Purpose, length/overflow and per-key checks remain
mandatory before the reservation. Whole-record body length equals plaintext
length for this GCM framing; no later output may silently exceed its charge.

Successful encryption is not successful durable verification. Original output
must first be synced and independently reopened at the bound physical context
before the original writer can install the immutable digest. A separate newly
charged authentication/readback follows completion. That readback, ordinary
transaction selection and paired receipt/ownership rules still control durable
destination success and source retirement. Completion alone grants none of them.

The pending-to-completed update remains serialized by existing namespace locks
and exact before/after ledger identity. It adds no lock or reverse acquisition;
L2's short authority gates and outside-lock resource cleanup remain required.
If replacement becomes visible but the caller fails, the disk winner is
inspected under fresh authority rather than downgraded from stale memory.
If the winner is still pending after restart or original-writer revocation,
no rediscovered output or new operation can reconstruct finalization authority.

The exception is explicitly P1-local. It does not reinterpret existing S1
credential registration, change U82 fields, migrate legacy Primary records or
allow A2 fresh registration. Public zero hash, matching header or q filename
cannot authorize deletion, resume, copying or established key service.

No new contradiction, failure-open query path, repeat-encryption allowance,
bootstrap-owner cycle or proof/cleanup ownership expansion was identified.
PF-01's initial incompatibility is resolved at the contract level.

## Evidence limits

No tests, Gradle builds, production edits, helper agents or commits were made
by this reviewer. Only this public report was written; the scratch finding was
retained. A local whitespace diff check passed. The parent's reported30
reference passes are attributed to the parent and do not exercise PF01..06,
real encryption, filesystem durability or production permit encapsulation.

Implementation evidence still must cover exclusive registration, every
reservation/invocation/completion interruption, exact planned charges,
private one-shot construction/consumption, original-output replacement races,
pending-service denial, installed completion recognition, fresh query charging
and legacy fixture preservation. Independent implementation review, exact CI,
Android/OEM durability, permanent-signer APK identity and owner physical
acceptance remain pending. No merge, owner migration, later-phase work or
product/owner acceptance follows from this design-only closure.

## Recovery reconfirmation

After the workspace was replaced, the reviewer reconstructed the five PF-01
normative edits and the original review text above from retained tool history
onto the restored exact `6c8773dff5ecfd9f4ced29da0beb5b91220df402` checkout.
No new normative wording was introduced. The following current full Git blob
hashes match the post-image identifiers recorded in the original reviewed diff:

| Recovered file | Current Git blob hash | Original recorded post-image |
| --- | --- | --- |
| PRIMARY_TRANSFER_FORMAT.md | `3da3b457338044c2d6d59b7612f153303f634382` | `3da3b45` |
| VIDEO_AND_USAGE.md | `9aea0f256950bb51f0b706ff67a856a8c9848cd4` | `9aea0f2` |
| STORAGE_FORMAT.md | `a0d23f481eddf6c185b42ae16d27d7a1e15b72de` | `a0d23f4` |
| NEGATIVE_TEST_MATRIX.md | `b4ea07fee7f55d439f25c9616ed3351eb3721caa` | `b4ea07f` |
| EXIT_GATES.md | `e120ee5d1cf03aac4f805e3a1a7a46be0f78f29d` | `e120ee5` |

The recovered clauses were re-read: the pending tuple, exclusive installed
reservation, consumed original one-shot, original-output completion, separately
charged query and restart/revocation denials retain the same closed design
verdict. No remaining Critical or Important finding was identified. A fresh
whitespace diff check passed; no tests, production edits or commits were made.
This recovery appendix is the only new review wording. Other lost implementation,
toolchain or scratch findings were not reconstructed by this reviewer. Earlier
test reports remain historical evidence, not newly executed tests or recovered
production acceptance. **PHASE 3 RESULT remains NO-GO.**
