# Diagnostic privacy and future format evidence

Baseline authority: audited main `a19218479eb9b9bcdb35ff0035fc78522263c891`; security contract `docs/phase0/SECURITY_CONTRACT.md`. All fixtures/inputs below are synthetic. No owner content, production Hidden key/format, Primary ciphertext rewrite or deployment is involved.

## SOURCE FACT

The original fatal serializer retained thread name, Throwable.message processed by regex, arbitrary crash-context atoms and frame.toString(). Acceptance console admitted arbitrary categories/detail keys/values/summary labels and regex-redacted console prose. BrowserDiagnosticRecorder trusted arbitrary caller strings. Regex replacement cannot establish privacy for arbitrary unquoted names, prompts or tokens.

## IMPLEMENTED PHASE 0 CHANGE

- Fatal format omits all messages, thread names, wall-clock timestamps and active-tab count. It emits fixed route/event/parent/error categories, bounded structural flags/counters and parsed/rebuilt geometry. Exception class names use an exact allowlist; stack locations require exact allowed class/method pairs, reconstruct constant filenames, and bound line numbers. Unknown exceptions/frames/categories become OTHER/OTHER_FRAME/UNKNOWN_EVENT. A package-prefix filter alone would leak fabricated sensitive identifiers; none is used.
- Acceptance console discards original console prose/source lines and arbitrary summary labels. Exact allowed categories and per-field typed values survive; unknown keys including item/container counts and media sizes do not. Provider package identity has a finite allowlist and version has a bounded numeric grammar. URL/name/prompt/token fields are absent. Browser network/error counters remain diagnostic outcomes, not container item totals.
- BrowserDiagnosticRecorder admits complete codes from the policy's closed grammar; extended prefixes and malicious suffixes are rejected before recording/counting.
- Stored fatal reports from the obsolete raw-message format are rejected and deletion is attempted before readback/install. Read allocation is bounded at 64,001 characters; obsolete temporary report files are deleted best-effort on installation. This removes normal logical retention, not a promise of flash erasure or hostile same-UID tamper protection. New fixed-category reports retain historical/current-session separation.

These changes apply to the assigned Browser diagnostic boundaries. They do not claim all third-party/platform logs, heap/provider buffers or other app diagnostics are scrubbed. Increasing an allowlist requires review; arbitrary strings must not be reintroduced as a convenience fallback.

## TESTED EVIDENCE

Privacy source-only Kotlin/JUnit RED/GREEN used the actual assigned production source files and Android SDK types, not mock diagnostic implementations. Kotlin compiler 2.0.21, stdlib 2.0.21, JDK 17, JUnit 4.13.2; Android API36 android.jar supplied compile/runtime types. The source-only compiler runs no Android lifecycle/WebView calls.

1. `privacy-initial-red.txt` — initial RED: 23 tests, 8 failures. Included `recorderRejectsUnknownAndExtendedEventCodes`, `arbitraryUnquotedCategoriesKeysValuesAndSummaryLabelsAreDiscarded`, `arbitraryUnquotedInputsNeverEnterFatalReport`, `customExceptionClassAndMalformedBoundsAreDiscarded`, and assertions replacing regex prose with fixed console categories. Synthetic markers appeared in original recorder, keys/values/summary labels, unquoted exceptions/thread/state/bounds and fabricated frame names.
2. `privacy-initial-green.txt` — initial GREEN: 23 tests, zero failures. Selected useful exception/frame/structural evidence remained.
3. `privacy-legacy-red.txt` — legacy-readback RED: 25 tests, one failure, `obsoleteRawReportsAreDiscardedBeforeReadback` returned the original synthetic raw thread/message report.
4. `privacy-legacy-green.txt` — legacy-readback GREEN: 25 tests, zero failures; obsolete report absent after read; fixed-category report still readable.
5. `future-vectors-green.txt` — future vector command `python3 docs/phase0/test_future_format_vectors.py`: five passing test methods, RFC 5869 SHA256 case 1, 11 purpose domains with exact 93-byte info and two providers (Python HMAC reference and cryptography HKDF), 156-byte whole-record header bytes, 94-byte key-slot info, and independently changed context/master/salt domains. No production F1 parser, AEAD writer, key slot or recovery flow was exercised.

The standalone runner was an ephemeral orchestration aid in `/tmp/run-phase0-privacy-standalone.py`; repository tests remain the authoritative permanent test sources. Source-only results do not replace Android Gradle/full-suite/physical acceptance.

## PROPOSED 2.0 DESIGN

`FUTURE_CRYPTO_FORMAT.md` F1 and `HIDDEN_CREDENTIAL_POLICY.md` H1 freeze canonical field widths/offsets/endian, purpose/container/master/object/generation/algorithm context, authenticated full headers, video chunk relationships, bounded KDF/slot/recovery state, standard HKDF domains, nonce/key usage limits, catalog identity, authenticated journal/receipt matching and corruption/unknown-version handling. Future Hidden master and recovery secret are independently random; strong independent secret is mandatory, including optional biometric layered access. Primary credentials/recovery cannot unlock or reset Hidden. Portable recovery is independent of Keystore. Existing Primary bytes/AAD stay untouched.

These are review candidates. The proposed scrypt profile, device-layer composition/security-level/nonce/auth policy, parser implementation and durable file/index/receipt/root protocol need independent review and implementation evidence before admission. Vector correctness is not a production format or credential security claim.

## UNRESOLVED / PHYSICAL ACCEPTANCE REQUIRED

Full Android Gradle was initially blocked by missing JDK compiler/network/certificate trust, then reached compilation using `/tmp/private-gallery-jdk17` and system trusted certificates. Intermediate whole-branch compilation errors occurred while other assigned changes were in progress (VaultVideoDataSource close return; MainActivity key/commit integration). Final integrated Gradle results are recorded below once available; initial failures are not a green suite claim. The historical-report instrumentation fixture was adapted to the new safe format but was not executed on a physical Android device by this workstream. No signed build/OEM/biometric/upgrade test or future Hidden proof is claimed.
