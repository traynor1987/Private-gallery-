#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.36
release_code=71
baseline_version=1.7.34
baseline_code=69
baseline_sha=cc8aaed81b8d6ed27c71c9923edbf51334466cb2
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-69}" == 69 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
