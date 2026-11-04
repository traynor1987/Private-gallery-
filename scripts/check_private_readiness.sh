#!/usr/bin/env bash
# Focused host verification only: not Android routing or release approval.
set -euo pipefail
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
go_bin=${NOVA_GO_BIN:-go}
[[ $("$go_bin" version) == 'go version go1.20.3 '* ]] || { echo 'Pinned Go1.20.3 required' >&2; exit 1; }
for module in gate transport readiness; do
  (
    cd "$repo/native/continuousvpn/$module"
    "$go_bin" mod verify
    "$go_bin" test -mod=readonly -race -count=1 -timeout=45s ./...
    "$go_bin" vet -mod=readonly ./...
  )
done
(
  cd "$repo/native/continuousvpn/android"
  "$go_bin" mod verify
  "$go_bin" test -mod=readonly -race -count=1 -timeout=45s ./bridge uk.co.traynor.privategallery/continuousvpn/readiness
  "$go_bin" vet -mod=readonly ./bridge uk.co.traynor.privategallery/continuousvpn/readiness
)
