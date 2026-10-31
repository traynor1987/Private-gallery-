# Task 1 foundation report

Status: foundation implemented and host-verified. This is **not a 1.7.9 release-completion claim**. No Android/Kotlin build, CI, signing, commit, or push was performed by this task.

## Owned changes

- `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceCatalog.java`: exact Java 17 nested-record contract, immutable owner-local catalogue, bounded validation, source fingerprints and monotonic lineage, audited functional mutations, raw-preserving OCR corrections, incident/event operations, search, and explicit accept/reject grouping hints.
- `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceCodec.java`: permitted same-package pure helper; versioned, bounded, explicit binary UTF-8 codec. No Java serialization. Strict UTF-8 and booleans; foreign owner, duplicate IDs/keys, trailing/truncated data, inconsistent references/lineage and quotas rejected.
- `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceEncryptedStore.java`: exact shared constructor/lease interfaces; existing `HiddenRecordStore` metadata cipher/staging reused without modifying it. HMAC domain and fixed owner UUIDs match binding addendum.
- `scripts/tests/Evidence179Checks.java`: real model, disk AES-GCM, HiddenSession authority/revocation, atomic promotion, uncertain durability, concurrent-instance and quota checks.
- `scripts/check_evidence179.sh`: Java host compile with `-Xlint:all` and focused check runner; includes root-owned Java evidence helpers if present.
- This report.

No Kotlin/Main/Gradle/workflow/legacy-cipher or 1.7.8 product files were edited by this task. Shared-workspace changes owned by other workers were left untouched.

## TDD evidence: commands and observed outputs

The executable bootstrap test was written before production code and used reflection so missing functionality produced an assertion, rather than a javac unresolved-type failure. Command:

```text
scripts/check_evidence179.sh
```

Observed initial RED, exit 1:

```text
Exception in thread "main" java.lang.AssertionError: Evidence179 owner-bound catalog foundation is missing
    at Evidence179Checks.main(Evidence179Checks.java:5)
Caused by: java.lang.ClassNotFoundException: uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
```

The bootstrap was then replaced with the full behavioral test source before production implementation. The first compile exposed Java record accessor names shadowing unqualified outer validation helpers (`text`, `mime`, `confidence`); qualifying the outer helper names resolved that compile error. The initial behavioral GREEN was 64 assertions.

Further hard-edge checks were added before their corresponding fixes. Each used the same `scripts/check_evidence179.sh` command. Observed RED assertions, in order:

```text
Exception in thread "main" java.lang.AssertionError: unknown timestamps must follow every known timestamp, including Long.MAX_VALUE
Exception in thread "main" java.lang.AssertionError: generic update replaced immutable original
Exception in thread "main" java.lang.AssertionError: applying analysis after record removal declassified retained content
Exception in thread "main" java.lang.AssertionError: removed/re-registered source conflicts with retained lineage
Exception in thread "main" java.lang.AssertionError: unlink leaves clickable source event references
```

Fixes respectively: explicit known/unknown ordering instead of a colliding timestamp sentinel; historical transition validation in the store; retain old analysis lineage when applying after record removal; reject conflicting identities across live and retained source fingerprints; and implement the coordinator's resolved unlink/move/split event-reference semantics.

Final focused GREEN, exit 0:

```text
Evidence179 host checks passed: 91 assertions (model/codec/lineage/search/real crypto/revocation/concurrency)
```

The existing eight-program regression group was run **once**, as clarified by the coordinator (not all unrelated top-level scripts):

```text
bash scripts/check_private_apps178.sh
```

Observed exit 0, complete output:

```text
PrivateAppPolicy checks PASS
PrivateAppCatalog checks PASS
HiddenPrivateAppStore checks PASS
PrivateAppCommand checks PASS
PrivateAppIconSourceChecks passed
Browser screenshot policy, PNG bounds and memory wipe checks passed
VPN DNS wire identity, CNAME, IPv4/6, truncation and malformed-response checks PASS
Bounded fixture HTTP status/length/truncation and no-EOF-wait checks PASS
```

`git diff --check`: exit 0, no output. No Android compilation or CI was run, and no broad camera/AI/history suites were run.

## Security and data behavior

