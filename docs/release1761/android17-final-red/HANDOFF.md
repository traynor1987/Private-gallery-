# Private Gallery 1.7.6.1 — final Android17 regression checkpoint

**NO-GO for signing or owner installation.** All ten identified video/player/Panic-precondition/native-picker failures are repaired and passed in both broad and unfiltered final regression. One mandatory Tor natural-timeout classification assertion failed in the unfiltered suite. No signed code42 acceptance APK exists. Stop further speculative CI.

## Verified source and bounded execution

Recovered backup `56b3902e473412bba5c4729963ef81ee3b57c67f`, tree `0b368d4764ee6e80b7dfa84939840469aecb4b3e`; its tested parent is `719cec4be24d909c6dbae3aaa0b1842af6e6e925`, tree `76aa3dcd82bea27b801bcc915a2f97cba0db75d7`. GitHub ref/commit and fresh local clone/fetch matched; no active competing writer or CI observed. Existing branch `release/1.7.6.1-editing-settings-polish` retained. No reset, force push, new branch, main merge, unrelated features or replacement production signer.

One independently reviewed focused comparison ran at `a01a4fa15d0cf1b0b1f7b00c266e1481519cffb2` / `abf56e947b206701f7adcdf2fd92dc1d2151274f`: run40418481709, passed354JVM and27positive cases, retaining one deliberately failing original default-codec control. Its digest/CRC-verified evidence is in `../android17-repair/focused`.

