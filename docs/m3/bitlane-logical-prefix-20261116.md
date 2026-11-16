<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT logical-prefix bit-lane continuation — 2026-11-16

The existing VirtualBitLanes widget owner retains all its arrays and public contracts.
Only count(int) and nextSet(int,int) bound scanning by
min(physicalWords, ceil(logicalLength / 32)) after shrink.

The two new JUnit regressions exercise shrink/regrow and word boundaries. A Java21 headless
contract compiles actual product VirtualBitLanes, VirtualGrowth and PackedHeapGrowth sources;
only SWT error constants are provided by an explicitly test-only stand-in. It runs a
seeded boolean oracle and a retained-capacity relative-work probe.

This qualification is not a native GUI, exact SWT exception-type, or whole-platform test.
Canonical date-scoped hash-pinned Java/text recipes live in Synexia; public files are their
emitted outputs. All existing widget ownership, selection/scrolling and event APIs stay intact.
