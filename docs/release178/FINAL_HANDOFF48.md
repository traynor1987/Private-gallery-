# Private Gallery 1.7.8 — code 48 owner handoff

The signed owner-acceptance APK is ready. Install over code 47 without uninstalling or clearing data. Physical Galaxy Z Fold acceptance remains required.

Saved apps now open directly in app-style presentation on their existing authenticated WebView: compact name/icon/connection header, expandable controls, no permanent browser tab strip or large bottom toolbar, optional address bar, safe scope/origin warnings and branded loading. App/browser-style and address preferences are independent, encrypted and backward-compatible with existing catalogues. Settings expose name/icon/start-page editing and existing PIN/profile controls. The switcher shows icons, current/open state, library return and session close; reopening preserves the active renderer/page state after the existing authentication checks. Closing/removing an app retires its view and owned transfers.

Adding a URL automatically checks bounded public metadata. Supported same-origin HTML base, entity URLs, declared start/scope/display overrides and raster icons are handled; later valid icons can replace a failed candidate, and optional icons cannot exhaust metadata discovery. Compatible entries offer **Install as Private Web App**; other entries offer **Add as Private Website** with a reason. No separate Android package or public shortcut is installed. Redirected, login-only, script-generated or cross-origin metadata may be unavailable. Push, background execution and guaranteed offline operation are unsupported.

Primary apps/tabs retain their shared browser profile and existing retention policy. Hidden apps/tabs share their current temporary profile and lose logins when that browser closes. Per-app cookie isolation/clearing is not promised. VPN/Tor ownership, DNS isolation, no direct fallback, Tor JavaScript-off and authentication revocation remain enforced.

Final source `41b6d065a3fbef2f711198c9c778f7100e620c8f`:41 Android/33 JVM PASS, strict network gates PASS, independent review approved zero open Critical/Important findings. Production control `1613339778a8c29bafe699af87b40504152977b3`/run41156213785: lint, original-key production identity, exact signed47→48 upgrade and restart PASS. Independent artifact review passed full signature/digest/manifest and preservation proofs.

- Package: `uk.co.traynor.privategallery`; version1.7.8/code48; min/targetAPI37; nondebuggable.
- APK: `Private-Gallery-1.7.8-code48-owner-acceptance.apk`,105503318bytes.
- APK SHA256: `c9f43de796f0af15b95df54aabba686a11b4b6b6bb4eb08cf00169cccf7b7ec5`.
- Original certificate SHA256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.
- Owner artifact: https://github.com/traynor1987/Private-gallery-/actions/runs/41156213785/artifacts/13025351799 .

Evidence: FINAL_REPAIR48_EVIDENCE.json, SECURITY_REVIEW48.md, SIGNED_ARTIFACT_REVIEW48.md and production-evidence/41156213785. Raw verification archive saved separately without browser screenshots or another APK. Original47/177 sources and evidence remain available.

Physical checklist: cover/inner portrait+landscape; fold/unfold; light/dark+large text+keyboard; app launch/loading/Back; compatibility choice; settings and switching/close/PIN; VPN/Tor failure-closed behaviour and Panic/Hide. See REPAIR48_OWNER_ACCEPTANCE.md. Do not mark physical acceptance complete until performed by the owner.
