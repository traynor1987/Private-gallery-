# Code 83 Cloud Teacher audit and development checkpoint

Base main: a5a88f831a0c4350a7dc6205a395517d9c777898 (18 November 2026).

The owner confirms a successful authenticated staging connection on 18 November,
with analysis disabled and no automatic uploads. Reuse the configured token; do
not ask for, print, rotate or embed it. No authenticated VPS administration session,
provider billing account or live SQLite ledger is available to this workspace.

## Confirmed existing implementation

- One real HTTPS client and shared Keystore-encrypted connection record.
- Guardian and primary Evidence Locker have manual edited-excerpt consent flows.
  Hidden Evidence is excluded. Existing cloud responses are advisory strings;
  they are not structured learning or evidence of held-out validation.
- Both use the existing local Guardian learning engine and Knowledge evaluation.
  Personal Impact remains optional encrypted owner feedback. Cloud results do not
  currently save to a durable learning-results archive or provide a master opt-in.
- The backend only accepts approved, bounded messages with the owner Bearer token.
  OpenAI credentials remain server-side. Paid calls are not automatically retried.
- The existing budget.db stores integer micro-USD reservations, uses SQLite BEGIN
  IMMEDIATE for concurrency and preserves its explicit funded-cycle expiration.
  Its existing funded-cycle maximum is $90, not a $100 calendar-month policy.
- UTC monthly activity was reporting only. Renaming a funded cycle permitted
  another allowance in the same month: two $60 reservations in separate cycles
  both succeeded, exceeding $100. The added regression failed against baseline.
- The baseline discards returned usage, has no request-level idempotency ledger
  and cannot reconcile token-derived cost against reservation bounds.

## Budget repair in progress

Extend the SAME budget.db and Budget class; preserve old tables/totals, legacy
reserve() API and the existing tighter $90 funded-cycle default. Add a separate
$100 maximum UTC calendar-month exposure check across all funded cycle IDs.

New request records contain only UUID, keyed payload digest supplied by the server,
amounts, dates and state; never excerpts, tokens or model text. Duplicate request
identities cannot reserve twice. Uncertain outcomes carry their full upper bound
across months. Trusted server-side usage reconciliation is idempotent; cross-month
costs count conservatively in both months. Bound violations halt future requests.
Clock rollback fails closed. Legacy undated/unreconciled amounts are preserved,
not assumed to be paid, refunded or attributed to an invented month.

Initial local verification: all 25 backend tests passed, including all original 13 tests,
monthly cross-cycle concurrency, duplicate identities, unknown outcomes, rollover,
legacy preservation and usage reconciliation. The initial checkpoint did not yet wire API dispatch. The subsequent development
now binds all paid requests to durable identities and reconciliation; see below.
There is no full-feature completion claim.

## Deployment and release boundaries

The supplied staging Compose explicitly runs --staging, mounts only the owner
secret and forces analysis off. It mounts neither the OpenAI key nor production
SQLite ledger. Production deploy.sh rejects paid configuration on Ubuntu24.10;
it permits supported Ubuntu24.04/26.04. Do not change this gate or silently turn
staging into production. Owner-confirmed staging is not a paid-analysis environment.

No real paid request is authorized by this directive until mock/security/budget
and deployment checks pass AND a separate explicit cost-limited approval is given.
Owner-reported $100 monthly budget and about $37 remaining are not verified provider
billing. The $37 figure is not embedded as a balance. Before a live test, use an
explicit small server-side allowance (proposed $0.25 maximum exposure), not a
renewed assumption of available credit. No live spending or deployment occurred.

v1.7.41 / versionCode87 is ALREADY published from source
9075b111a87e9dbcf39566b71c2763b380b58088:
https://github.com/traynor1987/Private-gallery-/releases/tag/v1.7.41
APK SHA256 05b1122c80e6746e242419d08f48882d516ac6730eda4a4befa57a8e83afd0d4.
It is the connection-screen repair, not this new Cloud Teacher integration.
Preserve it. Publishing new source requires an unused version/code (next is
1.7.42 /88); do not retag or replace87. The updater is untouched.

## Remaining implementation and acceptance

1. Paid API dispatch, durable identities, bounded usage reconciliation and structured
   advisory responses are now implemented and locally verified; no live paid test.
2. Add encrypted default-off Android opt-in, per-request edited preview/approval,
   structured advisory history/deletion, shared local-learning review and current
   authenticated budget/model status. No automatic training/validation or uploads.
3. Run focused and full applicable Android17/security/privacy/offline tests,
   Fold layouts, original-key populated upgrade and real public update verification.
4. Establish an authorized supported production host without weakening the current
   unsupported-host guard. Safest plan: encrypted backup of the existing service's
   SQLite/volumes/secrets/config; move only that same service to supported LTS;
   verify with analysis disabled; keep one active backend; controlled HTTPS/DNS
   cutover and rollback. Preserve all unrelated services and the current VPS OS.
   Host selection/cutover requires owner direction and actual deployment access.
5. Verify account/project spend controls and ledger/config using sanitized evidence.
   Then request explicit approval for one synthetic test with a stated maximum
   charge. Do not request the existing owner token again.
6. Merge verified code to main and release with the existing original-key workflow
   and normal in-app updating only. Never reuse the already-published87 identity.

## Official documentation checked

- https://developers.openai.com/api/docs/models/gpt-5-mini
- https://developers.openai.com/api/reference/resources/responses/methods/create/
- https://developers.openai.com/api/docs/guides/spend-limits
- https://developers.openai.com/api/reference/resources/admin/subresources/organization/subresources/usage/methods/costs

