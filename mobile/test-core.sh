#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac --release 21 -Xlint:all -d "$OUT" \
  "$ROOT/bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/mobile/MobileWidgetRuntime.java" \
  "$ROOT/mobile/tests/MobileWidgetRuntimeTest.java"
java -ea -cp "$OUT" MobileWidgetRuntimeTest
