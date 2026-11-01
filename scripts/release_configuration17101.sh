#!/usr/bin/env bash
# NovaOS patch identity; prior release controls remain immutable at their historical SHAs.
[[ "${GITHUB_REF:-}" == refs/heads/release/1.7.10-private-home ]] || { echo 'Unsupported NovaOS release branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.10.1
release_code=53
baseline_version=1.7.10
baseline_code=52
baseline_sha=f301ee73320df2e366b7cd258dae5105296237a7
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-52}" == 52 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
