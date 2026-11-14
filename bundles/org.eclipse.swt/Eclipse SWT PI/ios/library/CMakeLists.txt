# Copyright (c) 2026 Hitesh Soliwal and contributors.
# SPDX-License-Identifier: EPL-2.0
cmake_minimum_required(VERSION 3.20)
project(swt_mobile_ios LANGUAGES C OBJC)

if(NOT CMAKE_SYSTEM_NAME STREQUAL "iOS")
  message(FATAL_ERROR "Select a real iOS SDK with -DCMAKE_SYSTEM_NAME=iOS and -DCMAKE_OSX_SYSROOT=iphoneos or iphonesimulator")
endif()
set(SWT_JAVA_HOME "" CACHE PATH "JDK 21 providing JNI headers; the embedding app provides the JavaVM")
if(NOT EXISTS "${SWT_JAVA_HOME}/include/jni.h" OR NOT EXISTS "${SWT_JAVA_HOME}/include/darwin/jni_md.h")
  message(FATAL_ERROR "SWT_JAVA_HOME must name a macOS JDK with include/jni.h and include/darwin/jni_md.h")
endif()

add_library(swt-mobile-ios STATIC swt_mobile.m)
target_include_directories(swt-mobile-ios PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}" "${SWT_JAVA_HOME}/include" "${SWT_JAVA_HOME}/include/darwin")
target_compile_options(swt-mobile-ios PRIVATE -fobjc-arc -fblocks -Wall -Wextra -Werror)
set_target_properties(swt-mobile-ios PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED YES POSITION_INDEPENDENT_CODE ON)
find_library(SWT_UIKIT UIKit REQUIRED)
find_library(SWT_FOUNDATION Foundation REQUIRED)
find_library(SWT_COREGRAPHICS CoreGraphics REQUIRED)
find_library(SWT_CORETEXT CoreText REQUIRED)
target_link_libraries(swt-mobile-ios PUBLIC "${SWT_UIKIT}" "${SWT_FOUNDATION}" "${SWT_COREGRAPHICS}" "${SWT_CORETEXT}")

# No native binary or JavaVM is committed or downloaded by this target.
