# Task4 implementation handback

Status: owned AI/credential/immutable-route adapters and focused tests are ready for independent source review. Android Kotlin compilation and all instrumented/runtime assertions remain **pending**, not passed. No paid provider calls, real credentials, subagents, CI, Gradle bootstrap retries, commits, pushes or signing were used. Main/UI/Controller/Access/Gradle/OCR/export/store/workflow files were not edited.

## Delivered files and exact APIs

- `core/evidence/EvidenceAi.kt`: `EvidenceAi.prepare(catalog, selectedIds, imageIds=emptyList(), model=Model.LUNA, task=Task.FACTUAL_SUMMARY, sourceReader, route): EvidenceAiPreview` is suspend and performs blocking reads/image work onIO. `EvidenceAi.analyze(preview, credential:EvidenceCredentials, currentCatalogDigest:()->String): EvidenceCatalog.Analysis` is suspend/onIO, one-shot and owner-confirmed by caller. The callback must provide the authoritative current full-catalogue digest, not a captured constant; it is called before and after send and result parsing. `Model` is `LUNA`/`TERRA`, exact provider IDs and input/output/cache rates. `Task` is `FACTUAL_SUMMARY`, `GROUPING`, `ORDERING`, `DUPLICATES`, `STRUCTURE` (factual event extraction) and `TIMESTAMP_CONFLICTS`.
- `EvidenceAiPreview : AutoCloseable`: immutable owner/revision/catalogDigest, routeIdentity/routeLabel, model/task, selectedIds/imageIds/sourceFingerprints, bodyDigest/bodyBytes, consentDigest, estimatedUsd, pricingRevision, disclosure, exactText and images. Body bytes are private and unchanged. Images expose evidenceId/MIME/dimensions/encodedBytes/SHA256 and `copyThumbnail():ByteArray`; copies belong to the caller and must be wiped. Closing clears owned body/text chars/JPEG bytes. Once consumed, closing also closes the route. `EvidenceAiFailure` has fixed local sanitized messages.
- `core/evidence/EvidenceCredentials.kt`: `EvidenceCredentials(context, capturedAccess, onRemoved={})`, `isConfigured()`, `setup(CharArray)` and `remove()`. Setup/remove must be called offMain and caller owns/wipes its original character/UI copies. Captured lease commit gates all protected mutations/send reads. Distinct provider IDs reuse `AiCredentialStore`. Keys are obtained only after `analyze` claims consent. Shared per-path/owner generations revoke in-flight disposable keys across accessor instances on removal/replacement; the bound route is closed. Actual-key output echoes, including JSON escapes, are rejected before Analysis construction. No key is retained in a catalogue/preview/export or logged.
- `core/evidence/EvidenceNetwork.kt`: exact requested `primarySystem(current)`, `ownedVpn(owner,context,network,current,onClose={})`, `tor(context,unixPath,current,onClose={})`, returning `EvidenceRoute`. Root owns resources/startup/permission handoff. Primary factory is Primary-only by construction; caller must enforce the existing explicit system policy and Hidden never calls it. VPN independently verifies transportVPN/ownerUID/live interface and pins sockets+OwnedVpnDns with no fallback. Tor is Hidden-only, verifies a private sameUID Unix socket, uses existing authenticated loopback/TOR_ONLY bridge, and prohibits originDNS/TCP SOCKS fallback. DefaultTLS; no redirects, cache/cookies, connection retries, origin auth follow-ups or multiplex/coalescing; one-shot POST bodies and bounded requests/responses. Call check/create/register is atomic against close; cancel calls before proxy/onClose teardown.
- `androidTest/core/evidence/EvidenceAi179Test.kt`, `EvidenceNetwork179Test.kt`, `EvidenceCredentials179Test.kt`: 14 focused methods listed below. AI tests were authored before implementation; no Android RED/GREEN is claimed. Network tests reflect the private client with a disposable pinned fixture certificate entirely inside androidTest; production has no TLS injection or endpoint override.
- `docs/release179/AI179.md`: API/lifecycle, exact selected fields/framing, limits, model rates/estimator/disclosure, route policy, credentials and pending runtime checks.

## Security/behavior details

