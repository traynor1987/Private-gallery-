# Independent Hidden restore review

Reviewed by a separate read-only agent, including final source `a46288059fd7dc8d094798dd67d4ad87af5ee6a8`.

No remaining source-level blocker found after corrections:

- Fresh Primary destination PIN authority is separate from revocable Hidden source authority; destination authentication does not select Primary.
- Both destinations are verified before recoverable Hidden trashing. Opposite-direction transfer lock ordering is aligned.
- Phone exports use pending rows, bounded streaming, authenticated source EOF, destination read-back and constrained recovery cleanup.
- Restricted-media policy survives cross-vault copying and prevents public export.
- Bounded BC encryption preserves V1 nonce/AAD/tag/ciphertext format, with exact-byte interoperability coverage.
- Candidate upload is gated on automated verification, original permanent signing identity and production-package synthetic upgrade.

Review findings corrected during implementation: MediaStore filename normalization and pending-row exclusion; Primary-to-Hidden policy loss/deduplication; Primary rename-directory durability; opposite-direction lock inversion; Android provider whole-message encryption buffering.

CI615 passed 235/237 Android tests, including expanded 500MiB destination/playback, concurrency, corruption, storage-failure and source-removal boundary tests. Its two failures were corrected in source for CI616. This review does not itself establish CI616 runtime success; see its separately recorded results.

Physical-device acceptance, actual installed-package compatibility and owner authorization to install remain outstanding. Pending export recovery runs on the next Hidden entry; source deletion is never replayed. API29+ is required for safe public export. Historical copies with lost provenance cannot have restrictions reconstructed retrospectively.
