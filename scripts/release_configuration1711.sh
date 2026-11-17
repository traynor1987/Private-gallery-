#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.38.8
release_code=84
baseline_version=1.7.38.7
baseline_code=83
baseline_sha=c22bad35c3c934b8d95cf91e95b0b893cacd8e2c
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-83}" == 83 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
