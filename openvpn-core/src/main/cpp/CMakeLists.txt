# NetPilot :openvpn-core native build (v2.3.0).
#
# INVOKED BY scripts/build-ovpn-native.sh (CI native job) — deliberately NOT
# wired into the Gradle externalNativeBuild so the plain quality job needs
# no NDK and the app builds identically with or without the engine.
#
# Produces: libovpncore.so (one per ABI) with the official OpenVPN 3 core
# compiled in (header-only single TU: client/ovpncli.cpp + the few real
# .cpp files) — the same source set the official OpenVPN for Android
# client compiles (minus lzo: compression stubs only, modern servers).
#
# Expected CMake variables (set by the build script):
#   OVPN_CORE_DIR   — vendored core sources (openvpn3 clone)
#   DEP_PREFIX      — prefix with cross-built openssl/asio/lz4/fmt
#   ANDROID_ABI     — provided by the NDK toolchain file

cmake_minimum_required(VERSION 3.22)
project(ovpncore CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CORE "${OVPN_CORE_DIR}")
if(NOT EXISTS "${CORE}/client/ovpncli.cpp")
    message(FATAL_ERROR "OVPN_CORE_DIR does not contain the openvpn3 core (${CORE})")
endif()

add_library(ovpncore SHARED
    ovpncore_jni.cpp
    "${CORE}/client/ovpncli.cpp"
    "${CORE}/openvpn/crypto/data_epoch.cpp"
)

target_include_directories(ovpncore PRIVATE
    "${CORE}"
    "${CORE}/client"
    "${DEP_PREFIX}/include"
)

target_compile_definitions(ovpncore PRIVATE
    -DUSE_OPENSSL
    -DASIO_STANDALONE
    -DUSE_ASIO
    -DHAVE_LZ4
    -DOPENSSL_API_COMPAT=0x10100000L
)

# The official client's proven Android define set (ics-openvpn), minus
# lzo (compression stubs only) and external PKI (no keystore provider yet).
target_compile_options(ovpncore PRIVATE -Wa,--noexecstack)

find_library(LOG_LIB log)
target_link_libraries(ovpncore
    ${LOG_LIB}
    "${DEP_PREFIX}/lib/libssl.a"
    "${DEP_PREFIX}/lib/libcrypto.a"
    "${DEP_PREFIX}/lib/liblz4.a"
)
