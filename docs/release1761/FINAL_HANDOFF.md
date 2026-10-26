# Private Gallery 1.7.6.1 — blocked release handoff

**NO-GO for signing or owner installation.** The three requested refinements are implemented and backed up. Mandatory live Tor regression remains red. No signed 1.7.6.1 acceptance APK exists; no physical acceptance is claimed.

## 1. Verified baseline

Authoritative 1.7.6 remote: `01b4e09f7d5d04e09b2578dbc5bb3d763b91f5cf`, tree `a8594b6357ffc856fc2bbefe4d64e42884dda39d`. Its tested/signed source was `180a7ce7048f56371d1e0566b6333de65fd1aa75`. The owner reported this release installed as code 41. Baseline recovery, CI and signing evidence were independently checked. No physical device is attached here.

Target remains genuine `uk.co.traynor.privategallery`, version 1.7.6.1/code 42, minimum API 36. No package, database, key-format or signing-identity change.

## 2. Exact implemented changes

- **External editing:** consistent Primary/Hidden **Edit in another app** entry; compact chooser/import/cancel flow; explicit encryption-boundary/cloud consent; implicit Android ACTION_EDIT chooser with read-only content grants. Explicit verified public Gallery export/ACTION_VIEW/edit/reimport fallback. Preview, verified encrypted Save as New, separately confirmed Replace Original, originating-vault authority checks, app-owned temporary cleanup and post-import user-authorised known-copy cleanup remain enforced.
- **Settings:** one Panic Exit configuration location in Settings → Privacy & Security, including Quick Lock, safe test and screenshot controls. Original Secret/Hidden Settings discovery, fresh authentication and Hide Content access are preserved.
- **Hide Content:** existing empty gallery retained. In-app Settings/About show 0.0.1 · Build 4 and “Basic gallery functionality. More features coming soon.” while concealed; authorised reveal restores genuine version display. Android metadata, diagnostics and update compatibility remain genuine. Concealed storage/Hidden indicators, neutral tab routing and late storage callbacks are guarded.

Production source is unchanged since the reviewed `813dd65c` correction; later commits modify tests, diagnostics and recovery evidence. The working Primary browser compatibility correction was not modified.

## 3. Samsung compatibility findings

Samsung is offered only when it advertises a compatible handler. A private read-only URI is not promised to support Samsung direct edit-and-return. The supported fallback explicitly publishes a verified generic Gallery copy, opens a compatible Gallery viewer, then lets the owner edit, select, preview and encrypt the result back into the originating vault.

Samsung Gallery/Object Eraser, actual Fold handler filters, cloud behaviour and inner/cover-screen operation require physical acceptance. Process death requires reopening the original and selecting the saved result again. Known public copies are offered for cleanup only after verified encrypted import; unsupported document-provider copies need manual cleanup. Private Gallery does not claim to erase Samsung caches, backups or cloud copies.

## 4. Hide Content verification

New native tests passed on API 36 and 37 in the last full-admission early suites. They cover concealed tabs, selected neutral routes, empty states, no Storage panel, fake Settings/About display, genuine PackageManager version, restart/background/Panic persistence, authorised reveal, genuine-version restoration and retained synthetic state. Original Secret/Hidden access and independent vault authority tests remain required. No owner media was used.

## 5. Exact test and CI results

