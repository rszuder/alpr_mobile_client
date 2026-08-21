cmake_minimum_required(VERSION 3.22.1)

project(alpr_ncnn LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(
        ncnn_DIR
        "${CMAKE_CURRENT_LIST_DIR}/vendor/ncnn/${ANDROID_ABI}/lib/cmake/ncnn"
        CACHE PATH
        "Directory containing the official NCNN Android package"
)
find_package(ncnn REQUIRED)

add_library(alpr_ncnn SHARED ncnn_jni.cpp)

find_library(log_lib log)

target_compile_options(
        alpr_ncnn
        PRIVATE
        -Wall
        -Wextra
        -Werror=return-type
)
target_link_libraries(alpr_ncnn PRIVATE ncnn ${log_lib})
