#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ndk=${1:?Pass the pinned NDK directory}
work=$(mktemp -d)
trap 'python3 -c "import shutil,sys; shutil.rmtree(sys.argv[1])" "$work"' EXIT
# Android's VM-neutral JNI declaration uses only stdint/stdarg. Copy this one
# header to avoid mixing Android libc headers into the host executable.
cp "$ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/jni.h" "$work/jni.h"
${CC:-cc} -O2 -finstrument-functions -ffunction-sections -fdata-sections \
  -I"$work" -I"$here" "$here/secret_cleanup_test.c" -Wl,--gc-sections -o "$work/secret_cleanup_test"
"$work/secret_cleanup_test"
