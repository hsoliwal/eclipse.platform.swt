# Sort and filter examples

Runnable SWT snippets (one class, a `main` method each) in
`examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3/`. They use only the enhanced API in
`org.eclipse.swt.m3`. Screenshots are captured on the hidden test desktop by
`examples/SnippetShots.java.txt` (each snippet's first shell printed with `Control.print`, WM_PRINT) into
`S:/m3-scratch/sort-filter/shots/`.

| snippet | shows | screenshot |
|---|---|---|
| `Snippet_M3MultiColumnSort` | a virtual table of 100,000 rows sorted by City ascending then Amount descending; header click sorts, Shift+click adds a key; numbered direction marks on every sorted column | `S:/m3-scratch/sort-filter/shots/Snippet_M3MultiColumnSort.png` |
| `Snippet_M3ColumnFilterRow` | a filter row above a virtual table (contains, ignoring case; `red,black` in the Colour field), the header popup on right click, funnel marks on filtered columns, a header click sorts the filtered rows | `S:/m3-scratch/sort-filter/shots/Snippet_M3ColumnFilterRow.png` |
| `Snippet_M3FilterDebounce1M` | a live filter box over 1,000,000 rows: asynchronous filter (150 ms debounce, worker thread, generations), the result sorted on the worker and applied in one step; the status line shows the applied generation | `S:/m3-scratch/sort-filter/shots/Snippet_M3FilterDebounce1M.png` |
| `Snippet_M3TreeFilterKeepsAncestors` | a virtual tree of 22,620 rows (three arrays: name, parent); a wildcard filter keeps every ancestor of a match; siblings sorted inside their parent | `S:/m3-scratch/sort-filter/shots/Snippet_M3TreeFilterKeepsAncestors.png` |

Run one with the SWT jar of this branch on the class path, for example
`java -cp <swt jar>;<compiled snippets> org.eclipse.swt.snippets.m3.Snippet_M3FilterDebounce1M`.
