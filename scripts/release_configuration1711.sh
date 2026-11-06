#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/integration/novaos-1.7-home2 ]] || { echo 'Unsupported NovaOS integration release branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.11.3
release_code=59
baseline_version=1.7.11.2
baseline_code=58
baseline_sha=ce743678cca903f41a376e5227eb381f9fbb9574
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-58}" == 58 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