Prepare never obtains a key or sends a POST. It checks explicit catalogue/reader/route owner equality, max32 distinct selected originals/max8 explicit subset images/max100k selected text, and AI egress. All selected originals drain to EOF, size/hash-check, and return through the authenticated/live-state reader before preview publication. Images defaultoff, decode sampled within source100MP/32MiB bounds, fresh-render with white background and no metadata, longedge<=1024/JPEG85/encoded<=2MiB each. Exact body<=20MiB. Only direct selected record fields/current transcript are included; no incident/retained notes, earlier AI, source filename/path/URI/GPS/embedded metadata is added. Unrelated retained restricted incident content stays outside this API entirely. Future incident-derived input would require all retained lineage checks rather than using this direct-record API.

Consent binds full catalogue digest/revision, owner, immutable routeidentity, selectedIDs/images, selected model/task, bodydigest and pricingrevision. Bodybytes are privately owned and transferred unchanged. Reader/live source/route/currentdigest checks occur before key read, immediately before/after execute, after parse and before return. Failure retires consent; no automatic upload/retry. Caller must retire UI preview when selection/model/task/route changes. Current reader/route authority and coroutine cancellation fence returned results. Root must still publish/save using captured access publication/commit gates.

Responses accepts only fixed exact HTTPS endpoint and flags from the brief. Response<=2MiB, strictUTF8, pre-parser nesting<=32; message/content arrays<=128; finaladvisory<=100k. Completed/incomplete/refusal labels, no-output incomplete case, returned actual input/output/cached token usage, locally computed selectedIDs/lineage/model/task/bodydigest/time, and appliedfalse. Structured citations must reference selected IDs; suggestions/events have type/size/count checks and unrecognised IDfields/incidentIDs are rejected. Structured content is still raw advisory, with no automatic action or application parser. Provider errors discarded and network/parser errors sanitized. Actual credential echoes rejected in parsed success/refusal text.

Cost is approximate ceil(allframedchars/4)+documented bounded imagepatch multiplier, higher cachewrite inputprice and2048 maximumoutput, no assumedcachehit. It is not a hard spendingcap. Recon2026-10-31 rates: Luna input0.20/cache0.02/cachewrite0.25/output1.20; Terra2.00/0.20/2.50/12.00 USD/million. Disclosure explicitly includes transfer, storefalse notzeroretention, up30day abusemonitoring/exceptions/accountcontrols, defaultnotrainingabsentoptin, AIuncertainty and noautomaticchanges. Mutableownedbuffers/bitmaps are cleared; immutable Java/JSON/UI/AuthStrings and TLS/librarybuffers cannot be reliably erased and no fullmemoryerasure is claimed.

## Observed local checks

A scoped Python source assertion invocation passed14 assertions against the actual new sources: exact endpoint; exact model pair; no send/key acquisition in prepare; explicitowner+AIegress; all quota constants; one-shot/body/text clearing; no AI file/log writes; redirects/retries/cookies/cache disabled; VPNsocket/DNS/ownerUID pin; TorprivateUnix/DNS refusal; cancellation before resource close; absence of releaseTLS overrides; owner-scoped protected credential store+capturedcommit gate; key-echo rejection+removal cancellation. These are source assertions, not proof of runtime networking or Android compilation.

`git diff --no-index --check /dev/null <owned-file>` passed for all six owned new Kotlin source/test files (ordinary gitdiff skips untracked files). The disposable test certificate was generated locally and `openssl verify -CAfile /tmp/evidence179-fixture-cert.pem /tmp/evidence179-fixture-cert.pem` returned OK. Certificate/key are public disposable testfixture material, not account credentials. No compiler/jars/AndroidSDK were available locally; no blocked wrapper bootstrap was repeated. No host source check is described as an instrumentation result.

## Authored Android tests — unexecuted

`EvidenceAi179Test`:

