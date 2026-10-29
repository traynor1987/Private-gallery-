# Private Gallery 1.7.7 — recovery

Status: RECOVERED / NOT IMPLEMENTED / NOT APPROVED FOR SIGNING.

Baseline branch release/1.7.6.2-ui-browser-polish at 8915d2e6f3915573afeaf850205f302c07eab7eb.
Code45 application source 97e94452ceb9c5262521d7552f0c25eeb07374eb; release control a19d19603c8ccbfaa1449113ba61592f5249c19e.
Original-key production run40852039088 and artifact12887938200 are recorded in docs/release1762/CODE45_OWNER_ACCEPTANCE.md. APK SHA256 ad39f9b3a4d04d75621eeb21969f131fd96deae0620a5e34325a9bf400359aff. Code45 physical acceptance remains OWNER_REQUIRED; the owner's new reports are unresolved.

Fresh clone and fetch on 2026-10-29; no competing local git/Gradle process observed. Remote branch checked unchanged at 8915d2e6. No existing 1.7.7 branch. Inspected all fetched remote app/build.gradle.kts files: maximum versionCode45. Code46 is provisional until release-time recheck. Metadata unchanged so far.

Confirmed existing basic trash in both vault repositories, with fixed30-day retention and single-item list UI. Restore currently flips state without payload verification. Primary thumbnail callback rejects Hidden-selected cross-vault Primary gallery even though loadItems admits that context. Remaining Hidden thumbnail and browser address/handoff causes still under investigation.

Local Gradle unavailable: wrapper distribution9.5.0 is not cached and download fails java.net.SocketException: Network is unreachable; Android SDK absent. Existing GitHub focused workflow can supply Android build/runtime verification after coherent changes; do not treat host tests as Android proof.

No app code changed, signing credentials accessed, workflow launched, or acceptance claimed at recovery.

## Draft implementation checkpoint — NOT release approval

1.7.7/code46 prepared. Existing stores extended with encrypted independent7/15/30/60/never retention; payload verification before restore; bulk themed trash grid; Primary preview ownership repair; authenticated Hidden cache regeneration; shared connection chrome and empty-WebView/clipping candidate. No physical acceptance claimed.

Independent draft review found6 Important issues. Corrections drafted: expiry-only periodic paths and single lifecycle timer; current COMPLETE guards on ordinary Primary reads and derivative publication; orphan encrypted-preview reconciliation and expiry cache invalidation; expensive presentation cleanup outside authority locks; cancellable Primary restore verification; bounds-aware legacy thumbnail repair. These fixes require runtime verification and final review. Startup recovery remains distinct from periodic expiry.

Local200 Python host tests pass. Android tests are authored but not executed locally (SDK/distribution unavailable). Existing focused workflow expanded to18 selected affected cases, preserving original transport packet gates. New release identity test retains historical code45 identity test against immutable source. Original-key45→46 fixture is prepared, not executed. Signing workflow is still pinned to historical code45 and MUST NOT be triggered for this candidate.
