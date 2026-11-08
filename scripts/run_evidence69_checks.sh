#!/usr/bin/env bash
set -euo pipefail

bash scripts/run_visual_checkpoint.sh EvidenceTimeline69Test evidence-timeline-69 uk.co.traynor.privategallery.ui
bash scripts/run_visual_checkpoint.sh EvidenceViewer69UiTest evidence-viewer-69 uk.co.traynor.privategallery.ui
bash scripts/run_visual_checkpoint.sh EvidenceDashboardPresentationTest evidence-dashboard-69 uk.co.traynor.privategallery.ui
bash scripts/run_visual_checkpoint.sh NovaNavigationHaptics69Test navigation-haptics-69 uk.co.traynor.privategallery.ui