1. `prepareMakesZeroPostsAndExactFramingHasOnlySelectedContent`: authenticatedEOF; no preparePOST/keyread; exact transmittedtext includes selectedfields/instructions; no sourcefilename/GPS; fixed flags; locally bound advisory/usage/lineage; one-shot.
2. `imagesAreExplicitBoundedReencodedAndThumbnailCopiesAreIndependent`: defaulttextonly, explicit subset, edge/encoded bounds, exactJPEG independentcopies, imagepayload/detail, closedimagecannotread.
3. `foreignOwnerVaultOnlyAndChangedSourcesBlockBeforeCredentialReadOrPost`: foreignroute, vaultOnly, livepolicychange; zeroPOST/keyreads.
4. `changedDigestRevisionRouteAndForeignCredentialRetireConsent`: digestchange, routeclose, foreigncredentials; differentmodelconsent.
5. `revocationAfterSendNeverPublishesAndProviderFailuresNeverEchoSecrets`: post-send authorityretirement; malformed echoedkey/privateproviderbody sanitized.
6. `refusalIncompleteAndForeignStructuredReferencesRemainBoundedAdvisory`: explicitrefusal/incomplete labels, noautoapply, foreignstructuredID rejection.
7. `selectedTextAndResponseLimitsAreRejectedWithoutTruncatingConsent`: duplicatedselection, nonselectedimageID, actual120k selectedfields, response>2MiB.
8. `retainedUnrelatedIncidentAndAnalysisTextNeverEnterDirectSelectedPayload`: unrelatedretained restrictedlineage/incidentnotes excluded; structuredtaskasksnullunknowns; directlineageonly. EarlierAIis excluded by implementation; this fixture does not separately seed an Analysis.
9. `actualKeyEchoIncludingEscapedJsonNeverEntersAdvisory`: parsedUnicodeescaped keyecho rejected without echoedfailuretext.
10. `bodyAndConsentModelDigestChangeAndClosedCopiesCannotBeUsed`: same-route differentmodel body/consentdigest, closedpreviewcannotread/send.

`EvidenceNetwork179Test`:

1. `controlledHttpsRedirectsAreNotFollowedAndCookiesCacheAreNotReused`: actual controlledTLS fixture, redirectcount1, noCookie, repeatednetworkresponse rather than cache.
2. `responseBoundsAndRevocationCancelAdmittedHttpsCalls`: declaredoversizeresponse rejected; guard revocation during activeHTTPS request cancels within fixturebound.
3. `unavailableExplicitProxyNeverFallsBackAndCloseRejectsNewCalls`: pinnedfailedproxy+originDNSrefusal; no fixturedirectrequest; closedrouteadmitsnone.

`EvidenceCredentials179Test`:

1. `ownerScopedKeystoreCredentialsAreIsolatedEncryptedRemovedAndRetired`: isolatedContext directories, actualexistingKeystorestore, perowner setup/read/foreignowner rejection, ciphertextdoesnotcontainfixturekeys, removalacrossanotheraccessor cancelsactivekey, otherownerpersists, retirementrejectssetup.

No one of these assertions has yet run on Android. Additional actual packet tests for ownedVPNsocket/DNS binding, privateTor DOMAIN/no-localDNS/no-TCPfallback and root startup/service/lifecycle remain root work. ControlledTLSfixture tests do not prove VPN/Tor packet routing.

## Root integration requirements and remaining risks

1. Only connect an alreadyverified route through the existing root policy/readiness coordinator; pass onClose or otherwise close/reset that coordinator after a consumed preview/removal closes its immutable route. Release Tor/isolation services after calls/proxy retire. A retired route cannot be reused; no silent directfallback or automatic billedretry.
2. Show exactText, explicitexactJPEGthumbnail/count/bytes, owner/route/model/task, estimate/rates/pricingdate/disclosure in a separate confirmation. Images defaultoff. Clear preview on selection/model/task/catalogue/routechange, dismissal, background/lock/Hide/Panic. Do not retain preview/password/key/text through externalpermission/SAF handoff.
3. KeyentryUI must be secure, never retained in savedstate/clipboard/logs/screenshots; wipe callercharcopies and UIthumbnailbytes/bitmaps. Setup/remove runonIO. onRemoved clears previews and closes/resetcoordinator; activekeysalsocanceltheirboundroutesindependently.
4. Supply an authoritative currentcataloguedigest callback onIO; store/publish analysis only under originating capturedaccess gates. Separate owner approval/audit is mandatory before any incident/timeline application; no modeloutput genericmutation.
5. Grouped Android37 Kotlin/Java compilation, API compatibility, instrumentedtests, actualsecureUI, keyisolation/Keystore behavior, imagecodec/memorylimits, cancellation/revocationtiming and ownednetworkpacket/lifecycle checks are pending. Source checks and the certificate check do not replace them.

