#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.32
release_code=67
baseline_version=1.7.31
baseline_code=66
baseline_sha=de641d42df1ddae6ba6a59a23f0bc44af85ae1f9
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-66}" == 66 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