| Evidence | Result |
| --- | --- |
| Latest host checks | 88 Python tests, 20 synthetic SOCKS diagnostic checks and 13 readiness-policy checks passed |
| Last full admission | [40258874814](https://github.com/traynor1987/Private-gallery-/actions/runs/40258874814), source `e4d55c678183609b27b99414fc0e206fab6271e1`, **FAILED** |
| That source's JVM/lint/build | 354 JVM cases passed; compilation/lint/debug assembly passed |
| That source's API36 early gate | 99/99 passed, zero errors/skips, including real exact-byte onion readiness and fresh navigation |
| That source's API37 early gate | 98/99 passed, zero errors/skips; sole failure was controlled onion readiness exhausting 90 seconds |
| Historical broader evidence | Source `fe650286` passed both 98-case early suites and API36 broad 201; security stopped at73/74 on onion timeout. This is not current-source full verification |
| Diagnostic-only run | [40262863378](https://github.com/traynor1987/Private-gallery-/actions/runs/40262863378), source `dfd7f3c5b998e87aecc6e48592f85428b6d07080`, **FAILED**, original verdicts preserved |
| Diagnostic API36 | Original case failed; independent fixture retrieval passed immediately before and after |
| Diagnostic API37 | Original case passed, real exact-byte readiness/fresh navigation; independent fixture retrieval passed immediately before and after |
| Remaining release gates | Both full platform suites, complete security regression, debug upgrade/restart and original-key production41→42 upgrade/restart remain incomplete. Signing is blocked |

Admission history is preserved without reclassifying failures:

| Run | Exact checkpoint | Outcome |
| --- | --- | --- |
| 40247364990 | `40d645b6` | API36 focused 96/97; legacy expected title case while the existing page heading renders uppercase |
| 40249372374 | `813dd65c` | Both focused 97/97; API36 broad200/201, legacy child-only Panic fixture expected its parent heading |
| 40253419557 | `fe650286` | Both focused 98/98 and broad 201 passed; security 73/74, controlled onion timeout |
| 40258874814 | `e4d55c67` | API36 early 99/99; API37 early 98/99, controlled onion readiness exhausted |
| 40262863378 | `dfd7f3c5` | Diagnostic only: API36 failed below HTTP; API37 passed; fresh fixture checks passed around both |

All existing mandatory counts remain: 354 JVM; each supported SDK 99 early, 201 broad, 74 security and 434 unfiltered cases, plus source/APK/name and upgrade/restart gates. No assertions, deadlines, route restrictions or signing gates were relaxed. Failed runs remain failed; no fifth full admission was launched.

## 6. Independent security review and blocker

Read-only implementation review found concealed Storage exposure and a CI test-entrypoint defect; both were corrected and covered. Legacy heading fixtures were corrected to test the actual UI while retaining/strengthening assertions. The bounded controlled-readiness phase and diagnostic-only instrumentation received independent actual-diff review before their runs.

Final independent evidence audit confirms **NO-GO**. Both diagnostic SDKs used the same source and debug APK hash. On the failing API36 case, loopback TCP and SOCKS5 authentication completed immediately; no complete SOCKS CONNECT reply arrived within the bounded 30-second post-failure probe. HTTP was never reached. Fresh independent fixture checks succeeded before and after. API37 passed in that same diagnostic, so the problem is not deterministically API37-specific.

The evidence narrows this reproduction below HTTP but does not identify descriptor retrieval, introduction/rendezvous, circuit failure or another Tor-side condition. No production fix, timeout increase, fallback route or Primary browser change is justified. The watchdog's failure result was preserved; diagnostic success cannot count as release acceptance. Established-path reachability does not prove cold first-request reliability.

## 7. GitHub backup and recovery

Branch: `release/1.7.6.1-editing-settings-polish`. Last full-admission tree: `91b9a78aa467ff534e05b9cdf4825e3d42c52dcc`. Diagnostic tree: `e3bbf05de318339e724356a86a8c7c2bedddeb2d`. Source checkpoints and documentation trees were independently fetched and matched locally. The accompanying handoff states the exact final documentation SHA.

Raw reports, numeric logs, fixture receipts, original verdicts and digest audits are preserved under `first-red`, `second-red`, `third-red`, `fourth-red` and `tor-diagnostic`. Diagnostic ZIP SHA-256: `99a089787a777df2dd27b06eb272894db945a5de6c09e78f2a9df285ac4e64ae`; all ZIP CRCs verified. Read [V1_7_6_1_RECOVERY.md](../../V1_7_6_1_RECOVERY.md) for exact continuation. No force push, main merge, old-branch overwrite or restart of prior programmes.

## 8. Original certificate

Expected original production certificate SHA-256, verified against baseline release evidence:

`94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`

The new signing stage was never reached. No replacement signing material was generated. A new signed artifact cannot be certified until all mandatory gates pass.

## 9. New APK SHA-256

**Unavailable: no signed production code 42 APK was built.** A debug APK hash in diagnostic receipts is not an owner-acceptance checksum.

## 10. Signed acceptance APK

**Not issued.** The mandatory regression block prohibits signing and delivery. Never uninstall or clear data to work around this gate.

## 11. Remaining limitations and continuation

Investigate a specific Tor connection-establishment hypothesis using narrowly filtered Tor control-stage evidence, preferably with Tor transport expertise, before another run. Preserve the same programme/branch and original failures. Do not extend deadlines, relax assertions, add routes, alter the working Primary browser or launch speculative full admissions.

After an evidence-backed correction is independently reviewed, a stable exact source must complete every original JVM/Android/security/upgrade gate before original-key signing, actual APK/certificate/checksum verification and owner delivery. Physical Samsung and Fold acceptance remains pending. No owner media, credentials or encryption keys were modified.

## 12. Recommendation and owner checklist

**NO-GO for signing/release.** Do not claim completion or physical acceptance. The [Galaxy Z Fold checklist](OWNER_ACCEPTANCE.md) is prepared for a future fully verified signed APK. Do not begin 1.7.7/1.8 or rewrite vault architecture to bypass this blocker.