No release APK/security verification is claimed by this handback. Root independently reviews/integrates before any commit/build/sign/release.

## Fix round1 of5 — AI179-R1/R2/R3 and typed structured output

Read `reviews/AI179_INITIAL.md` in full and checked the three reported root causes against the implementation. The old double-quote-only scanner accepted a grammar different from Android JSONTokener; non-object-prefix text bypassed structured reference checks; and AiCredentialStore synchronized only a single instance while every provider/owner shared its Keystore alias. These required product corrections. Used receiving-code-review and systematic-debugging guidance. Scope was explicitly expanded to the shared credential store; no Main/UI/Controller/Coordinator/Access/Gradle/OCR/archive/workflow file was changed.

Status: corrections and targeted test package are ready for independent rereview. **Android Kotlin/Java compilation and all32 instrumented methods remain unexecuted/pending.** No paid request, real API credential, real shared-alias test/reset/deletion, Gradle bootstrap retry, CI, subagent, commit, push or signing was used. Statements in the initial handback about permissive prose output, header-prefixed Analysis.text, unseeded earlier Analysis and14 tests are superseded by this section.

### R1 — strict complete grammar before the Android parser

New pure Java `core/evidence/EvidenceStrictJson.java` exposes `public static void validate(String value,int maxDepth,int maxCharacters)`. It validates the complete strict grammar, including proper double-quoted strings/escapes, strict numbers, exact literals, separators and full consumption. It rejects comments, single/unquoted keys, trailing data/commas, duplicate decoded keys (including escaped-name collisions), unpaired surrogates and unsupported input. It bounds total characters, values<=100000 and recursion before invoking any Android parser; configured depth must be1..64 and configured characters<=4MiB. AI uses depth32/2MiB for outer Responses and depth16/100k for structured content. The old scanner was removed; there is no StackOverflowError catch or post-parse-only workaround.

`scripts/tests/EvidenceStrictJson179Checks.java` and `scripts/check_evidence179_ai_json.sh` were authored first. Running `bash scripts/check_evidence179_ai_json.sh` before adding the helper produced observed RED: javac reported the missing production EvidenceStrictJson.java. This was a missing-implementation RED, **not observed Android stack exhaustion**. After implementing the helper, the same command ran the production Java class and reported `EvidenceStrictJson179Checks: 52 passed`. Cases include the exact comment/quote12000-level exploit, single quotes/comments/trailing input, escaped strings/surrogates, malformed numbers/literals/delimiters, decoded duplicate keys, valid32-level and invalid33-level nesting, character and flat-value limits. It was rerun after integration and again passed52. `bash -n scripts/check_evidence179_ai_json.sh` passed. No transcription is substituted for the actual production Java helper in these observed checks.

### R2 — unambiguous validated structured response and read-only Details

Every provider output_text must now be one complete strict JSON object with exactly `summary`, `suggestions`, `events`. There may be at most one output_text; a refusal cannot be mixed with it. Markdown fences, prefacing prose, arrays as root, multiple blocks, duplicate keys, foreign/unknown fields and unknown ID fields fail closed. Suggestions have exactly kind/evidence_ids/title/reasons; events have exactly evidence_ids/title/date_time_quote/provenance/uncertainty. All field types/counts/lengths are checked. Citations are distinct nonempty subsets of selected evidence IDs. No wrapping text is silently treated as unchecked prose. The request prompt describes this exact contract.

Root additionally requested and approved `EvidenceAi.details(analysis): EvidenceAi.Details`, with the **same** grammar/schema/selected-analysis-ID validator. It is read-only and has no apply operation. Exact additive properties:

- `Details.summary:String`, `suggestions:List<StructuredSuggestion>`, `events:List<StructuredEvent>`, `status:String` (completed/incomplete), `refusal:Boolean`, `cachedInputTokens:Long`.
- `StructuredSuggestion.kind:String`, `evidenceIds:List<String>`, `title:String`, `reasons:List<String>`.
- `StructuredEvent.evidenceIds:List<String>`, `title:String`, `dateTimeQuote:String?`, `provenance:String`, `uncertainty:String`.

