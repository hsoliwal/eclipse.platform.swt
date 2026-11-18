#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"
src="$root/cc/swt"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"${CXX:-clang++}" -std=c++17 -Wall -Wextra -Werror -pedantic -O2 \
    -I"$src" "$here/dirty-check.cc" -o "$tmp/dirty-check"
"$tmp/dirty-check"
