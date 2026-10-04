# SWT viewport toolkit distillation

This note records the donor categories used to distill the viewport rewrite. The
goal is not to copy toolkit implementations. The goal is to preserve SWT public
contracts while moving repeated rendering, scrolling, selection, expansion and
event mechanics into compact internal owners.

## Java2s source catalogues

- SWT: https://www.java2s.com/Tutorial/Java/0280__SWT/Catalog0280__SWT.html
- SWT 2D Graphics: https://www.java2s.com/Tutorial/Java/0300__SWT-2D-Graphics/Catalog0300__SWT-2D-Graphics.html
- Swing: https://www.java2s.com/Tutorial/Java/0240__Swing/Catalog0240__Swing.html
- Swing Events: https://www.java2s.com/Tutorial/Java/0260__Swing-Event/Catalog0260__Swing-Event.html

These catalogues are used as behavioural/example indexes only. Their source is
not copied into SWT.

## Distilled owners

| Donor concern | SWT viewport owner |
| --- | --- |
| ScrolledComposite / JScrollPane / JViewport | `VirtualViewportPlanner`, `VirtualTreeViewport`, `ViewportScrollLayout` |
| Table / Tree / JTable / JTree logical models | `VirtualItemStorage`, `VirtualTreeTopology`, `VirtualTreeVisibleProjection` |
| Table/tree selection examples | `VirtualSelectionModel`, packed state lanes |
| Headers / JTableHeader / layered panes | `ViewportLayerState` |
| GC transforms / Swing affine graphics | `ViewportAffineTransform` |
| Paint, clipping, paths and repaint dependencies | `ViewportPaintGraph` |
| Reusable stroke/fill/text/image paint atoms | `ViewportRenderCommandBuffer` |
| Renderers/editors | transient editor/chrome planes; not retained as one widget per logical row |
| Mouse/event coordinate conversion | logical-to-device transform inversion at viewport/event boundaries |
| Progress / timers / animation | bounded invalidation and scheduled viewport updates; never eager whole-model repaint |

## Render model

The public SWT `GC` remains immediate mode and native-resource backed.
Viewport internals do not retain a public `GC` and do not replay application
paint callbacks.

The retained internal model is split deliberately:

1. **`ViewportPaintGraph`** — bounds, z-plane, dirty state and forward
   dependencies.
2. **`ViewportRenderCommandBuffer`** — primitive opcodes for SWT-owned
   reusable paint atoms.
3. **`ViewportAffineTransform`** — cheap logical/device transforms that do
   not require a `Device` or native `Transform`.
4. **short-lived real GC replay adapter** — resolves resource IDs and executes
   only commands belonging to dirty visible/overscan nodes.

This is preferable to a long-lived proxy GC because public GC lifetime,
clipping, transforms, disposal, native handles and application callbacks remain
observable SWT contracts.

## Z-plane rule

Body, frozen content, headers, editors, scrollbars and feedback are independent
dirty planes. Vertical body scroll must not dirty the column header plane.
Horizontal body scroll dirties normal headers but not frozen columns.

## Event rule

Events are coordinate/model operations first. A pointer event is mapped from
device coordinates through the inverse viewport transform to a logical row,
column and subpart. Only API-visible interactions materialize/pin an SWT item
facade.

## Regression catalogue

Keep representative examples for:

- very large virtual Table and Tree;
- selection/check/gray state across coordinate shifts;
- expand/collapse to level and selection-scoped expansion;
- ScrolledComposite logical-origin scrolling;
- owner draw and clipping;
- transform/path/stroke/text/image commands;
- header/body z-plane invalidation;
- mouse/key/focus/DND event ordering;
- accessibility and editor identity;
- screenshot/WidgetSpy comparison.

The examples should include normal-size correctness cases and deliberately huge
logical extents so accidental O(logical-size) allocation or repaint is visible.