Collections are defensive unmodifiable copies. All event information remains source claims/uncertainty, with no invented epoch/zone/participant or verification status. Root must retain the full Analysis lineage when separately approving a subgroup/event, and continue to require independent edit/confirm/audit. UI must use Details, not a second lenient parser.

`Analysis.text` is now strict JSON preserving the accepted summary/suggestions/events values apart from serialized formatting, with an app-owned `_receipt` containing exactly status/refusal/cachedInputTokens/advisory:true/label. Provider content cannot include `_receipt`. The stored receipt is independently type/range/schema checked and its fixed label regenerated when read; cached usage is bounded by validated input usage<=1e9. Final serialized JSON, including receipt, is limited to100k characters and key-echo checked, with rejection rather than truncation. Incomplete-with-no-output gets a fixed safe summary; refusal wording is discarded after key-echo checking and replaced by a fixed refusal summary. Analysis model/bodydigest/time/selectedIDs/lineage/usage remain locally derived and appliedfalse. `details` failures are sanitized local messages. Root was sent the exact integration API and pure-JSON storage change.

### R3 — process-wide Keystore alias lookup/create fence

`core/editor/AiCredentialStore.kt` now uses a companion process-wide `KEY_GATE` for the full alias lookup/first creation. This covers all store instances and existing AI consumers, not just one evidence owner. The public one-argument Context constructor, default alias `private-gallery-ai-provider-v1`, original key, `ai-provider-$id.enc` files and ID-bound AAD are unchanged. A containsAlias=true but inaccessible/wrong-type/incompatible key fails; the implementation never regenerates/replaces an existing alias. The existing evidence per-owner/shared-path operation-generation gates remain separate.

A private `(Context,String alias)` constructor is reflected only by androidTest to use UUID disposable aliases. There is no public runtime test override. `AiCredentialAlias179Test` first-use threads start at a barrier across two store instances and distinct provider IDs, then require both ciphertexts remain decryptable through another instance. A second case creates an incompatible disposable HMAC alias, requires AES save failure and verifies that HMAC alias remains unchanged. Both delete only their own UUID disposable alias in finally. The original evidence Keystore test was also changed to use a disposable alias with injected protected backends/shared state; none of these tests uses/deletes/resets the real shared app alias. Production owner-ID construction/default alias and shared-state registry remain source-checked; the isolated test exercises the same store and generation mechanism rather than creating a real app key.

### Targeted regression expansion — authored, not run on Android

There are now20 AI methods,8 HTTPS route methods,2 evidence credential methods and2 alias methods (32 total). The17 initially new methods plus one final response-wait replacement case are:

1. `strictOuterAndOutputGrammarRejectsCommentQuotesSingleQuotesTrailingAndDeepInput`
2. `onlySingleStrictObjectAcceptsSelectedStructuredReferencesAndWrappersFailClosed`
3. `simultaneousOneShotAttemptsAdmitAtMostOnePostAndRetireBothResults`
4. `credentialRemovalDuringBodyWriteCancelsRouteAndPublishesNoAnalysis`
5. `actualIdentityRevisionCorrectionForeignReaderAndFailedAuthenticatedEofRejectConsent`
6. `prepareCancellationAfterConsumedBytesBeforeReaderReturnPublishesNothing`
7. `incompleteWithoutOutputIsLabelledAndOutputCharacterOverflowIsRejected`
8. `typedDetailsReusesStrictSchemaAndSelectedIdsAndKeepsReceiptAppOwned`
9. `selectedRecordImageAndTextQuotaBoundariesHavePositiveAndNegativeCases`
10. `credentialReplacementDuringResponseWaitRejectsAndWipesLateBytes`
11. `resetAfterReceiptAnd408Or503NeverRetryBilledPost`
12. `unknownLengthChunkedResponseOverflowIsRejectedWhileStreaming`
13. `coroutineCancellationDuringResponseReadDisconnectsWithoutPublishing`
14. `coroutineCancellationDuringUploadClosesSocketBeforeSourceWriterReturns`
15. `admissionPausedDuringCallCreationCannotEscapeConcurrentClose`
16. `sharedOwnerAcquireVersusRemoveAndReplacementUsesOneGenerationAndWipesCopies`
17. `existingDisposableIncompatibleAliasIsNeverRegeneratedOrReplaced`
18. `disposableAliasConcurrentFirstUseAcrossInstancesKeepsBothOwnersDecryptable`

