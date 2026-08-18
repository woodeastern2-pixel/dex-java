cmake_minimum_required(VERSION 3.22.1)

project(lumi_local_image)

set_property(GLOBAL PROPERTY JOB_POOLS lumi_compile_pool=1 lumi_link_pool=1)
set(CMAKE_JOB_POOL_COMPILE lumi_compile_pool)
set(CMAKE_JOB_POOL_LINK lumi_link_pool)

set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_WEBP OFF CACHE BOOL "" FORCE)
set(SD_WEBM OFF CACHE BOOL "" FORCE)
set(SD_CUDA OFF CACHE BOOL "" FORCE)
set(SD_HIPBLAS OFF CACHE BOOL "" FORCE)
set(SD_METAL OFF CACHE BOOL "" FORCE)
set(SD_VULKAN OFF CACHE BOOL "" FORCE)
set(SD_OPENCL OFF CACHE BOOL "" FORCE)
set(SD_SYCL OFF CACHE BOOL "" FORCE)
set(SD_MUSA OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/stable-diffusion.cpp EXCLUDE_FROM_ALL)
target_compile_definitions(stable-diffusion PRIVATE
        LUMI_SD_MOBILE_CLIP_ONLY=1)

add_library(lumi_local_image SHARED
        lumi_local_image.cpp)

find_library(log-lib log)
find_library(z-lib z)

target_link_libraries(lumi_local_image
        stable-diffusion
        ${log-lib}
        ${z-lib})

target_compile_definitions(lumi_local_image PRIVATE
        LUMI_ENABLE_STABLE_DIFFUSION=1)