# Evidence179 access/controller initial source review

Date: 2026-10-31. Scope: `review179-access.diff` and actual access/controller/metadata/egress source, with the existing repository/session primitives followed through. Read the full CONTRACTS.md, Task2 and design specification. This is an independent read-only product review; only this report was written. MainActivity/UI integration is pending and outside this verdict.

**Spec verdict: changes required. Quality/security verdict: changes required (three Important findings, one Moderate finding). No Critical finding identified in this bounded review. This is not release approval.**

## Findings

### A179-1 — Important: verified Primary reads do not recheck live original state after streaming

`EvidenceAccess.kt:127-130` delegates to `AndroidVaultRepository.withHiddenCopySource`; that reader captures the item once (`AndroidVaultRepository.kt:247`) and verifies the captured length/hash/GCM at EOF, but never reloads the item's COMPLETE status, policy or fingerprint. The access wrapper's final checks (`EvidenceAccess.kt:123`, `:100`) only check authority. Primary read streaming does not hold `METADATA_LOCK`, so another repository operation can trash/remove/replace the selected source while its already-open file is read. On Unix, an open deleted file remains readable. The call can consequently succeed and `register` can commit or `verify` can report a match for a source that is no longer available in the vault. This violates the explicit before/after live-original check contract.

Re-inspect the same originating repository after authenticated EOF and before returning; require COMPLETE and the captured ID/MIME/hash/size/vaultOnly identity. Keep source validity checks on registration/OCR commit paths as appropriate, rather than treating the authentication epoch as source validity. Add a deterministic test that pauses a Primary consume callback, trashes the original via another repository, resumes through EOF, and expects failure with no registration/publication. Also test changed policy/fingerprint and missing file. Hidden's shared repository gate serializes ordinary concurrent mutations during its read, but a common reader postcondition remains preferable.

### A179-2 — Important: UI publication is not serialized with retirement

`EvidenceController.kt:38-39`, `:47` and `:116` perform `access.checkCurrent()` and then assign the StateFlow without a shared retirement boundary. `EvidenceAccess.close()`/Hidden revocation can retire from another thread between these operations. Retirement cancels the scope and posts clearing to Main (`EvidenceController.kt:25-27`); cancellation does not stop already-running non-suspending code, so the old catalogue/names/integrity can still be assigned after retirement and remain until the queued clear executes. Checking before a write is not an atomic publication fence.

Provide a small current-authority publication gate that serializes the final check and state assignment against retirement, and ensure the originating controller's state is cleared on Main. Keep blocking resource cleanup outside that gate. Use a controlled latch test to retire precisely after the prepublication check and verify that no nonempty state is emitted afterwards. Exercise refresh, operation result, integrity result and error/busy publication. Main integration must additionally close/retire on lock, Hide, Panic, background and screen dismissal.

### A179-3 — Important: failed final verification/authority checks can abandon plaintext buffers without wiping

`readOriginal` and `extractRecord` return the allocated result of `readBounded` directly from the reader callback (`EvidenceController.kt:90-98`). If the consumer has returned its ByteArray and a later repository/access check throws (`EvidenceAccess.kt:100`, `:123`, `:148`), assignment to the caller's `bytes` never completes. Neither the following `.also` nor `extractRecord`'s `finally` (`:106`) runs for that buffer. HiddenRepository wipes a direct ByteArray when its own operation's final session check fails, but cannot cover failures in the outer EvidenceAccess checks. A cancelled `withContext(IO)` return can likewise discard a successful ByteArray before ownership reaches `readOriginal`'s caller. This fails the revoke/cancel buffer cleanup contract even though plaintext is not deliberately published.

Keep explicit ownership of the produced ByteArray across the entire verified-reader call and coroutine handoff, and wipe it on every exceptional/rejected transfer. A scoped verified-byte consumer can avoid transferring sensitive resource ownership through a cancellable dispatcher return. Add a test retaining the callback-created array, revoking after EOF but before the outer reader return, and asserting it is zeroed; separately test cancellation during IO-to-Main delivery. Do not rely solely on GC or on the inner repository's cleanup.

### A179-4 — Moderate: SAF acquisition provenance has no path into controller registration

