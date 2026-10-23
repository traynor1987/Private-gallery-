#!/usr/bin/env bash
# Source only for release operations. Never infer a release from version text or PR input.
case "${GITHUB_REF:-}" in
  refs/heads/release/1.7-encrypted-camera)
    release_version=1.7
    release_code=33
    baseline_version=1.6
    baseline_code=32
    baseline_sha=7765c79874556e8093612b64eeb529e095c9c287
    ;;
  refs/heads/release/1.7.1-professional-ui)
    release_version=1.7.1
    release_code=34
    baseline_version=1.7
    baseline_code=33
    baseline_sha=18789f0f595abdfcd54e931671a82c23289af1ea
    ;;
  *) echo 'Release operation refused: unsupported branch.' >&2; exit 1 ;;
esac
readonly release_version release_code baseline_version baseline_code baseline_sha
