# Private Gallery 1.7.6.1 — release status

**NO-GO for signing and owner installation.** Implementation is complete, but mandatory regression is blocked by unresolved live Tor reachability. No signed code 42 acceptance APK exists. No physical acceptance is claimed.

| Item | Verified state |
| --- | --- |
| Baseline | Owner-reported installed 1.7.6/code 41; authoritative remote `01b4e09f7d5d04e09b2578dbc5bb3d763b91f5cf`, tree `a8594b6357ffc856fc2bbefe4d64e42884dda39d` |
| Latest frozen source | `e4d55c678183609b27b99414fc0e206fab6271e1`, tree `91b9a78aa467ff534e05b9cdf4825e3d42c52dcc` |
| Target identity | `uk.co.traynor.privategallery`; genuine 1.7.6.1/code 42; minimum API 36 |
| Latest admission | [40258874814](https://github.com/traynor1987/Private-gallery-/actions/runs/40258874814), failed |
| Host checks | 87 Python tests and 13 controlled-readiness policy checks passed; prior selection/journal/lease/presentation/security checks retained |
| Current JVM/lint/build | 354 JVM cases passed; compilation, lint and debug assembly passed |
| Current API 36 early gate | 99/99 passed, no errors or skips; real controlled onion exact bytes and original fresh navigation passed |
| Current API 37 early gate | 98/99 passed, no errors or skips; sole failure was controlled onion readiness exhausting its unchanged 90-second bound |
| Current full regression / upgrade | Not reached. Required 201 broad, 74 security, 434 unfiltered per SDK plus debug and original-key production code 41→42/restart remain mandatory |
| Historical evidence | Previous source `fe650286` passed both 98-case early suites and all 201 API36 broad cases; security stopped at 73/74 on a 30-second onion timeout. This does not count as current-source completion |
| Expected original certificate | SHA-256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`, verified in baseline release evidence; new signing stage has not been reached |
| New APK SHA-256 / acceptance APK | Unavailable; signing is deliberately blocked by the mandatory gates |

## Implemented refinements

Both authenticated vaults expose **Edit in another app** with a compatible Android chooser, explicit external-edit consent, manual result selection, preview, verified encrypted Save as New and separately confirmed Replace Original. The explicit public Gallery export/view/edit/reimport fallback supports editors that cannot open the private read-only URI. Imports remain bound to the originating vault and require current authority. Known exported and edited public URIs are offered for cleanup only after verified import. Unsupported document-provider copies require manual cleanup. Private Gallery does not claim to erase editor caches, backups or cloud copies.

Panic Exit and Quick Lock configuration now have one location, Settings → Privacy & Security. Original Secret/Hidden Settings discovery, authentication and Hide Content remain intact.

Hide Content retains the existing empty-gallery presentation. Ordinary Settings/About show 0.0.1 · Build 4 and the requested basic-gallery description while concealed, then restore the genuine version after authorised reveal. Android metadata, diagnostics and update compatibility remain genuine. Concealed storage, Hidden indicators, neutral navigation and asynchronous storage callbacks are guarded. New editing, settings and concealment cases passed on both platforms in the latest early suites.

## Samsung compatibility

Samsung Gallery is offered only when it advertises a compatible handler. ACTION_EDIT does not guarantee edit-and-return; manual result selection and the explicit public-copy fallback remain available. Samsung's actual Fold intent filters, Object Eraser, cloud behaviour and inner/cover-screen acceptance require the owner's physical device. After process death, reopen the original photo and select the saved result again.

## Independent review and blocker

Read-only implementation review found concealed Storage exposure and a CI test entrypoint defect; both were corrected and covered. Two legacy UI fixture mismatches were diagnosed and corrected without weakening assertions. The controlled-onion readiness prerequisite was independently reviewed: it uses the same app transport/connection, has a strict watchdog, requires exact upstream bytes and retains the original fresh navigation assertions. It proves established-path reachability, not cold first-request reliability.

The latest raw evidence shows API36 upstream HTTP200/exact81-byte readiness and fresh navigation. API37 reached Tor bootstrap100 but had two 30-second local timeouts; watchdog closure at 90,001ms ended readiness. No API37 HTTP request reached the fixture backend. The exact SOCKS/onion/HTTP failure stage is unresolved. Initial independent runner readiness was about 23 minutes earlier; the new early gates did not yet take fresh independent health checks immediately around that case. This does not establish a production or API37-specific defect. Production browser source is unchanged.

Independent final assessment: **NO-GO for signing or another full admission**. Preserve the failed evidence and prepare one reviewed diagnostic-only affected-case run with fresh independent fixture checks before/after and bounded SOCKS-stage diagnostics after an original failure. Retain the failure verdict. Do not increase timeouts, add fallback routes, change the Primary browser or treat diagnostic success as release acceptance.

## Recovery and owner acceptance

Source and evidence are backed up on `release/1.7.6.1-editing-settings-polish`; no force push, main merge or old-branch overwrite. Read [V1_7_6_1_RECOVERY.md](../../V1_7_6_1_RECOVERY.md) for exact continuation and the failed-run receipts under this directory. No owner media, credentials or encryption keys were modified. No replacement signing material was generated.

The [Galaxy Z Fold checklist](OWNER_ACCEPTANCE.md) is prepared for the eventual verified APK. Install over code 41 only after all mandatory tests, original certificate and synthetic in-place upgrade checks pass; never uninstall or clear data. Physical acceptance remains pending.

Final diagnostic outcome and complete12-field blocked handoff: [FINAL_HANDOFF.md](FINAL_HANDOFF.md). API36 failed belowHTTP/SOCKSCONNECT; API37 passed withfreshfixturechecks onboth. No fifthfulladmission/signing justified.
