# Complete probe manifest and actual buffer checkpoint (2026-10-03)

**PR59 remains DRAFT/unmerged. Phase3 NO-GO. Task3, CB01–CB12 and all63 product gates remain incomplete.**

Parent remote source32173e94a6471f880ee9bfdb9a4357317eb7ba78, tree6782e8ffc95f5183b7eb46762997dfc3678f0386. This checkpoint continues the recovered remote implementation; no historical unpushed state supplies acceptance evidence.

## Actual production and test changes

BrowserMediaProbe.protectedManifest checks the exact operation's BROWSER_UPLOAD_EGRESS before reservation or native construction. It preclaims a complete three-child original manifest for transport, input and bounded actual read buffer. Every native factory immediately attaches its actual original. Redirects await previous terminal retirement outside ranked gates before replacing that complete manifest. Failed construction retires its captured exact original. Normal result publication follows terminal retirement and renewed guard validation.

OwnedByteBuffer owns the one actual native bulk-read array, bounded at64KiB. Its independent funded wipe waits for the reader to return; separately funded transport/input releases dispatch to unblock that reader. Reads check the exact guard before and after native return. The complete64KiB manifest requires actual EOF; an extra byte rejects rather than treating a truncated prefix as proof of absent protection. No intermediate growing byte-array stream remains. The immutable UTF8 String copy and generic borrowing/factory-capability narrowing still require further audit; this checkpoint makes no complete plaintext-copy or Task3 closure claim.

The existing video authentication negative is strengthened, preserving its fresh-reader verifyAll rejection. The previous expression readByte advanced the RandomAccessFile cursor and wrote the following byte, which could already equal the requested value. The revised test seeks back to the intended offset, verifies its exact XOR change and an unchanged neighbor, then verifies authentication rejection. The included native cursor diagnostic deterministically demonstrates the previous possible no-op and corrected byte selection. Production cryptography is unchanged.

## Measured regressions and scoped independent review

The original producer failed2 of8 actual component tests: complete-capacity denial and actual native-read-array wiping. The complete-manifest producer without explicit entrypoint egress failed1 of16. The corrected frozen producer passed33 targeted cases:16 actual probe component cases,6 buffer cases,8 copied-array cases and3 terminal retirement cases. These probe component runs use a CookieManager stub and synthetic transports; they are not device execution. Ten new Android cases cover capacity, wipe, blocked read/revocation, late factory return, byte bounds/zero progress, partial reads, failed close, redirects and direct read-only denial. The existing two video JVM cases separately pass with zero failures/errors/skips.

Read-only independent final review against32173e94 reported no Critical/Important finding in this scoped diff. It checked independent unblocking, exact failed-construction obligations, redirect terminal waits outside reader gates, egress admission and retained video rejection. It ran no builds/tests and edited no source. Immutable String copies, generic borrow/factory narrowing, wider manifests and device/full Task3 acceptance remain explicit limitations.

## Frozen final verification

Final strengthened-test source:779 debug JVM and786 Phase0 JVM, zero failures/errors/skips; both lint variants and all four app/instrumentation APK assemblies pass. Reference contracts/vectors, Browser helpers, immutable fixture hashes and Android fixture equality, secret/VPN/model scans and all14 SDK-backed backup mutation checks pass without skips. Actual packaged backup and retired-model checks pass for both application APKs. Source/workflow/build/scripts hashes match before every serialized Gradle command and after completion. Evidence/probe-buffer retains commands, raw logs, measured RED/GREEN XML, full-suite XML/hash summaries and development APK hashes. New-head remote push/PR CI and actual added Android execution remain pending at preparation; development signing cannot satisfy permanent-signer or physical-owner gates.

An earlier verifier overlapped an egress correction; attempted termination was not confirmed. Its source-freeze guard later rejected the changed inputs before the next command. That exit1/mixed trace and interruption record are preserved and excluded from final green. A complete pre-tamper-fix sequence is intermediate evidence only. The final accepted sequence started after the earlier writers ended and froze the strengthened video test.

## Preserved CI history and diagnostics

Push505 at5e3a8dd succeeded, all27 steps and202 actual Android cases; PR506 failed its existing process retirement JVM negative, retained in BROWSER_JOB_CHECKPOINT.md. At parent32173e94, push507/run37098625292/job111133569400 FAILED with773 JVM tests/1 failure in ChunkedVaultVideoStoreTest.boundedReaderAuthenticatesChunksAcrossSeekAndEndOfFile line45; Android did not execute. Its full decoded log is retained. No retry masks that failure. The cursor defect independently demonstrates a possible no-op and is consistent with this failure, but the CI ciphertext bytes were not retained, so the exact CI cause remains an inference.

Independent PR508/run37098627882/job111133577123 at the same parent source SUCCESS, all27 steps and210 actual API36 Google ATD Android cases, including all8 Browser session Job admission cases. Checkout merge3661206f3bdcf49e20e7663a8b0ed07db63bed9b has tree6782e8ffc95f5183b7eb46762997dfc3678f0386, exactly the branch tree, parents unchanged main93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10 and32173e94. The earlier failure and independent success are separate evidence.

Original487 attempt1 remains :app:packageDebug FAILURE with underlying cause UNKNOWN; same-source retry487 and independent488 SUCCESS at a8f6467 neither erase nor explain it. RECOVERY_2026_10_03.md preserves those diagnostics. No automatic retry, test weakening, task omission, production crypto change or owner migration is introduced.

## Remaining authorized implementation

RUNTIME_MIGRATION_LEDGER.md remains binding. Browser normal Job cancellation must still prove transport unblocking before native Job completion across the combined pipeline; hook/start branch device coverage, complete buffer/copy ownership, video/AI/provider/presentation manifests, exact-attempt/controller lock separation, global identity/factory capabilities and removal of raw registration/synchronous drain remain Task3 work. Tasks4–8 and full security/negative/fault/runtime/signer/owner gates remain pending. No merge or later phase is authorized.
