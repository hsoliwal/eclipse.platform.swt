<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT native tree residency: reusable coordinate lanes (2026-11-16)

The original `VirtualTreeResidentPlan.plan(Residency)` created three new
`int[count]` coordinate arrays for each viewport reconciliation, although
`Residency` is already a parent-owned array-backed projection. This change
retains and reuses the three scratch lanes in the existing plan owner,
growing them only on a capacity miss and staging all replacements before
publication. It does not create new per-row objects.

**Compatibility:** The original plan and commit signatures are unchanged.
The native handle for a logical coordinate is still reused only while
its parent survives; retirement, reorder, sibling identity, measurement,
and event/native ownership remain unchanged. A committed plan releases
only its pending references, not its reusable scratch capacity. Clear
forgets committed state, but reuses scratch on the next plan.

**Donors:** WinUI ItemsRepeater element lifecycle and JavaFX VirtualFlow
item recycling reinforce this retained-parent-buffer pattern. No external
framework code was copied. It is an intrinsic SWT-native optimization,
not a wrapper or new widget API; Nebula widgets stay outside SWT core.

**Qualification:** Existing `Test_VirtualTreeResidentPlan` adds an original
owner identity/capacity regression. New standalone Java21 CI compiles six
actual SWT model source owners without native stubs and checks 12,000
seeded viewport transitions, handle identity and scratch ownership.
Full SWT JUnit/native GTK/Cocoa/Win32/Android/iOS acceptance is a separate gate.
