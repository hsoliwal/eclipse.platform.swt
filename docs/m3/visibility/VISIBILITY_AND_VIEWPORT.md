# Part visibility and the viewport listener: `SWT.PartVisible`, `SWT.PartHidden`, `SWT.Viewport` (brief rule 25)

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

User, 2026-10-20: "the viewport becoming visible should come from SWT or Swing", "INTRODUCE A NEW LISTENER PART
VISIBLE", "it is not a layer, it is the implementation itself". SWT itself tells a control when it becomes effectively
visible or hidden. The viewports consume the events internally: Tree, Table, List, Combo, the drawn planes and the
custom widgets. M3JDK has a parallel Swing listener with the same names. This page fixes the names. Implementation
order: win32 first, then GTK and Cocoa. All three must compile.

## API

| Name | Kind | Meaning |
|---|---|---|
| `SWT.PartVisible` = 57 | event type | the control became part-visible (see `isPartVisible`) |
| `SWT.PartHidden` = 58 | event type | the control stopped being part-visible |
| `org.eclipse.swt.events.PartVisibilityListener` | typed listener | `partVisible (PartVisibilityEvent)`, `partHidden (PartVisibilityEvent)`; static helpers `partVisibleAdapter (Consumer)` and `partHiddenAdapter (Consumer)` |
| `org.eclipse.swt.events.PartVisibilityAdapter` | adapter | empty implementations of both methods |
| `org.eclipse.swt.events.PartVisibilityEvent` | typed event | `widget`: the control that changed; `cause`: one of the constants below; `origin`: the control whose change caused the transition (the control itself, or an ancestor) |
| `PartVisibilityEvent.VISIBILITY`, `SIZE`, `MINIMIZE`, `REPARENT`, `CONTAINER` | causes (1 to 5) | `setVisible` of the control or an ancestor (this covers tab and stack-page selection and expand/collapse, because SWT's containers show and hide their pages and item controls with `setVisible`); width or height crossing zero; shell minimized or restored; `setParent`; a container's `setPartHidden` |
| `Control.addPartVisibilityListener (PartVisibilityListener)` / `removePartVisibilityListener` | methods | the typed listener, equal to `addListener (SWT.PartVisible / SWT.PartHidden, ...)` |
| `Control.isPartVisible ()` | query | see below |
| `Control.setPartHidden (boolean)` / `Control.getPartHidden ()` | container hook | for a container that hides content without `setVisible` or a zero size (a drawn collapsible pane, a drawn stack, an overlay): marks the control hidden for part visibility. Each descendant with a listener gets its transition. |

In an untyped `Event`, `detail` holds the cause and `item` holds the origin.

## `isPartVisible ()`

True when the control and every ancestor up to its shell are visible (`getVisible ()`), have a width and height
above zero, and are not marked hidden with `setPartHidden (true)`, and the shell is visible and not minimized.
SWT's containers hide non-selected tab pages (TabFolder, CTabFolder), non-top StackLayout pages and the controls of
collapsed ExpandItems with `setVisible (false)`, so selection, stacking and collapsing are all covered. The query walks
up the parent chain: O(depth), no native call beyond those of `getVisible`, `getSize` and `getMinimized`. A control
that has a listener also keeps the last state it was told about, so each transition fires only once.

Being scrolled out of a ScrolledComposite, or covered by another window, is not part of this state. The visibility
gate (`org.eclipse.swt.internal.VisibilityGate`, dirty-while-hidden and one catch-up) handles both for painting.

## When SWT fires

SWT fires after the change, from inside its implementation (hooks in the fork, applied with OpenRewrite recipes),
once per transition. It fires only to controls that have a `PartVisible` or `PartHidden` listener and whose
`isPartVisible ()` actually changed. Ancestors are told before descendants (pre-order). A no-op change fires nothing.
Dispose fires nothing. When the first listener is added, SWT records the current state and fires nothing.

| Trigger | Origin | Cause |
|---|---|---|
| `setVisible` on the control or any ancestor (shell `open`, `setVisible`, `close`) | the control whose visibility changed | VISIBILITY |
| TabFolder / CTabFolder selection, StackLayout `topControl` + `layout`, ExpandItem expand / collapse | the page or item control (shown or hidden by the container) | VISIBILITY |
| width or height of the control or an ancestor crossing zero (`setBounds`, `setSize`, layouts, native resizes) | the resized control | SIZE |
| shell minimized or restored (`setMinimized`, the user, the task bar) | the shell | MINIMIZE |
| `setParent` | the moved control | REPARENT |
| `setPartHidden` | the marked control | CONTAINER |

Cost: each control counts the listeners in its subtree. A transition walks only into children whose count is above
zero, so it costs O(listening controls) and nothing when nobody listens.

## For containers outside SWT (Nebula, custom widgets)

A container that hides its content with `setVisible (false)` or a zero size needs no code: SWT propagates the
transition down the subtree. A container that hides content in another way calls `content.setPartHidden (true)`, and
`false` again when it shows the content. A bundle that must also run on stock SWT can bind at class init by
reflection: read the `SWT.PartVisible` / `SWT.PartHidden` fields, and get `MethodHandle`s for `Control.isPartVisible`
and `Control.setPartHidden`. When they are absent, it keeps its own Hide/Show gate.

## Tests (rule 25, item 4)

`Test_org_eclipse_swt_widgets_Control_PartVisibility`: a matrix of every trigger above (`setVisible`, CTabFolder,
TabFolder, StackLayout, ExpandItem, zero size, minimize, `setParent`, `setPartHidden`) at nesting depths 0, 1, 3 and 8.
Each case checks exactly one event per transition, the cause and the origin, no event for no-op changes or for
controls without a state change, and pre-order delivery. The stock-behaviour differentials stay at zero mismatches,
because the events are additive.

## The viewport listener (`SWT.Viewport`, rule 25 sub-rule)

User, 2026-10-20: "would like to add viewport listener too?". A viewport widget (Tree, Table, List, Grid, the drawn
planes, StyledText, the custom widgets) tells its listeners which part of its flattened visible order is on screen,
once per coalesced change. The M3JDK Swing listener mirrors it with the same names and values (`javax.swing.event`).

| Name | Kind | Meaning |
|---|---|---|
| `SWT.Viewport` = 59 | event type | the window of visible rows changed |
| `org.eclipse.swt.events.ViewportListener` | typed listener (functional) | `viewportChanged (ViewportEvent)` |
| `org.eclipse.swt.events.ViewportEvent` | typed event | the fields below |
| `ViewportEvent.firstVisible`, `lastVisible` | `int` | the first and last index on screen in the flattened visible order (a Tree counts the expanded rows); both -1 when nothing is on screen |
| `ViewportEvent.entered`, `left` | `int []` | the indexes that came onto or went off the screen since the last event, as ascending, coalesced pairs `[start0, end0, start1, end1, ...]` with **inclusive** ends; empty arrays (never null) when none; no per-row objects |
| `ViewportEvent.extentsChanged` | `boolean` | the scroll extents (total rows, total size) changed |
| `ViewportEvent.cause` | `int` bit mask | `SCROLL` = 1, `RESIZE` = 2, `LAYOUT` = 4, `MODEL_DELTA` = 8, `VISIBILITY` = 16, `EXPAND` = 32 (the union of everything coalesced into the event) |
| `Scrollable.addViewportListener (ViewportListener)` / `removeViewportListener` | methods | the typed listener, equal to `addListener (SWT.Viewport, ...)` |
| `org.eclipse.swt.internal.ViewportReporter` | internal helper for widget implementations | `new ViewportReporter (scrollable)`, then `changed (first, last, cause, extentsChanged)` (or with explicit `entered` and `left` pairs for a model delta) from every place that moves the window; it coalesces and fires |

In an untyped `Event`: `start` = firstVisible, `end` = lastVisible, `detail` = cause, `count` = 1 when the extents
changed (else 0), and `data` = `int [][] {entered, left}`.

Rules:

- The widget implementation fires it, once per coalesced change: every change within one turn of the event loop
  goes into one event (the reporter posts one flush per turn, which runs after the layout and before the paint of that
  turn). The causes are OR-ed, and entered/left are the difference between the window of the last event and the
  current one, plus explicit ranges for model deltas.
- Never while the widget is not part-visible (`Control.isPartVisible ()`): changes accumulate. The first event after
  a `PartVisible` carries `VISIBILITY` and the difference from the last window reported.
- Zero cost when no listener is attached: `changed` returns after one `isListening (SWT.Viewport)` test, and no
  window is tracked.
- Nothing is sent when a listener is added; the first event comes with the next change, or with the next
  `PartVisible`.

Tests: `Test_org_eclipse_swt_internal_ViewportReporter` checks the event sequences for scroll, resize, expand, a model
delta inside and outside the window, and hide/show, with exactly one event per coalesced change.

## Defect 2026-10-24: the gate parked every virtual Tree in viewport mode (fixed)

Found while building the Tree custom-draw differential: with the jar of the integration head (50b0d51254), a
`SWT.VIRTUAL` Tree in the default viewport mode never materialized a row and its vertical bar stayed at maximum 0
(official jar: row 0, maximum 299); every Tree viewport acceptance and differential test aborted on its "mediation is
active" assumption. Bisect by build and probe: round 12 (4654d451d3) good, the 2026-10-22 head bad; the gate
(EffectiveVisibility, VisibilityGate) landed with round 13.

Cause: `EffectiveVisibility.recompute` clipped each node against the client area of `node.handle`. A Tree in viewport
mode is wrapped (`hwndParent`), and its inner TreeView window is sized by the viewport's own layout; while the viewport
is parked that window is 0 x 0. The first `TreeViewport.layout()` asked `partHidden()` before any layout, the gate saw
the zero inner window, answered OFFSCREEN, the viewport parked, and a parked viewport never lays out, so the inner window
stayed at zero and every later recompute confirmed the verdict (probe: `parked=true parks=1`,
`gate.defer(LAYOUT|DATA)=true`, `tree.handle win=201,224,201,224`, wrapper `200,223,560,463`; `-Dswt.visibility.gate=false`
restored stock answers, `-Dswt.visibility.occlusion=false` did not).

Fix: the clip a node hands down is its top handle's client area (`screenClientRect (node.topHandle (), clip)`): the
wrapper is what an ancestor clips; the inner window is the control's own. Only the Tree consulted the gate for parking
(`VisibilityGate.defer`); the List viewport uses `EffectiveVisibility.hidden` without a wrapper and was not affected;
the Table viewport does not park.
