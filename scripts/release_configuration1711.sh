#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.37
release_code=72
baseline_version=1.7.36
baseline_code=71
baseline_sha=dd23090dd72682a9f9d0d22e7de39e1778fffa71
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-71}" == 71 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
