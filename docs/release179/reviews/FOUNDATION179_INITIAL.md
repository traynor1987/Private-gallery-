# PrivateGallery 1.7.9 foundation review

Date: 2026-10-31. Scope: Task 1 catalog/codec/encrypted store and native Task 2 captured lease factories only. This is not Android/Main integration approval or release approval.

## Verdicts

| Scope | Spec verdict | Security/quality verdict |
|---|---|---|
| Task 1 catalog, codec, encrypted store | Changes required: historical source identity validation and plaintext buffer wiping are incomplete | Changes required: two Important findings; no Critical finding |
| Native Task 2 `EvidenceAuthorities` factory | Pass within the factory-only contract | Pass within scope; no Critical or Important finding; caller lock/epoch integration remains unverified |

Read the Task 1 brief, the full `CONTRACTS.md` including its binding review addendum, Task 1 report, authority report, all seven files in the review package, and the unchanged `HiddenRecordStore` / `HiddenSession` implementations that determine actual crypto and locking behavior. Inspected the real working-tree code corresponding to the package. Existing evidence accepted for this review: 91 focused assertions, eight existing regression programs, and 21 captured-authority checks reported PASS; root independently reran the 91/21 on this code. I did not rerun those passing suites. I ran only two small, new synthetic probes for the findings below. No product files, credentials, workflows, original media, signing assets, or certificate were edited; no CI, commit, signing, or Android build was run.

## Important findings

### I1 — Reconstructed successors can change historical source policy through replacement lineage

Location: `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceCatalog.java:170-176`, especially line 174; admission path `EvidenceEncryptedStore.java:45-50`.

`validateSuccessor` builds `priorSources` from all previous evidence, incident lineage and analysis lineage, but checks that map only against the successor's live `Evidence` records. The incident loop checks monotonic lineage only when the incident ID remains the same. New incident IDs and new analyses do not have their fingerprints checked against historical source identities. The constructor checks consistency only within the new snapshot.

Concrete accepted transition:

1. Start with vault-only evidence source S and an incident containing derived text with S's restricted fingerprint.
2. Reconstruct a successor that removes that evidence and incident, appends one audit entry/revision, and adds a different incident ID containing the copied derived text and S's same UUID/MIME/hash/size with `vaultOnly=false`.
3. The constructor and `validateSuccessor(before)` both accept it. The store calls that validator and then stages/promotes it; there is no subsequent lineage check.

A temporary package-local probe against the actual model/codec produced:

```text
successor accepted changed retained source policy=true
equivalent domain upsert rejects unverified lineage=true
```

The equivalent sequence through the domain `removeEvidence`, `deleteIncident`, and `upsertIncident` rejects the supplied fingerprint. Thus the reconstructed-catalog path bypasses a check deliberately enforced by the domain path. This is a source policy/integrity identity defect even though no remote egress adapter is present yet. Correct future live-source revalidation should independently block egress when the actual source is restricted, changed or unavailable; that defense does not make the persisted historical fingerprint trustworthy.

Required change: validate every successor lineage fingerprint, including newly created incidents and analyses, against one canonical source-identity map containing prior sources plus validated successor live originals. Reject any historical source UUID whose fingerprint changes, and reject unverified lineage that cannot be explained by those sources. Preserve legitimate new originals registered in the same update and monotonic lineage for surviving incidents/analyses. Also enforce the contract that newly saved analysis lineage is derived from its validated selected evidence, rather than trusting arbitrary reconstructed lineage. Add a focused regression for replacement incident/new-analysis lineage with a reused source UUID and changed policy/hash; retain current deletion/reference semantics.

### I2 — Codec growth leaves retired plaintext arrays unwiped

Location: `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceCodec.java:13-17` and `21-24`; invoked for every catalog validation at `EvidenceCatalog.java:64-65` as well as actual store encoding.

`Buffer` inherits `ByteArrayOutputStream` capacity growth. Its `write` overrides bound the total but call the superclass, which replaces the backing array when it grows. `close()` wipes only the currently attached array. Earlier backing arrays contain plaintext catalog prefixes and are abandoned without wiping, on successful encoding and on exceptional encoding. Since every catalog constructor encodes to enforce quota, this happens during ordinary edits as well as persistence.

A temporary probe used reflection solely to retain references to the old and current backing arrays while writing synthetic bytes through the actual private Buffer, then closed it:

```text
growth replaced buffer=true
retired plaintext buffer wiped=false
final plaintext buffer wiped=true
```

The probe used `--add-opens java.base/java.io=ALL-UNNAMED` only to inspect array contents; it changes no product behavior. This does not write plaintext to disk or demonstrate a remote exploit. It does violate the explicit transient encoded-buffer wiping requirement, leaving recoverable heap remnants beyond the lifetime of the intended byte buffers.

Required change: use bounded fixed-capacity storage or implement controlled growth that copies the used bytes and wipes the retired array before replacing its reference, including exceptional paths. Fixed 4 MiB allocation is simplest but incurs 4 MiB per validation/encode, so controlled wiping growth is preferable if allocation pressure matters. Ensure no superclass path can grow behind the custom logic, keep the exact 4 MiB quota, retain wiping of per-string UTF-8 temporary arrays and final returned arrays at their owning call sites. Add a narrow growth/close/exception regression that checks retired arrays, rather than only the final buffer.

