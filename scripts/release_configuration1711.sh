#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.40
release_code=86
baseline_version=1.7.39
baseline_code=85
baseline_sha=e062aac04c5de268db52f4b3fc6066e90cb4bbcd
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-85}" == 85 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
