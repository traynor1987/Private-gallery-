#!/usr/bin/env bash
set -euo pipefail
if [[ "$GITHUB_REF" == refs/heads/release/1.7.4-camera-metadata-privacy || "$GITHUB_REF" == refs/heads/release/1.7.3-smart-unlock-gallery-sort || "$GITHUB_REF" == refs/heads/release/1.7.1-professional-ui || "$GITHUB_REF" == refs/heads/release/1.7.2-camera-ai-refinements ]]; then
  python3 scripts/verify_professional_ui_results.py app/build/outputs/androidTest-results/connected/debug uk.co.traynor.privategallery.ui.ProfessionalUiTest,uk.co.traynor.privategallery.ui.HiddenBrowser17LayoutTest,uk.co.traynor.privategallery.ui.GalleryProfessional171Test,uk.co.traynor.privategallery.ui.AiEditorProfessionalTest,uk.co.traynor.privategallery.ui.CameraPresentationTest,uk.co.traynor.privategallery.ui.BrowserProfessionalUiTest > app/build/acceptance-diagnostics/full-regression-summary.json
  git rev-parse HEAD > app/build/acceptance-diagnostics/full-regression-source-sha.txt
fi
case "$GITHUB_REF" in refs/heads/release/1.7.4-camera-metadata-privacy|refs/heads/release/1.7.3-smart-unlock-gallery-sort|refs/heads/release/1.7-encrypted-camera|refs/heads/release/1.7.1-professional-ui|refs/heads/release/1.7.2-camera-ai-refinements) bash ./scripts/verify_synthetic_upgrade.sh ;; esac
