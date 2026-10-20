# M3 viewport programme: documentation index

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

## What this is

Every SWT and Nebula widget, and its Swing, JFace, GEF and Initiative 31 counterparts, becomes a **mediated viewport**:
- the public contract and the visible behaviour stay exactly those of the stock toolkit;
- the native control (or drawn plane) holds only the **resident window** of rows;
- the model lives in data-oriented lanes: arrays, offsets and bit masks, with lazy facades.

Native work is O(window); model work runs off the UI thread with generation checks.

Five startup modes are chosen with `-Dswt.widget.mode=viewport|original|custom|m3|gpu`, plus a per-class override `-Dswt.widget.mode.<Widget>`:
- `viewport` is the default;
- `original` is stock SWT;
- `custom`, `m3` and `gpu` are the drawn modes.

## Start here

| Topic | Document |
|---|---|
| Programme plan (state, tracks, PRs, decisions) | [VIEWPORT_PROGRAMME_PLAN.md](VIEWPORT_PROGRAMME_PLAN.md) |
| Rules every contributor follows (1-27) | [VIEWPORT_AGENT_BRIEF.md](VIEWPORT_AGENT_BRIEF.md) |
| Optimization pass (measure first, ledger per track) | [OPTIMIZATION_PASS.md](OPTIMIZATION_PASS.md) |
| How behaviour is proven equal to stock | [VIEWPORT_DIFFERENTIAL_HARNESS.md](VIEWPORT_DIFFERENTIAL_HARNESS.md) |
| Object residency (lazy, parent-owned facades) | [M3_ITEM_RESIDENCY.md](M3_ITEM_RESIDENCY.md) |

## Widgets

| Widget | Design | Status (see the plan for numbers) |
|---|---|---|
| Tree | [TREE_MEDIATED_VIEWPORT.md](TREE_MEDIATED_VIEWPORT.md), [tree-viewport/](tree-viewport/) | Identical to official SWT 3.135.0 on all differential variants |
| Table | [TABLE_MEDIATED_VIEWPORT.md](TABLE_MEDIATED_VIEWPORT.md), [table-viewport/](table-viewport/) | Identical to official on 88/88 seeds; 100% line coverage |
| List, Combo | [LIST_MEDIATED_VIEWPORT.md](LIST_MEDIATED_VIEWPORT.md), [list-viewport/](list-viewport/) | Differentials pass; coverage and remaining edge cases in progress |
| Scrollables, ToolBar, Text, ExpandBar, CTabFolder | [ALL_WIDGETS_VIEWPORT_PLAN.md](ALL_WIDGETS_VIEWPORT_PLAN.md) and the per-widget docs | Per-widget docs |
| StyledText | [STYLEDTEXT_VIEWPORT_DESIGN.md](STYLEDTEXT_VIEWPORT_DESIGN.md) and related | Per-widget docs |

## Cross-cutting

| Topic | Document |
|---|---|
| Drawn modes (custom / m3 / gpu), CPU-GPU interaction model | [RENDER_MODES.md](RENDER_MODES.md), [render-modes/](render-modes/) |
| Look and feel, themes, LLM theme generator | [LOOK_AND_FEEL.md](LOOK_AND_FEEL.md), [look-and-feel/](look-and-feel/) |
| Enhanced (additive, opt-in) API, SWT and Swing | [ENHANCED_API.md](ENHANCED_API.md), [ENHANCED_API_MAP.tsv](ENHANCED_API_MAP.tsv), [enhanced-api/](enhanced-api/) |
| Multi-column sort, column filters, debounced model-side filtering | [sort-filter/](sort-filter/) |
| Attribution and licences | [attribution/](attribution/) |
| Naming (M3, never Skija/Skia) | [naming/](naming/) |

## Beyond SWT (other repositories)

| Track | Where |
|---|---|
| Nebula Grid and other Nebula widgets | hsoliwal/nebula: `docs/m3/grid-viewport/`, `docs/m3/custom-widgets/` |
| JFace viewers and e4 part stacks | hsoliwal/eclipse.platform.ui: `docs/m3/jface-viewport/` |
| GEF classic (Draw2d, GEF, Zest) | hsoliwal/gef-classic: `docs/m3/gef-viewport/` |
| Initiative 31 (SWT on Swing) | this repository, branch `m3/initiative31-integration-20261016`: `docs/m3/initiative31-viewport/` |
| M3JDK Swing (JTree, JList, JTable) and the shared viewport core and collection views | hsoliwal/M3jdk21: `m3/docs/swing-viewport/`, `m3/docs/viewport-everywhere/`, `m3/docs/swing-enhanced-api/` |
| Collections superset (JDK, Guava, Apache, Eclipse, Spring) | hsoliwal/M3jdk21: `m3/collections/qualification/` |
| Regex over M3 strings, token regex | hsoliwal/M3jdk21: `m3/docs/regex-cursor/` |
| Donor patterns (76 donors) | hsoliwal/com.synexia: `docs/M3-SCALE/runs/20261017-ui-donor-atomize/` |

## Examples

Runnable snippets live under `examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3/`. Each track lists its own in an `EXAMPLES.md` next to its design document.

## Conventions

- **Proof:** a widget change is done when the recorded differential against the official jar shows zero mismatches and JaCoCo line and branch coverage reach 99%.
- **Code:**
  - Java only (M3JDK may also change JNI and HotSpot);
  - logging through java.util.logging;
  - hooks into upstream files only through OpenRewrite recipes, whose second pass must report UNCHANGED.
- **Files:** new files carry the Synexia header; modified upstream files get a contributor line.
