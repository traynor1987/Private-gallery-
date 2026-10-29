#!/usr/bin/env bash
# Source only for release operations. Never infer a release from version text or PR input.
release_min_sdk=26
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
  refs/heads/release/1.7.5.1-privacy-viewer-repair)
    release_min_sdk=36
    release_version=1.7.5.1
    release_code=39
    baseline_version=1.7.5
    baseline_code=38
    baseline_sha=69bfade789df4a49d911091bb8cf8205c88a3a39
    ;;
  refs/heads/release/1.7.5.2-recents-browser-repair)
    release_min_sdk=36
    release_version=1.7.5.2
    release_code=40
    baseline_version=1.7.5.1
    baseline_code=39
    baseline_sha=00509aa729d0aba4283fa0d000186153984cf87f
    ;;
  refs/heads/release/1.7.6-media-management)
    release_min_sdk=36
    release_version=1.7.6
    release_code=41
    baseline_version=1.7.5.2
    baseline_code=40
    baseline_sha=ec05b23ec28a44730439bd1090c1e64afff8f140
    ;;
  refs/heads/release/1.7.6.1-editing-settings-polish)
    release_min_sdk=37
    release_version=1.7.6.1
    release_code=42
    baseline_version=1.7.6
    baseline_code=41
    baseline_sha=180a7ce7048f56371d1e0566b6333de65fd1aa75
    ;;
  refs/heads/release/1.7.8-private-apps)
    # Configuration only: final source review and original-key upgrade evidence remain required.
    release_min_sdk=37
    release_version=1.7.8
    release_code=47
    baseline_version=1.7.7
    baseline_code=46
    baseline_sha=25aae6d59f79c455e47acc73a5c99f9c8f15242a
    [[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-46}" == 46 ]] || { echo 'Unsupported upgrade baseline' >&2; exit 1; }
    ;;
  refs/heads/release/1.7.7-recently-deleted)
    release_min_sdk=37
    release_version=1.7.7
    release_code=46
    baseline_version=1.7.6.2
    baseline_code=45
    baseline_sha=97e94452ceb9c5262521d7552f0c25eeb07374eb
    [[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-45}" == 45 ]] || { echo 'Unsupported upgrade baseline' >&2; exit 1; }
    ;;
  refs/heads/release/1.7.6.2-ui-browser-polish)
    release_min_sdk=37
    release_version=1.7.6.2
    release_code=45
    case "${PRIVATE_GALLERY_UPGRADE_BASELINE:-44}" in
      41) baseline_version=1.7.6; baseline_code=41; baseline_sha=180a7ce7048f56371d1e0566b6333de65fd1aa75 ;;
      42) baseline_version=1.7.6.1; baseline_code=42; baseline_sha=33e9669daeb3bf5a3d2cb0aae4d3480fe4b538c2 ;;
      44) baseline_version=1.7.6.2; baseline_code=44; baseline_sha=1ac9ebf7f5c6e4665ca3416e0e37b78bc52131e9 ;;
      43) baseline_version=1.7.6.2; baseline_code=43; baseline_sha=234d2d5c688a3f598408eebca24275757a40b8e8 ;;
      *) echo 'Release operation refused: unsupported upgrade baseline.' >&2; exit 1 ;;
    esac
    ;;
  *) echo 'Release operation refused: unsupported branch.' >&2; exit 1 ;;
esac
readonly release_min_sdk release_version release_code baseline_version baseline_code baseline_sha
