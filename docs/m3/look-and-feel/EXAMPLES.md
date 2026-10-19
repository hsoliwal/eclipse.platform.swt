# Look and feel examples (LF6)

Runnable SWT snippets for the Swing-style look and feel of the drawn widgets ([`../LOOK_AND_FEEL.md`](../LOOK_AND_FEEL.md),
LF6), in [`examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3`](../../../../examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3).
They use the internal `org.eclipse.swt.internal.laf` API of the M3 SWT jar. Build the jar (`build_and_tests.sh`), compile
the snippets against it, and run them with both on the class path:

```
javac --release 21 -cp swt.jar -d snippets examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3/*.java
java -cp swt.jar;snippets org.eclipse.swt.snippets.m3.M3LookAndFeelSwitchSnippet
```

Every snippet takes `--screenshots <directory>`: it saves one PNG per state (`Control.print`) and exits. The
screenshots of the hidden desktop are regenerated, not committed: `S:/m3-scratch/allwidgets/screenshots/`.

| Snippet | Shows | Screenshots |
|---|---|---|
| `M3LookAndFeelSwitchSnippet` | runtime switching across `native`, `swing`, `initiative31`, `light` and `dark` in one shell (`M3LookAndFeel.setLookAndFeel`); a "Next accent" button (`M3LookAndFeel.setAccent`); rows drawn by the look and feel's row delegate (`M3RowUI`, `M3DefaultRowUI`: selection, stripe, hover, grid) next to a native `Table` that keeps native pixels | `switch-<look>.png`, `switch-light-accent.png` |
| `M3ThemeImportSnippet` | an IntelliJ `.theme.json` and a FlatLaf `.properties` theme imported (`M3ThemeImport`) and applied; with a file argument that file, and with `--watch` live reload while the file is edited (`M3ThemeWatcher`) | `import-sample-intellij.png`, `import-sample-flatlaf.png` |

Only widgets with a UI delegate change: a switch diffs the two look and feels and repaints (or relays out) the
widgets whose delegate reads a changed key; native widgets and the `viewport` and `original` modes are never visited.
