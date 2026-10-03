# Phase 3 cleanup capacity contract CB1

This supplements L2 and Task 3. Independent scoped closure review passed after
fixing both partial-construction and authentication-handoff findings. Production
integration may proceed; implementation and device acceptance remain pending.

Intent: preserve nonblocking revocation, fail-closed authentication and original
key-domain ownership while bounding every independently blocking release before
its resource can be constructed. This is an implementation support limit, not
a storage-format change or a new product feature.

## Selected bounded release contract

Use **pre-creation owned-factory reservations plus physically reserved independent
release slots**. Do not attempt to salvage unrestricted post-creation ownership
with a queue cap, emergency pool, timeout, cached workers, inline fallback or
optimistic nonblocking close. Keep the existing authority/lock/proof predicates.

The numerical candidate below is a selected finite support contract for
Phase 3 implementation; it is not an assertion that existing consumers already
satisfy it or a measured Android optimum. Its consumer-fit restrictions are part
of the contract, not optional later tuning.

| Partition | Concurrent occupied release slots | Required admission/consumer-fit support limit |
| --- | --- | --- |
| Primary |32 total:16 pipeline/IO/job/native +16 presentation wipe resources | One protected media/transfer pipeline at a time; at most one player, one retriever, two live player data sources and two pipeline producer/verification jobs. Other existing IO/editor/browser jobs share the16 nonpresentation slots and must reserve before starting. At most16 live protected bitmap/byte-buffer registrations, including cache, fullscreen/editor and pending decoded results. |
| Ordinary Hidden, including its authentication/enrollment factories |32 total:16 pipeline/IO/job/native +16 presentation wipe resources | Same one-pipeline/player/retriever/data-source/job limits. Hidden credential preparation/request/provider cleanup uses this partition even before epoch promotion. At most16 live protected presentation registrations. |
| Restricted proof |8 total, no ordinary presentation/media slots | At most one independent proof/restoration transaction. Its authentication worker, prompt/request, credential temporary resources and eventual cleanup must fit the declared8-slot manifest. No media reader/player or ordinary Hidden capability is admitted. |
| Process aggregate |72 slots, at most72 application-created cleanup workers and72 pending release invocations | Exactly the sum of the fixed partitions across ALL authority/registry instances and generations in one app process. Reinstantiating an authority or moving to a newer epoch does not create another pool/quota. |

Budget rationale and limits: a single source branch can require up to six pinned
directory handles (existing depth6), one actual channel and one reader/wrapper
release; independently reserved two pipe endpoints, two producer/verification
jobs, one player, one retriever, one prompt and one additional provider/cleanup
action bring a deliberately conservative branch manifest to16. The limit is on
actual independently blocking release actions, not merely outer objects. If a
factory has another endpoint, callback, pinned branch or child, it must reserve
it too or refuse BEFORE construction. Close a finished output and acknowledge
its retirement before opening a subsequent readback when both would exceed the
manifest. Do not assume all possible existing paths use at most16 today.

The16 presentation slots accommodate either a bounded preview cache, or a smaller
cache plus current fullscreen/editor buffers and rendered/crop results. Cache
count must shrink/retire normally when another screen needs those slots; the
current20MiB byte cap alone does not establish fit. More simultaneous UI images
are an explicit unsupported capacity request, denied before read/decode/copy,
while existing resources stay owned. Root may choose different numeric service
limits, but their manifests, aggregate worker bound and starvation proof must
then change together before normative closure. Storage item/picker quotas do
not imply simultaneous readers: a16-URI selection is processed sequentially.

Cost: up to72 independent cleanup threads is a real upper bound, not a claim of
zero memory/OS cost or Android/OEM adequacy. Physical execution capacity must be
obtained successfully before resources are constructed; initialization/admission
failure refuses work and grants no new authority. Device memory/thread viability
remains a gate. This deliberately simple conservative design avoids having to
prove arbitrary opaque close/cancel bodies nonblocking. Smaller physical pools
would require an independently proved typed release/dependency contract, which
these sources do not yet supply.

## Normative clauses

1. A reservation is private, nontransferable and binds the original authority,
   domain/epoch or exact authentication attempt, original live operation/scope,
   factory kind, declared bounded child manifest and one fixed physical release
   execution slot for EVERY independently blocking action. Allocate bounded node,
   queue and worker capacity before constructor/open/decoder/key-copy/job creation.
   Reserve all manifest entries atomically or reserve none. No resource use,
   publication, producer start or scope escalation follows reservation alone.
   Factory/crypto/IO remains outside all ranked authority/controller gates.

