#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.38.1
release_code=74
baseline_version=1.7.38
baseline_code=73
baseline_sha=c0db80efc8d1342896984bd937abb01dc786a0f4
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-73}" == 73 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
