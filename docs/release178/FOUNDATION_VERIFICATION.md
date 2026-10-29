# Foundation evidence — not release approval

- Source baseline: 64d4c213fe3b3a92ecd946e334e8a918851636c9.
- `bash scripts/check_private_apps178.sh`: three Java check programs passed.
- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests -p 'test_*.py'`: 201 tests passed, existing ResourceWarnings retained.
- `./gradlew --version`: failed downloading uncached9.5.0, `java.net.SocketException: Network is unreachable`.
- Primary Kotlin/JUnit: not executed; Android SDK absent. GitHub remains the available build/runtime route.
- Independent design/foundation review performed. Reported five source-level findings corrected; Java regression checks rerun. No whole-feature or signing approval.
- Catalogue code is not referenced by production UI. No Private Apps capability is yet exposed.
- No new Android workflow, APK, upgrade test or production signing performed.
