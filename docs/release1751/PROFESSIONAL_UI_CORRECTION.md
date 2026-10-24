# Full regression UI fixture correction

Recovery parent: `5c85a32ca9759a140250830c1c5a876ee48d3f10` (tree `d350f188e1f87cbaa1da3e61d6b3b3355278d41a`), independently checked through GitHub and git ls-remote. Full candidate source: `b15dd211eb7dd1ee355c509f2a29e538745c778e`.

## Evidence and classification

Run 40135349594: JVM 349/349; broad focused UI 115/115; browser/security preflight 74/74; unfiltered Android16 384/389, five failures, zero skips. Artifact 12577258000 SHA256 `300f21ba55f4fd227721424ad638019674f4d4afa76fc9691e1efd0143e526a7`; raw XML and ZIP CRC independently audited. Exact failing method names and frames are in FULL_CANDIDATE_1_EVIDENCE.json.

Three protected-settings fixtures select `onFirst()` or index1 of all toggles. The preceding new Panic section makes those selectors operate Enable Panic Exit or a shortcut preference instead of Hide content/Allow screenshots. This is a concrete instrumentation targeting defect; it also changes shared neutral Panic preferences and affects later fixture layout. The Hide content display assertion also omitted scrolling the control into view.

settingsLight/settingsDark scroll deep into Security and click the header Back without making it visible. The next Updates & About lookup fails. This is an evidenced test visibility-precondition gap. The original artifact has no failure-time hierarchy proving exact input coordinates or a production navigation defect; corrected runtime navigation must confirm the diagnosis.

No production authentication, media, encryption, screenshot or Privacy Shield behaviour is changed. Two existing Switch modifiers gain unique test identifiers, `privacy-hide-content` and `privacy-allow-screenshots`; handlers remain byte-for-byte unchanged. Tests use those exact identifiers, scroll and assert displayed before interaction. Back navigation similarly scrolls/asserts the visible header and confirms the resulting Settings page. All original fresh-PIN, cancellation, stale-biometric-callback, concealed-media and screenshot-state assertions remain enabled. Each synthetic fixture snapshots/restores only the three neutral Panic preferences and begins with deterministic defaults; no vault storage/credentials are touched.

## Bounded verification

One focused run will execute the five exact affected methods first on each SDK, requiring each method in fresh passing raw XML, then the unchanged47 repair/privacy/player/authentication cases plus all22 ProfessionalUiTest cases. Reports are cleared before each invocation and archived separately, including failed evidence. Missing, failing or skipped required methods cannot satisfy the gate, including duplicate pass-plus-skip data. A failed affected stage stops its SDK phase; both supported SDK outcomes must succeed.

Local host verification and independent exact-diff review precede the run. Full Android16/17 regression, debug and original-key production38→39 upgrade/restart, signing identity/package/APK hashes remain mandatory afterward. Android12 failures remain unresolved on an unsupported platform. No owner physical acceptance or signed39 APK is claimed.

Independent review confirmed the original security assertions remain intact and the two production changes are identifier semantics only. Review caught and corrected a draft layout-ancestor matcher before execution. Review also required fresh raw XML before every invocation and explicit required-method skip rejection, including duplicate pass/skip; four new host checks cover these conditions and are wired into both existing CI host steps. All37 Python host tests passed (one pre-existing mocked onion log ResourceWarning, no failure). Local compile is pending recovery of the temporary public JDK/SDK toolchain; this is not a runtime pass or CI approval.

Clean production and instrumentation compile passed:32tasks executed,1m58s. See professional-ui-verification/VERIFICATION.json for exact logs/hashes and prior incremental diagnostic limitation. One affected run remains pending.
