#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/integration/novaos-1.7-home2 ]] || { echo 'Unsupported NovaOS integration release branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.11.4
release_code=60
baseline_version=1.7.11.3
baseline_code=59
baseline_sha=ab659cf3578d08cdc0deb643f887e561abb14c63
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-59}" == 59 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
