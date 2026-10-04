# Primary / Hidden / recovery resource lock contract L2-r1

Current Task3 implementation admission: **CB-01 DESIGN CLOSED; production
implementation authorized under [CLEANUP_CAPACITY.md](CLEANUP_CAPACITY.md).**
The rank DAG and acknowledgement barriers below remain binding. Unrestricted
cached close pools and unreserved factories remain unacceptable. Scoped review
closed partial-construction and exact-attempt authentication-handoff gaps; it
is not a runtime, Android/device or product PASS.

Design-only; no current deadlock claimed. Existing source constraints:
Primary setup/storage→METADATA_LOCK; local crop previewCache→metadata;
SecondaryStore root→SecondaryController gate→authority→enrollment during
biometric promotion; controller lifecycle calls authority under controller gate.
Authority locked() currently drains cleanup after its gate exits even when a
caller still holds outer controller/storage locks. That drain must not execute
new media close/join callbacks under those outer locks.

## Total order and forbidden edges

| Rank | Lock | Permitted subset |
| --- | --- | --- |
| 0 | Primary preview cache | cache-only operations, or before Primary metadata mutation |
| 1 | Hidden preview cache | same rule; no cache acquisition from storage/controller/authority |
| 2 | Primary setup/storage | backup/restore/source/hold and legacy mutation admission |
| 3 | Primary METADATA_LOCK | source revision/index/relationship/catalog ownership |
| 4 | Hidden normalized-root store lock | selected state/media usage/credential projection |
| 5 | One owned reader/player resource mutex | no simultaneous Primary and Hidden reader locks |
| 6 | Hidden controller OR restricted-proof controller gate | original route/sequence check only |
| 7 | Primary authority gate | original Primary lease/epoch and paired promotion check |
| 8 | Hidden authority OR narrow-proof authority gate | original independent authority check |
| 9 | Biometric enrollment gate | existing final enrollment commit only |

Auth-policy/rate-limit/preferences mutex is a leaf after rank9 (rank10): no IO,
callbacks, storage/authority/controller acquisition while held. Existing
controller→policy calls follow this subset; future writes must keep policy
mutex separate from SharedPreferences provider callbacks. Read immutable
settings before gates; no blocking preference commit inside promotion.

Never acquire a lower rank while a higher rank is held; never obtain both
ordinary and proof controllers/authorities for one operation. Primary-only
operations use their subset. Reader readAt/checkValid uses5→7 or5→8; never
invokes storage/controller while locked. Close/decode/player-release cannot be
called from authority/controller/storage gates. Controller capture releases6
before root IO at4. No authority→controller reverse edge. Main-thread player
release is marshalled without app locks. Cleanup callback only reacquires its
own authority briefly after resources/jobs have acknowledged outside all locks.

Paired promotion subset:2→3→4→6→7→8, atomic rename/ownership mutation only;
independent selector/source predecessor checks while locks held. Large crypto,
full readback, decoder operations and producer joins occur outside6/7/8;
fsync/reopen occurs outside authority gates while storage predecessor remains
serialized. If revocation races readback, deny success/next destructive step;
disk promotion may already exist and restart inspects it. Cleanup unlink is
fenced by paired gates, parent fsync and terminal catalog follow; receipt remains
proof not a public authority token. Metadata extraction may be long under3;
do not acquire cache locks to evict: collect IDs, release storage, then cache
invalidate using full container/item/revision/epoch matching.

## Nonblocking revoke and cleanup barrier

Future authority/controller refactor required before registering long media:
under controller/authority locks, increment sequence/revoke admission, close
leases, wipe controllable master/lease arrays, detach presentation and enqueue
bounded cleanup tickets. Return without draining callbacks under any outer app
lock. Schedule cleanup executor after locks release; no synchronous joins.

Cleanup ticket owns source pipe/descriptors, jobs, decoded buffers, readers and
main-thread player. Outside all application locks: signal cancellation; close
transport so producer unblocks; request native/player release; await job and
resource acknowledgements; wipe remaining arrays. Resource locks never wait for
controller/storage/producer while held. A terminal ack updates authority
pending counts; no join under any rank. Streaming checks deny after revoke even
if native close is delayed. Producer failure/completion reaches coordinator
before any destination verification result, outside reader/storage locks.

New authentication requires cleanupComplete for the relevant ordinary/proof
registry: all queued callbacks succeeded, all registered jobs finished, all
native resources acknowledged release. Timeout/failure keeps admission blocked
and neutral unavailable; never declares success or silently drops registration.
Primary need not be revoked merely to exit Hidden. Primary lock signals both
in separate lock scopes (never holds Primary authority while taking controller).
Proof registry stores no master; its small revocation still obeys barriers.

Cancellation before promotion leaves stage invisible/source intact. After
promotion do not roll back by deleting selected winner. Restart has empty
registries, closed routes and no persisted key/proof; reconcile authenticated
disk as the other contracts prescribe. All transport buffers are bounded and
owned; no plaintext temp/cache/player file. No complete JVM/native/GPU wipe claim.

Negative tests must use latches: controller exit while store→controller
promotion waits; cache eviction concurrent metadata crop; reader release needing
authority while revocation enqueues; native main-thread completion during
background; producer blocked at pipe while cleanup waits; paired commit versus
both credential rotations; proof restore versus Primary lock/Hidden rotation.
Assert no reverse lock edges, no callbacks under outer locks, cleanup barrier
blocks fresh authentication and no sole-copy deletion. Model order tests are
design evidence; actual Kotlin concurrency tests remain implementation gates.

Mappings:N004–010,N053,N083–089,N095;G09,43–45,46,63;DC36–40.
