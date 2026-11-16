# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
#
# This program and the accompanying materials are made available under the
# terms of the Apache License, Version 2.0, or (at your option) the Eclipse
# Public License 2.0, which are available at
# https://www.apache.org/licenses/LICENSE-2.0 and
# https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# The native library of the Android port: libswtpixels.so, loaded by
# org.eclipse.swt.internal.android.AndroidPixels. Configure with the NDK's toolchain file
# (build/cmake/android.toolchain.cmake) and ANDROID_ABI / ANDROID_PLATFORM; mobile/android/build_native.py
# does that for every ABI and checks the result.

cmake_minimum_required(VERSION 3.22)
project(swtpixels C)

add_library(swtpixels SHARED swt_pixels.c)
set_target_properties(swtpixels PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON C_EXTENSIONS OFF)
target_compile_options(swtpixels PRIVATE -Wall -Wextra -Werror)
# AndroidBitmap_getInfo, AndroidBitmap_lockPixels and AndroidBitmap_unlockPixels
target_link_libraries(swtpixels PRIVATE jnigraphics)
