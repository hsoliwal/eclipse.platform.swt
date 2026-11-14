#!/usr/bin/env bash
# Copyright (c) 2026 Hitesh Soliwal and contributors.
# SPDX-License-Identifier: EPL-2.0
set -euo pipefail
task_root=$(cd "$(dirname "$0")/../.." && pwd)
task_java=${SWT_JAVA_HOME:-${JAVA_HOME:-}}
if [[ -z "$task_java" || ! -f "$task_java/include/darwin/jni_md.h" ]]; then
  echo 'SWT_JAVA_HOME must select a macOS JDK 21 with real JNI headers.' >&2
  exit 2
fi
task_sdk=$(xcrun --sdk iphonesimulator --show-sdk-path)
task_clang=$(xcrun --sdk iphonesimulator --find clang)
task_arch=$(uname -m)
case "$task_arch" in arm64|x86_64) ;; *) echo 'Unsupported simulator host architecture.' >&2; exit 2;; esac
task_out=${SWT_IOS_BUILD_DIR:-"$task_root/target/mobile-ios"}
mkdir -p "$task_out"
task_native="$task_root/bundles/org.eclipse.swt/Eclipse SWT PI/ios/library"
task_flags=(-target "$task_arch-apple-ios15.0-simulator" -isysroot "$task_sdk"
  -fobjc-arc -fblocks -Wall -Wextra -Werror -std=gnu11
  -I "$task_java/include" -I "$task_java/include/darwin" -I "$task_native")
"$task_clang" "${task_flags[@]}" -c "$task_native/swt_mobile.m" -o "$task_out/swt_mobile.o"
xcrun libtool -static -o "$task_out/libswt-mobile-ios.a" "$task_out/swt_mobile.o"
"$task_clang" "${task_flags[@]}" "$task_root/tests/org.eclipse.swt.tests/ios/swt_mobile_render_test.m" \
  -framework UIKit -framework Foundation -framework CoreGraphics -framework CoreText \
  -o "$task_out/swt-mobile-render-test"
echo 'SWT_IOS_NATIVE_COMPILE_PASS static_bridge=1 render_test=1'

# Use an installed Apple simulator, never an emulated SDK or replacement headers.
task_device=$(xcrun simctl list devices available -j | python3 -c '
import json,sys
d=json.load(sys.stdin)
for runtime,devices in sorted(d["devices"].items(),reverse=True):
    if ".iOS-" not in runtime: continue
    for device in devices:
        if device.get("isAvailable") and "iPhone" in device.get("name",""):
            print(device["udid"]);sys.exit(0)
sys.exit("No installed iPhone simulator")
')
task_state=$(xcrun simctl list devices -j | python3 -c 'import json,sys; d=json.load(sys.stdin); print(next(x["state"] for v in d["devices"].values() for x in v if x["udid"]==sys.argv[1]))' "$task_device")
if [[ "$task_state" != Booted ]]; then
  xcrun simctl boot "$task_device"
  trap 'xcrun simctl shutdown "$task_device" >/dev/null 2>&1 || true' EXIT
fi
xcrun simctl bootstatus "$task_device" -b
xcrun simctl spawn "$task_device" "$task_out/swt-mobile-render-test"
# This gate is native frame/metrics evidence, not JavaVM or SWT/Nebula app execution.
