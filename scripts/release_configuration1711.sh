#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.34
release_code=69
baseline_version=1.7.33
baseline_code=68
baseline_sha=0de573ccd4b5f2ef0ee557ca8c90ebf7d2aba1d3
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-68}" == 68 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
