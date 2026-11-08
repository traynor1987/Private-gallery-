#!/usr/bin/env bash
set -euo pipefail

bash scripts/run_visual_checkpoint.sh EvidenceCollectionSync68Test evidence-collections-68 uk.co.traynor.privategallery.core.evidence
bash scripts/run_visual_checkpoint.sh EvidenceAi68Test evidence-ai-68 uk.co.traynor.privategallery.core.evidence
bash scripts/run_visual_checkpoint.sh EvidenceDates68Test evidence-dates-68 uk.co.traynor.privategallery.ui
bash scripts/run_visual_checkpoint.sh EvidenceLocker68UiTest evidence-locker-68 uk.co.traynor.privategallery.ui
screenshot_output=app/build/home2-evidence/evidence-locker-68/screenshots
mkdir -p "$screenshot_output"
shopt -s nullglob
for screenshot in folded-home folded-timeline unfolded-home unfolded-timeline; do
  captures=(app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/*/evidence-locker-68/"$screenshot.png")
  if [[ ${#captures[@]} -ne 1 || ! -s ${captures[0]} ]]; then
    echo "Expected one Android 17 screenshot for $screenshot" >&2
    exit 1
  fi
  cp "${captures[0]}" "$screenshot_output/"
done
bash scripts/run_visual_checkpoint.sh SanctuaryPatternLearning68Test evidence-sanctuary-68 uk.co.traynor.privategallery.core.sanctuary
bash scripts/run_visual_checkpoint.sh EvidenceLocker179UiTest evidence-legacy-locker uk.co.traynor.privategallery.ui
bash scripts/run_visual_checkpoint.sh EvidenceAi179UiTest evidence-legacy-ai uk.co.traynor.privategallery.ui
bash scripts/run_visual_checkpoint.sh EvidenceReportWorkflow60Test evidence-legacy-reports uk.co.traynor.privategallery.ui