- Catalogues and all nested collections are copied and immutable. Domain mutations produce one revision and one audit entry; actual no-ops return the existing object. Unknown IDs and inconsistent references fail.
- Original record/source UUID, MIME, SHA-256, size, imported timestamp, and source metadata (including vault-only policy) cannot change in an annotation edit. Extraction/correction history is append-only. A ninth OCR version fails; no earlier extraction or audit is silently discarded.
- Incident lineage unions old fingerprints, linked evidence/event sources and validated incorporated analysis sources. Unlink, record removal, moving, merge and split preserve retained restrictions. Analysis lineage is derived from selected evidence, disregarding caller-provided policy, and survives removal/application. Conflicting re-registration against retained source identities fails.
- Store callbacks are also checked as historical transitions: owner, prior audit prefix, revision/audit increment relationship, original identities, metadata, OCR/correction prefixes, incident lineage and analysis history cannot be rewritten through a reconstructed catalogue.
- Constructor root is a parent directory: the store creates only `evidence-179-primary` or `evidence-179-hidden` beneath it. A canonical evidence-only path plus owner identifies a shared lock across instances. This prevents lost read/modify/write updates and keeps ciphertext staging cleanup away from media and existing catalogues.
- Exact key derivation: `HMAC-SHA256(vaultKey, UTF8("private-gallery:evidence:179:v1:" + owner.name()))`. Metadata UUIDs end in `17901`/`17902` exactly as contracted. Tests independently derive the key and decrypt with the unchanged existing record cipher, and reject swapping ciphertext between owners.
- Per-operation leases, decoded/encoded/derived buffers and returned lease key bytes are wiped/closed. Current authority is checked before and after decrypt/mutation/staging and immediately around promotion/publication.
- Encryption and verification complete before entering the real HiddenSession commit monitor; final atomic promotion runs inside it. Directory sync runs afterwards. Pre-promotion failure/revocation preserves exact previous ciphertext. A post-promotion directory-sync failure propagates failure and leaves authoritative reread to discover the promoted revision: no automatic replay or old-state survival promise.
- The post-promotion fsync test uses reflection **only in the host test** to replace the private `HiddenRecordStore` field with another real `HiddenRecordStore` using its existing `DirectorySync` injection seam. Production exposes no testing hooks; encryption, verified staging and atomic filesystem promotion are real.
- Catalogue quota failures occur before promotion and preserve exact prior ciphertext. Limits include 4 MiB total encoding, 4096 records, 1024 incidents, 20000 audit entries, eight OCR versions, 100000 UTF-16 units per extraction/correction and 65536 per notes/description field. No quota evicts data.

## Event semantics confirmed with coordinator

- Unlink detaches selected evidence IDs from both incident links and every event; event title/text/notes/provenance remain, including provenance-only events with empty ID lists.
- Move/split transfers matching event content with only selected evidence IDs to the target. Mixed events retain only remaining IDs in the source; fully transferred events leave the source; standalone source notes stay there. Target lineage conservatively inherits all contributing source fingerprints for transferred content.
- Merge reunites same-ID events with identical content by unioning evidence IDs, avoiding duplicate timeline events after split/move. Conflicting same-ID event content is rejected rather than lost. Source incidents remain archived with their information preserved.
- Tests verify no clickable old event links, target provenance/notes, conservative restrictions, one audit/revision for an operation, and unchanged original evidence records.

## Minor validation/behavior decisions

- Domain IDs and source IDs use canonical lower-case UUIDs; hashes and stable suggestion IDs use lower-case 64-character SHA-256 hex. MIME values are bounded MIME essences without parameters.
- Zero dates/dimensions mean unavailable; negatives fail. OCR rotations are 0/90/180/270, page count 1..32 and every word/time index must fit the extraction. Confidence is finite 0..100 or -1. Source claims remain claims.
- Tags and participants are unique bounded lists (256 entries, 256 UTF-16 units each); metadata has up to 256 bounded entries; corrections/incident events cap at 20000, analyses at 4096, rejected hint IDs at 20000, and lineage at 4096 sources. Aggregate encoded quota remains binding across all fields.
- Transcript is the most recent correction matching the most recent extraction, otherwise that extraction's raw text. Chronological timeline sorts known times first and unknowns last, stably; manual ordering preserves stored order.
- Search is case-insensitive, all supplied whitespace-separated terms must match, and it covers names, description/notes, tags, explicit participants, latest corrected transcript, retained raw extractions and linked incident titles. Date filters prefer an explicitly sourced capture timestamp, falling back to recorded import time. Records linked only to archived incidents are excluded when requested; unassigned records remain visible.
- Suggestions are bounded/deterministic, using hash, explicit participant, same-hour sourced capture time and exact normalized transcript signals. At most 8192 signal buckets, 256 suggestions, 128 records per suggestion; they only propose groups. Rejection persists stable digest IDs; acceptance revalidates the current suggestion and is a single separately audited mutation.

## Remaining integration concerns

Android adapters/controller/UI and full-app compilation are outside this task's ownership and remain unverified here. Root integration must still recheck every retained fingerprint against live originating originals for remote analysis/export, capture the correct owner authentication/session lease, and preserve revocation/cancellation publication gates. The domain/store alone does not supply those live-source checks or establish a completed release.

