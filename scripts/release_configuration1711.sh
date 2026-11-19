#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.43
release_code=89
baseline_version=1.7.42
baseline_code=88
baseline_sha=430774e549a86d5c0db159ec24230bc4ac05bbc3
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-88}" == 88 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
