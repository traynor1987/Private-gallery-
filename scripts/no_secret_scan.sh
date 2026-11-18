#!/usr/bin/env bash
set -euo pipefail

# Read the complete tracked inventory before matching: grep -q plus pipefail can
# turn git's SIGPIPE into a false negative on large repositories.
python3 - <<'PY'
import hashlib
from pathlib import Path
import re
import subprocess

forbidden = re.compile(r'\.(jks|keystore|p12|pem|der|key)$|(^|/)(local\.properties|keystore\.properties|signing\.properties|release\.properties|\.env)$', re.I)
# This historical transfer recipient is a PUBLIC X.509 certificate, not a key.
# Its exact reviewed bytes are the only filename exception; replacement fails closed.
public_certificate = 'docs/release1711/code80-transfer-recipient.pem'
public_sha256 = '0055c59e3cb0fe8c6a72f3e6861f0f6231f23cb7e2a3a0ea9af1dce32c831a23'
paths = subprocess.check_output(['git', 'ls-files', '-z']).decode().split('\0')
for path in paths:
    if not path or not forbidden.search(path):
        continue
    if path == public_certificate:
        body = Path(path).read_bytes()
        if hashlib.sha256(body).hexdigest() == public_sha256:
            subprocess.run(['openssl', 'x509', '-in', path, '-noout'], check=True, capture_output=True)
            continue
    raise SystemExit('FAIL: tracked secret or signing-material filename detected: ' + path)
PY
if git grep -InE 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|-----BEGIN.*PRIVATE KEY-----' -- ':!docs/' ':!scripts/no_secret_scan.sh'; then
  echo 'FAIL: secret-looking material detected in public source' >&2
  exit 1
fi
echo 'PASS: no tracked secret material detected'
