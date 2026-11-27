#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- ]]
[[ "${GITHUB_EVENT_NAME:-}" == push && "${GITHUB_REF:-}" == refs/heads/feature/novaos2-wave-a ]]
[[ "${GITHUB_WORKFLOW_REF:-}" == traynor1987/Private-gallery-/.github/workflows/novaos99-full.yml@refs/heads/feature/novaos2-wave-a ]]
[[ "${GITHUB_SHA:-}" =~ ^[a-f0-9]{40}$ && "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]]
[[ "$(git log -1 --format=%B)" == *'[novaos-build99-full]'* ]]
grep -Fxq '    versionCode = 99' app/build.gradle.kts
grep -Fxq '    versionName = "2.0.0"' app/build.gradle.kts
