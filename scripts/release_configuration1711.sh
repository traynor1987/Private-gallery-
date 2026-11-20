#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.48
release_code=94
baseline_version=1.7.47
baseline_code=93
baseline_sha=39e29649ce961b251a9783e09731814f6e0ab30c
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-93}" == 93 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
