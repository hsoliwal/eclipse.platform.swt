# Correction sites in the Eclipse forks

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

User, 2026-10-20: "identify and mark all the places where platform UI, JDT, JDT UI and resources need to be corrected",
"mark all the Eclipse forks", "using grep or something, and then doing multipass mechanical, and finally your pass",
"save multipass mechanical as recipe".

## What a mark is

Every site is one row in CORRECTION_SITES.tsv with a permalink to the exact line at the fork's current commit:
`https://github.com/hsoliwal/<repo>/blob/<sha>/<path>#L<line>`. The marks are stable and clickable, and they change
no source. Each converting track records its fix against the row and re-runs the recipe, so the count goes down.

## Method (the recipe, in recipe/)

| Pass | Kind | What it does |
|---|---|---|
| 1 | grep | `git grep -P` over HEAD of a shallow bare clone of each fork (tracked .java). Eleven patterns: `refresh()`, `setInput(`, `removeAll()`, `clearAll()`, `setItemCount(0)`, `refreshChildren()`, Swing full-structure events, per-element objects (MarkerEntry, MarkerCategory, TreePath), per-row TreeItem/TableItem, per-element HashMap/HashSet in viewer code, `syncExec(`. |
| 2 | mechanical | Drop tests, examples, snippets and dot-directories (.superset conflict archives); drop SWT's own widget code for per-row items; keep element maps only in viewer, view, provider, outline and hierarchy code. |
| 3 | mechanical | The enclosing method of each hit (enclosing_method.awk; one `git show` per file). |
| 4 | mechanical | Classify the caller by enclosing method (correction_classes.awk, one source): MODEL_LISTENER, ASYNC_OR_REFRESH_METHOD, QUERY_PATH_ALLOCATION, UI_EVENT, USER_ACTION, INITIAL_OR_SETUP, MAINTENANCE, FIELD_OR_STATIC, REVIEW. |
| 5 | mechanical | Permalink, snippet and summaries per repo by caller class (SUMMARY_BY_CALLER.tsv) and by category (SUMMARY_BY_CATEGORY.tsv). |
| 6 | model (last) | My review of the MODEL_LISTENER sites, recorded as final_pass_model_listener.awk so it re-applies: FINAL_PASS_MODEL_LISTENER.tsv, with a verdict, fix pattern and owner per site. |

Re-run: `bash recipe/correction_sites.sh S:/m3-scratch/correction-sites` (clones go to S:/m3-scratch/forks).

The OpenRewrite form of passes 1-4 is still to be written. It is a search recipe that puts SearchResult markers on
the same sites in source, with the classification in a data table.

## Counts (2026-10-20, fork HEADs in CORRECTION_SITES.tsv)

| Fork | Sites |
|---|---|
| eclipse.platform.ui (includes JFace Text: eclipse.platform.text is empty upstream) | 876 |
| eclipse.pde (via hsoliwal/initiative31-eclipse.pde) | 745 |
| eclipse.platform (includes Resources, Debug, Team, Compare, Ant, Help) | 718 |
| eclipse.jdt.ui | 459 |
| egit | 261 |
| nebula | 196 |
| org.eclipse.emf | 125 |
| eclipse.jdt.debug | 116 |
| gef-classic | 77 |
| eclipse.platform.swt | 28 |
| eclipse.jdt.core | 29 |
| **Total** | **3,630** |

By caller class, all forks:

| Caller class | Sites |
|---|---|
| REVIEW | 1,869 |
| ASYNC_OR_REFRESH_METHOD | 524 |
| INITIAL_OR_SETUP | 477 |
| FIELD_OR_STATIC | 184 |
| UI_EVENT | 178 |
| USER_ACTION | 130 |
| MAINTENANCE | 117 |
| MODEL_LISTENER | 104 |
| QUERY_PATH_ALLOCATION | 47 |

By category: setInput 1,232, refresh-all 956, per-element maps 611, syncExec 338, removeAll 210, per-row items 141,
per-element objects 82, clearAll 23, refreshChildren 21, Swing full events 10, setItemCount(0) 6.

## Final pass: model listeners that rebuild (FINAL_PASS_MODEL_LISTENER.tsv)

Of 104 sites where a model listener triggers a rebuild, 72 are DELTA_DEFECTs. The change event already says what
changed, and the view throws that away. The remaining 32 split as DIALOG_LOW 15, PREFERENCE_OR_TOGGLE 9,
STRUCTURE_PREFERENCE 6 and FILTER_LANE 2.

| Sites | Where | Fix pattern | Owner |
|---|---|---|---|
| 28 | PDE form editor sections (modelChanged) | IModelChangedEvent (INSERT/REMOVE/CHANGE + objects) to M3Patch in the shared TableSection/TreeSection base: one fix for every section | platform-views |
| 11 | eclipse.platform views (Variables, MarkerView, Compare outline, sync, breakpoint organizer, Ant) | apply the change delta to the lane model and patch the window | platform-views |
| 8 | search results (JDT search, file search, EGit commit search) | `elementsChanged(Object[])` is already a delta: result lanes, no per-element maps | platform-views |
| 6 | EMF MappingEditor and GenModelEditor (notifyChanged / handleChangedResources then `setInput`) | EMF Notification to M3Patch on the item-provider tree | data-binding/EMF |
| 3 | Memory view renderings | address-range lanes, viewport over memory | platform-views |
| 2 | Problems view (CachedMarkerBuilder working-set refresh) | scope set-diff (markers-view FALLBACKS_TO_DELTAS.md) | jface |
| 2 | JDT Java outline and breadcrumb (elementChanged) | IJavaElementDelta to M3Patch | platform-views |
| 4 | GEF palette refreshChildren, Zest input maps | child add/remove deltas; no per-element maps | gef |

The 524 ASYNC_OR_REFRESH_METHOD sites are the next tier: refresh methods that a change schedules. The 1,889 REVIEW
sites are mostly `setInput` and refresh-all outside any recognizable caller (1,038), per-element maps (330) and
`syncExec` (226). Each track triages its own repos' REVIEW rows into the DELTA_GATE.tsv of the view or surface it
converts.
