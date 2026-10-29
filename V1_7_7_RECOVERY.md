# Private Gallery 1.7.7 — recovery

Status: RECOVERED / NOT IMPLEMENTED / NOT APPROVED FOR SIGNING.

Baseline branch release/1.7.6.2-ui-browser-polish at 8915d2e6f3915573afeaf850205f302c07eab7eb.
Code45 application source 97e94452ceb9c5262521d7552f0c25eeb07374eb; release control a19d19603c8ccbfaa1449113ba61592f5249c19e.
Original-key production run40852039088 and artifact12887938200 are recorded in docs/release1762/CODE45_OWNER_ACCEPTANCE.md. APK SHA256 ad39f9b3a4d04d75621eeb21969f131fd96deae0620a5e34325a9bf400359aff. Code45 physical acceptance remains OWNER_REQUIRED; the owner's new reports are unresolved.

Fresh clone and fetch on 2026-10-29; no competing local git/Gradle process observed. Remote branch checked unchanged at 8915d2e6. No existing 1.7.7 branch. Inspected all fetched remote app/build.gradle.kts files: maximum versionCode45. Code46 is provisional until release-time recheck. Metadata unchanged so far.

Confirmed existing basic trash in both vault repositories, with fixed30-day retention and single-item list UI. Restore currently flips state without payload verification. Primary thumbnail callback rejects Hidden-selected cross-vault Primary gallery even though loadItems admits that context. Remaining Hidden thumbnail and browser address/handoff causes still under investigation.

Local Gradle unavailable: wrapper distribution9.5.0 is not cached and download fails java.net.SocketException: Network is unreachable; Android SDK absent. Existing GitHub focused workflow can supply Android build/runtime verification after coherent changes; do not treat host tests as Android proof.

No app code changed, signing credentials accessed, workflow launched, or acceptance claimed at recovery.
