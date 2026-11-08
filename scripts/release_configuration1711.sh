#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.33
release_code=68
baseline_version=1.7.32
baseline_code=67
baseline_sha=5ee1a5458bb5d62daeae2369ee9dde05952a0faa
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-67}" == 67 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