2. Supported factories cannot create an unreserved child. Streams, pinned handles,
   copied reader keys, producer outputs, decoder results, prompt/provider/native
   objects and coroutine Jobs—including lazy/root/child jobs—are included. A
   parent token cannot hide arbitrary children, callbacks or fan-out. A child
   needed to unblock another close/join has a separate slot and dispatch path;
   it must not wait to be invoked behind that dependent closer or its mutex.
   Existing source/wire/context/usage limits still apply independently.

3. The token states are reserved → constructing → attached → retiring → successful
   terminal acknowledgement; rejected-after-construction and factory-partial
   failures enter the same retiring obligation. Failure/noncompletion remains
   accounted and unavailable, not a reusable success. Revoke marks ALL reserved,
   constructing and attached tokens unusable, wipes existing controllable small
   authority/lease keys and enqueues already funded release. A late factory result
   is never returned/published: its original token owns cleanup without requiring
   a current lease. A constructing token is not dropped merely because no object
   has arrived yet. Factory failure retires every actual partial child and only
   releases unused reservations after exact no-resource/no-job completion.

Existing short authority/lease-key exception: the existing authority-issued
32-byte operation/lease arrays remain synchronously owned and wiped under their
original issuance/epoch/scope/close/revoke rules. They have no independently
blocking release and do not consume physical release slots. This preserves the
clause3 small authority/lease-key wipe exception and original cryptographic
interfaces. It does not exempt reader keys, producer/result/temporary keys,
authentication-handoff keys or arbitrary byte-array ownership; their original
pre-creation manifests and bounded accounting remain mandatory. It supplies no
resource creation, protected IO, Job start, publication or promotion permission.
An existing lease cannot label a long-lived child key as its short lease array
or extend that array's existing lifecycle to evade reservations.

Partial construction rule: immediately after each actual handle, job, copied key,
transport or native child becomes available, attach it to its own ORIGINAL reserved
token before the next blocking or throwing factory step. Revoke dispatches every
attached partial child while the enclosing factory is still constructing; it does
not wait for a composite return. Constructor completion separately gates retirement
of unused slots. Every factory records its exact child manifest and preserves
ownership of partially acquired children on failure.

4. A release worker is assigned/reserved before construction and remains available
   independently of other occupied slots. One slot schedules at most one release
   invocation; duplicate own/close/revoke/reject/normal-disposal requests join its
   exact state. A finite shared executor is acceptable ONLY if it proves equivalent
   independent physical capacity for every simultaneously admitted slot, including
   blocked cancellation/close bodies; otherwise use a fixed per-slot worker.
   No callbacks execute on enqueueing threads or under any caller-held ranked
   locks. Reservations/queue bookkeeping are bounded internal metadata and invoke
   no client code. Dispatch all independent cancel, transport and native-release
   requests without waiting for another callback to return. Close/unblock transports
   before any join; joins are not cleanup-worker prerequisites for dispatch.

5. A slot is reusable only after BOTH its release invocation has actually returned
   and its required positive acknowledgement has occurred. Native future completion
   while closeAcknowledged is still running cannot prematurely recycle that worker.
   Job completion while cancel/handler is blocked cannot prematurely recycle it.
   Hook installation/possible inline Job completion occurs outside all application
   gates under its reservation, and no protected job starts/exposes a result until
   its completion tracking is installed. Do not occupy a worker merely waiting on
   a future/job join; keep the token pending instead. A completion callback is only
   bounded internal accounting; it cannot run another user close/cancel/factory.

6. Main/native release may be marshalled to its required platform executor, but
   enqueue/post success is not acknowledgement. Keep the slot until actual native
   release/NonCancellable finalization completes successfully. Rejected posting,
   throwing close, failed acknowledgement, worker failure or OOM is failed/pending
   ownership, never success, timeout completion or an invitation to spawn fallback
   threads. Retain the failed obligation/diagnostic and block new authentication.
   Hung OS/provider/main-thread release is a finite-service limitation; the proof
   guarantees dispatch capacity, not completion of a broken platform. No existing
   underlying exception may be swallowed into an acknowledgement without an
   independently proved prior successful release.

7. Accounting is process-wide and partitioned. Old unacknowledged registries retain
   their slots; new registries cannot reset counters or bypass them. Primary,
   ordinary Hidden and restricted proof keep independent cleanupComplete predicates,
   credentials and admissions. Ordinary Hidden stalled slots cannot consume proof
   slots; Primary exit does not gain Hidden authority, and Hidden exit does not
   revoke Primary. Shared physical implementation cannot allow one partition's
   blocked callbacks to occupy another's reserved workers. Original paired checks
   remain mandatory for restoration; cleanup reservations are no key/proof bridge.

