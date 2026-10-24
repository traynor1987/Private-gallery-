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
  refs/heads/release/1.7.2-camera-ai-refinements)
    release_version=1.7.2
    release_code=35
    baseline_version=1.7.1
    baseline_code=34
    baseline_sha=77b3b9c1b30cb913e5fcbd76d66e40d585b13399
    ;;
  refs/heads/release/1.7.3-smart-unlock-gallery-sort)
    release_version=1.7.3
    release_code=36
    baseline_version=1.7.2
    baseline_code=35
    baseline_sha=e0cc1f0d8ddb8482910f01a9de5abf1e9472332b
    ;;
  refs/heads/release/1.7.4-camera-metadata-privacy)
    release_version=1.7.4
    release_code=37
    baseline_version=1.7.3
    baseline_code=36
    baseline_sha=569b3ea2e7bf600980e331b3b634843d01d0042e
    ;;
  refs/heads/release/1.7.5-media-experience-privacy)
    release_version=1.7.5
    release_code=38
    baseline_version=1.7.4
    baseline_code=37
    baseline_sha=0af69436147731d70dbdcac7455a4f252f199b6e
    ;;
  *) echo 'Release operation refused: unsupported branch.' >&2; exit 1 ;;
esac
readonly release_version release_code baseline_version baseline_code baseline_sha
