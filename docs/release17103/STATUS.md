# NovaOS UI and Private App repair checkpoint

Base: authoritative release/1.7.10-private-home HEAD 53da3f286a11f1431e2b465af78995f222688bd9. Same branch; no reset, replacement signing key, release activation or version increment. Last delivered production APK remains 1.7.10.2/code 54 from source 9efd19540a1d75efa1b30f4e23c9cbb096f93aff.

## Approved scope and implementation

Owner's detailed specification authorizes continuing the existing implementation. Work is batched before one focused existing-workflow run. This directory is a workstream name, not an activated Android version.

- Hidden popup tabs inherit initiating Private App identity and retain an opener graph. Closing a popup restores its live opener; Close App retires its whole popup group. Return Home, Lock, Panic and background revocation remain distinct existing operations.
- Retired WebView membership is fenced before cancellation, listener detachment and destruction. Late page, renderer, download, find, auth, dialog and upload callbacks cannot mutate another active app. TLS/client certificates remain denied. Debug diagnostics contain event/reason/scheme classification and booleans, never URLs, tokens, cookies or raw app IDs.
- Shared Nova connection panel replaces duplicated Primary/Hidden/Tor connection bodies without changing the owner's WebView composition or routing. Existing address/header/status/navigation remain owned by their shells.
- Shared full-page surface supports safe-drawing/IME insets and Fold width limits. Hidden Restore, bounded media preview and transfer progress use it. Shared transfer summary/progress preserve direction-specific authentication and existing cancellation/recovery engines.
- Shared media tiles add bounded thumbnail preview, information, selection feedback, count and sort controls to browser upload and AI reference pickers. AI reference selection commits only on Confirm. Collection covers now use thumbnail selection with Confirm/Cancel.
- Evidence pre-registration review uses image/PDF thumbnail grids. Exact source ownership/fingerprint is verified before reading; bytes are wiped by the existing verified-read engine. Tile-owned bitmaps are bounded, unpublished after retirement, wiped/recycled on retirement/disposal. No new plaintext files or network requests.

## Google authentication: unresolved release blocker

This is not diagnosed as merely a cookie problem. Source inspection proves popup ownership/close handling was missing; it does not prove that defect is the owner's exact Google return trigger. Existing onStop terminates the Hidden child and revokes broker authority. Main explicitly navigates Home only for RETURN, not every BACKGROUND termination. No external OAuth callback receiver, registered client/redirect, state/PKCE handoff or Custom Tabs integration exists. Only safe public HTTP(S) navigation is admitted.

An external browser does not inherit NovaOS's process-bound VPN or loopback WebView proxy; Tor isolation only permits the NovaOS package. Launching arbitrary external Google authorization would bypass these controls. Google provider policy also disallows directing OAuth to a developer-controlled embedded user agent. A third-party site's supported external authorization/one-time website handoff and owned redirect must be established before adding a callback; they cannot be invented or replaced by copying external cookies.

The exact Private App site/redirect and success/cancellation runtime trace have not been supplied. The unanswered clarification did not authorize relaxing routing or authentication. Relevant owner screenshots show only empty Browser Beta connection/connected shells, no website or OAuth redirect. Therefore popup repairs are not labelled a complete Google OAuth fix. No external browser launch, new exported callback, persistent cookie or routing exception is added.

## Verification plan and limits

Local Java popup ownership/retired-callback checks and duration-label checks pass; editor presentation 4/4 and evidence presentation 3/3 pass. Android SDK/Gradle distribution is unavailable locally. Existing fast-development.yml gains only a focused selection flag, not a new workflow. It compiles all app/test sources, retains existing host/JVM checks, and selects 28 Android 17 tests covering changed ownership, previews, selection, source isolation, transfer auth, connection lifecycle, large text and Fold-themed modal presentation. Android callback fixtures use real WebView identities and retained callbacks; they are not an actual Google transaction or complete Activity integration test.

