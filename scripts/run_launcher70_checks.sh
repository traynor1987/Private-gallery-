#!/usr/bin/env bash
set -euo pipefail

bash scripts/run_visual_checkpoint.sh SanctuaryLauncherPolicy70Test sanctuary-launcher-policy-70 uk.co.traynor.privategallery.core.sanctuary
bash scripts/run_visual_checkpoint.sh LauncherWorkspace70Test launcher-workspace-70 uk.co.traynor.privategallery
bash scripts/run_visual_checkpoint.sh NovaLauncherPlatform70Test launcher-platform-70 uk.co.traynor.privategallery.launcher
bash scripts/run_visual_checkpoint.sh LauncherLayoutStore70Test launcher-storage-70 uk.co.traynor.privategallery.launcher
bash scripts/run_visual_checkpoint.sh NovaLauncher70UiTest launcher-ui-70 uk.co.traynor.privategallery.launcher
bash scripts/run_launcher70_restart.sh
