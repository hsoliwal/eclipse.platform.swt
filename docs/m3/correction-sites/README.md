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
| 4 | mechanical | Classify the caller (correction_classes.awk, one source), in sub-passes, one dimension each: 4/4b enclosing-method names; 4c category (DOOP_STORE, DOOP_OBJECTS, THREAD_BLOCKING); 4d method-name shape (handle*/on*/*Changed, update*/refresh*, set*, create*/fill*, dispose*/clear*); 4e words in handler names (button/user actions, UI events, otherwise MODEL_LISTENER). |
| 5 | mechanical | Permalink, snippet and summaries per repo by caller class (SUMMARY_BY_CALLER.tsv) and by category (SUMMARY_BY_CATEGORY.tsv). |
| 6 | model (last) | My review of the MODEL_LISTENER sites, recorded as final_pass_model_listener.awk so it re-applies: FINAL_PASS_MODEL_LISTENER.tsv, with a verdict, fix pattern and owner per site. |

Re-run: `bash recipe/correction_sites.sh S:/m3-scratch/correction-sites` (clones go to S:/m3-scratch/forks).

The OpenRewrite form of passes 1-4 is still to be written. It is a search recipe that puts SearchResult markers on
the same sites in source, with the classification in a data table.

## Counts (2026-10-20, fork HEADs in CORRECTION_SITES.tsv; generated from the TSV)

| Fork | Sites |
|---|---|
| eclipse.platform.ui | 876 |
| initiative31-eclipse.pde | 745 |
| eclipse.platform | 718 |
| eclipse.jdt.ui | 459 |
| egit | 261 |
| nebula | 196 |
| org.eclipse.emf | 125 |
| eclipse.jdt.debug | 116 |
| p2 | 103 |
| gef-classic | 77 |
| eclipse.jdt.core | 29 |
| eclipse.platform.swt | 28 |
| **Total** | **3733** |

By caller class after the multipass (passes 4, 4b-4e), all forks:

| Caller class | Sites |
|---|---|
| INITIAL_OR_SETUP | 677 |
| ASYNC_OR_REFRESH_METHOD | 669 |
| REVIEW | 576 |
| DOOP_STORE | 335 |
| THREAD_BLOCKING | 234 |
| UI_EVENT | 214 |
| FIELD_OR_STATIC | 187 |
| MODEL_LISTENER | 180 |
| MAINTENANCE | 175 |
| USER_ACTION | 170 |
| DOOP_OBJECTS | 135 |
| STATE_SETTER_REBUILD | 134 |
| QUERY_PATH_ALLOCATION | 47 |

By category: set_input 1267, full_refresh 986, element_map 622, sync_exec 356, remove_all 214, per_row_item 144, per_element_object 82, clear_all 23, refresh_children 21, swing_full_event 12, item_count_reset 6.

## Final pass: model listeners that rebuild (FINAL_PASS_MODEL_LISTENER.tsv)

Of 180 model-listener rebuild sites, 131 are DELTA_DEFECTs: the event already says what changed, and the view throws that away. Verdicts: DELTA_DEFECT 131, DIALOG_LOW 27, STRUCTURE_PREFERENCE 10, PREFERENCE_OR_TOGGLE 10, FILTER_LANE 2.

| DELTA_DEFECT sites | Fork |
|---|---|
| 59 | initiative31-eclipse.pde |
| 20 | eclipse.platform |
| 16 | eclipse.jdt.ui |
| 9 | eclipse.platform.ui |
| 7 | org.eclipse.emf |
| 5 | gef-classic |
| 5 | egit |
| 4 | p2 |
| 3 | nebula |
| 3 | eclipse.jdt.debug |

Fix patterns and owners per site are in the TSV. The biggest single fix is the PDE form-section base class (IModelChangedEvent to M3Patch), which closes most PDE rows at once. Under brief rule 27, each owner closes its rows by writing the `.m3view` spec and adapter, not per-view code.

The new classes DOOP_STORE and DOOP_OBJECTS are per-element stores and objects (rule 18). THREAD_BLOCKING covers syncExec calls to review under rule 28. STATE_SETTER_REBUILD covers setters that rebuild instead of updating a lane.
