#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.41
release_code=87
baseline_version=1.7.40
baseline_code=86
baseline_sha=15c1a2754d90bba0be58881101a72dc9aa480391
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-86}" == 86 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
