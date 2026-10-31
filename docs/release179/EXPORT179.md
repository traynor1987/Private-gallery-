# Evidence179 portable container (version 1)

`.pgevidence` is an authenticated encrypted container, not an interoperable password ZIP. Its PDF is ordinary PDF and is protected by the outer container only. The default destination name must be generic. No legal certification or proof of content authenticity is implied. Original file bytes, source metadata claims, raw extraction history, corrections and advisory AI are separate.

## Wire format

All integers in the fixed 52-byte header are unsigned big endian. There is no owner, incident title, filename or plaintext length in the header.

| Offset | Bytes | Value |
|---|---:|---|
| 0 | 8 | ASCII `PGEVID17` |
| 8 | 4 | Version 1 |
| 12 | 4 | scrypt N=32768 |
| 16 | 4 | scrypt r=8 |
| 20 | 4 | scrypt p=1 |
| 24 | 16 | Random salt |
| 40 | 12 | Random GCM nonce |
| 52 | remaining | AES-256-GCM ciphertext followed by 16-byte tag |

The entire header is GCM associated authenticated data, verbatim. scrypt derives exactly 32 key bytes. Password characters are encoded as strict UTF-8 without normalization or trimming; unpaired UTF-16 surrogates fail. New encrypted exports require at least 12 characters. Password policy does not silently downgrade encryption. Header version/parameters/length are checked before KDF allocation. Maximum ciphertext, including header, is 2GiB+32MiB. Salt and nonce must be freshly random for every export.

The decrypted payload is a conventional ZIP: `evidence.json`, `report.pdf`, `transcript.txt`, `hashes.json`, then `originals/<lowercase-source-UUID>.bin`. ZIP integers are little endian. Entries use STORED or raw DEFLATE; the writer uses standard Java DEFLATE with signed data descriptors. Accepted flags are UTF8 and data-descriptor only. ZIP64, extra fields, archive/entry comments, encryption, directories, symlinks/special files, multi-disk archives, duplicates, traversal, absolute paths, unsupported compression and trailing data are rejected. Local entries, actual CRC/packed/expanded lengths, central-directory order/names/offsets and end record must agree. The parser drains all entries and verifies the GCM final tag.

Limits: 128 distinct attached originals, 256 entries, 2GiB combined expanded entry bytes; combined evidence+hash metadata 4MiB; transcript 2MiB; PDF 20MiB. Limits apply during streaming, before publication. Large originals are streamed and hashed, never decoded or retained in memory. PDF uses platform PdfDocument, paginated wrapped text and platform font fallback; no full-resolution image allocation. Unicode glyph coverage depends on device fonts.

## Metadata and hashes

JSON is strict UTF8 and passes EvidenceStrictJson (depth16, 4MiB, bounded values, duplicate-key and surrogate rejection) before Android JSONObject. Version1 uses exact canonical regeneration: documents with unknown keys, conflicting claims or noncanonical representations are rejected. Its readable projections are backed by the independently bounded versioned EvidenceCatalog binary codec; owner, IDs, references and retained source identity are validated by that codec. This deliberate duplication makes machine interpretation independent of a lenient JSON parser. Binary codec format is defined by EvidenceCodec.java; this is an app-specific versioned format, not a standardized evidence exchange protocol.

`evidence.json` keys: `version` integer1, `owner` PRIMARY/HIDDEN, `catalogBinary` Base64 NO_WRAP of selected EvidenceCatalog encoding, `records`, `incidents`, `analyses`, `sources`. Records include original metadata, raw OCR text/words/times, corrections and annotations. Incidents include events with provenance and all retained lineage. Analyses include the full accepted advisory JSON string, model/task/input digest/token receipt/owner-applied state and source lineage. Sources contain exact fingerprint, attached boolean and generated path or JSON null. Unattached contributors are fingerprint-only and explicitly labelled in the report. Unrelated live-vault records, audit entries, credentials and rejected suggestions are excluded. Analyses spanning unselected contributors are excluded rather than expanding the selection.

`hashes.json` keys: version1, algorithm SHA-256, documents array (`path`, `bytes`, `sha256`) for evidence.json/report.pdf/transcript.txt, sources array (`fingerprint`, `path`, `attached`). The manifest does not hash itself. Original hashes come from declared immutable fingerprints and are verified against actual streamed bytes to EOF before writer success and during opening. Every archive entry must match the declared set; missing/extra originals or document mismatches fail. Vault-only declarations, including retained lineage, fail even in encrypted archives. The owner must equal the explicitly authenticated expected owner before any report is exposed.

## Open and plaintext re-export

Copy the provider once into an exclusively created 0600 file under a 0700 owner-specific no-backup directory; validate header, sync and seal0400. Only ciphertext reaches disk. Retain one private descriptor and use it for every pass. Pass1 decrypts into a bounded discard buffer and authenticates the complete tag before any ZIP parser/report allocation. Pass2 rewinds that descriptor, decrypts and strictly parses/drains/hashes all entries, completes GCM again, validates schema/owner/hash manifest/transcript, then returns a bounded report resource. Wrong password/tag/truncation/foreign owner never returns the report. Input/output streams remain caller-owned.

EvidenceOpenedArchive retains only ciphertext descriptor/path, bounded report, header and derived key; close wipes owned buffers/key, closes descriptor and best-effort deletes staging. Plaintext re-export requires explicit true and current owner authority, performs another complete authentication-discard pass before the output receives any bytes, then streams the original ZIP from another authenticated read. No plaintext disk staging. Output failures may leave a partial destination; root SAF code must attempt deletion and must never claim secure deletion. Output receipts identify actual bytes; ciphertextSha256 binds re-open/re-export to the authenticated external archive rather than any live vault data.

## Deterministic crypto-only known-answer vector

Independent Python hashlib.scrypt + cryptography AESGCM calculation observed on the host. This payload is a crypto test string, not a ZIP. Android BouncyCastle comparison is a pending instrumentation gate (`fixedScryptGcmVectorPreservesWhitespaceUnicodeAndHeaderAad`).

```
Password: two leading spaces + café export passphrase + two trailing spaces
UTF8 password: 2020636166c3a9206578706f727420706173737068726173652020
Header: 504745564944313700000001000080000000000800000001000102030405060708090a0b0c0d0e0f101112131415161718191a1b
Derived key: 249094999462050835ee330c7d7efbffeb222a0c4e209bdb149e5326ce81d0db
Plaintext: 5072697661746547616c6c657279313739206b6e6f776e2d616e7377657220706c61696e746578740a
Ciphertext+tag: adb397bd6dc6c7466d187a759abc4d12abcee3e3141620590ffd477654d81631026881bb67afd60f004aa65b723524883d399cbf3f1df5f301
SHA256(header+ciphertext+tag): f48b5a07fb539f447b27b7cd915532273a37dc584623449c30ea275fc3635471
```