The ONE full admission tested frozen `52ef76cf5f69fd271a712424f7db6038b02d5d5d`, tree `89353aff5575c31ccd9f66b31fab6fd43dd8a569`: [run40420314223](https://github.com/traynor1987/Private-gallery-/actions/runs/40420314223), job121912820276, FAILED. Artifact12699959429,65702567bytes,SHA256 `6ab7eb6b91a767d28d391d547b4eb29917ae3c3be37cf504511eaff55bf780a2`; independently downloaded, allZIPCRCs/path safety/source/SDK/debugAPK/namedcases checked. Subsequent GitHub handoff commit changes documentation/evidence only; distinguish branch backup from tested source.

## Established causes and exact corrections for the original ten

**Eight player cases and Panic playback precondition4003:** every failing archived path selected `c2.goldfish.h264.decoder`, then SELinux denied mediacodec read/write of app `memfd:MessageQueue`; binder EPERM and BufferPool errors led to `queueCSDInputBuffer` error14. Ordinary FileProvider playback showed the same failure as protected/encrypted playback. The unchanged representative case reproduced this chain on the default AVD. The same source/APK/fixture/image then passed all27original/adjacent cases with actual `c2.android.avc.decoder` and SELinux Enforcing before/after. This proves the emulator codec path caused these failures on this image; it does not prove hardware decoding on the Fold.

Correction: acceptance AVDs use `-feature -HardwareDecoder`; source-bound software/API37/SELinux-Enforcing receipts are required. No production player/crypto code, fixture, playback timeout, session authority or security assertion changed. The previous1751 inline-buffering/ownership/revocation corrections remain intact. Actual Primary and Hidden30second continuity, seeks/speeds, revocation/read-output wiping and reader retirement passed focused verification; all original eight player tests and real-playback Panic test passed broad and full final suites.

**Native TimePicker:** the original DatePicker OK tap was explicitly discarded by InputDispatcher while window opacity was0.348877. The test then looked for a TimePicker before submitting the date. Correction is test-only public `UiAutomation.injectInputEvent(event,true)`, which synchronises WindowManager animations and still injects real native input. Both owner FLAG_SECURE states remain asserted. Date Cancel, Time Cancel, confirmed date/time Save, exact adjusted metadata, original capture time/hash preservation are now asserted in the same case. Production date/time UI is unchanged. Both this and restore-original-date passed broad and full suites.

All ten repaired cases are explicitly named mandatory requirements in the final broad and unfiltered verifier; none was removed, skipped or replaced. Raw native XML preserves their actual outcomes.

## Final release gate results

| Gate | Exact result |
| --- | --- |
| Host release checks |128Python pass; browser53/readiness13/SOCKS20/control-redaction16 pass before freeze |
| Compilation/lint/debug assembly |PASS |
| JVM |354/354pass,0failure/error/skip |
| Early Android17 |101/101pass,0failure/error/skip |
| Broad Android17 |201/201pass,0failure/error/skip; all original ten pass |
| Security preflight |76/76pass,0failure/error/skip |
| Unfiltered Android17 |435/436pass,1failure,0error/skip; all original ten pass |
| Debug code41→42/restart |NOT REACHED because unfiltered suite failed |
| Final platform verifier |NOT REACHED; cannot accept failed full XML |
| Permanent signer probe / production build/signing |NOT REACHED |
| Original-key code41→42/restart |NOT REACHED |
| Owner physical acceptance |NOT PERFORMED |

Supported platform remains Android17/API37 only. Installed37.1SDK reports major37/nonpreview/Platform.Version17; emulator SDK37/release17/fingerprint and min/target37 debugAPK independently checked. Before/after media receipts record Enforcing/software decoding and exact source. Physical Fold API/hardware has not been read here. Historical Android16 failures remain FAILED historical records, not fixed.

## One precise remaining mandatory blocker

`uk.co.traynor.privategallery.core.browser.hidden.HiddenRuntimeFailureTest#stalledConnectTimeoutDisconnectsRuntimeAndClosesSocket`, assertion at test line108: **Expected natural production CONNECT timeout**.

Same frozen candidate/control results:

| Stage | Observed category / timing | Cleanup postconditions |
| --- | --- | --- |
| Early101,26Oct23:50:11.719UTC |TRANSPORT_TIMEOUT,30075ms |peer closed,reuse denied,no extra connection,failures0 |
| Security76,27Oct00:10:57.925UTC |TRANSPORT_TIMEOUT,30082ms |same,failures0 |
| Full436,27Oct00:15:03.570UTC |TRANSPORT_LOST,30091ms |disconnected/tabs empty;00:15:04.072peerClosedtrue,reuseDeniedtrue,extraConnectionfalse;failures1 |

The one failed postcondition is the required timeout classification; actual runtime disconnection, tab destruction, peer EOF, rejection of transport reuse and prevention of subsequent connection all passed. The same full suite's separate session-revocation control passed (CLOSED,513ms). Cleanup finally ran; no suppressed cleanup failure is in the raw XML. This is not evidence of a direct-network or DNS leak, but the mandatory natural-timeout assertion remains failed.

Live positive Tor verification passed at all three stages. Full suite: exact upstream200/81byte onion readiness3092ms, BBC HTTPS200/256bytes2761ms, fresh onion probe200/81bytes894ms. Early observer subscribed,106events,0dropped/ignored,readerAlivefalse. These results do not waive the failed negative control or establish historical onion-timeout causation.

Source path analysis: page transport errors can set `Page transport failed (timeout)` in `HiddenBrowserRuntime.kt`; the periodic Tor validity monitor sets `Transport lost` when `tor.port()` is zero. `HiddenTorConnection.port()` returns zero for closed/unavailable binder or failed status transaction; service status also depends on circuit readiness. A transient real readiness/service loss or ordering race/overwritten failure reason is plausible. Current logs do not capture the initiating event ordering or establish which occurred. **Do not call it a harmless label, established harness defect, established production routing defect, or solved historical Tor failure.** No speculative Tor production correction, timeout increase or assertion relaxation was made.

## Independent security decision

Read-only reviewer `/root/regression_security_review` independently audited the actual focused and acceptance diffs, then raw final XML/logs/receipts. Earlier GO authorised one focused comparison and then one full admission only. Final decision: **NO-GO; no further CI/signing/release admission justified by this run.** Reviewer confirmed all ten repairs pass, all above counts, successful fail-closed cleanup and positive onion observations; failure-category causation remains unproved.

Security objective remains real transport failure followed by bounded fail-closed runtime/tabs/socket cleanup, no fallback/DNS escape, correct isolation/authentication/revocation and the required natural-timeout classification. Available evidence demonstrates cleanup; missing evidence is why this original natural-stall request was classified TRANSPORT_LOST instead of timeout. No equivalent-gate waiver is approved. Existing production routing/DNS/session code and all release assertions remain intact.

One concrete next action, requiring owner approval before any additional CI: ONE focused affected-control diagnostic recording monotonic request-failure, Tor validity/port result and first/subsequent disconnect reason ordering, with positive/negative controls and redacted numeric categories. Review its actual instrumentation before launch; preserve this failed verdict. Do not start another full admission merely to seek green.

## Signing, preservation and handoff

Genuine target remains `uk.co.traynor.privategallery`,1.7.6.1/code42 (reserved next code above installed41),min/target37. Expected original production certificate SHA256 from verified code41 baseline: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. Candidate production certificate verification, production APK SHA256 and signed acceptance APK are unavailable because signing was not reached. The debug APK hash in AUDIT.json is evidence only and is not an owner-installable acceptance checksum.

No owner media, credentials, encryption keys, database formats or production signing identity were modified; no uninstall/data-clear on the owner's device. All completed Samsung editing, consolidated Panic settings and Hide Content presentation features are preserved. Production main tree remains `5bfab0394d64f27750bf6f72bb1e091a13b6dbc3`, identical to recovery start. Physical Samsung editor/inner-cover-screen/hardware decoding/high-bitrate/audio-sync acceptance remains pending. Synthetic upgrade preservation remains unproved for this candidate because the gate was not reached.
