#!/usr/bin/env bash
set -euo pipefail

# Public source must never carry user/runtime secrets or Android signing material.
forbidden='\.(jks|keystore|p12|pem|der|key)$|(^|/)(local\.properties|keystore\.properties|signing\.properties|release\.properties|\.env)$'
if git ls-files | grep -Eiq "$forbidden"; then
  echo 'FAIL: tracked secret or signing-material filename detected' >&2
  exit 1
fi
if git grep -InE 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|-----BEGIN.*PRIVATE KEY-----' -- ':!docs/'; then
  echo 'FAIL: secret-looking material detected in public source' >&2
  exit 1
fi
echo 'PASS: no tracked secret material detected'
