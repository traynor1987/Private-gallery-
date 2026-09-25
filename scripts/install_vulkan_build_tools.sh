#!/usr/bin/env bash
# Host-only compiler. No shader compiler, loader or model enters the APK.
set -euo pipefail
sudo apt-get update
# Ubuntu 24.04's shaderc package, pinned to avoid changing generated shaders
# merely because the runner image gains a newer shader compiler.
sudo apt-get install --no-install-recommends -y build-essential ninja-build \
    glslc=2023.8-1build1 libshaderc1=2023.8-1build1
command -v glslc
glslc --version
dpkg-query -W glslc libshaderc1
