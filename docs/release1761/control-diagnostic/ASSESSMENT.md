# Same-instance Tor control diagnostic — 26 October 2026

**NO-GO for release/signing or another speculative full admission.** Both focused cases passed, but the earlier live-onion failure was not reproduced and its root cause remains unproved. No production or gate change is justified by this run.

## Verified recovery and scope

Starting SHA `1e53242f39750cc90f1c5d02666fad283d99ff0d`, tree `d89abbc74e7fb5122e7227a18be07568c489064e`; same release branch, clean fresh clone, no active local competing writer/build or remote in-progress run observed at recovery. All five previous failed job logs independently read, and all previous evidence preserved. Original production, browser, dependencies and admission gates unchanged throughout this continuation.

The prior API36 case exhausted its90second controlled-readiness budget. Its subsequent diagnostic probe completed local TCP and SOCKS authentication immediately, then lacked a complete SOCKS CONNECT reply within30seconds. That stage localization applies to the **post-failure probe**, not conclusively to the original request. Infrastructure versus harness versus production causation remains unclassified.

The new instrumentation uses only a separate connection to the same Tor instance's existing private Unix ControlSocket. Pinned upstream TorService source0340ba1609cb73e663e202a316d083803e399db4 independently inspected; source SHA256 `9a80508dc2e0c9a6f18d9edee88541446f268d6f47bc8b187f79545ea5ebdb62`. AUTHENTICATE and SETEVENTS STREAM/CIRC/HS_DESC only; no listener/configuration/circuit manipulation or alternative route. Subscribe before original requests; match fixture in memory; emit whitelisted enums/numeric IDs and monotonic elapsed times. Preserve original verdict and independent fixture checks.

## Diagnosed instrumentation compile failure

Run40346491402, sourceb6fc708c22db53cb7989d76f937429f4cdd4a99d, failed compiling the test-only collector: Java-style records.size() on a Kotlin list. No Tor/emulator case ran. Correction is solely records.size; independent review confirmed the diagnosed change. Original failure remains recorded in compile-red.txt and original GitHub logs. No unchanged-source retry.

## Focused runtime evidence

Run [40347268991](https://github.com/traynor1987/Private-gallery-/actions/runs/40347268991), source `b04460ad1212de63a8f2602f660e67b6b9c16efa`, tree `e4823dcb915fdffa52ed3f26757430d895a69ad1`, SUCCESS. Only existing diagnostic workflow ran. No full admission or production signing.

Artifact12661303209 ZIP SHA256 `138c3b4ebb5b94bb45aed4dec1388e59c4b4e8cd872ecb1293e3347b2f26f690`, independently downloaded; digest, all ZIP CRCs and extraction path safety verified. Raw filtered receipts retained under runtime/. Both platforms used debug APK SHA256 `ead880368e9d0e6007b1c7337b4f0623f53cfcb42cc45793ea6877a3460f5ff1`.

| Observation | API36 | API37 |
| --- | --- | --- |
| Original case | 1pass/0failure/0error/0skip | 1pass/0failure/0error/0skip |
| Fixture checks immediately before/after | Both exact81byte PASS | Both exact81byte PASS |
| First onion readiness | upstream200,81bytes,4425ms | upstream200,81bytes,4835ms |
| Fixture descriptor requested→received | 1087→2820ms | 913→3403ms |
| Original fixture stream | 52 SUCCEEDED,circuit13,4950ms | 54 SUCCEEDED,circuit13,5262ms |
| Fresh onion navigation | stream57 SUCCEEDED,circuit13 | stream59 SUCCEEDED,circuit13 |
| Collector record counts | 93,dropped0,ignored0 | 79,dropped0,ignored0 |
| Collector reader after close/join | Still alive after1.5s | Terminated |

Event timestamps above use collector-relative monotonic time; readiness duration is request-phase-relative. Both listeners subscribed successfully before original requests. Bootstrap reached100 and ordinary HTTPS worked. The original case also verifies authority revocation/port0 when service connection closes. These are integration observations, not packet-level DNS-leak proof. Separate existing routing/DNS policy assertions remain mandatory.

API36 reader shutdown/completeness is not fully established; existing service cleanup still executes afterward. Do not interpret missing events, fixture=false, or these successful traces as proof of an absent failure stage. Observation itself can alter timing. No failed original request has yet been captured with this observer; successful traces cannot retrospectively explain older timeouts.

## Comparison and host verification

Fresh independent artifact12612289763 and12624109820 downloads repeated baseline/candidate comparison. ZIP SHA256 `65bb6f8555cfe3ace4eb8317b7dc944d35439207c7f16aaaf6ebd7a7e2279416` and `f182e21fb127789d93d4701562dd8453e52b7bcc4e8c98cfb0d64c6fda757e33`, respectively; all CRCs verified. All four APK Tor ABIs byte-identical, matching preserved owner-tor-acceptance/COMPARISON.json. Browser production tree remains `8e9df17cceb860b2bbee8fb2ea8d54c013055ec5` on accepted baseline and candidate. This excludes detected component differences, not all security/integration defects.

Host88Python,53browser-policy,13readiness,20SOCKS and16new control-redaction checks PASS. Three existing mocked fixture-log ResourceWarnings retained/reported. No-secret and whitespace checks PASS. New redaction checks reject raw targets, paths, relay identities, credentials, malformed IDs, injected lines and unknown event types. Existing synthetic SOCKS/readiness controls continue rejecting broken negotiation, wrong bodies, revoked authority and prohibited failures. No timeout/assertion relaxation.

## Independent review and release decision

Independent read-only reviewer checked original evidence, pinned upstream path/auth, actual instrumentation and compile correction before runtime; independently rechecked runtime XML/source/SDK/APK/fixture receipts and correlated timelines afterward. Verdict: NO-GO for release/signing or another full admission. No causal production fix or policy exception established. Successful diagnostic evidence is explicitly not acceptance.

Completed Samsung editor, Panic settings and Hide Content changes are untouched. Package remains uk.co.traynor.privategallery; intended genuine1.7.6.1/code42, minimumAPI36. Existing decoy remains UI-only0.0.1/Build4. No owner data, credentials or keys touched; no replacement signer; no uninstall/data-clear/main merge/new programme.

Full candidate regression, complete security suites, original-key41→42 upgrade/restart and physical Samsung/Fold acceptance remain incomplete. Expected original certificate is `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`; no new signed candidate exists, so candidate certificate verification, production APK SHA256 and acceptance APK are unavailable.

Stop here per repeated-unexplained-failure constraint. Preserve successful and failed records together. The remaining evidence gap is a **failing original request with correlated control events**, followed by an independently reviewed cause-specific correction. Do not infer infrastructure-only failure or retry full admission solely because this focused run is green.
