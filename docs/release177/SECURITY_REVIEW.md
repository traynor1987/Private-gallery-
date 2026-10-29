# Private Gallery 1.7.7 independent security review

Status: **NOT_APPROVED_FOR_SIGNING — source finding and runtime evidence pending**.

Reviewer: independent security-review agent, separate from implementation.
Baseline: `8915d2e6f3915573afeaf850205f302c07eab7eb` (code45 release branch evidence).
Reviewed candidate: `0a87491ea7d01400539022166bc1c4d3646bc249`.
The reviewer fetched that exact commit and reviewed its application changes against the baseline. The working tree subsequently changed during review as the implementer addressed feedback; those subsequent edits are not automatically approved by this report. Candidate tree: `fec1a7769aa582db85dc6c4b3f2d68b9e02f4cc5`.

The complete application diff was reviewed, including repositories, preview cache, presentation metadata, cancellable payload verification, retention policy, scheduler/manifest, MainActivity ownership and lifecycle, shared trash UI, Hidden callbacks, and both Hidden browser presentation changes. Associated new tests, focused selection/verifier, version metadata and upgrade allowlist changes were inspected. Production-signing workflow changes are not yet part of this reviewed candidate.

## Findings

### Important I-7: expiry triggered by retention settings or trash loading lacks preview revocation

`MainActivity.saveTrashRetention` and `loadRecentlyDeleted` call `deleteExpiredRecentlyDeleted` directly, without `previewCacheLock`, removed-ID detection or `invalidatePreview`. The periodic `reconcileTrashExpiry` path has those protections, but these two paths do not. A trash thumbnail load can pass its record check, then have its item purged by a direct expiry call, and subsequently republish the decoded bitmap to the global preview cache because its epoch was not revoked. This violates complete cache/reference removal on permanent expiry.

Required correction: share one serialized Primary purge mechanism for every expiry entry point, capture removed IDs and revoke their preview epochs/cache references, including partial-failure cleanup. Prove the retention-setting and load-triggered paths cannot repopulate a purged item's cache.

No Critical finding was established in this source pass. This is not a claim that unexecuted runtime checks pass.

## Disposition of the initial six findings

1. **Periodic reconciliation deleting active staging:** source correction present. Periodic paths call expiry-only methods; full interrupted-operation recovery remains an unlock operation. MainActivity tracks one timer and cancels it on stop. Concurrency proof remains required.
2. **Stale Primary item references used for viewing/editing/AI:** source correction present. Current COMPLETE records and matching payload identity are required; AI derivative publication rechecks the parent under metadata serialization. Tests now attempt stale reads/derivation after trash. Their Android execution is pending.
3. **Orphan previews after interrupted permanent deletion:** source correction present for restart cleanup through `EncryptedPreviewCache.retain` and presentation retention reconciliation. Its generated-filename restriction is correctly escaped. The initially reported regex concern was a tool-output escaping misread and was explicitly retracted. Post-index-commit failure/restart behavior is not yet demonstrated; I-7 identifies an additional decoded-cache gap.
4. **Expensive metadata work inside lock/Panic Exit authority monitors:** source correction present. Presentation cleanup is outside outer Hidden session/Primary publish monitors; its own guarded final atomic move remains. The repository metadata serialization itself is retained.
5. **Primary restore integrity verification continuing after revocation:** source correction present. `primaryTrashOperation` supplies captured authorization; legacy verification checks cancellation per buffer and chunked verification per chunk and before success. A mid-verification revocation test remains required.
6. **Unbounded/MIME-blind legacy Hidden thumbnail repair:** source correction present. Image dimensions select sampling to at most512 pixels on the largest dimension; legacy video uses an authenticated memory-backed media source and a scaled frame; originals are not rewritten. Large-image/video runtime proof remains pending.

## Security properties retained by source inspection

