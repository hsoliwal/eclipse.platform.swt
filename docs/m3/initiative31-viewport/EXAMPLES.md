<!--
Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
-->
# Initiative 31 viewport examples

Runnable examples of the viewport mode of the Initiative 31 SWT-on-Swing widgets, each with 1,000,000 rows and the
Initiative 31 look and feel. Sources: `examples/m3/i31/examples/`.

| Example | Widget | Content |
|---|---|---|
| `m3.i31.examples.M3TreeExample` | virtual `Tree` | 1,000,000 root items; every tenth item has three children; opens at the middle with that item expanded |
| `m3.i31.examples.M3TableExample` | virtual `Table`, 3 columns, header and lines | 1,000,000 rows filled by SetData; opens at the middle |
| `m3.i31.examples.M3ListExample` | `List` | 1,000,000 strings; `setTopIndex` and `showSelection` to the middle |

The line above each widget states the row count, the mode, how long filling took, how many SetData calls the
widget sent and the row at the top.

## Running

Compile the Swing source set (`tools/compile_swing.sh`), then the examples against it, and run on the hidden
desktop (UI rule of this programme):

```
CP='<classes>;<examples>'
bash C:/m3-superset/tools/ui_lock.sh <agent> bash C:/m3-superset/tools/hidden_java.sh 300 <log> \
  -Xmx2g -cp "$CP" m3.i31.examples.M3TreeExample --shot S:/m3-scratch/initiative31/examples/tree-native.png
```

| Argument / property | Effect |
|---|---|
| `--rows N` | number of rows (default 1,000,000) |
| `--top N` | row shown at the top after opening (default: the middle) |
| `--shot file.png` | write a screenshot of the window and exit |
| `-Dswt.widget.mode=viewport\|original` | mode of every widget (viewport is the default) |
| `-Dswt.swing.laf=<class>` | Initiative 31's look and feel switch; without it the system look and feel |

## Screenshots

Screenshots are regenerable output and live on `S:/m3-scratch/initiative31/examples/` (not in the repository):

| Example | Look and feel | File | Result |
|---|---|---|---|
| Tree | system (Initiative 31 default) | `tree-native.png` | pending run |
| Tree | Metal | `tree-metal.png` | pending run |
| Table | system | `table-native.png` | pending run |
| Table | Metal | `table-metal.png` | pending run |
| List | system | `list-native.png` | pending run |
| List | Metal | `list-metal.png` | pending run |
