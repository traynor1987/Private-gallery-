#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
tag=$(git rev-parse HEAD)
[[ -z "$(git status --porcelain -- Dockerfile compose.yaml server.py budget.py)" ]]
export NOVA_IMAGE_TAG="$tag"
config=${NOVA_CONFIG_DIR:-/etc/nova-cloud-teacher}
# Staging on the current host is loopback-only and must not enable API spending.
if grep -Eq '^[[:space:]]*(export[[:space:]]+)?NOVA_CLOUD_CREDIT_CYCLE[[:space:]]*=' "$config/settings.env"; then
    . /etc/os-release
    [[ "${ID:-}:${VERSION_ID:-}" == ubuntu:24.04 || "${ID:-}:${VERSION_ID:-}" == ubuntu:26.04 ]] || {
        echo 'Migrate to supported Ubuntu LTS before enabling cloud analysis.' >&2
        exit 1
    }
fi
docker compose config --quiet
docker compose build --pull teacher
docker compose up -d --no-build --wait --wait-timeout 90 teacher
printf 'Cloud Teacher image: %s\n' "$tag"
