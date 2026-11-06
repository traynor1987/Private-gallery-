#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/integration/novaos-1.7-home2 ]] || { echo 'Unsupported NovaOS integration release branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.11.5
release_code=61
baseline_version=1.7.11.4
baseline_code=60
baseline_sha=f2727d75d142c2a733c53861e1a0f74440e0b6b5
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-60}" == 60 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
