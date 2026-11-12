#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

[[ -z "$(git status --porcelain -- Dockerfile compose.staging.yaml server.py budget.py deploy-staging.sh)" ]]
export NOVA_STAGING_IMAGE_TAG="$(git rev-parse HEAD)"
compose=(docker compose --project-name nova-cloud-teacher-staging --file compose.staging.yaml)

case "${1:-up}" in
    up)
        "${compose[@]}" config --quiet
        "${compose[@]}" build --pull teacher
        "${compose[@]}" up -d --no-build --wait --wait-timeout 90 teacher
        ;;
    down)
        "${compose[@]}" down
        ;;
    *)
        echo 'Usage: deploy-staging.sh [up|down]' >&2
        exit 2
        ;;
esac
