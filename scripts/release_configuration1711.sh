#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.42
release_code=88
baseline_version=1.7.41
baseline_code=87
baseline_sha=9075b111a87e9dbcf39566b71c2763b380b58088
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-87}" == 87 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
