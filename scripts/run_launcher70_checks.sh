#!/usr/bin/env bash
set -euo pipefail

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
