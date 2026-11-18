<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT viewport CPU / UI ownership boundary

Date: 2026-11-18. Receiver: `hsoliwal/eclipse.platform.swt`, existing `Synchronizer`
and `DispatchQueue`. Toolkit-neutral producer: `jdk.m3.viewport.M3ViewportScheduler`
from `hsoliwal/M3jdk21/m3/viewport-core`.

## One event loop, two execution domains

* **CPU worker:** immutable input-model snapshots, packed row arrays, filtering,
  sorting, projection, sparse geometry, overscan, diff plans and cancellation.
  Do not capture `Control`, `Widget`, `GC`, `Image`, `Display` or a native
  handle in a CPU computer callback.
* **SWT UI thread:** capture event/viewport bounds and relevant detached model
  input; schedule CPU work; install the latest published snapshot, retain native
  widget/facade identity, update accessibility, and schedule painting.
* **Transfer:** the existing `Synchronizer.MODEL` queue, coalescing by a
  per-widget stable key. The queue handles only short publication callbacks.
  `syncExec` is not a viewport compute interface.

## Connecting the toolkit-neutral core

This is **adapter pseudocode** for an application that has M3 viewport-core
on its classpath; SWT itself does *not* gain a hard module dependency.
Capture the synchronizer on the SWT thread during widget initialization.

```java
Synchronizer synchronizer = display.getSynchronizer(); // SWT thread
Object key = new Object(); // per widget; never use one global key

M3ViewportScheduler<DetachedRequest, M3ViewportSnapshot> scheduler =
    new M3ViewportScheduler<>(
        cpuPool,
        uiRunnable -> synchronizer.asyncExec(Synchronizer.MODEL, key, uiRunnable),
        (request, cancellation) -> computeFromDetachedLanes(request, cancellation),
        (generation, snapshot) -> {
            if (control.isDisposed()) return;
            if (snapshot.modelVersion() != currentModelVersion) return;
            installResidentRowsAndGeometry(snapshot); // UI thread only
            control.redraw();
        });

// SWT wheel/resize/sort/model-change callback, while on UI thread:
scheduler.request(captureDetachedRequest());

// SWT Dispose callback:
scheduler.close(); // do not dispose application-owned cpuPool here
```

The computation is only as safe as `DetachedRequest`: it must not hold mutable
SWT model state or a live UI widget. Model/version changes invalidate old
snapshots; the scheduler generation independently invalidates outdated viewport
coordinates. A disposed widget must not accept later publication.

## Existing architecture retained

* `VirtualViewportPlanner` and `ViewportRuntime.RowWindow` continue to own
  logical range, directional overscan, clipping and generation.
* `TreeViewport` and `TableViewport` remain native Win32 owners. No new
  native items, row objects, event loop or alternative display is created.
* `LayeredViewport` remains the SWT-owned presentation object; native
  `GC`, `Image` and widget compositing stay on its UI/render owner.
* M3 core contains only primitive/data structures and scheduling contracts.
  A separate Swing adapter supplies `EventQueue.invokeLater`, not SWT code.

## This correctness leaf

`DispatchQueue` now scopes keyed coalescing to **(priority, key)**, preserves
frame requests made during a model-patch callback, and wakes queued `syncExec`
callers when disposal clears their pending messages. These repairs do not
replace the public SWT API or require an additional dispatch thread.

## Verification and follow-up gates

`Test_org_eclipse_swt_widgets_Synchronizer` now covers the priority-key,
re-requested-frame and disposal/waiter cases. SWT product/native UI suites
still need to run on Win32, GTK and Cocoa before this branch can be merged.

Before enabling CPU scheduling inside each concrete widget, qualify:
1. a captured immutable model-input/version, without reading SWT objects from workers;
2. stable item and selection identity across a stale result and native reorder;
3. burst scrolling and resize backpressure; native disposal during work;
4. accessibility, paint, click and focus reentrancy;
5. existing SWT `syncExec`, default FIFO `asyncExec`, Workbench
   `UISynchronizer` and platform event-loop compatibility.

No actual Tree/Table/LayeredViewport CPU-worker rewiring is claimed by this
leaf; it supplies the shared scheduler and correct SWT publication transport.