## Reviewed behavior with no additional blocking finding

- Original evidence fingerprints, import timestamps, source metadata and extraction/correction prefixes are immutable for surviving records through the regular domain API; collection values are copied. Raw OCR is preserved, ninth extraction is rejected, corrections remain separately appended, page indexes/confidence/UTF-16 values are validated.
- Regular incident operations preserve source fingerprints on unlink/removal, archive source incidents on merge, preserve mixed-event provenance on transfer/split, and require current record IDs. Analysis save derives fingerprints from selected records, and existing analysis application retains old lineage. The defect in I1 is the reconstructed successor boundary, not those regular domain operations.
- Functional mutations append one audit/revision, and known ordinary no-ops return the same catalog. The store checks audit-prefix and revision-delta preservation. No separate Critical/Important audit finding was identified.
- Codec is explicit and versioned, bounded to 4 MiB, with bounded collection/string lengths, strict UTF-8/boolean parsing, and rejection of foreign owner, duplicate IDs/keys, trailing/truncated data and inconsistent references. No unbounded serialized-object construction or disk plaintext was found. I2 concerns heap buffer lifecycle.
- HMAC domain text and fixed owner UUIDs match the binding addendum. The store uses a canonical owner-specific evidence subdirectory and one shared lock per canonical root/owner across instances, with no access to legacy media/index records.
- Encryption and verification occur before `KeyLease.commit`; only final atomic promotion is inside the authentication monitor. Pre-promotion failures preserve the old file. Directory fsync occurs afterwards; failure propagates without replay and requires authoritative reread. The failure message and report correctly avoid promising that old ciphertext survived post-promotion failure.
- Store-owned vault/derived/decrypted/encoded arrays are wiped in `finally`; leases close on all normal/exceptional paths. The existing cipher wipes sealed/read/verification arrays. Java strings and crypto-provider internals are not claimed to be securely erasable. I2 remains a separate fix for explicitly owned codec arrays.
- The Primary factory captures key identity and a caller-supplied immutable authentication guard, copies a per-operation key under the caller's commit gate, and checks/wipes on observed staleness. Hidden uses the actual captured `HiddenSession.Authority` and operation, with no Primary fallback. Observed revocation closes/wipes its operation copy. Native commit checks prevent already-stale operations from promoting.
- Lock order observed here is store lock → Primary commit gate or Hidden session monitor. Hidden `authorized` callbacks must remain nonblocking and must not take a reverse-order owner/store lock. Retirement and authentication invalidation must share the appropriate commit boundary; revocation callbacks must set flags/enqueue cleanup rather than synchronously acquire the store lock or reenter forbidden session work. The factory cannot supply these future caller guarantees on its own.

## Evidence limits and integration gates

No controller, live source reader, cancellation registry, resumed-vault callback ticket, Android UI/publication gate, AI/export egress adapter, or full-app integration was approved. The native lease tests exercise epoch/identity and actual HiddenSession behavior, but are not proof of future Main/callback lock ordering. Future integration must revalidate every retained fingerprint against live original bytes/policy, reject stale publication/SAF callbacks, and preserve the existing 50-byte/original/credentials/API 37 certificate constraints. This review did not independently inspect or certify unrelated release preservation assets.

Minor maintenance note: `scripts/check_evidence179.sh` compiles all Java evidence helpers but runs only `Evidence179Checks`; the 21 authority checks currently rely on a separate invocation. Once the foundation fixes are integrated, make the native-authority invocation durable in the intended host validation entry point so future changes do not silently omit it. This is not an additional blocking finding.

## Reviewed hashes

Review package SHA-256: `140f2922eed755e1590f0117d22ea1820124f16e45355f3100e9f0aca88554d3`.

| File | SHA-256 |
|---|---|
| EvidenceAuthorities.java | eab05a6ab1f2501fe5625c2671a146a77181b501693196be5eccec93de265704 |
| EvidenceCatalog.java | 51cb03c53a920cdeba2266edf87c97e4712fbf4c4c7c618c4a9f5ac97fb4f900 |
| EvidenceCodec.java | c58cd6550bbbe82acd9bdbd7834798d020be805a6fcf688479f319fff56786a7 |
| EvidenceEncryptedStore.java | 861bdc5c0e0f92aaeb55d9c6d8cd7abd6bdfe3853f2e4c7eab3604bc9d931bb8 |
| Evidence179Checks.java | bb37147f6de1b8c3ff754d1eeb23e945ce53b507d31095375d8c9d0febeae364 |
| EvidenceAuthorities179Checks.java | f1b3681e56e8ec226e351c2f0884d527d8d2375bc9e8ac467ed7f7f9eae2388d |
| check_evidence179.sh | 62179fafe6930b22b3d594666993dffcea905433d2d7fd6190d6b2707c01ac4d |
