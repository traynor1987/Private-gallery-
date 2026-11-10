#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.38.3
release_code=76
baseline_version=1.7.38.2
baseline_code=75
baseline_sha=dc4f0b19be8391b899364fb62d890ad322350e2e
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-75}" == 75 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
