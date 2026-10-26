#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && ( "${GITHUB_REF:-}" == refs/heads/release/1.7.6.1-editing-settings-polish || "${GITHUB_REF:-}" == refs/heads/release/1.7.6-media-management || "${GITHUB_REF:-}" == refs/heads/release/1.7.5.2-recents-browser-repair || "${GITHUB_REF:-}" == refs/heads/release/1.7.5.1-privacy-viewer-repair || "${GITHUB_REF:-}" == refs/heads/release/1.7.5-media-experience-privacy || "${GITHUB_REF:-}" == refs/heads/release/1.7.4-camera-metadata-privacy || "${GITHUB_REF:-}" == refs/heads/release/1.7.3-smart-unlock-gallery-sort || "${GITHUB_REF:-}" == refs/heads/release/1.7.1-professional-ui || "${GITHUB_REF:-}" == refs/heads/release/1.7.2-camera-ai-refinements ) ]]
# Exercise changed UI first. The unchanged full suite below still runs every existing test,
# including the slower ProfessionalUiTest authentication/settings cases.
classes=uk.co.traynor.privategallery.ui.BrowserConsent1752Test,uk.co.traynor.privategallery.ui.HiddenVaultIntegrationTest,uk.co.traynor.privategallery.PrivacyViewerRepair1751Test,uk.co.traynor.privategallery.PanicCapture1751Test,uk.co.traynor.privategallery.ui.MediaExperience175Test,uk.co.traynor.privategallery.PrivacyShield175Test,uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest,uk.co.traynor.privategallery.core.media.MediaPrivacy174Test,uk.co.traynor.privategallery.ui.MediaDetails174Test,uk.co.traynor.privategallery.ui.HiddenFoundationDialogTest,uk.co.traynor.privategallery.SmartUnlockLifecycle173Test,uk.co.traynor.privategallery.core.gallery.MediaStoreSort173Test,uk.co.traynor.privategallery.ui.PasscodeKeypadTest,uk.co.traynor.privategallery.ui.DiscreetPinUnlockTest,uk.co.traynor.privategallery.core.vault.VaultPresentationStoreTest,uk.co.traynor.privategallery.ui.ReadingCameraViewTest,uk.co.traynor.privategallery.ui.MediaChromeTest,uk.co.traynor.privategallery.ui.GalleryProfessional171Test,uk.co.traynor.privategallery.ui.AiEditorProfessionalTest,uk.co.traynor.privategallery.ui.CameraPresentationTest,uk.co.traynor.privategallery.ui.BrowserProfessionalUiTest,uk.co.traynor.privategallery.ui.HiddenBrowser17LayoutTest,uk.co.traynor.privategallery.ui.PrimaryCopyDialogTest
if [[ "$GITHUB_REF" == refs/heads/release/1.7.6-media-management || "$GITHUB_REF" == refs/heads/release/1.7.6.1-editing-settings-polish ]]; then
  classes+=",uk.co.traynor.privategallery.ExternalEditingLifecycle176Test,uk.co.traynor.privategallery.core.media.ExternalEditing176Test,uk.co.traynor.privategallery.ui.ExternalEditingUi176Test,uk.co.traynor.privategallery.ui.MediaSelection176Test,uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccessTest,uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest"
fi
if [[ "$GITHUB_REF" == refs/heads/release/1.7.6.1-editing-settings-polish ]]; then
  classes+=",uk.co.traynor.privategallery.core.media.ExternalEditorIntents1761Test,uk.co.traynor.privategallery.core.media.ExternalPhotoExport1761Test,uk.co.traynor.privategallery.ui.SettingsPolish1761Test,uk.co.traynor.privategallery.HideContent1761Test,uk.co.traynor.privategallery.ui.PhotoEditorUiTest#externalEditorEntryCarriesOriginInBothVaults"
fi
results=app/build/outputs/androidTest-results/connected/debug
evidence=app/build/acceptance-diagnostics/professional-ui
rm -rf "$evidence"
mkdir -p "$evidence"
# No stale reports may satisfy this release's focused gate.
rm -rf "$results"
adb shell wm size 1200x1800
adb shell wm density 160
status=0
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --stacktrace --info || status=$?
if [[ -d "$results" ]]; then cp -a "$results" "$evidence/results"; fi
if [[ -d app/build/reports/androidTests/connected/debug ]]; then
  cp -a app/build/reports/androidTests/connected/debug "$evidence/reports"
fi
if [[ -d app/build/outputs/connected_android_test_additional_output ]]; then
  cp -a app/build/outputs/connected_android_test_additional_output "$evidence/additional-output"
fi
[[ "$status" == 0 ]]
python3 scripts/verify_professional_ui_results.py "$evidence/results" "$classes" > "$evidence/results-summary.json"
git rev-parse HEAD > "$evidence/source-sha.txt"
printf '%s\n' "$GITHUB_RUN_ID/$GITHUB_RUN_ATTEMPT" > "$evidence/workflow-run.txt"
# Ensure the subsequent unfiltered full suite actually runs with fresh outputs.
rm -rf "$results"