- Primary and Hidden still use separate directories, keys, credentials and authenticated metadata domains. No replacement key or credential migration was added.
- Hidden PIN-only authentication is untouched. The expiry service is nonexported and permission-bound; its process-local weak activity reference stores no keys or media identifiers.
- Retention choices7/15/30/60/never are stored in each vault's encrypted presentation metadata. Missing settings default to30 days. Never uses0; clock rollback does not expire items early.
- Restore authenticates retained ciphertext before changing state and keeps the original item identity, metadata and surviving membership. Failed integrity checks do not delete the retained payload.
- Recently Deleted previews use explicit TRASHED-only repository paths. Ordinary lists and transfer/upload selectors continue to exclude trash. No public plaintext thumbnail path was introduced.
- Browser changes concern chrome/layout and mounting of blank WebViews. They do not modify VPN/Tor routing, proxy readiness, DNS handling, JavaScript policy, broker authority, upload confirmation or transport teardown. Existing actual packet/protocol cases remain required.
- The new UI confirms permanent deletion and retention changes. It states that locked vaults reconcile on unlock and does not promise physical flash erasure.
- Platform remains Android17/API37; target metadata is1.7.7/code46. Upgrade fixture adds only45→46 to its existing exact allowlist.

## Required evidence before signing

The parent reported grouped Android workflow `40883312782` running against the candidate with18 selected cases. This reviewer has not treated its in-progress status as a pass. Exact-source XML, zero failed/skipped selected methods, existing VPN/Tor packet/body/revocation evidence, and any build/lint results must be inspected after completion.

Directly affected security tests must additionally establish:

- revocation during Primary restore verification, not merely access attempted after lock;
- permanent deletion interrupted **after** index removal, followed by restart cleanup of ciphertext, preview and presentation references;
- orphan-preview cleanup preserving unrelated live media/cache entries;
- expiry concurrent with active import without staging loss;
- the I-7 expiry/preview-publication race and all affected entry points.

Current new tests cover corruption retention, a staged deletion before index removal, stale Primary reads, basic restore/idempotence, independent retention, Hidden metadata and membership, Missing Hidden thumbnail repair, and destructive UI confirmation. Those tests are useful but do not substitute for the above affected boundaries.

Original-production-certificate45→46 in-place upgrade/restart, encrypted-data/credential preservation, package/version/signature and final APK hashes remain release gates. No production-signing approval is given here. Physical Fold/Samsung acceptance remains OWNER_REQUIRED; code45's prior physical acceptance is not silently upgraded by this review.

## Delta review — candidate4405081

Reviewed exact candidate `4405081dd3d46dbd54bc9646c834ab8963544033`, tree `3fdd404966059d5072ed796e379d3feb2f1e2d25`, fetched independently. Application files matched this commit at the start of the delta pass.

The new shared `expirePrimaryTrash` path covers periodic expiry, retention-setting expiry and load-triggered expiry. `previewPublicationGate` serializes epoch/cache publication with revocation, the revoked mutable bitmap is wiped, and retained UI refreshes use the existing activity revision. The callback Unit corrections and retention-aware wording do not alter credentials or transport policy.

The three added fault/concurrency tests exercise mid-read Primary restore authorization loss, deletion interrupted after the index commit with unrelated-media/cache preservation, and expiry while an import owns a staging file. The actual MainActivity test now exercises both retention-setting and load-triggered expiry against decoded cache identity and pixels. Their source assertions address the earlier identified test gaps; execution remains pending. The previous candidate's run failed compilation before instrumentation and is not runtime evidence.

**I-7 remains open in one boundary case:** candidate selection calls `System.currentTimeMillis()` independently of actual repository expiry. A retained item can cross its expiry boundary between selection and purge (especially during recovery), so repository expiry can remove an ID not included in the invalidated candidate set. Capture one cutoff and use it for both selection and the actual expiry, including recovery, or obtain the exact deletion set atomically. No unrelated scope expansion is requested.

Status remains **NOT_APPROVED_FOR_SIGNING** until I-7 is fully resolved and exact-source runtime/release evidence is available.

## Subsequent delta review — exact expiry set and prepared release gates

