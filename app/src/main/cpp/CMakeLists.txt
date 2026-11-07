cmake_minimum_required(VERSION 3.22.1)
project(pinbom_fx LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(pinbom_fx SHARED particle_jni.cpp)

# -O2 keeps the loop tight; hidden visibility leaves only the JNI entry points exported.
target_compile_options(pinbom_fx PRIVATE -O2 -fvisibility=hidden -Wall -Wextra)
target_link_options(pinbom_fx PRIVATE -Wl,--gc-sections)
# Android 15+ devices use 16 KB pages: keep every segment aligned for them.
target_link_options(pinbom_fx PRIVATE -Wl,-z,max-page-size=16384)

find_library(log-lib log)
target_link_libraries(pinbom_fx ${log-lib})
