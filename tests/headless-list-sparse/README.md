<!-- SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 -->
# Native SWT List: independent text-lane regression

This runner compiles the **actual, unchanged** SWT `VirtualListModel` and
`VirtualListWidth` production Java sources with `--release 17 -Xlint:all -Werror`
and runs an executable headless reference oracle. The only replacement is
`VirtualViewportPlanner`, a **test-only stub** confined to this directory
because the native widget / selection / scroll implementation depends on the
SWT platform. It MUST NOT ship in production bundles.

The oracle checks 100,000,000 logical rows with bounded resident text arrays,
sparse writes at extreme indices, 20×800 randomized edits against a dense
reference, and preservation of the eager-list API behavior. It checks internal
sorted row IDs, resident count, row/text correspondence, and indexed search.

Run `bash tests/headless-list-sparse/verify.sh` from any working directory on
JDK17 or newer.

Passing this is **not** proof of Win32/GTK/Cocoa native List, viewport,
scrolling, selection, JFace, accessibility, ABI or full PDE/JUnit tests.
These remain required for release. No new runtime viewport, wrapper, event
handler, per-row object or public API is introduced.
