# Licence audit of the TV_5.0 adaptations (2026-10-19)

Reference: the donor header census `USER_DONOR_HEADERS.tsv` on com.synexia branch
`codex/m3-ui-donor-atomize-20261017` @ `15a9e1a51410` (154 TV_5.0 files: 100 user-owned by attestation, 44 EPL-1.0
with headers to keep verbatim, 9 Sun BSD-style / HP pattern-only, 1 non-commercial excluded).

Every TV_5.0 file named in `PROVENANCE.tsv` was checked against that census:

| donor file | census decision | our relation | verdict |
|---|---|---|---|
| IEntryFilter, IFilterStateProvider, IViewerUpdater, TreeUtil | ADAPT (user-owned) | ADAPTED | keep Synexia header |
| filters/IViewerTextFilter, IMultiPropertyTextFilter, AbstractRegexFilter, AbstractWildCardFilter, RegexFilter, WildCardFilter, MultiPropertyTextFilter | ADAPT (user-owned) | ADAPTED | keep Synexia header |
| filters/ui/ColumnFilterUI, PopUpFilterUI, ViewerFilterUI, FilterEditPanel | ADAPT (user-owned) | ADAPTED | keep Synexia header |
| tree/ElementFilter, FilteredTreeElementProvider, TreeFilterStateProvider, SortedElementProvider, Comparator, TreeViewer, TreeViewerUpdater, TreeViewerWork | ADAPT (user-owned) | ADAPTED | keep Synexia header |
| filters/resources/messages.properties | not a Java file in the census; headerless resource of the user-owned filters package (its `Messages.java` is ADAPT user-owned) | ADAPTED (texts) | keep |
| impl/LazySortUtil | PATTERN_ONLY (HP permission notice) | PATTERN_ONLY, no code taken | confirmed: `M3SortLanes`/`M3SortOrder` contain no heap code (LSD radix, bottom-up merge sort with insertion runs, binary-search moves, back merges, all written against the catalogue rows); `grep -c heap` = 0 |
| impl/LazySortList | ADAPT (user-owned) in the census, but the file states it derives from org.eclipse.ui MarkerSortUtil | PATTERN_ONLY, no code taken | kept PATTERN_ONLY (safer than the census) |
| MergeSort.java | PATTERN_ONLY (Sun BSD-style) | PATTERN_ONLY, no code taken | confirmed |
| dialogs/ViewerFiltersDialog | ADAPT under the IBM EPL-1.0 header, kept verbatim | DEFERRED (next leaf) | when adapted: keep its header verbatim, add a contributor line, no Synexia reheading |
| tree/EntryFilterUtil | ADAPT (user-owned) | DEFERRED | - |
| JTreeTable files (8, Sun), DOMTreeWalkerTreeModel (Flanagan, non-commercial), the 44 EPL-1.0 files | PATTERN_ONLY / header kept | not used by this leaf | nothing to change |

Result: no row changes to ADAPTED relations; `ViewerFiltersDialog` rows now carry the header-verbatim rule.
