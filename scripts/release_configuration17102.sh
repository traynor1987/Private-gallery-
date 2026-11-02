#!/usr/bin/env bash
# NovaOS patch identity; prior release controls remain immutable at their historical SHAs.
[[ "${GITHUB_REF:-}" == refs/heads/release/1.7.10-private-home ]] || { echo 'Unsupported NovaOS release branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.10.2
release_code=54
baseline_version=1.7.10.1
baseline_code=53
baseline_sha=f923d62de7696e731c2ec7809d772081ab0e1204
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-53}" == 53 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
