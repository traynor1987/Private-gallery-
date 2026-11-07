#!/usr/bin/env bash
# Prepared production identity only; this file does not authorize signing or publishing.
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-visual-foundation ]] || { echo 'Unsupported NovaOS visual foundation branch' >&2; exit 1; }
release_min_sdk=37
release_version=1.7.30-visual-foundation
release_code=65
baseline_version=1.7.30-visual-foundation
baseline_code=64
baseline_sha=2fe2e6b4f959704524fe4f7a3f44dcd770a5cdfb
[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-64}" == 64 ]] || { echo 'Unsupported NovaOS upgrade baseline' >&2; exit 1; }
