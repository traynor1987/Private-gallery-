#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.38.4
release_code=77
baseline_version=1.7.38.3
baseline_code=76
baseline_sha=c818c670baa4b980708dc2effb1ab2ab0331ade1
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-76}" == 76 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
