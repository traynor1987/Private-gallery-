# Local AI Vulkan build and process boundary

Reviewed 2026-09-25. Native build scope only; a successful compilation is not a
claim of Android driver compatibility, lower latency, or completed model testing.

## Pinned inputs and shipped components

| Component | Immutable revision / version | Role |
| --- | --- | --- |
| stable-diffusion.cpp | `19bbbca1c736bbb9538679fc0ae690cb2b46b492` | Existing modified MIT runtime |
| ggml | `4bf5f6000653b7881d00963cd6ddb665ccd62a8d` | Existing MIT CPU backend plus Vulkan backend and embedded shaders |
| Vulkan-Headers, including Vulkan-Hpp | `b5c8f996196ba4aa6d8f97e52b5d3b6e70f7e4e2` (`v1.4.341`) | Build headers; incorporated C++ code covered by offered MIT licence |
| SPIRV-Headers | `04f10f650d514df88b76d25e83db360142c7b174` (`vulkan-sdk-1.4.341.0`) | SPIR-V constants used by the backend; Khronos MIT-style notice retained |
| Ubuntu shaderc / glslc | `2023.8-1build1` on Ubuntu 24.04 | Host-only shader compiler, not an APK dependency |
| Android NDK / CMake | `27.2.12479018` / `3.22.1` | Existing Android native toolchain |

All three APK-building workflows install the same host shader compiler and NDK /
CMake versions. FetchContent retrieves source inputs at build time, never model
weights or executable code during app use. Header source repositories are not
packaged. The host compiler is not linked or copied into the APK. Android's system
`libvulkan.so` is linked through the selected NDK ABI/API stub; no desktop Vulkan
loader, validation layer, or ICD is bundled. Existing ggml notices also cover its
Vulkan shaders. New header notices are included both in the runtime notice assets
and the app's combined third-party notices.

## Build mechanism

`app/src/main/cpp/CMakeLists.txt` enables `SD_VULKAN` and keeps `GGML_CPU` enabled.
Backends remain statically linked into `libprivate_gallery_ai.so`;
`GGML_BACKEND_DL`, RPC, OpenCL, CUDA, examples, WebP/WebM and the other previously
disabled components stay disabled. The immutable SD source pin and
`patch_fd_loader.py` are unchanged. Its inherited model-FD path, literal prompt
conditioner, standard RNG, and excluded-source provenance checks still run before
adding the upstream source tree.

The pinned ggml Vulkan CMake file requires both
`find_package(Vulkan COMPONENTS glslc REQUIRED)` and
`find_package(SPIRV-Headers CONFIG REQUIRED)`. Our dependency file gives it the
pinned Vulkan C/C++ headers and a build-local SPIR-V header package. It explicitly
propagates the SPIR-V include target, which upstream's package lookup alone does
not do. `PG_HOST_GLSLC` must be executable on the build host. Shader generation is
an upstream ExternalProject using a separate native host C/C++ toolchain, supplied
as `GGML_VULKAN_SHADERS_GEN_TOOLCHAIN`; generated SPIR-V becomes C++ data in the
target library. Neither `glslc` nor `vulkan-shaders-gen` runs on the phone.
Embedded shaders do increase the native binary: the verified host static Vulkan
archive is approximately 42 MiB before final Android linking/stripping and APK
compression. Measure actual ABI/APK sizes in CI; do not infer them from this host
archive or describe acceleration as having negligible package cost.

Reproduce on Ubuntu 24.04 with JDK 17 and the Android SDK configured:

```sh
bash scripts/install_vulkan_build_tools.sh
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-36" "build-tools;36.0.0" \
  "ndk;27.2.12479018" "cmake;3.22.1"
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
python3 scripts/verify_local_ai_distribution.py \
  app/build/outputs/apk/debug/app-debug.apk
```

For another build host, provide a native `glslc` on PATH or set
`-DPG_HOST_GLSLC=/absolute/path/to/glslc`; native `cc` and `c++` must also be
available. Source pins and compiler package versions make inputs repeatable;
this is not a claim of byte-for-byte reproducible APKs (runner/JDK/Gradle dependency
and signing inputs must also be controlled for that).

## Runtime requirements and truthful discovery

### Android API 26 loader compatibility

Android CI exposed one upstream linker incompatibility: the pinned backend made
three direct calls to the Vulkan 1.1 `vkGetPhysicalDeviceFeatures2` symbol, absent
from the NDK's API 26 stub. Raising the app's minimum API would unnecessarily
remove older-device CPU compatibility. `patch_vulkan_android.py` instead rewrites
exactly those three calls to the existing Vulkan-Hpp instance dispatcher. The
dispatcher resolves instance functions through `vkGetInstanceProcAddr` after
upstream checks Vulkan 1.2 and creates its instance. It therefore preserves the
runtime capability requirement without introducing a newer mandatory ELF import.
The separate patch reads only the pinned ggml revision, rejects a different HEAD
or changed call sites, and is repeatable. The FD/provenance patch is unchanged.

