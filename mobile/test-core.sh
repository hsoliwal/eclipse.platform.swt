#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/headers"
javac --release 21 -Xlint:all -h "$OUT/headers" -d "$OUT" \
  "$ROOT/bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/mobile/MobileWidgetRuntime.java" \
  "$ROOT/mobile/ios/src/main/java/org/eclipse/swt/internal/mobile/ios/IosMobileHost.java" \
  "$ROOT/mobile/tests/MobileWidgetRuntimeTest.java"
java -ea -cp "$OUT" MobileWidgetRuntimeTest
HEADER="$OUT/headers/org_eclipse_swt_internal_mobile_ios_IosMobileHost.h"
grep -oE 'Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_n[A-Za-z]+' "$HEADER" | sort -u > "$OUT/generated-symbols"
grep -oE 'Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_n[A-Za-z]+' \
  "$ROOT/mobile/ios/native/SWTMobileUIKit.m" | sort -u > "$OUT/implemented-symbols"
diff -u "$OUT/generated-symbols" "$OUT/implemented-symbols"
echo 'UIKit JNI symbol contract PASSED'