Reviewed local changes after `4405081`, including the repository-selected expiry ID callback under `METADATA_LOCK`, one captured cutoff carried into recovery expiry, the corresponding MainActivity authorization, `@JvmOverloads` constructor compatibility, the initial native-browser real-touch address-field assertion, explicit1.7.7 fixture setup/cleanup branch admission, and the prepared acceptance workflow and `verify_final_scope177.py`.

**I-7 is resolved at source level:** `expireRecentlyDeleted(now,beforeDelete)` selects the actual immutable purge list under metadata serialization, invokes preview invalidation for that exact list before deletion, and uses the same cutoff during `reconcileAt`. All MainActivity expiry entry points use this mechanism. The publication gate still checks the original epoch before decoded cache publication. No new Important application-code finding was established in this delta. The original six source corrections remain present.

Constructor overloads preserve the original `(Context, ByteArray)` JVM signature needed by the baseline upgrade test. The new address-bar test requires a visible/enabled in-window field and actual injected touch/focus before navigation; it does not bypass routing or fabricate successful navigation. Fixture guard changes admit only the explicitly named new release branch while retaining the disposable GitHub runner requirement.

### Important release-gate finding I-8

The prepared `verify_final_scope177.py` verifies a review-file hash but does not require the review itself to identify the exact source or approve that source. A stale or NOT_APPROVED report can therefore be hashed and paired with independently asserted JSON approval metadata. It also does not bind the approval's focused run/artifact IDs and digest to the supplied run evidence record. The workflow's current ordered construction reduces accidental mismatch risk, but the supposedly exact-source independent-review gate should enforce these bindings itself.

Required correction before enabling signer: require an unambiguous final independent-review decision naming the exact source and APPROVED_FOR_SIGNING with zero open Critical/Important findings, and cross-check focused run ID, artifact ID and digest between approval and evidence. Preserve report digest verification. Negative tests must reject an unapproved/other-source report and mismatched run/artifact references.

The prepared workflow remains disabled by its all-zero source pin and retains original certificate identity, successful exact-source focused evidence, protocol revalidation before key access, and original-key45→46 upgrade/restart checks. It must remain disabled while I-8/runtime evidence is pending.

The parent reports run `40884745904` passed host/compilation/JVM/lint then stopped at the old fixture branch guard before instrumentation. That is not Android runtime success. The corrected next grouped selection has21 cases. This review does not convert either earlier failed run into a pass.

Status: **APPLICATION_SOURCE_REVIEW_CLEAR / SIGNING_NOT_APPROVED — I-8 and exact-source runtime evidence pending**. A later final reviewer decision must name the immutable final source and inspected runtime evidence.

## Release-control correction review

The corrected local `verify_final_scope177.py` resolves I-8 at source level: it requires exactly one final machine-readable report decision, compares its exact source/decision/open-finding fields with approval, retains review-byte hash validation, and binds focused run ID, artifact ID and artifact digest. The reviewer independently ran `python3 -m unittest discover -s scripts/tests -p test_final_scope177.py`; all tests passed. These host checks do not substitute for Android evidence.

The acceptance workflow preserves this release-control validator alongside approval/report before checking out frozen application source and executes the preserved validator before signing-key access. This separation is acceptable for validator-only changes; retain the release-control SHA and validator identity in production evidence. No new Android execution is required solely for this host gate correction. The workflow remains disabled until an exact final approved decision and runtime evidence exist.

No open Critical or Important source finding remains in the reviewed application/release-control changes. This is not signing approval: candidate `8281bd84704e7a66e9ab7917e4a4ea39eb09d208` still requires successful exact-source Android runtime evidence and the mandatory original-key upgrade/restart gates. Physical acceptance remains OWNER_REQUIRED.

## Final machine-readable decision
```json
{"source_sha":"8281bd84704e7a66e9ab7917e4a4ea39eb09d208","decision":"NOT_APPROVED_FOR_SIGNING","critical_open":0,"important_open":0}
```
