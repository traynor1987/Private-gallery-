#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.49
release_code=95
baseline_version=1.7.48
baseline_code=94
baseline_sha=46b9c78c15bcbe18d129e28be8677d21c59ca838
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-94}" == 94 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
