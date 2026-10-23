# The SWT Synchronizer as a dispatcher (brief rule 28)

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

User, 2026-10-20: "the SWT synchronizer too", "just like Swing" (with "CPU vs GPU thread separation must exist").
`org.eclipse.swt.widgets.Synchronizer` keeps its API and its stock behaviour. Its queue is rebuilt inside SWT
(rule 25: the implementation itself, not a layer) as `DispatchQueue`, a dispatcher in the manner of Swing's
`EventQueue` and `RepaintManager`.

## Before

A `ConcurrentLinkedQueue<RunnableLock>`. Every `asyncExec` allocated a `RunnableLock` and a queue node, and every
`syncExec` allocated its own lock. The queue was one first-in-first-out list, with no coalescing, no priorities and
no frames.

## The dispatcher

| Part | Behaviour |
|---|---|
| Rings | One growable ring of slots per priority class (parallel arrays for the runnable, the sync lock and the key; they grow by doubling). Posting writes a slot: no allocation per call. |
| Priority classes | `Synchronizer.INPUT` (0) > `LAYOUT` (1, layout and paint) > `DEFAULT` (2) > `MODEL` (3, model patches) > `IDLE` (4). Plain `asyncExec` and `syncExec` use `DEFAULT`, first in, first out, exactly as before. OS input and native messages still run before any async runnable, because `readAndDispatch` drains them first. |
| Keyed coalescing | `asyncExec (Object key, Runnable)` and `asyncExec (int priority, Object key, Runnable)` on `Synchronizer` and `Display`. While a runnable with an equal key is queued in the same class, a new one replaces it in place, at the same position (like `EventQueue.coalesceEvents`). Keys go in an open-addressing table, with no allocation per call. |
| Frames | `MODEL` runnables run only at a frame tick, all together. After them the frame generation increments and every `FrameListener.frameApplied (display, generation)` runs, on the UI thread. At most one tick is pending, paced to `swt.synchronizer.frameMillis` (16 ms by default). A tick is skipped when nothing is dirty. `requestFrame ()` asks for a tick when no patch is queued. The generation is per display (`getFrameGeneration ()`). |
| syncExec | Each calling thread reuses one lock, because a thread waits for one syncExec at a time. A wait longer than `swt.synchronizer.syncWarnMillis` (5000 ms by default) is logged once through JUL (`org.eclipse.swt.widgets.Synchronizer`, WARNING), with the waiting caller's stack and the UI thread's stack. A failure is rethrown as `SWTException (ERROR_FAILED_EXEC)`, as in stock. |
| Stock semantics kept | `runAsyncMessages (all)` runs one message, or all of them, with `sendPreEvent` / `sendPostEvent` around each, `syncThread` set for syncExec, and failures handed to the display's handlers and rethrown as stock does. A due frame counts as one message. `isMessagesEmpty` answers whether anything can run now: a frame that is not yet due lets the UI thread sleep until the tick. `moveAllEventsTo` inserts the moved messages at the front of the target's queue, class by class. `releaseSynchronizer` clears everything. `asyncExec (null)` wakes the display (win32) or queues an empty message (GTK, Cocoa). `timerExec` is unchanged (it is the display's). |

## The CPU and GPU thread split (with render-modes, docs/m3/RENDER_MODES.md "Thread architecture", leaf R28-4)

The thread roles are never mixed:

- The UI thread handles OS events, the native operations of the resident window, and one patch per widget per frame.
- Model threads handle lanes, deltas, sort, filter and scope.
- The render thread consumes only immutable snapshots stamped with a generation.

Per frame, in this order:

1. Model threads post their completions as `MODEL`, keyed by widget, so that each viewport applies one window patch per frame.
2. Each `frameApplied` lets each dirty drawn plane publish its snapshot (a lock-free triple buffer) and unpark the render thread.
3. The UI thread never waits on the render thread.
4. A hidden widget (rules 24 and 25) neither patches nor publishes. On `PartVisible` it posts its one patch.

## Proof and measurements

- `Test_org_eclipse_swt_widgets_Synchronizer_Dispatch` runs on the stock jar and on the rebuilt one. Its first part is the differential:
  - random interleavings of `asyncExec` and `syncExec` from 8 threads (5 seeds of 16 000 calls), which must run in posting order;
  - posting from the UI thread, null runnables, failures in async and sync runnables, and `syncExec` on the UI thread.
- Its second part covers the rebuilt queue: keyed coalescing in place, the class order, and model patches once per frame before the frame listeners. That part skips on the stock jar.
- The stock `Test_org_eclipse_swt_widgets_Display` and `Test_org_eclipse_swt_widgets_Synchronizer` run on both jars.
- `Test_org_eclipse_swt_widgets_Synchronizer_Bench` measures three things:
  - bytes allocated per `asyncExec` on the posting thread;
  - `asyncExec` per second for 10^6 calls from 4 threads;
  - the latency of one runnable behind a flood of 10^6, both plain and in the `INPUT` class.

  The numbers are in `docs/m3/allwidgets-viewport/OPTIMIZATION_LEDGER.tsv`, rows Y1 to Y3.
