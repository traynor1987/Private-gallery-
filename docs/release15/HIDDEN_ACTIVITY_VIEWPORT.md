# Hidden Activity viewport correction after Samsung physical failure

Owner19October2026 screenshots of both Hidden Vault and Favourite still show gesture handle through icons and clipped labels after CI594. Owner confirms Gallery, Browser and Settings fit. CI594 is therefore not physically accepted; its170-test pass did not prove Samsung viewport correctness.

The earlier inset test compared controls to the separate Hidden dialog's own measured root. That can accept an oversized/offscreen window. The test now requires unlocked media to use the Activity window and compares unclipped touch bounds (converted to window pixels) against actual Activity WindowMetrics/system inset bounds. PIN keeps its separate SecureOn dialog.

Correction removes only the media Dialog wrapper. Existing Hidden VaultHome, Favourite, viewer and ProtectedAppShell compose in the same Activity viewport as working tabs. Root Box places Hidden last and suppresses underlying Primary/device composition whenever the Hidden entry/scoped view is active. On external tabs the Hidden host stays composed for its independent session/lifecycle, while showEntry=false removes scoped content. Primary authority, Hidden repository/key/session and credentials remain separate. No data transfer or UI-flag-based authentication added. Existing Activity screenshot preference applies directly; PIN always protected. Existing viewer/Hidden Back handlers retain priority. No guessed padding, display-height constant or OEM-specific spacer.

Local foundation373 checks, storage/isolation/revocation/recovery/timeout checks, Node3/3, secret scan/WireGuard path/diff checks passed. Independent review found no material production finding; bounds test enhanced to avoid clipped-only geometry. Android CI still required before claiming the new source passed; previous tests apply only to their exact previous commits. Local Android red/green execution unavailable.

Physical acceptance remains required: both Hidden Vault/Favourite folded/unfolded; all5 icons AND labels above Samsung gesture bar; keyboard open/closed; Back/photo/video; screenshot preference on/off; manual/background timeout and Return to normal Vault. Only disposable media. No main/V2/PR59 modification or production installation/migration.


## Verified CI595 test candidate

Tested source `13f98aa10c66ed806416088932b484af28e2287a`. [Download APK/reports ZIP](https://github.com/traynor1987/Private-gallery-/actions/runs/39227238672/artifacts/12175572903); extract `outputs/apk/debug/app-debug.apk`. CI595 SUCCESS:170Android tests,0failures/errors/skips; JVM/lint/build passed. Activity viewport/no media-dialog/unclipped navigation test passed, as did both screenshot-policy tests and existing regressions. Pending-CI statements above describe the pre-CI checkpoint. Samsung physical acceptance is still OPEN.

APK SHA-256 `354bd425a0e65206fa367db3ed8080abb15e2efe52022b738cfc46a8609a83ac`. Package `uk.co.traynor.privategallery.hiddenprototype`,1.0.27-TEST-ONLY/code28, debuggable. APK v2 signature/content digest verified. Debug certificate SHA-256 `dce8fbed71a7878974b692dec1a753f47d3f54ffa80974591d856022f0747e71`, differs from CI594. In-place test update may be rejected; do not uninstall any app with needed media or production V1. No Hidden export/recovery.

Evidence: [summary](evidence/ci595-summary.txt), [APK inspection](evidence/ci595-apk-inspection.json). No production readiness claim.