The existing GPT-5 mini standard text prices are $0.25/M input, $0.025/M cached
input and $2/M output. Reasoning is included in output usage. Usage-derived cost
is an estimate at reviewed rates, not a provider invoice or remaining balance.
Provider spend alerts differ from enforced limits; current official hard limits
can block requests but are not instantaneous. Actual account settings remain
unverified. This local backend cannot cap unrelated API usage in other services.

## Paid API integration checkpoint

41 local backend tests now pass. The existing /guardian/analyze route reserves a
durable request identity with an owner-keyed excerpt digest before any provider
call. UUID retries and changed-payload conflicts cannot dispatch twice; legacy
clients without UUIDs get a stable owner-keyed identity. Timeouts keep the full
bound, including when recording the uncertain annotation fails. There is no
automatic paid retry. Responses carry bounded structured pattern hypotheses,
uncertainty and a summary marked model_prediction, never owner-confirmed facts.

Validated standard-tier token usage reconciles input, cached input and output
(reasoning is already inside output) using integer micro-USD and upward rounding.
Missing usage retains the full reservation; unverified pricing/model/tier/components
halt further paid dispatch. Backend records retain no excerpts or model text.
Unexpected request failures log only a static redacted event.

Paid availability now additionally requires NOVA_CLOUD_ANALYSIS_APPROVED=true on
a supported funded deployment. Defaults are a $0.25 total cycle allowance and one
approved request; neither a month change nor a successful refund renews that count.
A funded supported deployment can report its real ledger with approval OFF without
reading the OpenAI key. Staging ignores funded/approval settings, reads only the
existing owner token and never loads the ledger/OpenAI key. Its existing Compose
mounts, loopback binding and unsupported-host production gate remain intact.

Do not set approval or deploy paid configuration as part of this checkpoint. No
real key was used, no credit was spent and no VPS service was changed. Android
opt-in, encrypted advisory history and full integration verification remain in work.

## Android implementation checkpoint — not a production verification claim

The manual Cloud Teacher dialog now uses the saved Nova Brain configuration, a default-off encrypted global permission, read-only authenticated server budget preflight, an editable exact excerpt and fresh per-request consent. New POSTs use a persisted request UUID, never retry automatically, and fail closed on staging, unknown/inconsistent budget, exhaustion, revoked permission, changed configuration or malformed responses. TLS uses normal platform certificate/hostname validation with redirects disabled.

The same existing Keystore credential adapter stores at most20 encrypted advisory classifications and request/cost/time metadata. It does not retain excerpts or model summaries, which could echo private Guardian/Evidence content. Private history is behind the existing Guardian device-authentication route. Deletion intent prevents late replies from recreating removed records; clearing a corrupt index only removes this feature's own files. The original saved connection and encryption keys remain unchanged.

Cloud predictions remain distinct from owner labels and held-out evaluation. Guardian and Evidence Locker use their existing local correction/Personal Impact controls; cloud results never automatically train, validate, block, or change Sanctuary. No network request happens merely by opening settings or receiving a notification. Hidden Vault content is not wired into the cloud request path.

Local checks at this checkpoint:41 backend tests,17 pure Kotlin protocol/client/existing connection tests,10 CI/main-preservation/preflight control tests, YAML/Bash syntax and exact protected-core adapter/manifest checks passed. Added8 Android Keystore/history tests and4 consent/status/edited-excerpt/failure UI tests; these still require the existing API37 CI runtime, including both Fold viewport groups. No Android runtime pass is claimed before those reports exist. Existing updater files and their exact hashes are unchanged.

Paid deployment remains blocked by the reported Ubuntu24.10 staging host and lack of administrative deployment/billing evidence. No paid request, production activation, version/tag replacement, signing or publication was performed. Existing v1.7.41/build87 remains intact; a future release must use an unused version and complete its own production signing/populated-upgrade/public-feed gates.

Follow-up review checks the originating Guardian/Evidence authority again immediately before dispatch, in addition to encrypted opt-in and unchanged saved configuration. Added cancellation and between-check permission-revocation tests:19 focused JVM tests pass. The first API37 CI attempt496 stopped before compilation in the integration job because configured repositories could not resolve the pinned Kotlin2.3.10 plugin BOM/Kotlin2.3.20 dependencies; this is not an Android runtime result. No dependencies or tests were weakened.

## CI498 evidence and permission-readiness investigation

At source80de1cd36055e93c831c64653ffbec3254b31731 the fast Android build/lint job passed. The original integration artifact14473872296 has SHA25698f0de5e6efba98842d63d3222e9f4788163c890c144c1a4415d806f5c853912. Its767 JVM cases passed with zero failures/errors/skips. Existing updater cover/inner UI checks, Nova Brain87 cover/inner checks and the populated Primary Evidence cold regression passed before the first Cloud Teacher cover group stopped. Three of the four new cover tests passed; the staging test timed out waiting for its initial permission text at line53, before tapping status or sending a request. This does not establish a full Android pass or a VPS connection failure.

The manual dialog now distinguishes reading encrypted permission from a confirmed Off state, using an explicit status tag. The UI test waits for that exact tagged permission value, scrolls it into view and asserts its text. A bounded10-second failure includes only synthetic-fixture state/semantics diagnostics. Staging still must make only one read-only status request and disable both consent and paid submission; none of these assertions were removed or relaxed. Full API37 rerun is required after this UI change.
