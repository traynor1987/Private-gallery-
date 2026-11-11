#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.38.5
release_code=78
baseline_version=1.7.38.4
baseline_code=77
baseline_sha=15cdfd9e612ffc68960d1374420fe05e425a9877
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-77}" == 77 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
