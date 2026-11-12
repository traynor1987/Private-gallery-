#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.38.6
release_code=79
baseline_version=1.7.38.5
baseline_code=78
baseline_sha=c6ffbb769476b37b0c5bc4939e0f765eff3eef2a
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-78}" == 78 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
