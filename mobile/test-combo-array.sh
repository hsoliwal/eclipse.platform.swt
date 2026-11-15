#!/usr/bin/env bash
# SPDX-License-Identifier: EPL-2.0
set -euo pipefail
SWT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT
javac --release 21 -Xlint:all -d "$BUILD_DIR" \
  "$SWT_ROOT/bundles/org.eclipse.swt/Eclipse SWT/mobile/org/eclipse/swt/internal/mobile/MobileComboArrayOps.java" \
  "$SWT_ROOT/mobile/tests/MobileComboArrayOpsTest.java"
java -ea -cp "$BUILD_DIR" MobileComboArrayOpsTest
