# whisper.cpp (third_party/whisper.cpp, MIT) plus Power++'s small JNI bridge.
cmake_minimum_required(VERSION 3.22)

# Speech recognition at -O0 is unusably slow, so even debug builds of the app are optimised here.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)

project(powerpp_whisper C CXX)

# ggml's CPU code is built in several variants (ARMv8.0 up to ARMv9.2 with SME on arm64) and the
# best one for the device is loaded at runtime (GGML_BACKEND_DL). That's upstream's Android support;
# a single build would either crash old CPUs or leave modern ones' int8/fp16 instructions unused.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
endif ()
# ggml's own thread pool instead of OpenMP, which would add another runtime library.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# AGP builds and packages every library target. That includes whisper.cpp's Parakeet library
# (~150 KB, unused), which has no option to turn it off; limiting AGP to our own target instead
# would also drop the CPU backends, which are loaded at runtime rather than linked.
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/whisper.cpp whisper.cpp)

add_library(powerpp_whisper SHARED powerpp_whisper.cpp)
target_link_libraries(powerpp_whisper PRIVATE whisper ggml log)
