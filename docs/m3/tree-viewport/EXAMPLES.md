# Tree viewport examples

Runnable SWT snippets for the Tree viewport (the default Tree mode on win32) are in
[`examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3`](../../../examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3).
They use public SWT API only, so they also run on the official SWT jar and in the original mode
(`-Dswt.widget.mode.Tree=original`). Build the jar (`build_and_tests.sh`), compile the snippets against it, and run
them with both on the class path:

```
javac --release 21 -cp swt.jar -d snippets examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3/M3Tree*.java
java -cp swt.jar;snippets org.eclipse.swt.snippets.m3.M3TreeMillionNodesExample
```

| Snippet | Shows |
|---|---|
| `M3TreeMillionNodesExample` | A virtual Tree with 1,000,000 roots. Every tenth root has 1,000 children, and every fifth of those has 100 grandchildren. You can jump to any root, expand or collapse it, and scroll smoothly one row per frame. The status line shows how long `setItemCount`, `getItem` and each expansion took. |
| `M3TreeCustomDrawExample` | `MeasureItem`, `EraseItem` and `PaintItem` on the same million-root tree: taller rows, a gradient selection, a status dot and a progress bar column. The status line counts the `MeasureItem` events of the first paint. "Taller rows" changes the item height at run time. |
| `M3TreeCollapseKeysExample` | The native expand and collapse keys (numpad `-`, Left, numpad `+`, Right, numpad `*`). After each key a log shows the `Expand`/`Collapse` events, `getExpanded`, the child count, the top item and the vertical bar. "Prepare" sets up the case where the top row is inside the subtree that collapses. |
| `M3TreeCaretSelectionExample` | What a `SWT.MULTI` Tree selects after its caret row is disposed or removed by `setItemCount`. Four cases: dispose the caret, order not position, shrink past the caret, and selectAll/deselect/shrink. Each case logs the selection it leaves. |

## Screenshots and state logs

[`tree_example_shots.sh`](tree_example_shots.sh) runs the harness [`evidence/TreeExampleShots.java.txt`](evidence/TreeExampleShots.java.txt)
on the invisible test desktop. The harness opens each snippet and drives it through a few states. For every state it
logs one JUL line with what a client reads, and in the default mode it also saves the shell as a PNG
(`Control.print`). The script makes three runs: the default mode, the original mode and the official 3.135.0 jar.
Their logs can be compared line by line. The invisible desktop gets no real input, so the harness sends the native
keys as `WM_KEYDOWN`/`WM_KEYUP` messages, the same way the tests do.

```
bash C:/m3-superset/tools/ui_lock.sh <agent> bash docs/m3/tree-viewport/tree_example_shots.sh <out-dir> <stamp> <dest-dir>
```

The screenshots are regenerated, not committed. They go to `S:/m3-scratch/tree-presentation/examples/png/`.
