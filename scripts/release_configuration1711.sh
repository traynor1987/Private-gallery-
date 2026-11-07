#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/integration/novaos-1.7-home2 ]] || { echo 'Unsupported NovaOS integration release branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.11.7
release_code=63
baseline_version=1.7.11.6
baseline_code=62
baseline_sha=35d061c2c94297f7e063cc0b47037409f3642f9e
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-62}" == 62 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
