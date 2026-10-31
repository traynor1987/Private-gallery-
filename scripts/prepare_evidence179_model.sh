#!/usr/bin/env bash
# Build-time only. This public model is bundled into the APK; the app never downloads it.
set -euo pipefail
[[ $# == 1 ]] || { echo 'Usage: prepare_evidence179_model.sh OUTPUT_FILE' >&2; exit 2; }
readonly output=$1
readonly sha=7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2
readonly length=4113088
readonly url=https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/eng.traineddata
verify() {
  [[ -f "$1" && $(wc -c < "$1") -eq $length && $(sha256sum "$1" | cut -d ' ' -f 1) == "$sha" ]]
}
if verify "$output"; then exit 0; fi
mkdir -p "$(dirname "$output")"
# A corrupt generated output must not survive a failed refresh.
rm -f -- "$output"
staging=$(mktemp "${output}.partial.XXXXXX")
trap 'rm -f -- "$staging"' EXIT
if [[ -n ${EVIDENCE179_MODEL_CACHE:-} ]]; then
  verify "$EVIDENCE179_MODEL_CACHE" || { echo 'OCR public model cache has incorrect size or SHA256' >&2; exit 1; }
  cp -- "$EVIDENCE179_MODEL_CACHE" "$staging"
else
  curl --fail --location --proto '=https' --proto-redir '=https' --connect-timeout 20 --max-time 180 --retry 0 --output "$staging" "$url"
fi
verify "$staging" || { echo 'OCR public model has incorrect size or SHA256; refusing to package' >&2; exit 1; }
chmod 0644 "$staging"
mv -f -- "$staging" "$output"