The import adapters return provider name/last-modified provenance (`EvidenceAccess.kt:137`, `:153`, `:156-168`), but `register(sourceId)` accepts only an ID and reconstructs metadata from the repository read (`EvidenceController.kt:57-67`). Both read adapters create fresh `EvidenceMetadata.fromItem` metadata (`EvidenceAccess.kt:129`, `:147`), which sets fileModifiedAt to zero and replaces acquisition-name provenance. Thus the intended import-then-register flow discards the provider timestamp/name claim that was just collected. The returned import metadata cannot be supplied to this controller API.

Carry validated, bounded acquisition metadata through an import-and-register operation, while binding it to the verified imported identity and retaining claim labels. Avoid retaining provider URI or treating claimed timestamps as verified facts. Add an import/register assertion for provider name, modified timestamp and provenance. Preserve unavailable values for pre-existing vault references.

## Source properties that look sound within this scope

- Primary and Hidden adapters use distinct encrypted repositories and roots; each lists only COMPLETE supported originals. No cross-owner repository fallback is present.
- Existing repository read scopes enforce authenticated EOF and captured full-content SHA-256. Partial consumers are rejected. The missing live-state postcheck above is separate from cryptographic EOF verification.
- Registration hashes streamed original bytes; metadata sampling is capped at 2 MiB, OCR input at 32 MiB. Chunk assembly briefly needs approximately twice the byte-input bound, plus OCR working memory. No plaintext file creation appears in these root adapters/controller. Metadata image inspection uses bounds-only decode and bounded embedded fields.
- Controller mutations affect only the evidence catalogue. No original trash/delete method is invoked; annotated edits preserve the source fingerprint. Imports use existing encrypted repository acquisition without transcoding.
- Catalogue mutations use managed captured leases and the existing short commit boundary. No new store-promotion bypass was found. The previously reviewed Java authorities/store were not re-tested.
- Hidden's revocation callback only atomically marks closed and enqueues cleanup; it does not synchronously acquire repository locks or close provider resources under the session monitor. Currentness/commit guards still depend on Main integration supplying immutable authentication guards and no reverse lock ordering.
- `EvidenceEgress.requireAllowed` checks owner, current reader, direct evidence plus retained incident lineage, duplicate/conflicting contributors, inherited vaultOnly, and live repository identity/policy. AI has a common VaultEgress action. Actual AI/archive callers are not present in this scope, so end-to-end enforcement is not established by this helper alone.

## Verification limits and required gates

No tests, builds, CI, signing, commits or pushes were run for this review. The supplied status is 21 host authority checks and 117 host catalogue/store checks previously passed; these are not fresh execution evidence. Root Android sources and instrumentation have not compiled because the local Gradle download is unavailable. Source inspection cannot establish Kotlin/Android compilation or runtime behavior.

The new instrumentation currently covers basic owner separation, normal verified reads, simple revocation, annotation fingerprint preservation, bounded reads and catalogue-owner digest distinction. It does not exercise the above race/cleanup cases, actual controller publication, interrupted registration, retained restricted incident lineage, or SAF metadata registration. Required next gates are: compile the full integrated source and tests; targeted source-policy/EOF/tamper/post-read tests; controlled retirement/store/UI/buffer tests; import byte/provenance and reference-deletion tests; live retained-lineage egress tests; then resumed-auth/SAF ticket/lock-Hide-Panic integration tests on API37. Route and archive runtime/security gates remain separately required. No full historical suite was requested or run.

## Reviewed product hashes

SHA-256 at review:

| File | SHA-256 |
| --- | --- |
| EvidenceAccess.kt | 0860dd3b32979cbf534d71cb797400932363efb640ebf74a4e7ba920895fc482 |
| EvidenceController.kt | 5661b01234c1dc849599e27153fcc122c9e24264b195bfa73e58e94283d444fd |
| EvidenceMetadata.kt | c27652a1f74c00b9b9b76df4e4d538fbf668a6d5291c13cd1e81379b519bfd85 |
| EvidenceEgress.kt | 57500350cbe3b66060400c2103f8424d8d967b694ad56b4e4d4b3588117bf15d |
| VaultEgressPolicy.kt | 63f70906ab4534e96f703f6793c6a026f1e11803ad594979c82ab84c04aaa39b |
| review179-access.diff | cd673c0e887b868f31a896f614ef5e36a0a40828485e8dbec3bae7b06da4b741 |
