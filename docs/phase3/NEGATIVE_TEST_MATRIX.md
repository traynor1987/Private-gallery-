# Phase 3 proposed negative-test matrix

**PHASE 3 RESULT: NO-GO**

All cases below are **PENDING**. They are requirements, not execution evidence.
Retain the original Phase 2 42-case matrix and 50 gates separately; these new
cases do not replace or renumber that accepted evidence.

LS-01 closes additional ledger-staging requirements before durable storage
implementation. These cases are also PENDING production execution; unchanged
reference tests do not satisfy them.

| ID | Adversarial condition | Required result | Status |
| --- | --- | --- | --- |
| P3-LS01 | Restart with bounded0..82-byte media/proof or0..58-byte Primary/credential `q<32hex>` stages | Admit exact nonauthority quarantine shape; no replay, refund, repair or discard | PENDING |
| P3-LS02 | Unknown stage name, directory, link, oversize, inaccessible or replaced identity | Fail affected admission closed and preserve bytes | PENDING |
| P3-LS03 | Valid canonical counter plus leftovers; missing canonical counter plus complete-looking stage | Fresh charge from canonical may succeed; stage never reconstructs missing counter | PENDING |
| P3-LS04 | Failure before/during/after creation, write, file/directory sync, stage reopen, replacement, parent sync and canonical reopen; between video ledgers | Zero GCM/lease until required installed reopens; visible charges stay spent; restart has no lease | PENDING |
| P3-LS05 |16-stage quota, public-name cleanup, restart discard or wrapper retirement | Deny new updates at quota; preserve stages/canonical counters; only original updater can discard its own exact pinned uninstalled stage | PENDING |
| P3-LS06 | Restricted recovery with bounded proof/credential stages and opaque corrupt media | Use selected canonical credentials/A2 only; no media admission, stage replay/discard or proof authority broadening | PENDING |

LC-01 explicitly separates restricted credential-projection capacity from ordinary
whole-tree capacity. These additional executable requirements remain PENDING.

| ID | Adversarial condition | Required result | Status |
| --- | --- | --- | --- |
| P3-LC01 | Unavailable/inaccessible/oversized opaque media with valid bounded credential projection | Restricted selected charging may proceed without any media traversal or full-tree success claim | PENDING |
| P3-LC02 | Projection E+R+N at8192/8193 or proof-stage bound16/17 | Reserve exact permitted count; deny excess before creation/GCM; no lease | PENDING |
| P3-LC03 | Restricted transient stage exceeds ordinary whole-tree8192 and interruption leaves it behind | Preserve stage; projection remains bounded; ordinary full inventory denies, no false global PASS | PENDING |
| P3-LC04 | Unknown projection name/link/oversize/replacement/unreadable checked scope | Deny affected restricted admission and preserve bytes | PENDING |
| P3-LC05 | Missing selected canonical counter or attempted restricted creation/repair/discard/media mutation | Deny; no canonical reconstruction, failed-stage cleanup or expanded authority | PENDING |
| P3-LC06 | Restricted success followed by full-inventory failure, revocation or restart | No general session/cached census/reservation/lease; charges remain spent; ordinary admission requires fresh full inventory | PENDING |