Authentication handoff rule: current-attempt preparation validity is separate
from cleanup of previously revoked registries. Preparation cannot start while prior
cleanup is failed or incomplete. Current-attempt tokens do not grant key admission.
Before final key admission, quiesce and positively acknowledge that exact attempt's
worker, prompt, provider and temporary obligations OUTSIDE all ranked gates. Retain
the original independently verified result and attempt only in bounded private
handoff metadata with bounded, accounted exact-attempt ownership of the result
key; it is never an unowned byte array. The only successful terminal outcome is
atomic consumption into the SAME authority under original final checks.
Cancellation/failure synchronously wipes the controllable result key. This narrow
key-ownership transfer does not exempt any long worker/prompt/provider/temp release
from positive acknowledgement and must not introduce a self-dependent promotion Job.
A later short final promotion rechecks that ORIGINAL attempt and cleanupComplete.
Never promote inline from a still-accounted worker, join the current worker from
itself, exempt all current-attempt cleanup, or substitute an ambient attempt. The
handoff is not persisted and has no media/proof/deletion authority.

8. Normal success/disposal/recycle/fill must retire the exact registered token and
   receive the same positive acknowledgement as revocation. No epoch-long tombstones
   or new session resource on a closed lease. A weak token remains counted while
   its referent may be live, regardless of cache eviction; remove it after exact
   successful wipe/recycle/ownership retirement, or a confirmed cleared weak
   reference for that exact private weak token. Cleared-reference retirement is
   not native-player acknowledgement or a JVM/native/GPU wipe claim. Do not erase
   a still-used cached/view bitmap merely to make capacity: detach all entitled
   presentation owners first; otherwise deny new allocation. Duplicate references
   to one actual resource share its one token, not duplicate close authority.

9. Generic already-created `own(AutoCloseable)`, `ownForSession(AutoCloseable)` and
   `own(Job)` cannot remain unrestricted supported entry points. Replace them with
   owned factories/reservation-backed internal handles, or make overloads accept
   ONLY already stamped objects from those factories with exact original tokens.
   Every production caller must migrate; compile/audit must leave no unreserved
   generic path. A stale reservation request denies BEFORE creation. A previously
   reserved factory completing after stale/revoke is still cleaned/accounted by
   its original token. Keeping arbitrary already-created inputs and throwing on
   a post-creation cap is NOT closure: it leaves an unavoidable unbounded rejection
   obligation. There is no truthful bounded runtime emergency fallback for an
   unlimited stream of such unsupported objects. Do not turn them into accepted
   resources, silently drop them or claim their cleanup succeeded. API narrowing
   and caller migration must be explicit in the approved plan.

## Required consumer migration and preservation

| Path | Exact required adaptation; preserved behavior |
| --- | --- |
| MainActivity launchOwned and existing browser cleanup Job | Reserve Job/child obligations before lazy creation; register completion outside gates before start. Preserve original lease checks/key wipes and actual NonCancellable Main teardown completion; parent cancellation cannot serialize away independent child/transport requests. |
| Browser upload queued publication | Reserve cleanup/file/job resources before staging. Attach session cleanup while original lease is live before publication; queued epoch-only callback merely transfers/displays already owned output. Never substitute a newer browser operation. Four upload files remain bounded; no new browser/egress feature. |
| ScopedIoGuard, repository/EncryptedPayloadStore and existing network/provider callers | Change factories to reserve BEFORE opening sockets/streams/readers/file/provider resources; wrapper+underlying releases use an audited manifest and normal close retires tokens. `.use` must not leave a registry entry after successful close or run long close under storage/metadata/root gates. |
| PhotoEditor/fullscreen/preview/repository protected bytes | Reserve before read/decode/render/copy, propagate the same token with ownership, detach then retire on disposal/replacement/fill/recycle. Lazy UI effects need reserved Job scopes BEFORE creation, not discovery of an already running Job afterward. Enforce count AND existing byte/native limits; cleared weak tokens cannot accumulate for the epoch. |
| VaultVideoSession/F1/MediaUsageStore | Reserve actual copied-key/reader/input/output/pinned child actions before factories; bound liveSources to two and audit any player-created child factory. Independently unblock transport before waiting for consumer close/mutex/job; do not assume the current composite close already supplies that property. No new decrypt/counter/domain authority. |
| Secondary controller/enrollment/requests | Reserve under exact pre-authentication attempt or live operation before backend/request/fork creation; detach under controller/enrollment rules, release provider/native work outside all ranked gates. Failed registration/late preparation keeps original capacity/alias ownership; preserve selected alias/retirement-plan race. Short owned-array invalidation stays synchronous where already allowed. |
| Future restricted proof | Its own eight-slot accounting/worker partition only, current independent authentication and exact proof revocation/barriers; it cannot borrow ordinary media permission or persist cleanup/proof authority. |