Initial batch 4edee85 failed compilation on a missing testTag import; fixed at 79f6aaa. Run 41528043427 compiled app/test sources and JVM checks, then ran exactly 28 Android17 cases: 25 passed and three failed. Artifact 13180652740 SHA256 1375035d1282a3955709370b12c56f83a57c40e23edd77627bc556e3030d922c was downloaded and XML accounting verified. The overall run remains failed, not green. Failures were two stale Phone Gallery labels (production destination is Phone storage) and a new foreign-source test expecting IllegalStateException instead of the repository lookup NoSuchElementException. Test-only b2d0239 corrects those expectations; app/src/main and app/build.gradle.kts are byte-identical to 79f6aaa. Run 41529553329 reruns only the three failures from ui-cases.json. INITIAL_EVIDENCE.json records exact reusable passes; initial-cases.json preserves the complete original selection. Rerun SUCCESS: 3/3 exact selected Android cases passed once each; 7/7 focused JVM cases passed; app/test compilation and existing host checks passed. Artifact 13180569889 SHA256 dd12a4434b78be5a81f99c379b7ce7c4dca1e665fb2e92f33b7486bd10dad4d1 was downloaded and digest/XML/source/API37 preflight verified. RERUN_EVIDENCE.json records the result. Combined scope is 25 retained passes plus 3 passing retries against identical production source; this is not a claim of one 28-case green run. No live Google transaction, new live VPN/Tor route run, physical Fold acceptance or newly signed upgrade was performed.

Unchanged production code54 evidence remains source-scoped in docs/release17102. New focused UI evidence cannot substitute for Google success/cancellation, fresh live route verification if transport changes, or original-key upgrade evidence for a newly signed build. Signing remains blocked pending a supported and verified Google return flow. Existing code54 APK must not be presented as this request's completed update.

## Remaining UI limitations

Bounded fullscreen thumbnail preview is not original-resolution preview or video playback. VaultItem has no duration field: known-duration labels are supported by the shared tile but current browser Vault sources display an explicit unavailable label. Some already-professional pickers (AI source, Primary copy) retain their existing workflows; this checkpoint does not claim every picker requirement or universal modal migration is complete. Android-required system pickers and permissions remain unchanged.

## Verified checkpoint / next required evidence

Verified production-source checkpoint: b2d0239f0d40184f74a7f015bad326c80530d3b0 (app source identical to 79f6aaa). Source review found no remaining Important/Critical finding in changed popup/preview/transfer boundaries after retirement fencing. Google OAuth remains unapproved. Obtain the affected Private App base URL and identify whether the Google Yes screen is in NovaOS, Google app or an external browser; do not request or record authorization codes/tokens. Establish that site's provider-supported return contract and reproduce success and cancellation under verified routing before implementing an external handoff or signing. Independent UI work is checkpointed with explicit remaining picker/modal limitations, not represented as universal completion.

## 2026-11-03 verified continuation

Latest verified application source eaa518770f267957cc8383e1760b87167b5d32c9 adds shared full-page AI reference selection and encrypted transfer history. Run41629755724 passes 2 exact API37 behavior tests +7 JVM; archive digest/source/XML checked. Independent source review finds no Important/Critical regression in this narrow diff. CONTINUATION.md and CONTINUATION_EVIDENCE.json contain exact handover. Earlier test receipts remain source-scoped; no full updated suite claim. Google and signing blocker remains unchanged.


## Current coordinated lifecycle candidate

Earlier missing-website and diagnosis-only notes above are historical. The owner supplied a multi-site reproduction; four destructive paths are now coordinated in the current implementation. COORDINATED_IMPLEMENTATION.md records exact scope, boundaries and mandatory unexecuted gates. Candidate55 follows last signed54; no new APK has been delivered or signing activated. Do not reuse an old signed APK as this repair.

## Code55 failed runtime correction

17d9512 built successfully;41657787553 recorded18 passing cases and8 failed new continuity fixtures. Exact artifact/accounting, confirmed native-click defect and unresolved original Foreign-status observability are documented in FAILED_RUN_DIAGNOSIS.md. Current correction is test/fixture/evidence plumbing only; production lifecycle design and1.7.10.3/code55 remain unchanged. Reuse requires machine-checked compatibility and pinned original raw archive; all8 failed cases and their fresh packet gates remain mandatory. Signing remains inactive.
