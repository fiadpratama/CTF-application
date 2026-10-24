cmake_minimum_required(VERSION 3.22.1)

project("native-crypto")

add_library(
        native-crypto
        SHARED
        native-crypto.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        native-crypto
        ${log-lib})

if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(native-crypto PRIVATE -s)
endif()
