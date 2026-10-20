# Split panels: SashForm done right (BEHAVIOURS.md "Split panels", rule 25)

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

User, 2026-10-20: "SashForm is a pain". `org.eclipse.swt.custom.SashForm` keeps its API and its behaviour. With
none of the options below set, its answers and layouts are those of stock SWT, which the differential gate checks.
The additions live inside SashForm and SashFormLayout. The Swing track does the same for `JSplitPane`; the
multi-split widget (rows and columns in one control) belongs to custom-widgets. Donors are listed in
[FOSS_REUSE_DECISION.tsv](FOSS_REUSE_DECISION.tsv): VS Code splitview and allotment (MIT), react-resizable-panels
(MIT) and IntelliJ Splitter (Apache-2.0) are adapted at the pattern level; SwingX `MultiSplitLayout` (LGPL) is a
pattern only.

## API (published before the code; all new methods `@since 3.136`)

| Method | Meaning |
|---|---|
| `setMinimumSizes (int unit, int... sizes)`, `getMinimumSizes ()` | The smallest size of each pane, in `SashForm.POINTS` or `SashForm.PERCENT` of the area; 0 means none. Drags, resizes and `setWeights` stop at it. When the panes cannot all fit, the panes are shrunk in priority order (last first). |
| `setMaximumSizes (int unit, int... sizes)`, `getMaximumSizes ()` | The largest size of each pane; 0 means none. The space a pane cannot take goes to its neighbours. |
| `setCollapsible (int index, boolean)`, `getCollapsible (int)` | Whether a pane may collapse to nothing. Dragging a sash past half of a collapsible pane's minimum collapses it (allotment's snap). |
| `setCollapsed (int index, boolean)`, `getCollapsed (int)` | Collapses a pane to size 0, keeping its sash, or restores it to the size it had before. The size is remembered as the pane's weight. |
| `setMaximizedControl (Control)` | Stock. A restore (`null`) brings back the remembered weights, as stock already does. |
| `setSnapPositions (int... percents)`, `setSnapDistance (int points)` | Sash positions, in percent of the area, that a drag snaps to within the snap distance (default 8 points). |
| `setKeyboardStep (int points)` | With a step above 0, the sashes take the focus (Tab traversal). The arrow keys move a sash by the step, Shift+arrow by 1 point, Home and End to the limits, and Enter collapses or restores the collapsible neighbour. With 0 (the default) the sashes keep the stock behaviour. |
| `setDoubleClickRestore (boolean)` | A double-click on a sash restores a collapsed neighbour, or else the sizes before the last drag. |
| `setDragMode (int)`, `getDragMode ()` | `SashForm.LIVE`: the two neighbours of the sash re-lay out at each drag step. `SashForm.GHOST`: the sash is drawn as a ghost and the panes lay out once on release. The default follows the stock style: LIVE with `SWT.SMOOTH`, GHOST without it. |
| `getMemento ()`, `setMemento (String)` | The whole split tree as one string: orientation, weights, collapsed panes, the maximized pane, and the same recursively for SashForm children. `setMemento` applies only what matches the current children. |

A drag is a viewport delta (rule 24). Only the sash and its two neighbours get new bounds, which is already the stock
drag: `onDragSash` sets the bounds of the two controls and the sash. Nothing else is laid out, and the limits, snaps
and collapses act on those two panes only.

## Gates

- SashForm differential, `Test_org_eclipse_swt_custom_SashForm_Differential`: with no new option set, every stock
  answer (`getWeights`, `getMaximizedControl`, `getOrientation`, the bounds of the controls and the sashes) after a
  seeded sequence of operations (`setWeights`, resizes, drags, adding and disposing children, maximize, orientation)
  equals the official SWT 3.135.0 recording.
- Drag sweep, `Test_org_eclipse_swt_custom_SashForm_DragSweep`: 50 drag steps over a SashForm of 8 panes, counting
  the panes that get new bounds and the layouts (Resize events of the panes) per step. LIVE: at most 2. GHOST: 0
  during the drag and 2 on release.
- Feature tests per option: limits during drag and resize, collapse and restore, keyboard, snap, double-click, the
  memento round trip on a nested tree.

## Leaves

SF1 limits, SF2 collapse and double-click, SF3 keyboard and snaps, SF4 memento, SF5 drag mode and the gates. Each
leaf is committed with its tests and is applied to SashForm by an OpenRewrite recipe (rule 20).
