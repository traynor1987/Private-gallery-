#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/integration/novaos-1.7-home2 ]] || { echo 'Unsupported NovaOS integration release branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.11.2
release_code=58
baseline_version=1.7.11.1
baseline_code=57
baseline_sha=3af2289e444771fd87188743879bec3361ea6346
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-57}" == 57 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
