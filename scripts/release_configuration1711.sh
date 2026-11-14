#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.38.7
release_code=80
baseline_version=1.7.38.6
baseline_code=79
baseline_sha=06f85bb410580dc86622767d707f422ab5fe7ad9
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-79}" == 79 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
