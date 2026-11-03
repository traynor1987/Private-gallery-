#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Linux reproducible foundation build. No runtime integration or signing changes.
set -euo pipefail
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
out=${1:-"$repo/app/build/generated/continuousVpnJni"}
ndk=${2:-${ANDROID_NDK_HOME:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/ndk/27.2.12479018}}
out=$(realpath -m "$out")
ndk=$(realpath -m "$ndk")
work="$repo/app/build/continuousVpnNative"
[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]] || { echo 'Linux x86_64 build host required' >&2; exit 1; }
[[ -f "$ndk/source.properties" ]] || { echo 'Pinned NDK unavailable; invoke Gradle prepareContinuousVpnNative for automatic SDK provisioning' >&2; exit 1; }
grep -Eq '^Pkg.Revision *= *27\.2\.12479018$' "$ndk/source.properties" || { echo 'Wrong NDK revision' >&2; exit 1; }
mkdir -p "$work" "$out"
exec 9>"$work/build.lock"
flock 9
archive=${NOVA_GO_ARCHIVE:-"$work/go1.20.3.linux-amd64.tar.gz"}
go_hash=979694c2c25c735755bf26f4f45e19e64e4811d661dd07b8c010f7a8e18adfca
patch_file="$repo/native/continuousvpn/upstream/tunnel/tools/libwg-go/goruntime-boottime-over-monotonic.diff"
if [[ ! -f "$archive" ]]; then
  [[ -z ${NOVA_GO_ARCHIVE:-} ]] || { echo 'Explicit Go archive missing' >&2; exit 1; }
  curl --fail --location --retry 3 https://dl.google.com/go/go1.20.3.linux-amd64.tar.gz -o "$archive.tmp"
  printf '%s  %s\n' "$go_hash" "$archive.tmp" | sha256sum --check --status
  mv "$archive.tmp" "$archive"
fi
printf '%s  %s\n' "$go_hash" "$archive" | sha256sum --check --status
printf '%s  %s\n' 149ab7d9a1c20dad61fe279a837e0e0c81c6b2f5584614ac4d4cde67008755f0 "$patch_file" | sha256sum --check --status
go_root=${NOVA_GO_ROOT:-"$work/go"}
if [[ ! -d "$go_root" ]]; then
  [[ -z ${NOVA_GO_ROOT:-} ]] || { echo 'Explicit Go toolchain missing' >&2; exit 1; }
  tar -xzf "$archive" -C "$work"
  patch --silent -p1 -d "$go_root" < "$patch_file"
fi
[[ $("$go_root/bin/go" version) == 'go version go1.20.3 linux/amd64' ]] || { echo 'Wrong Go version' >&2; exit 1; }
python3 "$repo/native/continuousvpn/android/verify_toolchain.py" "$archive" "$go_root" "$patch_file"
export PATH="$go_root/bin:$PATH" GOROOT="$go_root" GOMAXPROCS=${GOMAXPROCS:-2}
cd "$repo/native/continuousvpn/android"
go mod download
go mod verify
clang="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  case "$abi" in
    arm64-v8a) arch=arm64; target=aarch64-linux-android;;
    armeabi-v7a) arch=arm; target=armv7a-linux-androideabi;;
    x86) arch=386; target=i686-linux-android;;
    x86_64) arch=amd64; target=x86_64-linux-android;;
  esac
  mkdir -p "$out/$abi"
  # API21 is the native libc ABI baseline, independent of APK min/target API37.
  # 16KB max/common-page-size is enforced and checked in each resulting ELF.
  env GOOS=android GOARCH="$arch" GOARM=7 CGO_ENABLED=1 CC="$clang/${target}21-clang" \
    CGO_CFLAGS='-O2 -fvisibility=hidden' \
    CGO_LDFLAGS='-Wl,-soname,libnova-wg.so -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,--build-id=none' \
    go build -p 2 -tags linux -trimpath -buildvcs=false -ldflags='-buildid=' -buildmode=c-shared -o "$out/$abi/libnova-wg.so" ./jni
  rm -f "$out/$abi/libnova-wg.h"
  python3 "$repo/native/continuousvpn/android/verify_elf.py" "$clang/llvm-readelf" "$out/$abi/libnova-wg.so"
done
