#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source="$root/bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/widgets"
fixture="$root/tests/headless-list-sparse"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac --release 17 -Xlint:all -Werror -d "$out" \
  "$source/VirtualListModel.java" \
  "$source/VirtualListWidth.java" \
  "$fixture/VirtualViewportPlanner.java" \
  "$fixture/VirtualListSparseDifferential.java"
java -ea -cp "$out" org.eclipse.swt.widgets.VirtualListSparseDifferential
