#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-visual-foundation ]] || { echo 'Unsupported NovaOS visual foundation branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.30-visual-foundation
release_code=64
baseline_version=1.7.11.7
baseline_code=63
baseline_sha=4738bcb12abb91ed289a78fcdcfaec7bc631cf0f
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-63}" == 63 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
