#!/usr/bin/env bash
set -euo pipefail

bash scripts/run_visual_checkpoint.sh LauncherLibrary73UiTest launcher-library-73 uk.co.traynor.privategallery.launcher
bash scripts/run_visual_checkpoint.sh LauncherSmartFolderStore73Test launcher-smart-folders-73 uk.co.traynor.privategallery.launcher
bash scripts/run_visual_checkpoint.sh LauncherSmartFolderEditor73Test launcher-smart-editor-73 uk.co.traynor.privategallery.launcher
bash scripts/run_visual_checkpoint.sh LauncherDefaultHomeStore73Test launcher-default-home-73 uk.co.traynor.privategallery.launcher
bash scripts/run_visual_checkpoint.sh LauncherSmartTrackingStore73Test launcher-smart-tracking-73 uk.co.traynor.privategallery.launcher
bash scripts/run_visual_checkpoint.sh NovaLauncher72GesturesTest launcher-gestures-72 uk.co.traynor.privategallery.launcher

python3 scripts/run_backup_repair62.py --evidence-incident

bash scripts/run_visual_checkpoint.sh SanctuaryLauncherPolicy70Test sanctuary-launcher-policy-70 uk.co.traynor.privategallery.core.sanctuary
python3 scripts/run_backup_repair62.py --calculator-copy
bash scripts/run_launcher70_restart.sh
bash scripts/run_visual_checkpoint.sh LauncherWorkspace70Test launcher-workspace-70 uk.co.traynor.privategallery
bash scripts/run_visual_checkpoint.sh NovaLauncherPlatform70Test launcher-platform-70 uk.co.traynor.privategallery.launcher
bash scripts/run_visual_checkpoint.sh LauncherLayoutStore70Test launcher-storage-70 uk.co.traynor.privategallery.launcher
bash scripts/run_visual_checkpoint.sh NovaLauncher70UiTest launcher-ui-70 uk.co.traynor.privategallery.launcher

bash scripts/run_visual_checkpoint.sh LauncherSanctuaryControls70Test launcher-sanctuary-controls-70 uk.co.traynor.privategallery.launcher
bash scripts/run_visual_checkpoint.sh LauncherUtility70Test launcher-utility-70 uk.co.traynor.privategallery.launcher
