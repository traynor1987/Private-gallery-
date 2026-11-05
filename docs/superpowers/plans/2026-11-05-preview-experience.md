# NovaOS Preview experience implementation plan

Goal: extend the existing preview without production changes or resetting owner state.
Architecture: preserve Compose shell and retained WebView model. Add preview-local Mind storage and native generated audio; extend the existing catalogue and browser controls.
Baseline: b82ac9127dfdf7be69efd8d40aca4600f60be622, code 4, uk.co.traynor.novaos.preview.

Constraints: existing branch/package/development certificate; API 37 only; no production database, credentials, signing, Hidden VPN or Tor. Never invent playback capabilities or PWA/offline support.

1. Mind: test monotonic breathing phase boundaries and pause semantics; implement breathing, grounding, local mood/journal CRUD/search, generated offline calm audio with volume/timer and Sanctuary. Native audio owner survives Home/configuration; pauses on leaving preview. Secure Android window prevents journal in system Recents. Default Calm Now breathing, configurable grounding alternative.
2. Games: research official destinations, persist dated verification evidence and append 30–50 accurately classified entries without changing existing entries. Test uniqueness/count and all classifications. Add per-app persisted orientation/fullscreen preferences; use normal WebView input and storage.
3. Browser and shell: standalone Browser V1 launcher using existing WebView; history/bookmarks/downloads and JS settings; keep App Mode. Extend real event records with read state/target compatible with old records. Integrate Mind/Calm/Sanctuary in drawer/Home/Quick/Edge without resetting layouts.
4. Verification: host checks, Java timing checks, focused instrumented tests for Mind storage/flow and existing navigation/retention/Fold checks. Compile app and tests locally; inspect review; sign/verify same certificate. One focused Android17 CI attempt with in-place code4 upgrade. Publish direct prerelease APK with truthful runtime results and integration handover.

Review focus: process/configuration changes; stale media callbacks; empty journal/mood input and storage failure; catalogue duplicates; profile changes and sensitive previews. Distinguish Home from Close throughout.
Execution: owner explicitly requested autonomous continuation; no additional design gate. Implement in this session, with final independent code review.

Execution ledger: all original 50 catalogue records compared structurally with baseline and unchanged. 40 appended destinations have HTTP/title/hash receipts; Wordle disabled after Site Unavailable. Host checks: 22 URL/layout,24 AppMode/OAuth/lifecycle,13 shell/media/navigation,12 Mind timing;7 Python tests plus catalogue/icon/package checks passed. App/test assemblies passed; final post-review assembly and Android runtime pending.
Independent review identified rotation audio pause, background breathing, internal Close semantics and explicit Calm Now destination restoration. Fixes implemented; focused instrumentation regressions included. No production source changes.

Final receipt: source f2750627165fd379a4eee5d509df018542bf6062; CI41948755747 passed build/signature/isolation/alignment and published prerelease0.5. Runtime did NOT pass: after preflight reported ready, Android17 Package Manager failed installing the prior code4 APK with Broken pipe(32), before either instrumentation suite. Zero tests executed; in-place configuration preservation, runtime Fold layouts and UI regressions remain pending a healthy Android17 environment or owner device. No assertions weakened and no repeated workflow launched. Public asset SHA256 d02a1a924cec9f5ac6f8fd5b6781052a14c3800b4945dae45e3e5b674ed843c0. Source and existing data are preserved; production integration remains separate.
