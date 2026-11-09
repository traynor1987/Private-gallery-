#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]] || { echo 'Unsupported NovaOS Sanctuary integration branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.38
release_code=73
baseline_version=1.7.37
baseline_code=72
baseline_sha=7422343e73b13569a6a18501ecfe077bcca55c3d
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-72}" == 72 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
