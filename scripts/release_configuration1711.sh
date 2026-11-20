#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/main ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.47
release_code=93
baseline_version=1.7.45
baseline_code=91
baseline_sha=022061984debb59f69d917f225ac08f1bfedfe82
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-91}" == 91 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
