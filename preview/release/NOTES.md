# NovaOS Home 2.0 + Nova Store — Experimental Preview 0.1

**Development prerelease for Android 17 / API 37. Runtime acceptance remains unverified.**

Download **NovaOS-Home2-Store-Preview.apk** below and install it as **NovaOS Preview** alongside existing NovaOS. Do not remove production NovaOS. Package: `uk.co.traynor.novaos.preview`; development signing only. It has no production vault, browser-data, credential, encryption-key, Hidden VPN or Tor integration.

Includes Home pages, wallpapers/photo picker/crop, Dock, folders, searchable drawer, Home editing, Quick Panel, demo-profile layouts, a50-entry curated Nova Store and internal app-style WebView containers. Production Primary V1 is untouched; the preview retains its already ported settings/policies, JavaScript, supported forms/navigation and app-owned tabs.

## Verification and limitations

- Host URL/layout checks and catalogue/icon/source-boundary checks pass.
- Actual APK package, development certificate, min/target API37, permissions, production-class exclusion and16KB alignment are independently checked before publication. SHA256 and certificate details are attached.
- Failed workflow41872542049 attempt2 successfully built and signed the APK, but the emulator Package Manager returned `Broken pipe (32)` during the first `adb install`. The preview never launched, no UI tests executed, and no test reports/screenshots were produced. This is **not** a passing runtime result.
- The test fixture now waits for a stable Android system_server and responsive Package Manager **before** installation. The publication path does not rerun unrelated tests or turn a runtime failure into a pass.
- This experimental prerelease is published for owner device testing with those limits explicitly accepted. Physical Galaxy Z Fold cover/inner/landscape, app switching, persistence and site-login compatibility still need acceptance.
- Demo profiles are not encrypted vaults. Preview apps/profiles share the preview-only WebView cookie store. Some providers restrict embedded authentication; external fallback requires confirmation. Internal downloads and camera/microphone permissions are unavailable.
- Development certificates can differ from earlier local preview builds. Android will refuse an update signed by a different key; do not uninstall production NovaOS to resolve a preview conflict.

## Quick Fold check

1. Open Home, swipe pages, search the drawer, edit a folder and rearrange the Dock.
2. Switch both demo profiles and choose/crop a wallpaper.
3. Install a Store entry, open it internally, return Home and reopen it.
4. Fold/unfold, rotate and relaunch; check saved layouts and installed entries.

Publication marker: preview-artifact-13358999836
