# Native SWT layout and viewport geometry contract

**18 November 2026 — draft native-backed viewport integration (not native release proof)**

SWT already owns the OS widgets. Do **not** replace them with per-entry
Java objects, composite wrapper canvases, a second layout toolkit, or a
paint-only simulation of native client geometry.

## The invariant

Physical state is owned by the operating system:

1. Window/control **outer** rectangle, **non-client border** and **client**
   rectangle are distinct; changing an inset/outset/etched border can alter
   the client rectangle while the outer rectangle stays unchanged.
2. The native client area is the authority for child control placement,
   visibility, clip, hit-testing, retained pixels, and the submitted Maya
   native-layer frame.
3. Native resource access remains on the toolkit's UI thread; an asynchronous
   CPU/DOOP worker may compute an immutable versioned layout snapshot, but
   cannot invoke native setBounds, GTK style allocation, AppKit NSView, UIKit
   layout, or DirectComposition APIs itself.
4. Same-size border appearance changes trigger a native *frame repaint*, not
   an unnecessary content layout or a second native owner.
5. Stale snapshots may be discarded before native apply. A reentrant native
   callback may enqueue another geometry pass; it must not recursively mutate
   an active composition submission array.
6. The original SWT Resize/Move/focus/selection/accessibility, constructor
   style bits, control handles, and public source/binary API are preserved.

## Implemented in this PR: original LayeredViewport owner

The original `org.eclipse.swt.custom.LayeredViewport` now observes the
platform's current `getClientArea()` after `setBorderRelief` as well as on
`SWT.Resize`. It stores the last four native client rectangle integers in
one parent-owned primitive array and only recomposes when those values
actually change.

When a native border causes a changed client rectangle, the same SWT
UI thread performs:

- invalidate its retained backing and spatial clipping;
- place and cull original child `Control` widgets using the live client
  rectangle; they retain their own HWND/GtkWidget/NSView/mobile peer;
- submit one refreshed native `NativeLayers.apply` scene for Maya-backed
  image layers (subject to the viewport's existing `beginUpdate` batching).

A second equal client rectangle is a no-op. Nested SWT listeners and
native callbacks are guarded; ordinary reentrant changes are drained in a
bounded pass and further changes coalesce to a later UI-thread callback.
No second viewport, thread, JNI method or renderer is introduced.

The original `Test_org_eclipse_swt_custom_LayeredViewport` suite now has a
border/client-area regression covering original native child identity,
native-layer visibility and client clipping, no-op transitions, and reset.

## Already implemented native facilities: use, do not duplicate

| Platform | Native ownership | Existing / safe integration |
| --- | --- | --- |
| Win32 | Existing HWND, GDI nonclient frame and SWT `Composite` | `SetWindowPos(SWP_FRAMECHANGED)` recalculates the client area. SWT already binds `BeginDeferWindowPos`, `DeferWindowPos`, `EndDeferWindowPos` in `Composite.resizeChildren`. Reuse that batch for future explicit multi-child layout APIs; **do not** call per-child JNI from CPU workers or pretend `LayoutedViewport.beginUpdate` already batches HWND movements. |
| GTK3/GTK4 | Original GtkWidget and native layout/measure pass | Existing CSS provider and `gtk_widget_queue_resize` re-negotiate size. GTK4's `gtk_widget_queue_allocate` is appropriate *inside native widget implementations* when only child positioning changes; avoid calling resize/allocate inside an active GTK size-allocate callback. |
| AppKit Cocoa | Original NSView / NSScrollView and its native bezel | Keep NSView sizing and `needsLayout` / `layoutSubtreeIfNeeded` on the AppKit thread. Support only actual native NSScrollView bevels admitted by the owner. Do not claim arbitrary Cocoa Canvas nonclient bevel support. |
| Android | Original `AndroidPlatform.PeerView` | Existing `ViewGroup.requestLayout`, onMeasure/onLayout, display-density-aware client padding and border drawing own physical geometry. Do not add a second Android View for a border. |
| UIKit | Original `SWTUIKitView` | Existing `layoutSubviews` updates clientView/editor bounds and CoreGraphics draws two-tone border. Preserve snapshots and touch offsets. |
| Maya / DirectComposition | Original NativeLayers / Maya composition | Submit native visual property frames from the UI thread, keep `beginUpdate/endUpdate` property coalescing, retain original native HWND clip. Do **not** claim that the current four-float native layer property frame supports arbitrary zoom/rotate/region clips — those need separate versioned ABI and native tests. |

## Follow-up requirements, not implemented by this change

A platform-neutral **immutable geometry snapshot** may contain:
`generation, clientX/Y/W/H, geometry[4*n], changedMask[n], visibleMask[n]`,
overscan bounds and per-layer transform/clip descriptors. CPU workers may
derive the array-backed snapshot; the original UI/native owner checks its
generation then applies it. Preserve the original SWT per-control event
ordering and synchronous getters at public API boundaries.

For Win32, a reviewed internal transaction can collect child HWND geometry
and use the already-bound `BeginDeferWindowPos/DeferWindowPos/EndDeferWindowPos`.
That function sends native `WM_WINDOWPOSCHANGING/CHANGED` messages at commit;
the transaction must preserve these, SWT Resize/Move order, mirroring, DPI,
focus, Z order, control disposal/reentrancy, foreign child HWND fallbacks and
partial allocation failure. The current `LayeredViewport` calls `setBounds`
individually, so **native batch HWND positioning is not yet implemented** in
this patch.

GTK4 allocation, Cocoa Auto Layout, Android ViewGroup and UIKit sibling
positioning need separate original-owner receivers and complete native
runtime qualification; do not mix those with a Win32-only HWND transaction.

## Acceptance gates

- Java 21/25 original SWT code, Tycho/PDE, and original LayeredViewport JUnit.
- Native `getClientArea` and `computeTrim` symmetry at 100/125/150/200% DPI,
  non-client updates without outer SWT.Resize, zero-size and fractional zoom.
- Native child/control visibility and scrollbars, hit tests, native Z order,
  keyboard focus, accessibility, RTL and overscan after border transitions.
- Maya real JNI/native frame + clipped pixels, backdrop restoration and
  teardown under nested native callbacks, with combined pinned native tests.
- GTK3/GTK4 Wayland/X11, native Win32 HWND/DirectComposition, Cocoa AppKit,
  Android device and UIKit simulator screenshots; generated JNI/ABI closure.
- Exact source-pinned OpenRewrite fixed point and source drift refusal; no
  unqualified PR should be merged solely on source readback.

The border/client geometry synchronization is a bounded original-owner first
step, **not** full native layout-engine replacement or proof that all
toolkits support identical layout and viewport semantics.