| ID | Area | Adversarial condition | Required result | Status |
| --- | --- | --- | --- | --- |
| P3-N001 | Authority | Primary-only operation or Primary PIN/key/biometric/recovery presented to Hidden | Reject before Hidden lookup; no destination write | PENDING |
| P3-N002 | Authority | Hidden-only operation presented to Primary source/hold | Reject before source lookup; no metadata or plaintext | PENDING |
| P3-N003 | Authority | Foreign item/object ID deliberately collides | Reject fixed-container/context mismatch before cache/storage lookup | PENDING |
| P3-N004 | Authority | Either operation locks, expires or closes during copy | Cancel transport; no publication/removal; source preserved | PENDING |
| P3-N005 | Authority | Lock/unlock ABA while old producer, callback or receipt returns | Old operation remains dead; never acquire ambient replacement | PENDING |
| P3-N006 | Authority | Timer delayed beyond monotonic expiry | Read/commit still denied | PENDING |
| P3-N007 | Authority | Either lease closes while same epoch remains current | checkValid denies; isCurrent/publish cannot authorize mutation | PENDING |
| P3-N008 | Authority | Both scopes valid at prepare, one revoked before destination selection | No stale selection or source retirement | PENDING |
| P3-N009 | Authority | Both valid at verification, one revoked before hold commit | Retain ordinary source; no destructive promotion | PENDING |
| P3-N010 | Authority | Concurrent credential/settings mutation during transfer | Serialize selected predecessor; preserve all media/journal/receipt/usage reachability | PENDING |
| P3-N011 | Authority | Replayed persistent operation ID after restart | ID is evidence only; fresh independent operations required | PENDING |
| P3-N012 | Authority | Transfer bridge attempts raw-key/root-selector export | No such public interface; adapter owns its key/root | PENDING |
| P3-N013 | Format | F1 wrong container/master/object/generation/purpose/algorithm | Fail closed without legacy/other-container fallback | PENDING |
| P3-N014 | Format | Every authenticated header byte tampered | Reject context/framing or authentication; preserve originals | PENDING |
| P3-N015 | Format | Unknown schema/enum, duplicate IDs, unsorted entries or trailing bytes | Reject before writable admission | PENDING |
| P3-N016 | Format | Negative/overflow/mismatched lengths, extreme counts/UTF8 | Bound allocation/work; unavailable without fresh index | PENDING |
| P3-N017 | Format | Schema-1 empty foundation upgraded with missing/unauthenticated predecessor | Deny; never infer upgrade from absence | PENDING |
| P3-N018 | Format | PIN/settings/recovery/biometric change after media import | Media/crops/collections/receipts/holds remain authenticated and reachable | PENDING |
| P3-N019 | Format | Missing selected index or catalog in schema 2 | Unavailable; no reconstructed empty container | PENDING |
| P3-N020 | Format | Descriptor reference swapped to different valid index/receipt/journal | Exact encrypted hash/context binding rejects | PENDING |
| P3-N021 | Format | Credential retirement while media ledger/verification snapshot reachable | Retire obsolete wrappers only; media usage/snapshots survive | PENDING |
| P3-N022 | Format | Unknown root/file/directory, symlink, inaccessible entry, missing file identity | Fail closed; no traversal/admission | PENDING |
| P3-N023 | Format | Root, ancestor, index or payload replaced between validation/open/read/commit | Pinned same-object identity rejects; source preserved | PENDING |
| P3-N024 | Format | Inventory exceeds 8192 entries/depth6 or any aggregate quota | Deny before promotion; existing readable state preserved | PENDING |
| P3-N025 | Video | Purpose10 header tag fails | No playback/plaintext/chunk service | PENDING |
| P3-N026 | Video | Chunk index/order/count/size/length/salt/context disagreement | Reject exact framing | PENDING |
| P3-N027 | Video | Missing, duplicated, reordered or appended chunk/bytes | Reject entire object for receipt | PENDING |
| P3-N028 | Video | Corrupt chunk outside initially played range | Full verification rejects; no move/cleanup | PENDING |
| P3-N029 | Video | Seek after revoke or player/retriever callback after route exit | Deny read; owned resources closed, controllable buffers cleared | PENDING |
| P3-N030 | Video | Empty/max/over-limit video and last partial chunk | Exact frozen encoding/EOF; over-limit safely rejected | PENDING |
| P3-N031 | Video | Chunk ledger charges separated by nonce/index rather than actual key | Test rejects this accounting; shared-purpose-key budget enforced | PENDING |
| P3-N032 | Video | Encryption invocation/GHASH charge would exceed either budget | Deny before invocation; no nonce/salt reuse | PENDING |
| P3-N033 | Video | Successful/failed verification reaches total query cap | Charge before GCM; close service at limit | PENDING |
| P3-N034 | Video | Ledger missing/malformed/detectably inconsistent, interrupted ledger update | No reset/fresh regeneration; fail closed. Replay of an otherwise valid old private-state ledger is an explicit hostile-rollback limitation | PENDING |
| P3-N035 | Video | Encryption process dies then attempts to resume same generation salt | Abandon attempt; fresh salt/nonces on retry | PENDING |
| P3-N036 | Import | Initially unknown provider length cannot be bounded/measured/reopened, or byte/time/progress limit exceeded | Reject without plaintext spool; reopenable input is allowed only after bounded first pass and exact second-pass match | PENDING |
| P3-N037 | Import | First/second provider read differ in bytes or length | No selected destination or source deletion | PENDING |
| P3-N038 | Import | Early EOF, extra bytes, IO error or changing provider MIME | Exact digest/length/format admission; stage invisible | PENDING |
| P3-N039 | Import | Provider URI grant expires or access revoked mid-read | Cancel safely; no success from EOF | PENDING |
| P3-N040 | Import | Malicious display name/path/URL or oversized metadata | Sanitize bounded display strings; never use metadata as path; omit source URI | PENDING |
| P3-N041 | Import | Photo Picker launched with non-immediate Hidden auto-lock setting | Synchronous revoke/clear before launch despite grace | PENDING |
| P3-N042 | Import | Late/duplicate/foreign picker result, replacement, expiry or process death | No authority revival; ticket dropped or exact nonce bound | PENDING |
| P3-N043 | Import | Return from picker while private route closed | Fresh discovery/unlock and explicit confirmation before import | PENDING |
| P3-N044 | Import | Inspect app temp/cache/player/preview storage after success/failure | No app-created plaintext media files remain | PENDING |
| P3-N045 | Import | Direct import requested as external move/delete | Not offered; deliberate device/provider original retention disclosed | PENDING |
| P3-N046 | Source | Legacy whole-GCM bad final tag after plaintext emitted to staging | Producer failure blocks receipt/selection; original unchanged | PENDING |
| P3-N047 | Source | Legacy whole-GCM plaintext digest differs from authenticated source metadata | Deny commit/removal even with valid tag | PENDING |
| P3-N048 | Source | Whole-GCM source exceeds64MiB/provider buffers excessively | Refuse safely; no automatic Primary migration; measured memory gate | PENDING |
| P3-N049 | Source | PGVIDEO1 uses header from one file and chunks from replacement | Same pinned opened object required; deny mismatch | PENDING |
| P3-N050 | Source | Primary source name/MIME/time/provenance/restrictions/crop/state changes | Full fingerprint mismatch denies move; no digest-only revision | PENDING |
| P3-N051 | Source | Membership/favourite/cover changes while payload unchanged | Revalidate complete source context; conflict rather than overwrite | PENDING |
| P3-N052 | Source | Source becomes Trash, held, deleted or reuses an ID | Deny original snapshot; no unrelated mutation | PENDING |
| P3-N053 | Source | Source producer fails before normal consumer EOF or cancellation races | Failure surfaced; bounded transport closes and producer joined | PENDING |
| P3-N054 | Source | Transfer path calls openVideoSession or migration/export API | Regression prohibits read-side Primary re-encryption/plaintext export | PENDING |
| P3-N055 | Receipt | Forged Boolean/public receipt-shaped DTO | Cannot mint VerifiedDestination/source-removal authority | PENDING |
| P3-N056 | Receipt | Receipt replayed for different transfer/source snapshot/policy/destination | Exact paired journal/snapshot/context mismatch rejects | PENDING |
| P3-N057 | Receipt | Ciphertext/index/receipt hash or full-auth mode wrong | Deny selection/removal; no filename/row-only success | PENDING |
| P3-N058 | Receipt | Receipt prepared but destination not durably selected | No source retirement | PENDING |
| P3-N059 | Receipt | Selection rename succeeds but directory sync/reopen fails | No durable success/removal; disk winner inspected after fresh auth | PENDING |
| P3-N060 | Receipt | Current credential generation differs from original receipt's index | Retain/authenticate immutable verification index and current membership; never silently discard receipt dependency | PENDING |
| P3-N061 | Receipt | Destination removed/trashed/corrupt/superseded before move/hold cleanup | Deny retirement/cleanup and retain valid source/hold | PENDING |
| P3-N062 | Receipt | Matching plaintext digest belongs to distinct destination request | Do not dedupe or authorize deletion; distinct item or exact retry only | PENDING |
| P3-N063 | Hold | Move source index removal before durable hold intent | Test denies ordering; original remains ordinary | PENDING |
| P3-N064 | Hold | Intent durable, ordinary source still present at crash | Authenticate exact ownership; finish/cancel idempotently without payload deletion | PENDING |
| P3-N065 | Hold | Source absent from ordinary index, hold activation interrupted | Authenticated intent preserves recoverable source metadata/ciphertext | PENDING |
| P3-N066 | Hold | Generic Trash/delete/video reconciliation sees held payload | Never expire/unlink retained original | PENDING |
| P3-N067 | Hold | Hold missing/corrupt or claims unrelated original payload | Unavailable; preserve bytes; no destructive reconciliation | PENDING |
| P3-N068 | Hold | Primary-only/Hidden-only access, restore or cleanup of hold | Both fresh authentications required; no ordinary disclosure | PENDING |
| P3-N069 | Hold | Hold reaches 30days or app performs startup garbage cleanup | No age-based deletion | PENDING |
| P3-N070 | Hold | Rollback after new unrelated Primary writes | Merge one exact item; later writes survive | PENDING |
| P3-N071 | Hold | Rollback conflicts with reused ID, deleted/renamed collection or changed cover | Explicit conflict; no overwrite | PENDING |
| P3-N072 | Hold | Interrupted rollback before/after Primary index promotion | Inspect selected index; preserve one valid restored/held copy | PENDING |
| P3-N073 | Hold | Permanent cleanup with stale/different/trashed Hidden destination | Deny; reverify current durable destination with fresh operations | PENDING |
| P3-N074 | Hold | Interrupted cleanup before/after payload unlink/catalog selection | Authenticated intent explains exact missing payload; no unrelated unlink | PENDING |
| P3-N075 | Backup | Backup export while transfer/hold unresolved | Refuse before publishing archive; no silent omission | PENDING |
| P3-N076 | Backup | Destructive backup restore/replacement while ANY retained transfer (including terminal/ACK) or unresolved attempt/ownership exists | Refuse before any archive/staging/root/key write; permit transfer blockade removal only after paired pruning and authenticated empty/resolved inventory | PENDING |
| P3-N077 | Backup | Ordinary Primary error/counter reveals Hidden or hold identity | Neutral unavailability; no names/counts/hints | PENDING |
| P3-N078 | Backup | Primary/platform backup enumerates Hidden sibling | Legacy allowlist/manifest/packaged exclusions remain enforced | PENDING |
| P3-N079 | Backup | Legacy fixture/archive read/write after namespace addition | Frozen bytes/compatibility unchanged; no new backup-v1 schema | PENDING |
| P3-N080 | Privacy | Primary search/collection/favourite/trash/cache sees Hidden data | Strict independent indexes/resources/identities | PENDING |
| P3-N081 | Privacy | Source URLs/collection names copied without explicit destination mapping | Do not copy by default; restrictions/provenance preserved privately | PENDING |
| P3-N082 | Privacy | Existing vaultOnly/provenance restriction weaker after transfer | Preserve restriction; no new egress route | PENDING |
| P3-N083 | Privacy | Hidden image/preview/crop decode exceeds byte/pixel/memory quota | Reject/release without app plaintext staging | PENDING |
| P3-N084 | Privacy | Screen-off/background/Primary lock/recreation while resource prepares | Revoke first, cancel/close/join, no stale UI/player publication | PENDING |
| P3-N085 | Privacy | Native preview/video reader survives lease close and later lock | Session-owned resource closes on revoke; no late data | PENDING |
| P3-N086 | Privacy | FLAG_SECURE applied after first protected frame or cleared during exit | Ordering regression denies unprotected frame; protection remains latched | PENDING |
| P3-N087 | Retry | Same transfer ID retried after any committed boundary | Join exact IDs/revisions; no duplicate destination/source removal | PENDING |
| P3-N088 | Retry | Same transfer ID reused for different bytes, metadata or action | Conflict/corrupt; never change original transaction silently | PENDING |
| P3-N089 | Retry | Two concurrent transfers select same source or overlapping destination/hold | Serialize predecessor/ownership; no double move or deadlock | PENDING |
| P3-N090 | Retry | Cancellation callback arrives after durable commit | Inspect authoritative outcome; report committed copy vs cancelled stage accurately | PENDING |
| P3-N091 | Recovery | Missing device maintenance key with media extant | Independent credential repair only; preserve selected media/ledgers; no reconstructed delete authority | PENDING |
| P3-N092 | Recovery | Forward recovery APK handles schema2/hold with later new writes | Keep current authenticated state; no blind old-index rollback | PENDING |
| P3-N093 | Recovery | Old APK/forced downgrade proposed as rollback | Not used as media/hold recovery; no uninstall/data clear | PENDING |
| P3-N094 | Recovery | Owner-only copy chosen for destructive fault fixture | Never run destructive tests on it; use expendable synthetic fixture | PENDING |
| P3-N095 | Recovery | Primary hold valid but ordinary Hidden admission fails due media-index corruption or exhausted selected-metadata query ledger | Reviewed restricted paired-auth hold recovery must remain possible with adequate surviving credential evidence; no Primary-only bypass or media/delete capability | PENDING |

