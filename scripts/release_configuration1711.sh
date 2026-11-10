#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.38.2
release_code=75
baseline_version=1.7.38.1
baseline_code=74
baseline_sha=90e84e9e477a551c9f8985daca230bbf543059a0
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-74}" == 74 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
