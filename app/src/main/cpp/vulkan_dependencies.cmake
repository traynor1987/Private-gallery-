# Only headers and a host-side shader compiler are build inputs. Android's
# libvulkan.so is provided by the OS; never link a host loader into the APK.
FetchContent_Declare(pg_vulkan_headers
    GIT_REPOSITORY https://github.com/KhronosGroup/Vulkan-Headers.git
    GIT_TAG b5c8f996196ba4aa6d8f97e52b5d3b6e70f7e4e2
    GIT_SUBMODULES "") # v1.4.341, peeled immutable commit
FetchContent_Declare(pg_spirv_headers
    GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Headers.git
    GIT_TAG 04f10f650d514df88b76d25e83db360142c7b174
    GIT_SUBMODULES "") # vulkan-sdk-1.4.341.0
foreach(dependency pg_vulkan_headers pg_spirv_headers)
    FetchContent_GetProperties(${dependency})
    if(NOT ${dependency}_POPULATED)
        FetchContent_Populate(${dependency})
    endif()
endforeach()
set(Vulkan_INCLUDE_DIR "${pg_vulkan_headers_SOURCE_DIR}/include" CACHE PATH "Pinned Vulkan C/C++ headers" FORCE)
if(ANDROID)
    # find_library follows the Android toolchain sysroot and selected ABI/API.
    find_library(PG_ANDROID_VULKAN_LIBRARY vulkan REQUIRED)
    set(Vulkan_LIBRARY "${PG_ANDROID_VULKAN_LIBRARY}" CACHE FILEPATH "Android system Vulkan loader" FORCE)
endif()
find_program(PG_HOST_GLSLC NAMES glslc NO_CMAKE_FIND_ROOT_PATH REQUIRED)
execute_process(COMMAND "${PG_HOST_GLSLC}" --version
    RESULT_VARIABLE pg_glslc_status OUTPUT_VARIABLE pg_glslc_version
    ERROR_VARIABLE pg_glslc_error)
if(NOT pg_glslc_status EQUAL 0)
    message(FATAL_ERROR "Host glslc is not executable: ${pg_glslc_error}")
endif()
message(STATUS "Host shader compiler: ${PG_HOST_GLSLC}\n${pg_glslc_version}")
set(Vulkan_GLSLC_EXECUTABLE "${PG_HOST_GLSLC}" CACHE FILEPATH "Host shader compiler" FORCE)

# Provide the exact header package to ggml's find_package on CMake 3.22, which
# predates FetchContent's OVERRIDE_FIND_PACKAGE. No headers/tools are installed.
set(SPIRV-Headers_DIR "${pg_spirv_headers_BINARY_DIR}/pg-package" CACHE PATH "Pinned SPIR-V header package" FORCE)
file(MAKE_DIRECTORY "${SPIRV-Headers_DIR}")
file(WRITE "${SPIRV-Headers_DIR}/SPIRV-HeadersConfig.cmake"
    "if(NOT TARGET SPIRV-Headers::SPIRV-Headers)\n"
    "  add_library(SPIRV-Headers::SPIRV-Headers INTERFACE IMPORTED GLOBAL)\n"
    "  set_target_properties(SPIRV-Headers::SPIRV-Headers PROPERTIES INTERFACE_INCLUDE_DIRECTORIES \"${pg_spirv_headers_SOURCE_DIR}/include\")\n"
    "endif()\n")

# ggml uses a separate ExternalProject to build vulkan-shaders-gen. Be explicit
# that it runs on the build machine, never as an Android ARM/x86 executable.
find_program(PG_HOST_C_COMPILER NAMES cc clang gcc NO_CMAKE_FIND_ROOT_PATH REQUIRED)
find_program(PG_HOST_CXX_COMPILER NAMES c++ clang++ g++ NO_CMAKE_FIND_ROOT_PATH REQUIRED)
configure_file("${CMAKE_CURRENT_LIST_DIR}/vulkan_host_toolchain.cmake.in"
    "${CMAKE_CURRENT_BINARY_DIR}/pg-vulkan-host-toolchain.cmake" @ONLY)
set(GGML_VULKAN_SHADERS_GEN_TOOLCHAIN "${CMAKE_CURRENT_BINARY_DIR}/pg-vulkan-host-toolchain.cmake"
    CACHE FILEPATH "Native host compiler for shader generation" FORCE)
