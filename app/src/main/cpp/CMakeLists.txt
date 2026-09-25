cmake_minimum_required(VERSION 3.22.1)
project(private_gallery_ai LANGUAGES C CXX)
include(FetchContent)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set_property(GLOBAL PROPERTY JOB_POOLS native_compile=2)
set(CMAKE_JOB_POOL_COMPILE native_compile)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
foreach(flag SD_BUILD_EXAMPLES SD_WEBP SD_WEBM SD_CUDA SD_VULKAN SD_OPENCL SD_RPC SD_METAL SD_BUILD_SHARED_LIBS GGML_NATIVE GGML_OPENMP GGML_CPU_KLEIDIAI GGML_LLAMAFILE GGML_RPC GGML_BACKEND_DL)
    set(${flag} OFF CACHE BOOL "" FORCE)
endforeach()
# Immutable reviewed source, never a model or an APK-time code download.
FetchContent_Declare(sdcpp
    GIT_REPOSITORY https://github.com/leejet/stable-diffusion.cpp.git
    GIT_TAG 19bbbca1c736bbb9538679fc0ae690cb2b46b492
    GIT_SUBMODULES ggml
    GIT_SUBMODULES_RECURSE FALSE)
FetchContent_GetProperties(sdcpp)
if(NOT sdcpp_POPULATED)
    FetchContent_Populate(sdcpp)
endif()
find_package(Python3 COMPONENTS Interpreter REQUIRED)
execute_process(COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/patch_fd_loader.py ${sdcpp_SOURCE_DIR} COMMAND_ERROR_IS_FATAL ANY)
add_subdirectory(${sdcpp_SOURCE_DIR} ${sdcpp_BINARY_DIR})
add_library(private_gallery_ai SHARED local_ai.cpp)
target_include_directories(private_gallery_ai PRIVATE ${sdcpp_SOURCE_DIR}/include)
target_link_libraries(private_gallery_ai PRIVATE stable-diffusion)
target_link_options(private_gallery_ai PRIVATE "-Wl,-z,max-page-size=16384")
