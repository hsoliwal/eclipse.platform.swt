# UI-thread abuse in the Eclipse forks

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

User, 2026-10-20: "keep doing multipass .. Eclipse is full of UI thread abuse". Brief rule 28 (thread architecture).
Same multipass as docs/m3/correction-sites, re-runnable with recipe/ui_thread_sites.sh:
- pass 1 git grep: syncExec, nested readAndDispatch loops, run(false, ...) without forking, BusyIndicator.showWhile and
  busyCursorWhile, Thread.sleep, job join(), workspace I/O (findMarkers, refreshLocal, members, getContents, markers),
  Display.getDefault();
- pass 2 drops tests, examples and dot-directories;
- pass 3 finds the enclosing method;
- pass 4 assigns the thread role: UI_PROVIDER (label and content provider methods, always on the UI thread),
  UI_CALLBACK (event handlers, part and dialog creation, runInUIThread), RUN_METHOD, OTHER;
- pass 5 gives the verdict.

## Counts (2026-10-20): 3235 sites in 12 forks

| Verdict | Sites |
|---|---|
| REVIEW | 2190 |
| UI_THREAD_WAIT | 547 |
| OFF_THREAD_DISPLAY | 259 |
| UI_THREAD_ABUSE | 174 |
| WORKER_BLOCKS_ON_UI | 65 |

UI_THREAD_ABUSE by fork: org.eclipse.emf 39, eclipse.platform.ui 34, initiative31-eclipse.pde 28, eclipse.platform 27, eclipse.jdt.ui 18, egit 12, gef-classic 5, eclipse.platform.swt 4, eclipse.jdt.debug 3, eclipse.jdt.core 2, p2 1, nebula 1.
By kind: workspace_io 90, busy_wait 52, modal_no_fork 19, nested_event_loop 10, job_join 3.

## Final pass (my review of the UI_THREAD_ABUSE rows): fix patterns

| Pattern seen | Example | Fix (rules 24, 28, 29) |
|---|---|---|
| Content providers doing workspace I/O (members() in getChildren/hasChildren) | ContainerContentProvider.java:67, WorkbenchFolder.java:44, ResourceExtensionContentProvider.java:68 | the resource tree is a lane model fed by IResourceDelta on a model thread; providers read lanes only, with no I/O on the UI thread |
| BusyIndicator.showWhile / busyCursorWhile around heavy work in event handlers | PreferenceDialog.java:345/657, ResourceWorkingSetPage.java:176-212, FindReplaceDialog.java:333/379 | run the work on a model thread; show the translucent progress overlay (behaviour catalogue); apply one patch when done |
| Progress dialogs run without fork (run(false, ...)) | DeleteResourcesWizard.java:181, ProjectReferencePage.java:209, AddFromHistoryAction.java:148 | fork=true with cancellation; the UI stays responsive |
| Job join() inside runInUIThread | DeleteResourceAction.java:524 | completion callback (done listener) applies the result; never join on the UI thread |
| Nested readAndDispatch loops | SWTInputHandler.java:90 | the dialog's own modal loop or an async completion; review each |

WORKER_BLOCKS_ON_UI (syncExec from job run methods) and THREAD_BLOCKING in correction-sites are the opposite direction.
The new SWT Synchronizer (rule 28: keyed coalescing, per-frame batching) removes most of the reasons for syncExec.
Owners close their rows exactly as for correction sites.
