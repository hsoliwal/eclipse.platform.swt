# Coverage of the sort/filter classes (JaCoCo 0.8.15)

Classes: every class of `org.eclipse.swt.m3` added by this track (the enhanced-API classes `M3Collation`,
`M3ProxyModel`, `M3SortKeys`, `M3TextSegments`, `M3TokenMatch`, `M3TokenVocabulary` are reported by their own
track). Runs merged (`S:/m3-scratch/sort-filter/coverage.sh`):

- headless: `Test_M3TextMatch`, `Test_M3ColumnFilter`, `Test_M3ColumnSort`, `Test_M3SortFilterDifferential` (36 tests);
- hidden desktop (`-Dswt.widget.mode` default = viewport): `Test_M3FilterAsync`, `Test_M3SortFilterWidgets` plus the
  three headless suites again (43 tests), JaCoCo agent on `org.eclipse.swt.m3.*`.

Run 1 (before the last test additions):

| measure | covered | total | percent |
|---|---|---|---|
| lines | 1683 | 1694 | 99.35% |
| branches | 1005 | 1069 | 94.0% |

Per class (lines covered/total, branches covered/total): M3Checks 8/8 10/14; M3ColumnFilter 187/188 112/114;
M3ColumnKey 44/44 12/12; M3ColumnSort 156/156 87/92; M3Columns 29/29 29/34; M3FilterCriterion 58/58 68/68;
M3FilterDelta 50/50 39/40; M3FilterEditPanel 95/95 50/53; M3FilterPopup 79/79 27/30; M3FilterRow 75/75 33/40;
M3FilterWorker 112/116 36/46; M3HeaderDecoration 69/69 55/58; M3Messages 20/20 1/2; M3RowBinding 114/120 50/68;
M3RowChange 37/37 15/15; M3RowMask 54/54 28/28; M3RowViewBase 74/74 40/40; M3RowWindow 29/29 14/14;
M3SortHeader 42/42 37/38; M3SortLanes 115/115 94/94; M3SortOrder 151/151 70/70; M3TextMatch 85/85 98/99.

Every missed line and branch is listed in `COVERAGE_GAPS.tsv` with its reason: the `SWT.error` probe artifact
(the throwing edge of a one-condition argument check is never recorded although an `assertThrows` exercises it),
the worker's generation guards (which guard sees a newer generation first depends on thread timing), and
platform-defensive checks (scroll bars that Table and Tree always have, disposal orders the listeners already
prevent). With the `SWT.error` artifacts counted as exercised, branches reach 1015 of 1069.

Run 2 (selection renumbering, filter row column removal, hidden header, regex info, mid-computation supersede
tests added) is queued on the hidden desktop; its numbers replace the table above when it lands.
