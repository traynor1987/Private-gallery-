# Private Gallery 1.7.6.1 implementation plan

> Execute inline with superpowers:executing-plans; one independent whole-branch security review before Android CI.

Goal: simplify external photo editing in both vaults, consolidate Panic configuration, and apply presentation-only Hide Content version0.0.1/build4.
Architecture: retain1.7.6 authenticated origin tickets, expiring read-only FileProvider, verified import/Replace, existing Hide Content gate and storage. Change chooser/dialog/settings presentation only; reuse verified MediaStore exporter for explicitly consented public fallback.
Tech stack: existing Kotlin/Compose/Java/Android36+.
Spec: owner's complete1.7.6.1 execution request; exact UI text/metadata restrictions binding.
Constraints: original package/certificate; next genuinecode42; no realmedia/keys/credential changes, uninstall, oldbranch rewrite, main merge or1.7.7/1.8. One writer; host checks first; grouped focused36/37 then stable full/security+41->42 upgrades on both and original-key signing only aftergates.
Review focus: same-origin Hidden auth expiry, stale external callbacks, public export cancellation/death/cleanup, decoy version restoration without update-version substitution, Hidden metadata on decoy tabs, screenshots retaining freshauth, secret entry sequence retainingaccess.

## Task1: presentation and Settings
Files: core/ui/InAppVersionPresentation.java; MainActivity.kt SettingsHome; host/ JVM policy and ProfessionalUi tests.
- [ ] RED host policy checks for exact0.0.1/build4/description and genuineversion restoration.
- [ ] Minimal UI-only policy: genuineBuildConfig unchanged; update service uses realversion; hide realstatus text in ordinary decoy while explaining genuineinstalledversion checks.
- [ ] Render Panic only Privacy & Security and not secretOpen. Move primary screenshot configuration there preserving auth; retain Secret Hide Content/hide-again/discovery and Hidden policy controls.
- [ ] Cover Settings/About normal/decoy/reveal, noHidden indicators; extend realactivity lifecycle/emptygallery preservation tests.

## Task2: chooser editing
Files: core/media/ExternalEditorIntents.kt; ExternalEditingDialog.kt; PhotoEditor.kt; MainActivity.kt; manifestqueries; Android tests.
- [ ] RED chooser tests require ACTION_CHOOSER+ACTION_EDIT content/png, read grants/ClipData, no hardcodedpackage; VIEWfallbackonly explicit; SENDnevermislabellededit.
- [ ] Compact initialdialog exactownertext +Choose photo editor/Import edited photo/Cancel. Short explicit boundary/cloud confirmation before creation/egress. Prepare+launch in one confirmedaction.
- [ ] Keep preview/verifiedSaveNew/confirmedReplace/reimport/expiry/cleanup. Guided explicitMediaStoreexport uses existingverifiedpending-row exporter and genericname; VIEWchooser openspublicphoto whenSamsungeditunsupported.
- [ ] Same entry inPrimary/Hidden explicitcallback wiring; preserve origin/epoch authority; no Primary fallback.
- [ ] Test realintents, unsupportedresolver, syntheticMediaStoreexport/cancel/cleanup, bothvaultUI and lifecycle/oldimport/security regressions; Fold sizes.

## Task3: verify and release
Files: focused1761/admission1761 workflows, releaseconfig/platform/evidenceverifier, upgradefixture41->42, recovery/acceptance docs.
- [ ] Preserve every176gate; exactcaseinventory; newnegativeverifier/configtests; cheaphost/test-compilation before runtime. No trigger untilindependentreview.
- [ ] Commit coherentimplementation/backup; verify remote tree; one grouped focusedAndroid36/37.
- [ ] StableRC exactsource JVM/full/security/browser/camera regressions+syntheticcode41upgrade/restart bothSDKs; original-key signature/hash andproductionupgradebothSDKs.
- [ ] Persist rawproof, finalrecovery, single signedacceptanceAPK/checklist; no physicalacceptance claim.
