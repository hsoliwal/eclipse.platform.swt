# Part visibility: `SWT.PartVisible` / `SWT.PartHidden` (brief rule 25)

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
