# Enhancement targets (mechanical pass three over all of Eclipse)

Generated 2026-10-20 by `sweep.sh` (git grep over the bare forks, no checkout, no LLM) from the catalogue
`TEMPLATES.tsv`. Marks only: nothing is changed by this pass. The agent's final pass reads these tables
(VIEWPORT_AGENT_BRIEF rule 32); new patterns found there go back into the catalogue (rule 31, MOP).

| Item | Value |
| --- | --- |
| Sites (all scopes) | 60284 |
| Sites in main code (MAIN) | 43933 |
| Files with at least one site | 10672 |
| Catalogue rows | 40 |

| File | Content |
| --- | --- |
| `TEMPLATES.tsv` | template id, regex, the interface or API it recognises, the enhancement target, the programme rule |
| `COUNTS.tsv` | repo, template, target, scope (MAIN/TEST/EXAMPLE), sites, files |
| `FILE_MARKS.tsv.gz` | one row per (repo, path, scope, target): sites |
| `TOP_FILES.tsv` | the 500 main-code files with the most sites, with their targets |
| `sites/<repo>.tsv.gz` | every matching line: template, scope, path, line |

Read with `zcat FILE_MARKS.tsv.gz | grep -P '\tMAIN\t'` or `zcat sites/eclipse.platform.ui.tsv.gz | awk -F'\t' '$1 == "T28"'`.

## Sites per repo (main code)

| Repo | MAIN sites | Top targets |
| --- | --- | --- |
| eclipse.platform.ui | 10604 | BACKGROUND_MODEL_THREAD 1152, SWT_TYPED_EVENT 734, PROPERTY_DELTA_SOURCE 689, SELECTION_LANE 676 |
| eclipse.jdt.ui | 6016 | ITERATION_TO_LANE 1008, MULTIMAP_TO_LANE 613, BACKGROUND_MODEL_THREAD 598, AUTOCOMPLETE 514 |
| eclipse.platform | 5465 | BACKGROUND_MODEL_THREAD 894, PROPERTY_DELTA_SOURCE 610, SELECTION_LANE 372, ITERATION_TO_LANE 313 |
| initiative31-eclipse.pde | 4692 | SELECTION_LANE 748, ITERATION_TO_LANE 670, BACKGROUND_MODEL_THREAD 548, FILTER_LANE 415 |
| nebula | 3351 | SWT_TYPED_EVENT 933, TEXT_INPUT_DEBOUNCE 276, ITERATION_TO_LANE 251, SELECTION_LANE 235 |
| eclipse.platform.swt | 3291 | DND 832, SWT_TYPED_EVENT 514, STYLEDTEXT_LANE 383, VIEWPORT_RESIZE_DELTA 352 |
| egit | 2250 | BACKGROUND_MODEL_THREAD 389, SELECTION_LANE 206, UI_THREAD_SYNCHRONIZER 182, PROPERTY_DELTA_SOURCE 165 |
| org.eclipse.emf | 1668 | BACKGROUND_MODEL_THREAD 213, AUTOCOMPLETE 197, VIEWER_SELECTION_DELTA 156, MULTIMAP_TO_LANE 144 |
| eclipse.jdt.core | 1470 | ITERATION_TO_LANE 847, JAVA_ELEMENT_DELTA_ADAPTER 329, SORT_LANE 86, BACKGROUND_MODEL_THREAD 74 |
| eclipse.jdt.debug | 1279 | ITERATION_TO_LANE 355, BACKGROUND_MODEL_THREAD 132, PROPERTY_DELTA_SOURCE 119, SELECTION_LANE 84 |
| m2e-core | 1275 | ITERATION_TO_LANE 264, TEXT_INPUT_DEBOUNCE 182, SELECTION_LANE 171, BACKGROUND_MODEL_THREAD 125 |
| gef-classic | 1160 | DND 341, ITERATION_TO_LANE 213, SWT_TYPED_EVENT 106, PROPERTY_DELTA_SOURCE 64 |
| p2 | 951 | ITERATION_TO_LANE 167, SELECTION_LANE 104, BACKGROUND_MODEL_THREAD 100, SORT_LANE 73 |
| equinox | 394 | ITERATION_TO_LANE 137, BACKGROUND_MODEL_THREAD 51, EXTENSION_REGISTRY_DELTA 45, SWT_TYPED_EVENT 37 |
| eclipse.platform.releng.aggregator | 67 | ITERATION_TO_LANE 55, SORT_LANE 8, MULTIMAP_TO_LANE 2, BACKGROUND_MODEL_THREAD 2 |

## Targets (main code)

| Target | Sites | Files |
| --- | --- | --- |
| ITERATION_TO_LANE | 5236 | 1713 |
| BACKGROUND_MODEL_THREAD | 4445 | 1664 |
| SELECTION_LANE | 3310 | 1194 |
| SWT_TYPED_EVENT | 3109 | 1092 |
| DND | 2415 | 227 |
| PROPERTY_DELTA_SOURCE | 2112 | 570 |
| UI_THREAD_SYNCHRONIZER | 1954 | 999 |
| FILTER_LANE | 1940 | 527 |
| AUTOCOMPLETE | 1848 | 248 |
| STYLEDTEXT_LANE | 1825 | 376 |
| TEXT_INPUT_DEBOUNCE | 1616 | 498 |
| VIEWER_SELECTION_DELTA | 1497 | 523 |
| LABEL_DELTA | 1430 | 728 |
| MULTIMAP_TO_LANE | 1263 | 426 |
| SORT_LANE | 1195 | 776 |
| INPUT_EVENT | 942 | 421 |
| LIFECYCLE_RESIDENCY | 874 | 444 |
| VIEWPORT_RESIZE_DELTA | 866 | 205 |
| JAVA_ELEMENT_DELTA_ADAPTER | 701 | 67 |
| PART_VISIBILITY | 597 | 113 |
| RESOURCE_DELTA_ADAPTER | 527 | 163 |
| DOCUMENT_DELTA | 448 | 125 |
| PAINT_DAMAGE | 423 | 146 |
| VIEWER_OPEN | 378 | 214 |
| CHECK_STATE_LANE | 364 | 150 |
| MENU_TRAVERSE | 356 | 277 |
| DATABINDING_OBSERVABLE | 299 | 117 |
| ASYNC_FUTURE | 295 | 81 |
| FOCUS | 251 | 184 |
| EXPAND_STATE_LANE | 240 | 65 |
| DEBUG_EVENT_ADAPTER | 236 | 80 |
| HELP | 218 | 37 |
| EXTENSION_REGISTRY_DELTA | 143 | 56 |
| COMPARE_DELTA | 132 | 35 |
| COMMAND_STATE | 118 | 33 |
| WIZARD_PAGE | 113 | 34 |
| ACTIVITY_STATE | 68 | 24 |
| TEAM_SYNC_ADAPTER | 55 | 24 |
| VIEWPORT_LISTENER | 52 | 18 |
| CALLBACK_SETTER | 42 | 27 |
