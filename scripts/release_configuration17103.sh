#!/usr/bin/env bash
# NovaOS patch identity; prior release controls remain immutable at their historical SHAs.
[[ "${GITHUB_REF:-}" == refs/heads/release/1.7.10-private-home ]] || { echo 'Unsupported NovaOS release branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.10.3
release_code=55
baseline_version=1.7.10.2
baseline_code=54
baseline_sha=9efd19540a1d75efa1b30f4e23c9cbb096f93aff
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-54}" == 54 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
