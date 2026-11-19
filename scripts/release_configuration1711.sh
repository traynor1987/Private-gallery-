#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.45
release_code=91
baseline_version=1.7.44
baseline_code=90
baseline_sha=b7390f3717bdf4ecc7b905ff265232e0fba80618
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-90}" == 90 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
