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
