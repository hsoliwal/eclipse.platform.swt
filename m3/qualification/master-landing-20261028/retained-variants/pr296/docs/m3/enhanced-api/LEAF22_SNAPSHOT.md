# Leaf 22: recorded control state for tests (M3Snapshot)

Map row M3-SNAPSHOT. The viewport programme proves "same contract, same behaviour" with recorded differentials
(`ViewportDifferential`: a line per step of answers, bars, selection, and pixels compared after two captures agree).
Clients that test their own views need the same thing without the test harness.

## Surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| member | meaning |
|---|---|
| `M3Snapshot.capture (Control)` | named fields from the public API: `class`, `bounds`, `state` (visible/hidden, enabled/disabled), for a `Scrollable` `client`, `vbar`, `hbar`, for a Table, List or Tree `items`, `top`, `visible`, `selection`; and a CRC-32 of the control's pixels (`Control.print`, printed until two prints agree, at most four) |
| `state ()`, `pixels ()`, `visibleRange ()`, `selectionRanges ()` | the fields as `name=value` lines plus `pixels=`; the CRC (0 without size); the visible rows (M3ItemSelection); the selection ranges (a Tree's top level) |
| `diff (M3Snapshot)` | `"name: this | other"` for the first differing field (then the pixels), `null` when equal |
| `equals`, `hashCode`, `toString` | value semantics over the fields and the CRC |

## Contract (tested in `Test_M3Snapshot`, `Test_M3SnapshotDifferential`)

- Two captures of an idle control are equal; a scroll changes the visible range and the CRC and `diff` names `vbar`
  first; a selection change is named `selection`; an edit that keeps the widest row changes only the pixels.
- Lists, trees, plain controls (no item fields), hidden and disabled controls, controls of no size (CRC 0), and a
  canvas that never settles (exactly four prints) behave as specified.
- Against the official 3.135.0 jar: the snapshot fields of a Table and a List under the shared seeded operations equal
  the same fields written from the public API (pixels are compared by the widgets' own viewport differentials).

## DOOP

A snapshot is two string lanes (names, values), one long and the ranges the caller asked for; the image used for the CRC
is disposed before `capture` returns.

## Status (2026-10-31)

| gate | result |
|---|---|
| build | master `d5f6048f398`, jar `master-d5f6048` plus the class; win32, GTK and Cocoa source sets 0 errors (`-Xlint:all` clean) |
| JUnit, hidden desktop | `Test_M3Snapshot` 6/6 viewport, 6/6 original, 6/6 custom |
| differential against the official 3.135.0 jar | `Test_M3SnapshotDifferential` (shared `M3OfficialScene`, 8 seeds x 120 steps, plain and virtual): original 0 mismatches. Viewport: the List's hidden vertical bar reads maximum 100 where stock reads 0 (seed 0 step 0: `removeAll`, then `add (text, 0)`): `ListViewport.updateBar` keeps the "default bar 0..100 after LB_RESETCONTENT" state, which the official jar does not show for this sequence. The same difference is in the M3-SCROLLBAR-GEOMETRY differential; it is the List viewport's bar emulation, not M3Snapshot (open) |
| first run findings | the first print of a viewport Table could differ from the second (a deferred paint): the CRC is now taken once two prints agree (at most four) |
| coverage (JaCoCo 0.8.15, three modes) | lines 79/79; branches 56/58, the other 2 are `SWT.error` probe artifacts: every reachable branch covered |
| example | `examples/.../snippets/m3/M3SnapshotExample.java` (`exit` argument for a scripted run) |

Open: the List viewport hidden-bar emulation above; GTK and Cocoa not run.

Update (2026-10-31): the viewport differences were the Tree and List viewports' hidden-bar emulation, fixed by PR #298 (TreeViewport: rows that fit read 0) and PR #299 (ListViewport: the default bar 0..100 only with SWT.H_SCROLL, measured on the official jar). With both, the official-jar differential of this leaf has 0 mismatches in viewport and original mode (plain and virtual).
