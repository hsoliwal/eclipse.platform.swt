# Code mapping to M3 (mechanical pass four over all of Eclipse)

Generated 2026-10-20 by `sweep.sh` (git grep over the bare forks, no checkout, no LLM) from the catalogue
`CODE_MAPPING.tsv`. Marks only: nothing is changed by this pass. The agent's final pass reads these tables
(VIEWPORT_AGENT_BRIEF rule 32); new patterns found there go back into the catalogue (rule 31, MOP).

| Item | Value |
| --- | --- |
| Sites (all scopes) | 789354 |
| Sites in main code (MAIN) | 236683 |
| Files with at least one site | 25935 |
| Catalogue rows | 33 |

| File | Content |
| --- | --- |
| `CODE_MAPPING.tsv` | template id, regex, the interface or API it recognises, the enhancement target, the programme rule |
| `COUNTS.tsv` | repo, template, target, scope (MAIN/TEST/EXAMPLE), sites, files |
| `FILE_MARKS.tsv.gz` | one row per (repo, path, scope, target): sites |
| `TOP_FILES.tsv` | the 500 main-code files with the most sites, with their targets |
| `sites/<repo>.tsv.gz` | every matching line: template, scope, path, line |

Read with `zcat FILE_MARKS.tsv.gz | grep -P '\tMAIN\t'` or `zcat sites/eclipse.platform.ui.tsv.gz | awk -F'\t' '$1 == "T28"'`.

## Sites per repo (main code)

| Repo | MAIN sites | Top targets |
| --- | --- | --- |
| org.eclipse.emf | 32237 | M3STRING_COMPOSITE 21611, LOOP_TO_LANE 6189, M3_ARRAY_LANES 1052, LAYOUT_DELTA 488 |
| initiative31-eclipse.pde | 29150 | M3STRING_COMPOSITE 8761, LOOP_TO_LANE 5090, LAYOUT_DELTA 3473, TEXT_API_LANE 2570 |
| eclipse.platform.ui | 29052 | LOOP_TO_LANE 7634, M3STRING_COMPOSITE 6363, LAYOUT_DELTA 3323, TEXT_API_LANE 2231 |
| eclipse.platform | 27893 | M3STRING_COMPOSITE 8752, LOOP_TO_LANE 6503, LAYOUT_DELTA 2038, TEXT_API_LANE 1561 |
| eclipse.jdt.ui | 26630 | M3STRING_COMPOSITE 8103, LOOP_TO_LANE 7722, LAYOUT_DELTA 2414, M3_ARRAY_LANES 2015 |
| eclipse.jdt.core | 23332 | M3STRING_COMPOSITE 10877, LOOP_TO_LANE 8093, M3_ARRAY_LANES 790, M3_PRIMITIVE_LANES 768 |
| nebula | 13701 | LOOP_TO_LANE 3391, M3STRING_COMPOSITE 3048, LAYOUT_DELTA 1746, TEXT_API_LANE 1653 |
| p2 | 10763 | M3STRING_COMPOSITE 3516, LOOP_TO_LANE 2182, FORMAT_PRECOMPUTE 937, M3FILE_IO 894 |
| equinox | 10288 | M3STRING_COMPOSITE 3254, LOOP_TO_LANE 2850, THREAD_OWNERSHIP 869, M3_ARRAY_LANES 633 |
| eclipse.platform.swt | 8940 | LOOP_TO_LANE 5264, M3STRING_COMPOSITE 1948, LAYOUT_DELTA 251, TEXT_API_LANE 205 |
| egit | 7676 | M3STRING_COMPOSITE 1195, LOOP_TO_LANE 1191, LAYOUT_DELTA 1112, TEXT_API_LANE 1007 |
| eclipse.jdt.debug | 7153 | M3STRING_COMPOSITE 2682, LOOP_TO_LANE 1626, FORMAT_PRECOMPUTE 455, M3_ARRAY_LANES 414 |
| m2e-core | 6256 | M3STRING_COMPOSITE 1571, LOOP_TO_LANE 1019, LAYOUT_DELTA 699, TEXT_API_LANE 695 |
| gef-classic | 2873 | LOOP_TO_LANE 1203, M3STRING_COMPOSITE 299, LAYOUT_DELTA 265, M3_ARRAY_LANES 201 |
| eclipse.platform.releng.aggregator | 739 | M3STRING_COMPOSITE 320, LOOP_TO_LANE 116, REGEX_PRECOMPUTE 91, M3FILE_IO 72 |

## Targets (main code)

| Target | Sites | Files |
| --- | --- | --- |
| M3STRING_COMPOSITE | 82300 | 11359 |
| LOOP_TO_LANE | 60073 | 10310 |
| LAYOUT_DELTA | 16753 | 1952 |
| TEXT_API_LANE | 13066 | 2621 |
| M3_ARRAY_LANES | 11793 | 4846 |
| M3FILE_IO | 7085 | 1604 |
| FORMAT_PRECOMPUTE | 5804 | 2072 |
| WIDGET_FULL_API | 5778 | 1447 |
| M3_HASH_LANES | 4426 | 2327 |
| M3_PRIMITIVE_LANES | 4089 | 1341 |
| THREAD_OWNERSHIP | 3333 | 930 |
| M3_SET_LANES | 3267 | 1671 |
| FULL_REBUILD_TO_DELTA | 3067 | 1182 |
| REGEX_PRECOMPUTE | 3005 | 1031 |
| CASE_FOLD_FACTS | 2777 | 1036 |
| BULK_LANE_PASS | 1637 | 714 |
| RESOURCE_RESIDENCY | 1547 | 671 |
| ITEM_FACADE_TO_MODEL | 1516 | 535 |
| CONTENT_PROVIDER_TO_WINDOW | 1218 | 597 |
| M3_ORDERED_LANES | 1132 | 568 |
| SORT_LANE | 583 | 435 |
| SPLIT_PANEL | 496 | 96 |
| JFACE_FORWARD | 461 | 380 |
| M3_DEQUE_LANES | 439 | 299 |
| M3_SORTED_LANES | 335 | 209 |
| M3FILE_STREAMS | 203 | 152 |
| M3FILE_ARCHIVE_STREAM | 193 | 122 |
| M3_IDENTITY_WEAK_LANES | 155 | 107 |
| M3_ENUM_BITS | 83 | 41 |
| TOKEN_INTERNER | 67 | 43 |
| M3_HEAP_LANES | 2 | 2 |
