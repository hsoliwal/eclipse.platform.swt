#!/usr/bin/env bash
# Copyright (c) 2026 Hitesh Soliwal and contributors.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
set -euo pipefail

usage() {
  cat <<'HELP'
Usage: bash mobile/ios/build.sh <iphoneos|iphonesimulator> <arm64|x86_64> <mobile-swt.jar> [CMake options...]

Compiles SWTUIKit and the application bootstrap against a real Xcode SDK.
JAVA_HOME must identify a macOS JDK 21, used to regenerate the JNI declarations
from the actual SWT sources. No simulator/device application is claimed by this
compile-only lane. See README.md for the separately gated iOS runtime app target.
HELP
}
if [[ $# -lt 3 ]]; then usage >&2; exit 2; fi
swt_sdk=$1
swt_arch=$2
swt_jar=$3
shift 3
case "$swt_sdk" in iphoneos|iphonesimulator) ;; *) usage >&2; exit 2 ;; esac
case "$swt_arch" in arm64|x86_64) ;; *) usage >&2; exit 2 ;; esac
if [[ "$swt_sdk" == iphoneos && "$swt_arch" != arm64 ]]; then
  echo "The iPhoneOS target requires arm64." >&2; exit 2
fi
if [[ "$(uname -s)" != Darwin ]]; then
  echo "A macOS host with Xcode and the actual iOS SDK is required." >&2; exit 1
fi
: "${JAVA_HOME:?Set JAVA_HOME to a macOS JDK 21}"
if [[ ! -x "$JAVA_HOME/bin/javac" || ! -f "$swt_jar" ]]; then
  echo "The JDK compiler or actual mobile SWT JAR is missing." >&2; exit 1
fi
swt_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
swt_jar=$(cd -- "$(dirname -- "$swt_jar")" && pwd)/$(basename -- "$swt_jar")
swt_sdk_path=$(xcrun --sdk "$swt_sdk" --show-sdk-path)
swt_build=${SWT_IOS_BUILD_DIR:-"$swt_source/../../build/ios-$swt_sdk-$swt_arch"}
cmake -S "$swt_source" -B "$swt_build" -G Xcode \
  -DCMAKE_SYSTEM_NAME=iOS \
  "-DCMAKE_OSX_SYSROOT=$swt_sdk_path" \
  "-DCMAKE_OSX_ARCHITECTURES=$swt_arch" \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=13.4 \
  "-DJava_JAVAC_EXECUTABLE=$JAVA_HOME/bin/javac" \
  "-DJava_JAVA_EXECUTABLE=$JAVA_HOME/bin/java" \
  "-DSWT_IOS_JNI_INCLUDE_DIR=$JAVA_HOME/include" \
  "-DSWT_IOS_JNI_PLATFORM_INCLUDE_DIR=$JAVA_HOME/include/darwin" \
  "-DSWT_IOS_MOBILE_JAR=$swt_jar" "$@"
cmake --build "$swt_build" --config Release --target swt_ios swt_ios_bootstrap
