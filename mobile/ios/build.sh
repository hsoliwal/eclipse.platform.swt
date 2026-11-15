#!/usr/bin/env bash
# Copyright (c) 2026 Hitesh Soliwal and contributors.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
set -euo pipefail

usage() {
  cat <<'HELP'
Usage: bash mobile/ios/build.sh <iphoneos|iphonesimulator> <arm64|x86_64> <mobile-swt.jar> [CMake options...]

Compiles SWTUIKit and the application bootstrap against a real Xcode SDK.
For iphonesimulator, also runs the native CoreGraphics/CoreText renderer tests
inside an available iOS simulator. SWT_IOS_TEST_DEVICE_UDID selects a device.
JAVA_HOME must identify a macOS JDK 21, used to regenerate the JNI declarations
from the actual SWT sources. This renderer harness does not run a JavaVM or an
SWT application. See README.md for the separately gated iOS runtime app target.
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
configure() {
  local target_build=$1 target_arch=$2
  shift 2
  cmake -S "$swt_source" -B "$target_build" -G Xcode \
  -DCMAKE_SYSTEM_NAME=iOS \
  "-DCMAKE_OSX_SYSROOT=$swt_sdk_path" \
  "-DCMAKE_OSX_ARCHITECTURES=$target_arch" \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=13.4 \
  "-DJava_JAVAC_EXECUTABLE=$JAVA_HOME/bin/javac" \
  "-DJava_JAVA_EXECUTABLE=$JAVA_HOME/bin/java" \
  "-DSWT_IOS_JNI_INCLUDE_DIR=$JAVA_HOME/include" \
  "-DSWT_IOS_JNI_PLATFORM_INCLUDE_DIR=$JAVA_HOME/include/darwin" \
  "-DSWT_IOS_MOBILE_JAR=$swt_jar" "$@"
}
configure "$swt_build" "$swt_arch" "$@"
cmake --build "$swt_build" --config Release --target swt_ios swt_ios_bootstrap

if [[ "$swt_sdk" == iphonesimulator ]]; then
  swt_host_arch=$(uname -m)
  case "$swt_host_arch" in arm64|x86_64) ;; *) echo "Unsupported simulator host architecture: $swt_host_arch" >&2; exit 1 ;; esac
  swt_test_build=$swt_build
  if [[ "$swt_host_arch" != "$swt_arch" ]]; then
    # Preserve the explicitly requested native-library architecture and run a
    # separately compiled harness matching this Mac's actual simulator runtime.
    swt_test_build="$swt_build-tests-$swt_host_arch"
    configure "$swt_test_build" "$swt_host_arch" "$@"
  fi
  cmake --build "$swt_test_build" --config Release --target swt_ios_native_tests
  swt_test_executable="$swt_test_build/native-tests/Release/swt_ios_native_tests"
  if [[ ! -x "$swt_test_executable" ]]; then echo "Missing native renderer executable: $swt_test_executable" >&2; exit 1; fi
  codesign --force --sign - "$swt_test_executable"
  swt_device=$(python3 - <<'PY'
import json, os, re, subprocess, sys
inventory = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "--json"]))
requested = os.environ.get("SWT_IOS_TEST_DEVICE_UDID")
candidates = []
for runtime, devices in inventory["devices"].items():
    if ".iOS-" not in runtime:
        continue
    version = tuple(int(value) for value in re.findall(r"\d+", runtime.rsplit(".iOS-", 1)[1]))
    for device in devices:
        if not device.get("isAvailable"):
            continue
        if requested and device["udid"] != requested:
            continue
        if not requested and not device["name"].startswith("iPhone"):
            continue
        candidates.append((device["state"] == "Booted", version, device["name"], device))
if not candidates:
    sys.exit("No matching available iOS simulator. Install an iOS runtime in Xcode or set SWT_IOS_TEST_DEVICE_UDID.")
device = max(candidates, key=lambda entry: entry[:3])[3]
print(device["udid"], device["state"])
PY
  )
  read -r swt_udid swt_device_state <<< "$swt_device"
  swt_booted_here=false
  cleanup() {
    if [[ "$swt_booted_here" == true ]]; then xcrun simctl shutdown "$swt_udid" || true; fi
  }
  trap cleanup EXIT
  if [[ "$swt_device_state" != Booted ]]; then
    xcrun simctl boot "$swt_udid"
    swt_booted_here=true
  fi
  xcrun simctl bootstatus "$swt_udid" -b
  echo "Running actual iOS native renderer tests: device=$swt_udid architecture=$swt_host_arch"
  xcrun simctl spawn "$swt_udid" "$swt_test_executable" 2>&1 | tee "$swt_build/native-render-tests.log"
fi