## Restart fault expansion

Each boundary below needs BEFORE and AFTER injection with: ordinary exception,
process death/restart, ENOSPC, and cancellation/revocation. Android synthetic
fixtures also cover supported device/emulator directory sync and reboot/power
interruption evidence. A test must inspect authenticated authoritative disk
state after fresh authentication, rather than assert event ordering alone.

| ID | Boundary | Restart invariant | Status |
| --- | --- | --- | --- |
| P3-F01 | Primary intent file write | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F02 | Primary intent file sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F03 | Primary intent directory sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F04 | Hidden attempt reservation write/sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F05 | media usage charge replacement/sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F06 | each destination chunk write/sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F07 | destination parent-directory sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F08 | full destination reopen/authentication | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F09 | verification-index snapshot write/sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F10 | receipt write/sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F11 | journal write/sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F12 | descriptor/index generation completion | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F13 | selected pointer stage/sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F14 | selected pointer atomic rename | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F15 | selected root sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F16 | selected pointer/index/receipt/object reopen | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F17 | Primary hold-intent write/sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F18 | Primary source-index stage/sync | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F19 | Primary source-index rename | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F20 | Primary source-index directory sync/reopen | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F21 | Primary hold activation selection | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F22 | final paired journal update | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F23 | rollback source-index promotion | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |
| P3-F24 | hold-cleanup intent/payload unlink/catalog selection | At least one valid ordinary/held source remains until selected Hidden copy is fully independently verified; no invisible stage/receipt authorizes removal | PENDING |