`scripts/verify_vulkan_api26_symbols.py LIBRARY [NM]` checks a built static archive
or shared library's undefined symbols. The reviewed direct imports are limited
to Vulkan 1.0's `vkGetInstanceProcAddr`, `vkGetDeviceProcAddr` and
`vkCmdCopyBuffer`; other entry points must use dynamic resolution. Use the NDK's
`llvm-nm` argument for Android ELF files. The regression check rejected the old
host archive specifically for `vkGetPhysicalDeviceFeatures2` before the patch.
After recompiling the actual pinned Vulkan backend, the same check passed: its
undefined Vulkan symbols are now exactly those three Vulkan 1.0 imports. Patch
application twice produced identical source hashes. CMake runs this verifier as
a mandatory post-build step on `libprivate_gallery_ai.so`, using its toolchain's
`CMAKE_NM`, for every ABI and workflow. Android CI must still confirm the final
API 26 link; the host archive check does not substitute for that build.

Evidence in the pinned ggml `src/ggml-vulkan/ggml-vulkan.cpp`:

- `ggml_vk_instance_init()` requires Vulkan loader API 1.2 or later.
- `ggml_vk_device_is_supported()` requires `storageBuffer16BitAccess`.
- FP16 arithmetic, cooperative matrices, integer dot-product and other advanced
  extensions are optional; the backend selects paths based on runtime features.
- The shader generator targets Vulkan 1.2 normally, and 1.3 for `_cm2` variants.
  Host compiler extension tests do not prove a phone supports those extensions.

The existing Android minimum API 26 alone does not establish these GPU features.
Use actual initialization in the process that performs generation. The stable
diffusion C API already provides `sd_list_devices(char*, size_t)`, with a
size-query call followed by an allocated buffer; records are
`name<TAB>description\n`. These exact names are accepted by
`sd_ctx_params_t.backend`. Do not use display marketing names, Android Vulkan
feature flags, or a hard-coded `Vulkan0` as proof of usable compute. A device
listing is still weaker than successful device creation and bounded inference.
`params_backend="disk"` can remain in use with a selected Vulkan compute backend.

Static ggml registration itself attempts Vulkan initialization. Before the first
ggml call in a CPU-only worker, set `GGML_DISABLE_VULKAN=1`; the pinned
`ggml-backend-reg.cpp` checks this variable before registering Vulkan. In a GPU
worker leave it unset. This choice must precede registry initialization, rather
than switching an already initialized process back and forth. Recover from
driver failure through process death handling and a separate CPU attempt; a C++
exception handler cannot recover every vendor-driver abort or hang.

## Android isolation constraint

AOSP's [isolated_app_all policy](https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/isolated_app_all.te)
explicitly neverallows ordinary isolated processes direct GPU device access.
The exception is the platform-controlled `isolated_compute_app` domain, not a
permission an ordinary app can add to its existing service manifest. Thus the
existing `android:isolatedProcess="true"` CPU service must remain isolated, and
compiling Vulkan into it does not deliver a working GPU path on standard Android.

A separate non-exported app-UID GPU process can access the Android GPU driver,
but it shares the app's UID privileges. A seccomp network-syscall restriction can
reduce direct socket access if installed before model processing, applied to all
threads, and treated as mandatory. It is **not equivalent to isolated UID
execution**: app-private file access and Binder-mediated operations remain part
of its security boundary. The model-FD interface should remain the only model
input, and GPU work must not be moved into the UI process. The overall GPU worker
implementation and its device acceptance results require separate review.

## Validation record

Source review verified exact backend enumeration/selection APIs and mandatory
Vulkan checks against the pinned source. The existing distribution guard, workflow
YAML parsing, shell syntax and whitespace checks pass. A local Linux x86-64 build
using CMake 3.22.1, GCC 13.3, glslc 2023.8-1build1 and these exact header/source
pins successfully generated all shaders, compiled the Vulkan backend, and linked
`libggml-vulkan.a` (299 successful build steps). This also exercised the unchanged
FD/provenance patch during CMake configuration. The combined `ggml` target then
built successfully; a separately linked native probe initialized and freed its
CPU backend with `GGML_DISABLE_VULKAN=1`, confirmed no Vulkan registry entry, and
returned success. Android NDK/SDK are unavailable
in this workspace; both Android ABI builds remain CI gates. No physical-device
inference was run, so generation correctness, driver reliability and performance
remain device acceptance gates.
