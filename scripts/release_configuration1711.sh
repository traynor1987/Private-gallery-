#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/integration/novaos-1.7-home2 ]] || { echo 'Unsupported NovaOS integration release branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.11.1
release_code=57
baseline_version=1.7.11
baseline_code=56
baseline_sha=0e7f7c07608a6b439a4471e613531a758028c849
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-56}" == 56 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
