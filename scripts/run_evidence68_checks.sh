#!/usr/bin/env bash
set -euo pipefail

bash scripts/run_visual_checkpoint.sh EvidenceCollectionSync68Test evidence-collections-68 uk.co.traynor.privategallery.core.evidence
bash scripts/run_visual_checkpoint.sh EvidenceAi68Test evidence-ai-68 uk.co.traynor.privategallery.core.evidence
bash scripts/run_visual_checkpoint.sh EvidenceDates68Test evidence-dates-68 uk.co.traynor.privategallery.ui
bash scripts/run_visual_checkpoint.sh EvidenceLocker68UiTest evidence-locker-68 uk.co.traynor.privategallery.ui
adb pull /sdcard/Android/media/uk.co.traynor.privategallery.hiddenprototype/additional_test_output/evidence-locker-68 app/build/home2-evidence/evidence-locker-68/screenshots
bash scripts/run_visual_checkpoint.sh SanctuaryPatternLearning68Test evidence-sanctuary-68 uk.co.traynor.privategallery.core.sanctuary
bash scripts/run_visual_checkpoint.sh EvidenceLocker179UiTest evidence-legacy-locker uk.co.traynor.privategallery.ui
bash scripts/run_visual_checkpoint.sh EvidenceAi179UiTest evidence-legacy-ai uk.co.traynor.privategallery.ui
bash scripts/run_visual_checkpoint.sh EvidenceReportWorkflow60Test evidence-legacy-reports uk.co.traynor.privategallery.ui
