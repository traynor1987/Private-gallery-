#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.38.7
release_code=82
baseline_version=1.7.38.7
baseline_code=80
baseline_sha=681cd0babbcd3a3b2733ae795ca27de08f1fbf68
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-80}" == 80 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
