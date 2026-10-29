# 1.7.8 completion tracker

Current feature candidate: `795bbe9617a49b53192b9db76a448f35d344c1d8`, version1.7.8/code47, Android17. Corrected grouped run40931786331 is pending. This document is not signing approval or release acceptance.

| Area | Implemented behaviour | Verification status |
| --- | --- | --- |
| Catalogue and ownership | Encrypted Primary/Hidden stores, authenticated handles, corruption/restart protection, bounded authenticated Hidden IPC | Prior and current JVM/host evidence passes; current final Android batch pending |
| Library and presentation | Adaptive themed tiles, CRUD, rename, pin/reorder, dedicated existing-browser controls, quick switching, address visibility, app settings | Prior controls/UI passed; new theme/font/width cases passed483; final source pending |
| PWA and icons | Public same-origin discovery, supported metadata, website fallback, encrypted raster/manifest/favicon/custom icons, bounded decode and cleanup | Prior discovery tests pass;483 icon scaling defect corrected with added portrait/odd-size case; final batch pending |
| Login/session | Separate PIN reopening, single-use launch fences, explicit shared Primary retention/clear/reset, durable clear-completion gate; Hidden ephemeral policy retained |27JVM and profile cookie/localStorage cases passed483; Hidden worker-thread authentication defect corrected; final batch pending |
| Documents and transfers | Bounded encrypted PDF/text/CSV imports, in-memory previews, owner-bound downloads, progress/cancel/retry, MIME-filtered confirmed vault uploads | Repository and17MiB PDF integrity/revocation cases passed483; full real browser transfers pending corrected labels/auth test batch |
| Screenshots | Confirmed visible-area PixelCopy into originating vault, conservative secure/protected/JS-off refusal, bounded memory and revocable Hidden pipe | Host/policy refusal cases passed; missing-window-frame fixture corrected; final capture case pending |
| VPN/Tor privacy | Existing immutable transports/processes, owned VPN DNS/sockets, Tor JS-off, fail-closed revocation; no new fallback | Historical native tests passed; final combined packet/protocol evidence mandatory and still pending |
| Preservation/signing | Code47 assignment audited; original177 archived APK checksum/source independently verified; separate exact-original-APK upgrade fixture and disabled production gate prepared | Original-certificate verification, code46→47 in-place upgrade/restart, final review and signed delivery not yet executed |

The483f281 batch compiled application/test APKs and passed27 JVM cases; Android ran35 cases with29 passes,6 failures and0skips. Its exact case results are preserved in `final-candidate-evidence/483f281`; original artifact digest and source are in its result manifest. XML line endings are normalized for Git text storage; original artifact bytes retain the recorded SHA256. Corrective source review found no security assertion weakening, but that is not a substitute for the corrected36-case runtime batch.

Next: inspect run40931786331 and its exact-source artifact. Diagnose any remaining failures; never treat skips or partial results as passing. Only after the complete focused gate and independent source/runtime approval, freeze the tested SHA, enable the existing acceptance178 job with reviewed control hashes, verify original signing identity and exact archived177 upgrade/restart, then publish one owner-acceptance APK.

User-visible compatibility and physical Fold checks: `OWNER_ACCEPTANCE.md`. Primary uses a shared cookie profile; Hidden sessions remain ephemeral. Screenshot and PWA limitations are explicit. Historical1.7.7 release/evidence and its pending physical acceptance are preserved.

## Owner-requested stop — 2026-10-29 18:43 UTC

Development stopped on the owner's explicit instruction. Last tested application source:795bbe9617a49b53192b9db76a448f35d344c1d8. Grouped run40931786331/job123651818969 has completed FAILURE. Host checks and application/JVM/test-APK compilation stage passed. Android logs report3failures: BrowserVaultScreenshotTest.visibleWebsiteOnlyAndWipedAfterConsumption (PixelCopy status3); PrivateAppSessionSettings178Test.fresh_pin_dialog_rejects_wrong_pin_accepts_correct_once_and_discards_revoked_request (presentation authority callback observed off Main); HiddenVpnLive1762Test.nativeChildPostsFormsUploadsAndRevokesOnOwnedVpn (Fresh visible web touch target Run JavaScript POST). Do not infer a completed packet/DNS gate from passing individual cases: the aggregate script stopped on instrumentation failure.

Artifact12924006012, API-reported SHA2566d472cd900c26b84fa58cba9db39b34f182440bf02433be0918b778fd8021984, contains current evidence. This artifact has not yet been independently downloaded/parsed at the stop; inspect it before further conclusions. URL:https://github.com/traynor1987/Private-gallery-/actions/runs/40931786331 . The earlier483f artifact and exact failing XML remain preserved separately.

No further fixes or test runs were started after the stop. No signing approval, original-key178upgrade, finalAPK or physical acceptance. Production gate remains disabled: frozen source zeros, no178marker or approvalJSON. Version remains1.7.8/code47. Historical177 and evidence remain unchanged.

Exact next task when authorised to resume: download/hash-check artifact12924006012, inspect its XML/logcat and the three failures above; distinguish test-harness mistakes from production defects, correct affected boundaries without weakening assertions, rerun only necessary grouped verification, then independent review. Signing and exact archived177 in-place upgrade/restart remain mandatory after the focused gate passes. Do not restart catalogue/PWA/library/documents foundations.
