#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.39
release_code=85
baseline_version=1.7.38.8
baseline_code=84
baseline_sha=b79b85efc5bae8e0bedb832e21a62d462e6f4397
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-84}" == 84 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
