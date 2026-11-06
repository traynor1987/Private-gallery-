#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/integration/novaos-1.7-home2 ]] || { echo 'Unsupported NovaOS integration release branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.11.6
release_code=62
baseline_version=1.7.11.5
baseline_code=61
baseline_sha=2da2895e387839ec37d0590dfb713dc1bec99853
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-61}" == 61 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
