# Code mapping to M3 (mechanical pass four over all of Eclipse)

Generated 2026-10-20 by `sweep.sh` (git grep over the bare forks, no checkout, no LLM) from the catalogue
`CODE_MAPPING.tsv`. Marks only: nothing is changed by this pass. The agent's final pass reads these tables
(VIEWPORT_AGENT_BRIEF rule 32); new patterns found there go back into the catalogue (rule 31, MOP).

| Item | Value |
| --- | --- |
| Sites (all scopes) | 132775 |
| Sites in main code (MAIN) | 90119 |
| Files with at least one site | 18046 |
| Catalogue rows | 30 |

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
| eclipse.platform.ui | 14854 | LAYOUT_DELTA 3323, TEXT_API_LANE 2231, M3_ARRAY_LANES 1802, WIDGET_FULL_API 1046 |
| initiative31-eclipse.pde | 14214 | LAYOUT_DELTA 3473, TEXT_API_LANE 2570, M3_ARRAY_LANES 1266, FORMAT_PRECOMPUTE 1142 |
| eclipse.platform | 12099 | LAYOUT_DELTA 2038, TEXT_API_LANE 1561, M3_ARRAY_LANES 1505, FORMAT_PRECOMPUTE 1250 |
| eclipse.jdt.ui | 10867 | LAYOUT_DELTA 2414, M3_ARRAY_LANES 2015, TEXT_API_LANE 1756, WIDGET_FULL_API 779 |
| nebula | 7156 | LAYOUT_DELTA 1746, TEXT_API_LANE 1653, WIDGET_FULL_API 782, M3_ARRAY_LANES 664 |
| egit | 4880 | LAYOUT_DELTA 1112, TEXT_API_LANE 1007, FORMAT_PRECOMPUTE 578, WIDGET_FULL_API 407 |
| org.eclipse.emf | 4672 | M3_ARRAY_LANES 1052, M3STRING_COMPOSITE 672, LAYOUT_DELTA 488, M3_HASH_LANES 433 |
| p2 | 4331 | FORMAT_PRECOMPUTE 937, M3_ARRAY_LANES 592, LAYOUT_DELTA 436, TEXT_API_LANE 371 |
| eclipse.jdt.core | 3989 | M3_ARRAY_LANES 790, M3_PRIMITIVE_LANES 768, M3STRING_COMPOSITE 569, M3_HASH_LANES 418 |
| equinox | 3717 | THREAD_OWNERSHIP 869, M3_ARRAY_LANES 633, FORMAT_PRECOMPUTE 388, M3_HASH_LANES 344 |
| m2e-core | 3328 | LAYOUT_DELTA 699, TEXT_API_LANE 695, WIDGET_FULL_API 366, M3_ARRAY_LANES 336 |
| eclipse.jdt.debug | 2721 | FORMAT_PRECOMPUTE 455, M3_ARRAY_LANES 414, LAYOUT_DELTA 370, TEXT_API_LANE 265 |
| eclipse.platform.swt | 1714 | LAYOUT_DELTA 251, TEXT_API_LANE 205, M3_PRIMITIVE_LANES 171, RESOURCE_RESIDENCY 152 |
| gef-classic | 1371 | LAYOUT_DELTA 265, M3_ARRAY_LANES 201, TEXT_API_LANE 188, RESOURCE_RESIDENCY 152 |
| eclipse.platform.releng.aggregator | 206 | REGEX_PRECOMPUTE 64, M3_ARRAY_LANES 32, BULK_LANE_PASS 31, M3_SET_LANES 30 |

## Targets (main code)

| Target | Sites | Files |
| --- | --- | --- |
| LAYOUT_DELTA | 16753 | 1952 |
| TEXT_API_LANE | 13066 | 2621 |
| M3_ARRAY_LANES | 11793 | 4846 |
| FORMAT_PRECOMPUTE | 5804 | 2072 |
| WIDGET_FULL_API | 5778 | 1447 |
| M3STRING_COMPOSITE | 4479 | 2623 |
| M3_HASH_LANES | 4426 | 2327 |
| M3_PRIMITIVE_LANES | 4089 | 1341 |
| THREAD_OWNERSHIP | 3333 | 930 |
| M3_SET_LANES | 3267 | 1671 |
| FULL_REBUILD_TO_DELTA | 3067 | 1182 |
| CASE_FOLD_FACTS | 2777 | 1036 |
| BULK_LANE_PASS | 1637 | 714 |
| RESOURCE_RESIDENCY | 1547 | 671 |
| ITEM_FACADE_TO_MODEL | 1516 | 535 |
| REGEX_PRECOMPUTE | 1420 | 716 |
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
