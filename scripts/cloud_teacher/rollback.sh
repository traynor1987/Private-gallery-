#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
[[ "${1:-}" =~ ^[0-9a-f]{40}$ ]] || { echo 'Usage: rollback.sh PREVIOUS_COMMIT_SHA' >&2; exit 2; }
export NOVA_IMAGE_TAG="$1"
config=${NOVA_CONFIG_DIR:-/etc/nova-cloud-teacher}
# Staging on the current host is loopback-only and must not enable API spending.
if grep -Eq '^[[:space:]]*(export[[:space:]]+)?NOVA_CLOUD_CREDIT_CYCLE[[:space:]]*=' "$config/settings.env"; then
    . /etc/os-release
    [[ "${ID:-}:${VERSION_ID:-}" == ubuntu:24.04 || "${ID:-}:${VERSION_ID:-}" == ubuntu:26.04 ]] || {
        echo 'Migrate to supported Ubuntu LTS before enabling cloud analysis.' >&2
        exit 1
    }
fi
docker image inspect "nova-cloud-teacher:$NOVA_IMAGE_TAG" >/dev/null
docker compose up -d --no-build --pull never --wait --wait-timeout 90 teacher
