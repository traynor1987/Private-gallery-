#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.50
release_code=96
baseline_version=1.7.49
baseline_code=95
baseline_sha=b003dafbad69a46d7f83896738030200a0dd0df4
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-95}" == 95 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
