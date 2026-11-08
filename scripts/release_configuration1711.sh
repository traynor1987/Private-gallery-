#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.31
release_code=66
baseline_version=1.7.30-visual-foundation
baseline_code=65
baseline_sha=a02cfcf6bb3bbae15f00935c2b2124ae7eb4e674
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-65}" == 65 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
