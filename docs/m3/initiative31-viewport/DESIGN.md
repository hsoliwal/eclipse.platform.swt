<!--
Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
-->
# Initiative 31 viewport (SWT on Swing): design

Status: design (D2C) for the Tree, Table and List viewport of the Initiative 31 SWT-on-Swing prototype.
Branch `m3/initiative31-swing-viewport-20261019` of hsoliwal/eclipse.platform.swt.
Everything marked "measured" was run on 2026-10-19 on the hidden desktop; the outputs are in `evidence/`.

## 0. Which Initiative 31

Initiative 31 (github.com/swt-initiative31) has two SWT reimplementations we work on:

| Line | Upstream | Fork branch | What Tree/Table/List are |
|---|---|---|---|
| prototype-skija | `swt-initiative31/prototype-skija` master `146eee2a23` | `initiative31/master` (integration ledger `docs/m3/INITIATIVE31_INTEGRATION_20261016.md`, PR #194 merged) | custom-drawn on Skija (`CustomComposite`, `Eclipse SWT/common`) |
| prototype-swing | `swt-initiative31/prototype-swing` branch `swing` `354887cc2c` (EPL-2.0, an import of Chrriis/SWTSwing) | `initiative31/prototype-swing/swing` (same commit) | Swing components (`Eclipse SWT/swing`, `Eclipse SWT PI/swing`) |

This track gives the **SWT-on-Swing** line its viewport. Decision D0: the branch starts at
`initiative31/prototype-swing/swing` and its pull request targets that branch. `initiative31/master` carries the
prototype-skija history (a different tree since 2024-11), so a pull request from this branch onto it would carry
unrelated history. The prototype-skija line already has its viewport work (PRs #172, #177-#180).

Fetches used explicit refspecs:
`+refs/heads/swing:refs/remotes/i31-swing/swing`, `+refs/heads/master:refs/remotes/i31-swing/master` (upstream) and
`+refs/heads/initiative31/prototype-swing/swing`, `+refs/heads/initiative31/master` (fork).

Build: `tools/compile_swing.sh` compiles the source set of `binaries/.classpath_swing` (440 sources, 0 errors at
`354887cc2c`, JDK 21, `--release 21`). The prototype needs no native library.

## 1. Inventory: how Initiative 31 implements Tree, Table and List

| Widget | Swing component | Model | Items | Renderer | SWT.VIRTUAL / SetData |
|---|---|---|---|---|---|
| Tree | `CTreeImplementation extends JScrollPane` holding `JTreeTable` (a `JTable` whose column 0 paints an inner `JTree`) | `DefaultTreeModel` over `DefaultMutableTreeTableNode`; the root is an anonymous subclass whose `insert` fires `nodesWereInserted` and expands the root | every `TreeItem` owns a `CTreeItemImplementation` node (one `TreeItemObject` per column); `Tree.itemList` / `TreeItem.itemList` are `ArrayList`s | `DefaultTreeTableCellRenderer` (a `JLabel`), plus a check box panel for `SWT.CHECK`; Erase/Paint item events through `CellPaintEvent` | `setItemCount` creates every `TreeItem` eagerly; `TreeItemObject.toString` calls `getText`, which sends SetData |
| Table | `CTableImplementation extends JScrollPane` holding a `JTable` | `CTableModel extends AbstractTableModel`; row count kept in `rowCount`, one `fireTableRowsInserted` per row | `Table.itemList` with `null` slots when virtual; `_getItem` creates a `TableItem` on demand | `JTable` renderer per cell | already lazy: SetData only for painted rows |
| List | `CListImplementation` holding a `JList` | `DefaultListModel` of strings | strings, no items | `JList` default renderer | not virtual (SWT List has no VIRTUAL) |

Common: the scroll position belongs to the `JScrollPane`'s `JViewport`; `getTopIndex` is answered by
`rowAtPoint` at the scroll pane's (0, 0), which lies on the border, not on the first visible row.
Look and feel: `Utils.installDefaultLookAndFeel` installs `-Dswt.swing.laf=<class>` or else the system look and feel.

## 2. Measured behaviour against official SWT 3.135.0

Same program (`harness/m3/i31/harness/TreeOracle.java`, `TableListOracle.java`, public API only), 320x240 shell.

| Case | Initiative 31 (swing) | Official SWT 3.135.0 (win32) |
|---|---|---|
| Tree VIRTUAL `setItemCount(10000)` | 1,511 ms, **10,000 SetData during the call** | 17,768 ms (native), 0 SetData |
| Tree after `open` | 10,000 SetData (all rows) | 12 SetData (visible rows) |
| Tree `getTopItem()` at the top | **null** | item 0 |
| Tree `setTopItem(item 5000)` | top = 4988 | top = 5000 |
| Tree `setItemCount(0)` | **item count stays 10,000** | 0 |
| Table VIRTUAL `setItemCount(10000)` | 5 ms, 0 SetData; 13 after open | 7 ms, 0 SetData; 11 after open |
| Table `setTopIndex(5000)` | top = 4999 | top = 5000 |
| Table `showSelection` (row 3, from the end) | top = 2 | top = 3 |
| List `setTopIndex(5000)` | **top = 0** | top = 5000 |
| List `showSelection` (last row) | **top = 0** | top = 9987 |
| List `setItems(10000)` | 5 ms | 5,896 ms |

These deviations are rows of `UPSTREAM_DEFECTS.tsv`. Visible-row counts differ between the two (different row
heights and borders); they are geometry, not defects.

## 3. The viewport contract for SWT on Swing

What "viewport" means here, taken from the win32 Tree viewport (`docs/m3/TREE_MEDIATED_VIEWPORT.md`, PR #196):

- **V1 Residency.** A virtual widget holds only the items an API call or the screen asked for. `setItemCount(n)`
  is O(1) in objects: no `TreeItem`, no Swing node and no model event per row.
- **V2 SetData on demand.** SetData is sent for an item when it is painted or when the API reads it, never for the
  whole list (official SWT behaviour).
- **V3 Bounded native work.** The Swing component sees one model event per bulk operation; Swing's own row math
  (`FixedHeightLayoutCache` for the inner `JTree`, `JTable` row heights, `JList` cell heights) only touches rows on
  screen.
- **V4 Scroll contract.** `getTopIndex`/`getTopItem` answer the first row shown in the viewport;
  `setTopIndex`/`setTopItem` put that row at the top, clamped to the scrollable range; `showItem`/`showSelection`
  scroll the least (none when visible). Same arithmetic for the three widgets.
- **V5 Same answers.** Every other public answer (counts, texts, indices, selection, events other than SetData)
  equals the original mode.

## 4. Where each piece comes from (consolidation)

Initiative 31 cannot depend on M3JDK at runtime, so shared pieces are copied, byte for byte except the package
line, with the source pinned in `PROVENANCE.tsv`; `FOSS_REUSE_DECISION.tsv` records every candidate.

| Capability | Owner | How it is consumed |
|---|---|---|
| Scroll geometry (V4): row at offset, offset of row, reveal offset (START/AUTO), clamp | **M3JDK `com.m3.collections.M3SparseExtents`** (hsoliwal/M3jdk21 `m3/viewport-everywhere-20261018` `10602ec170`, Apache-2.0; itself derived from SWT `VirtualRowExtents`) | copy in `org.eclipse.swt.internal.m3`, package changed by the recipe `ChangePackage`; one `M3SwingScroll` adapter serves Tree, Table and List |
| Sparse residency of item facades (V1) | **SWT viewport `VirtualItemStorage`** (fork `m3/viewport-integration-20261016` `a3c8bb4bdb`, EPL-2.0) | byte-identical copy into `Eclipse SWT/swing/org/eclipse/swt/widgets` (same package) |
| Mode switch and logging | `org.eclipse.swt.internal.WidgetMode`, `SwtLog` (`C:/m3-superset/tools/*.java.txt`) | byte-identical copies, as on every viewport branch |
| Tree row math (expanded subtrees to rows) | the JDK's `JTree` `FixedHeightLayoutCache` (large model) | used through Swing. On M3JDK with `-Dm3.swing.viewport=true`, `BasicTreeUI` installs the DOOP `M3FixedHeightLayoutCache` by itself; nothing is copied (GPLv2+CPE, `sun.*` internals) |
| List/Table row offsets | the JDK's `JList`/`JTable`; on M3JDK the `M3RowOffsets` hook in `BasicListUI` | through Swing, same as above |
| Planner, window and flattening views (`M3WindowView`, `M3FlatteningView`, `M3ListDiff`/`M3Patch`, `M3BoundedCache`) | M3JDK, **planned, not yet in any commit** (`m3/collections/recipe/viewport-everywhere-20261018/CLAIMED.tsv` rows 35-39) | not consumed; `VirtualItemStorage` is replaced by `M3BoundedCache` by recipe once it ships |
| Order-statistic sibling index (`M3OrderStatisticForest`) | M3JDK | not needed: Swing's layout cache does the row math, and sibling slots are a sorted sparse lane |
| Selection lanes (`M3RangeSet`) | M3JDK | later leaf (`selectAll` on a huge virtual tree still builds Swing `TreePath`s) |
| Look and feel, renderers, `JTreeTable`, `CTree`/`CTable`/`CList` | **Initiative 31** | unchanged; the viewport mode adds no paint code |

## 5. Mode switch

`static final boolean ENABLED = WidgetMode.of ("<Widget>", WidgetMode.VIEWPORT) == WidgetMode.VIEWPORT;` in each
mediator. `-Dswt.widget.mode=original` (or `.Tree`, `.Table`, `.List`) gives stock Initiative 31 behaviour,
unchanged, including its defects (brief rule 12). The viewport is the default.

## 6. Tree (leaf 1)

Lanes and facades:
- one `M3TreeChildren` per parent that has children (the tree, or a `TreeItem` the API or the screen reached):
  the child count (int) and a `VirtualItemStorage<TreeItem>` of the materialized children. It extends
  `java.util.Vector<TreeNode>`, and the parent's Swing node uses it as its `children` vector, so `JTree`,
  `DefaultTreeModel` and `DefaultMutableTreeNode` read counts and children from the lane.
  Reading child `i` that is not materialized creates its `TreeItem` facade (and its Swing node) at slot `i`
  without any model event: that is the only place a virtual item is created.
- `M3TreeViewport` (one per `Tree`) installs the lanes, sets the inner `JTree` to a large model with a fixed row
  height (so `JTree` keeps state only for expanded nodes), and answers the delegated public API.

Hooks (recipe `recipe/META-INF/rewrite/m3-swt-i31-tree-viewport.yml`, applied twice, second pass UNCHANGED):
`Tree`: `createHandle` (install), `createItem` (2 overloads: adopt/insert), `releaseItem`, `setItemCount`
(3-argument worker), `getItem(int)`, `getItemCount`, `getItems`, `indexOf(TreeItem)`, `removeAll`, `clearAll`,
`releaseChildren`, `createItem(TreeColumn,int)`/`destroyItem(TreeColumn)` (resident rows only), `getTopItem`,
`setTopItem`, `showItem`; `TreeItem`: `getItem(int)`, `getItemCount`, `getItems`, `indexOf`, `removeAll`,
`clearAll`; `DefaultMutableTreeTableNode`: one setter for the `children` vector; `JTreeTable`: one accessor for
the inner `JTree`. Each hook is one delegation `if (viewport != null) ...`; the logic lives in `M3TreeViewport`.

## 7. Table (leaf 2) and List (leaf 3)

Table items are already lazy in Initiative 31. The Table leaf adds V3 (one `fireTableRowsInserted`/`Deleted` per
`setItemCount` instead of one per row) and V4 through `M3SwingScroll`. The List leaf adds V4
(`setTopIndex`, `getTopIndex`, `showSelection`) through the same adapter. Both keep the `JTable`/`JList`
untouched otherwise.

## 8. Proof

- **Recorded differential** (`harness/m3/i31/harness/*Differential.java`, public API only): seeded random scripts;
  each step records answer | structure | selection | events (except SetData) | scroll | SetData count. The script
  runs in one JVM per mode; `original` is the reference. The comparer requires equality of every column except
  the divergences listed in `UPSTREAM_DEFECTS.tsv` (column `accepted_in_differential`); for those it checks the
  viewport side against the official rule instead (for example `getTopItem` after `setTopItem(i)` is `i` while
  `i <= count - visibleRows`).
- **Against the official jar**: the oracle and a short script run on `org.eclipse.swt.win32.win32.x86_64-3.135.0.jar`
  to confirm each defect row.
- **Coverage**: JaCoCo agent on the differential and unit tests, report under `coverage/`; target 99% line and
  branch for every added or changed class, unreachable lines listed with a reason.
- Every UI run goes through `ui_lock.sh` + `hidden_java.sh`; every build through `build_lock.sh`; output under
  `S:\m3-scratch\initiative31` only.

## 9. Look and feel

The viewport mode adds no paint code and installs no look and feel. Themes for the differential:
`native` = the system look and feel (the Initiative 31 default), `swing` = Metal (`-Dswt.swing.laf=javax.swing.plaf.metal.MetalLookAndFeel`),
`initiative31` = whatever `-Dswt.swing.laf` the application chooses (Initiative 31's own switch). The
differential runs under `native` and `swing`.