Preserve wrong-domain, closed lease, expired/ABA epoch, rejected ownership and
invalid key wiping behavior. Tests using a now-unsupported arbitrary post-created
object must instead exercise rejection of an ORIGINAL reserved factory racing
revocation, plus no factory invocation when reservation is denied. That changes
the unsafe interface shape, not the mandatory rejection cleanup outcome. Do not
label unsafe old generic calls as harmless or skip their actual caller migration.

## Capacity proof and proposed matrix coverage

At most72 slots are occupied across old/current registries, reservations, factories,
normal retirement and revocation. Each potentially blocking independent release
has its pre-existing reserved worker. Thus with k callbacks blocked there are
still workers for every other admitted independent action; no required action
is an unreserved73rd task. Delayed factory/native/job completion retains its slot,
so it cannot cause unbounded worker/node growth or free budget prematurely.
Partitioning preserves other domains' dispatch capacity. Failure/lack of capacity
denies BEFORE new resource creation and leaves existing ciphertext/ownership
intact; no queue overflow, retry loop, timeout, resource drop or fallback worker
is treated as successful cleanup. Resource-level dependency cycles remain
forbidden by L2; extra workers cannot repair a reverse lock edge or an unblocker
hidden inside a mutex-waiting consumer close.

New negatives, all PENDING implementation (CB01–CB12):

1. Boundary32/33 Primary/Hidden,8/9 proof, aggregate72/73 including old registries:
   excess invokes no resource/child/job factory, grants no IO/ownership and
   cannot consume another partition's reserved slot.
2. Every reservation/create/attach/start/retire race with original close/revoke/
   expiry/ABA: late results are cleaned once, no publish/ambient replacement;
   pending constructors block new auth until actual partial-child completion. Include
   an attached child that must release while its factory is blocked, and PIN,
   recovery and biometric handoff success plus cancellation before final promotion.
3. Fill every admitted release worker with blocking actions except the exact
   independent pipe/native-unblocker slot; it still dispatches and can release
   the waits. Repeat across all partitions, with Job cancellation handlers blocked.
4. Completion delivered inline while installing a Job hook; callback asks for an
   outer-lock-sensitive action: no user callback under gates, track before start,
   no deadlock or premature slot recycle.
5. Native future completes before close returns; Job completes before cancel
   returns; close returns before native/job completion: BOTH conditions required.
6. Arbitrarily repeated rejected ownership attempts without a reservation:
   production API/caller audit makes construction unreachable, not a silent leak,
   bounded emergency queue or accepted generic object.
7. Owned resource closed normally then revoke, duplicate wrappers/ownership
   transfers/rejected attachment: one actual cleanup and one acknowledgement;
   no registry growth/double release/counter underflow.
8. Long live epoch with repeated editor render/fullscreen/cache evictions, cleared
   weak references and delayed disposal: counts stay within16 per presentation
   partition; no retained tombstones or unowned decode, no wiping still-used views
   for quota. Capacity denial retains current presentation/ownership safely.
9. Main/native/provider close hangs or fails, backend alias race, worker startup/
   submit failure/OOM: no success/drop/timeout/fallback threads; relevant cleanup
   remains failed/pending and new authentication/key adoption denied/wiped.
10. Existing browser NonCancellable Main cleanup and upload late publication:
    preserve real completion ack, cleanup already attached before lease close,
    no new ownership/egress after the original lease is closed.
11. Two admitted data sources plus third request; nested child factory/reserved
    manifest exhaustion and blocked consumer mutex: deny before open, independent
    transport teardown remains dispatchable, no hidden close fan-out.
12. Ordinary Hidden cleanup stalled while proof partition remains available;
    Primary cleanup stalled/new epoch or re-created authority attempts quota reset:
    no shared-pool starvation, reset or domain bridge; paired proof/Primary gates
    and all original credential/keywipe regressions remain mandatory.

Relevant gate additions: G09/G43/G45/G63 require pre-creation accounting and real
nonstarving teardown; G54 requires complete generic consumer migration plus
capacity/ack audit. Task3 signatures sentence must explicitly admit the approved
reservation/factory API narrowing. Actual JVM latch tests, full regression,
Android main/native/provider and supported-device capacity evidence remain
necessary; no model/worker-count assertion alone establishes product PASS.


All CB01–CB12 production, Android and owner-device coverage remains pending.
**PHASE 3 RESULT: NO-GO.**
