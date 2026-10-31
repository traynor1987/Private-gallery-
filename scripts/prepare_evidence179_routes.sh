#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.9-evidence-locker ]]
[[ "${GITHUB_SHA:-}" =~ ^[a-f0-9]{40}$ ]]
[[ -f scripts/evidence179_cases.json && ! -e app/build/evidence179/capture-start.json ]]
mkdir -p app/build/evidence179
python3 - <<'PY'
import json,time
from pathlib import Path
Path('app/build/evidence179/capture-start.json').write_text(json.dumps({'epoch':time.time()})+'\n')
PY
bash scripts/setup_hidden_vpn_fixture1762.sh
python3 scripts/ci_onion_probe.py --evidence179
