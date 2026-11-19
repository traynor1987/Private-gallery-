#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.44
release_code=90
baseline_version=1.7.43
baseline_code=89
baseline_sha=699bc866905c52c46dfca44a397ea36bb225c471
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-89}" == 89 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