# Review fix round 1/5 — historical lineage and buffer retirement

Read the full `/workspace/scratch/4181ccd09b2e/REVIEW179_FOUNDATION.md` and verified both Important findings against the actual implementation. The following supersedes the earlier report's incomplete assertions about reconstructed-successor identity checks and complete codec buffer wiping.

## I1: canonical source identities across every successor lineage

Added a focused reproduction first. Command:

```text
scripts/check_evidence179.sh
```

Observed RED, exit 1:

```text
Exception in thread "main" java.lang.AssertionError: replacement incident declassified historical policy
    at Evidence179Checks.fails(Evidence179Checks.java:23)
    at Evidence179Checks.successorLineage(Evidence179Checks.java:126)
    at Evidence179Checks.main(Evidence179Checks.java:30)
```

`validateSuccessor` now builds a single canonical source-identity map from every previous live evidence fingerprint and every previous incident/analysis fingerprint. It then admits successor live originals, rejecting any changed historical identity, while allowing legitimate newly registered originals. **Every** successor incident and analysis lineage entry must match that canonical map; replacement object IDs cannot reset source policy/hash, and unexplained fingerprints fail. Existing incident/analysis monotonic-lineage and history checks remain.

New analysis lineage must exactly equal fingerprints derived from its currently validated selected evidence. An existing analysis additionally retains its already recorded lineage, allowing removed evidence references without declassification. Even a verified but unselected contributor cannot be injected into a new/existing analysis. This verifies the source set rather than trusting reconstructed lineage.

Regressions cover replacement incident/new-analysis policy changes, hash changes, unexplained sources, surviving incident extra lineage, new/existing analysis extra verified-but-unselected lineage, preservation of unchanged historical lineage, and legitimate original+incident+analysis registration in one successor. Actual encrypted-store admission checks reject substitutions **before overwrite**, verified by exact ciphertext comparisons. The original 91 assertions are retained.

After I1 implementation, the same command progressed through these tests to the independently added I2 RED, demonstrating I1 checks were passing before changing the buffer.

## I2: controlled, wiping buffer growth

Observed separate RED, exit 1:

```text
Exception in thread "main" java.lang.AssertionError: retired codec plaintext buffer not wiped after single-byte growth
    at Evidence179Checks.yes(Evidence179Checks.java:21)
    at Evidence179Checks.codecBufferWiping(Evidence179Checks.java:144)
    at Evidence179Checks.main(Evidence179Checks.java:30)
```

The private codec Buffer now owns capacity growth instead of invoking superclass writes. It allocates bounded replacement storage, copies the used prefix, wipes the entire retired array in `finally`, and only then installs the new array. If copying fails the replacement is also wiped; if allocation fails the old array remains attached for resource close to wipe. Writes validate their ranges before growth, remain synchronized, and cannot exceed exactly 4 MiB. Close wipes the whole current allocation, clears its count, and prevents subsequent writes. It does not preallocate 4 MiB for every catalog validation. Temporary per-string UTF-8 arrays and returned byte-array ownership/wiping are unchanged.

The narrow host probe uses reflection to retain references to the actual private Buffer's initial/intermediate/final arrays. It tests single-byte growth, inherited bulk-write dispatch, unchanged encoded bytes, wiping of all retired arrays before close, final close wiping, quota-failure cleanup, and exact 4 MiB capacity plus one-byte rejection. `--add-opens java.base/java.io=ALL-UNNAMED` is used **only by the host test invocation** to inspect `ByteArrayOutputStream.buf`; no production testing hooks were introduced.

## Durable host entry point and final evidence

`scripts/check_evidence179.sh` now compiles and runs the existing coordinator-owned `EvidenceAuthorities179Checks.java` and `EvidenceTextHints179Checks.java` alongside the foundation suite. Their source files and production authority/hint helpers were not modified by this fix round.

Final command:

```text
scripts/check_evidence179.sh
```

Final observed GREEN, exit 0, complete output:

```text
Evidence179 host checks passed: 117 assertions (model/codec/lineage/search/real crypto/revocation/concurrency)
Evidence179 captured authority 21 checks PASS
Evidence179 text hints: explicit-offset provenance, ambiguous/relative/clock uncertainty, unconfirmed names, malformed/hostile text and bounds PASS
```

`git diff --check`: exit 0, no output. The unchanged eight legacy programs were not repeated; the earlier passing run remains recorded above. Changes in this fix round are limited to the owned Catalog, Codec, foundation test, authorized host entry point, and this report. The encrypted store, coordinator-owned files and legacy files were not changed. No CI, Android build, signing, commit, push, or subagent was used. This remains foundation-only evidence, not release or Android integration approval.