Existing tests were updated for the strict output schema, pure-JSON receipt and actual seeded earlier Analysis exclusion. Barriers pause the actual fake send/body write/source authenticated-return, shared-state backend read, controlled HTTPS body read/upload, and private call creation. Assertions require no second POST/no Analysis after retirement, cancelled routes/sockets and wiped late response/credential copies. Simultaneous one-shot attempts deliberately retire the first request as the second attempt closes the consumed preview. Source cases change actual hash/size/MIME, a real catalogue revision and actual saved OCR correction; foreign reader owner is tested. Image source failure/cancellation occurs after raw bytes were captured inside the callback but before readVerified returns. Positive/negative quotas cover32/33 records,8/9 explicit images and100000/100001 selected text; response/output overflow and an exactly100000-character provider object whose receipt would exceed the final bound are rejected. Source32MiB/image2MiB/request20MiB edge allocations and real image codec/heap behavior still need runtime attention; no source constants are claimed as their runtime proof.

HTTPS fixtures use disposable pinned certs, no paid endpoint or account key. They now cover disconnect/reset after receipt,408/503 Retry-After:0 without billedPOST follow-up, unknown-length chunked overflow, coroutine cancellation during active upload/read, and deterministic admission-versus-close ordering. These remain prepared assertions, not observed packet/cancellation evidence. The new event-listener/reflection fixtures and private alias constructor must be checked by the grouped Android compiler/runtime.

### Observed source checks and remaining gates

Twelve focused source assertions passed: strict outer/structured validation precedes each Android parse, old scanner absent, no stack-overflow masking, single output_text, separate provider/receipt validation, receipt included in final quota/key check, Details reuses same parser/IDs, process-wide alias fence, existing-alias return/fail before generation, original alias/public API, and original files/AAD. A source scan found no real shared-alias deletion/reset in Task4 tests. These are source assertions, not Android execution. The32-method count is source enumeration only. Final whitespace checks include all owned files and the permitted shared credential-store delta.

Root must independently rereview these corrections and the additive Details/storage contract. Grouped Android37 compilation and all32 instrumentation methods, actualKeystore alias behavior, strict Android parser integration, secure UI/consent publication, private-socket permissions, ownedVPN/Tor packet/DNS/no-fallback checks, capability loss and service retirement remain mandatory and pending. No release/security approval or complete runtime verification is claimed.

## Test-only follow-up — AI179-F1-T1

Read `reviews/AI179_FIX1.md` in full. Its R1/R2/R3 source-clean dispositions remain unchanged. Corrected only the masked method `actualKeyEchoIncludingEscapedJsonNeverEntersAdvisory` and this report; no product/API change was needed.

The echo test now first accepts an otherwise identical valid summary/suggestions/events object with a non-key summary as a positive control. It then requires the fixed sanitized failure and no returned Analysis for four cases: literal fixture key in a valid structured summary, Unicode escape in the outer Responses envelope, Unicode escape inside the nested structured JSON summary, and a provider refusal containing the key. Fixture-only decode assertions establish that the three structured cases are valid schema and resolve to the same actual key; the nested case still lacks literal key bytes after outer decoding and exercises the final parsed/serialized echo guard. Each rejection also asserts one admitted fake POST and wiped response bytes. With echo checks removed, valid structured cases would return Analysis and the test would fail; refusal would incorrectly produce a successful fixed refusal receipt and also fail.

Observed checks are limited to the edited test/report whitespace and focused source/fixture-shape inspection. No Android compile/execution was available; the32 Android methods remain pending. The52 passing production-Java parser checks were deliberately **not rerun** for this Android-test-only change. No payload/key logging, real keys/provider calls, CI, subagents, commit, push or signing occurred. The HTTPS response-read cancellation fixture remains a completion/cancellation assertion, not independent peer-EOF proof before release, as the rereview notes.
