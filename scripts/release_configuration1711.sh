#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/integration/novaos-1.7-home2 ]] || { echo 'Unsupported NovaOS integration release branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.11
release_code=56
baseline_version=1.7.10.2
baseline_code=54
baseline_sha=9efd19540a1d75efa1b30f4e23c9cbb096f93aff
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-54}" == 54 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