Every test records its implementation/test path, exact SHA, command, result and
fixture ownership. Actual APK/provider/native lifecycle evidence is distinct
from JVM tests. Do not mark hardware power-loss or owner observations PASS from
event-order mocks, API availability or CI color.

## Design-closure negatives DC01–DC50

These 50 additions extend the original95 negatives and24 fault boundaries;
NONE is a product-test PASS. Canonical contracts resolve the expected behavior;
implementation/Android evidence is PENDING. The separate30 reference checks
exercise selected serializers/arithmetic/predicates/models only; they are not
a one-to-one execution of this matrix. S2/V2/R2/P1/L2/A2 are normative.

| ID | Boundary | Negative input/race | Required fail-closed result | Product evidence |
| --- | --- | --- | --- | --- |
| P3-DC01 | Storage | Unknown schema/reserved value/trailing bytes | Reject without allocating or freshening | PENDING |
| P3-DC02 | Storage | Duplicate or unsorted item/collection/context IDs | Reject canonical parse | PENDING |
| P3-DC03 | Storage | Counts/body exceed quota or integer overflow | Reject before allocation/write | PENDING |
| P3-DC04 | Storage | Partial schema2 selector/generation | Authenticate winner; never empty writable | PENDING |
| P3-DC05 | Storage | Wrong payload/header expected context | Deny; header is not identity authority | PENDING |
| P3-DC06 | Storage | Missing selected A2 anchor or foreign token | Restricted restore denied; retain hold | PENDING |
| P3-DC07 | Storage | A2 body catalog/envelope hashes differ | No proof or normal selected admission | PENDING |
| P3-DC08 | Storage | Attempt file at depth7 or unlisted depth6 child | Refuse inventory; no blind cleanup | PENDING |
| P3-DC09 | Storage | Attempt manifest has foreign source/transfer tuple | No idempotent join or discard | PENDING |
| P3-DC10 | Storage | Historical snapshot includes other archived transfers | Evidence-only parse; do not recursively mount | PENDING |
| P3-DC11 | Storage | Credential rotation while receipt snapshot needed | Keep identical envelope and actual-key ledger | PENDING |
| P3-DC12 | Storage | Terminal1..4 entry still present | Keep all evidence until paired release step4 | PENDING |
| P3-DC13 | Usage | 8GiB full verification counted as one query | Charge8192 chunk queries plus1 header | PENDING |
| P3-DC14 | Usage | Playback leaves insufficient full-sweep budget | No partial verification as receipt; preserve source | PENDING |
| P3-DC15 | Usage | Crash after only header ledger replacement | No lease; visible charge spent, no refund | PENDING |
| P3-DC16 | Usage | Crash after both charges but before lease | No restart lease; both charges spent | PENDING |
| P3-DC17 | Usage | Concurrent requests share or reconstruct lease | Deny; each original operation precharges its own | PENDING |
| P3-DC18 | Usage | Missing/malformed/detectably inconsistent ledger | Deny, never reset; valid hostile rollback remains out of guarantee | PENDING |
| P3-DC19 | Receipt | Metadata crop/name/membership changed after copy | Only explicit immutable payload predicate authorizes cleanup | PENDING |
| P3-DC20 | Receipt | Destination payload/context/generation replaced | Deny cleanup even same plaintext digest | PENDING |
| P3-DC21 | Receipt | Destination trashed/removed/restrictions weakened | Retain hold and all dependencies; no unlink | PENDING |
| P3-DC22 | Receipt | Two retained transfers share historical index | Retire only after both release and readers close | PENDING |
| P3-DC23 | Receipt | Crash after Primary terminal before Hidden terminal | Keep dependencies; repeat fresh paired acknowledgement | PENDING |
| P3-DC24 | Receipt | Crash after Hidden terminal before Primary ACK | Keep dependencies; no timeout-based release | PENDING |
| P3-DC25 | Receipt | Crash after Primary ACK before Hidden drop | Authenticate exact ACK, then select drop durably | PENDING |
| P3-DC26 | Receipt | Crash after Hidden drop before physical retirement | Current authenticated closure controls exact retirement | PENDING |
| P3-DC27 | Receipt | Stale/foreign terminal kind/revision/hash | Deny release; hashes are evidence not capabilities | PENDING |
| P3-DC28 | Receipt | Preselection cancellation lacks receipt | No fabricated zero receipt; prune only paired exact absence/ownership | PENDING |
| P3-DC29 | Hold | Source metadata/cover changes after copy | Retained Copy/conflict; no silent Move | PENDING |
| P3-DC30 | Hold | Source payload/root replaced under same name | Deny intent/removal; pinned identity required | PENDING |
| P3-DC31 | Hold | Crash after hold intent before index removal | Source stays ordinary or exact intent recovery; no unlink | PENDING |
| P3-DC32 | Hold | Crash after index removal before ACTIVE_HOLD | Intent owns unchanged ciphertext; recover exact winner | PENDING |
| P3-DC33 | Hold | Restore collides with reused itemID/membership/cover | Keep hold; preserve later writes, no old index swap | PENDING |
| P3-DC34 | Hold | Crash after restore index promotion before terminal | Recognize exact M1 item/relationships; ciphertext never unlinked | PENDING |
| P3-DC35 | Hold | Backup/reconcile/delete ignores unresolved catalog | Deny before write/publication; ordinary verified reads only | PENDING |
| P3-DC36 | Locks | Controller-to-root reverse acquisition | Prohibited by DAG; future latch/deadlock tests required | PENDING |
| P3-DC37 | Locks | Cache removal called under metadata/storage | Collect IDs, release, then scoped eviction | PENDING |
| P3-DC38 | Locks | Revocation drains callbacks under outer locks | Enqueue only; close/cancel/join outside ALL locks | PENDING |
| P3-DC39 | Locks | Player/producer cleanup never acknowledges | Fresh auth denied; timeout is not cleanup success | PENDING |
| P3-DC40 | Locks | Operation revoked between readback and promotion | Original gated operation denies promotion/deletion | PENDING |
| P3-DC41 | Recovery | Media index corrupt but credential projection valid | Paired restricted proof restores held Primary only | PENDING |
| P3-DC42 | Recovery | Video or index ledger exhausted | No reset; restricted proof uses surviving credential service | PENDING |
| P3-DC43 | Recovery | Unwrap succeeds but selected active slot does not match | No proof; exact A2/catalog/envelope binding required | PENDING |
| P3-DC44 | Recovery | Primary-only/BIO/unconfirmed recovery attempt | Deny restricted proof; no fallback | PENDING |
| P3-DC45 | Recovery | Proof reused/foreign transfer/changed holdRevision | One-shot exact tuple denies | PENDING |
| P3-DC46 | Recovery | Credential rotation/expiry/revocation after proof | Fresh tuple check denies before intent/promotion | PENDING |
| P3-DC47 | Recovery | Restart with old proof or saved capability | No capability persisted; authenticate both again | PENDING |
| P3-DC48 | Recovery | All credential evidence/query capacity unavailable | Fail closed; retain ciphertext/projection, no empty restore | PENDING |
| P3-DC49 | Recovery | Restricted restore tries media/write/cleanup/release | Deny all; Primary RESTORED only, Hidden deps retained | PENDING |
| P3-DC50 | Recovery | Ordinary Primary index corrupt during restricted restore | Preserve hold; no reconstruction or empty overwrite | PENDING |
