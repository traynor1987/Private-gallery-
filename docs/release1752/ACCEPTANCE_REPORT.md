# Private Gallery 1.7.5.2 — owner acceptance candidate

**GO for owner acceptance only.** Automated release gates and independent review passed. Physical Galaxy Z Fold acceptance is pending. Nothing has been merged into main.

## Source and delivery identity

| Item | Verified value |
| --- | --- |
| Authoritative 1.7.5.1 baseline | `00509aa729d0aba4283fa0d000186153984cf87f` |
| Baseline tree | `43227bf4328a13ba7fbc605af324bdef79724b22` |
| Frozen tested/signed source | `ec05b23ec28a44730439bd1090c1e64afff8f140` |
| Frozen source tree | `cc05dfd7d0ce7a0de2d8feccd388b4ebeb718bef` |
| Development branch | `release/1.7.5.2-recents-browser-repair` |
| Package | `uk.co.traynor.privategallery` |
| Version | `1.7.5.2`, code `40`, minimum SDK `36` |
| Original certificate SHA-256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |
| APK SHA-256 | `deddec834271291704806a25cb986aacd285b89f4346f10bad12f0e450577990` |
| APK size | 104,011,394 bytes |

[Signed acceptance APK](https://chatgpt.com/library?file_id=libfile_0ab33b1f9eb08191a56e3ca961ea2978&account_id=personal). Install over the existing code 39 application. Do not uninstall, clear application data, reset PINs or replace keys.

## Verified causes and corrections

The Hidden viewer marked every `ON_STOP` as requiring a covered preview, even when only the grid was open. A later explicit photo selection inherited that unrelated flag. A second lifetime defect let a rapid stop/start retain repository/editor objects whose read authority had already been revoked, because Compose could coalesce the foreground changes. The correction conceals only an existing selection, clears the old selection latch on a fresh explicit grid selection, and retires the old unlocked child before creating fresh authenticated read owners. It does not restore expired authentication or remove future privacy epochs.

Primary fresh selection already accepts its new request's current privacy epoch; focused MainActivity tests confirmed that path and denied re-open behavior without a production change. Previously displayed content remains concealed after external/background transitions. Android's public lifecycle and focus signals do not uniquely distinguish every Recents gesture from Home or another app. No timing-only classifier or automatic reveal was introduced; ambiguous transitions retain Privacy Shield.

The active Primary browser's WebView lacked explicit native layout parameters. AndroidView supplied `WRAP_CONTENT`, which makes Chromium treat layout height as content-sized and collapse CSS viewport-relative heights. Harmless delayed-dialog pages reproduced a 34-pixel dialog with zero-height scrollable text despite an 840×780 native viewport. Setting both native layout dimensions to `MATCH_PARENT` restored a 399-pixel dialog and 234-pixel scrollable text. A same-provider test-only `WRAP_CONTENT` control reproduced the collapse again on both SDKs.

No JavaScript consent bypass, website-specific injection, routing fallback, TLS exception, unsafe file access, cookie-policy change or permission expansion was introduced. The Hidden browser factory and Tor/VPN implementation were unchanged.

## Tests and independent review

[Full release workflow 40182954140](https://github.com/traynor1987/Private-gallery-/actions/runs/40182954140) completed successfully on frozen source `ec05b23`. Downloaded archive SHA-256 and ZIP integrity were independently checked. The strict release verifier was rerun against the downloaded raw XML and actual debug APK.

| Gate | Android 16 / API 36 | Android 17 / API 37 |
| --- | --- | --- |
| Focused UI | 134 passed | 134 passed |
| Security preflight | 74 passed | 74 passed |
| Unfiltered regression | 401 passed | 401 passed |
| Required new named cases | Passed in focused and full XML | Passed in focused and full XML |
| Debug 39→40 upgrade and restart | Passed | Passed |
| Original-key production 39→40 upgrade and restart | Passed | Passed |

All listed suites had zero failures, errors or skips. JVM XML independently totals 349 passed, zero failures/errors/skips. Host, encrypted-storage/large-media, camera, browser policy, transport, secret scan, compilation and lint gates passed. Original-key upgrade fixtures verified independent PINs, synthetic encrypted Primary/Hidden media, collections, settings and VPN configuration after install and restart. The same APK hash was bound to both production upgrade proofs.

Earlier focused runs isolated each actual failure before another run; their RED/diagnostic evidence is preserved in the recovery record. Final browser-focused tests passed 9/9 on each SDK. The separately reviewed [supplemental run](https://github.com/traynor1987/Private-gallery-/actions/runs/40184858082) passed 2/2 on each SDK against identical production/build inputs: generated PNG selection, explicit upload authorization, byte equality, local multipart serialization, manual modal dismissal, and retained WebView resize 840→392→840. It performed no network upload. Its results are separate from the frozen candidate's full XML.

The independent read-only reviewer rechecked both artifact digests, raw counts and case names, source/APK bindings, original certificate, package/version/minimum SDK, and both signed upgrade/restart proofs. Recommendation: GO for owner acceptance only, not exhaustive security assurance or main-merge approval. See [review history](REVIEW.md) and [raw final evidence](final-acceptance/EVIDENCE.json).

## Remaining physical acceptance

Use the [Galaxy Z Fold checklist](OWNER_ACCEPTANCE.md). Actual Samsung gesture/Recents frames, held/cancelled gestures, fold posture/configuration changes, device biometrics and the owner's real data have not been physically tested here. External-service behavior was not tested; compatibility evidence uses harmless synthetic HTML only. The local multipart fixture does not prove a real network upload. Owner media was never accessed or modified. VPN+Tor remains unavailable.

Preserve the conservative concealment of already displayed media when transitions are ambiguous. A fresh explicit selection in a still-valid session should display normally. Do not declare physical acceptance until the owner completes the checklist.

## Interrupted-work recovery

The signed APK must remain bound to frozen source `ec05b23`; later branch commits contain supplementary tests and evidence, with identical production/build inputs. Fetch and verify the branch SHA/tree before editing. Do not relaunch the completed full workflow for documentation changes. Recover using `V1_7_5_2_RECOVERY.md` and the immutable run/artifact IDs in final evidence. Never force-push, overwrite newer valid work, merge main without owner approval, or create replacement signing material.
