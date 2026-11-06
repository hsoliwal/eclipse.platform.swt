# Leaf 24: structured log access (M3Log)

Map row M3-LOG. SWT's own components log through `org.eclipse.swt.internal.SwtLog` (asynchronous
`java.util.logging`, lazy messages, one daemon publisher), which applications could reach only through JUL
configuration. `org.eclipse.swt.m3.M3Log` is the widget-free public accessor: levels by logger name, writing, and a
bounded ring of the last records of a name, including the records SWT writes under that name.

## Surface (package `org.eclipse.swt.m3`, public, `@since 3.138`)

| member | answer |
|---|---|
| `M3Log get(String name)` | the log of a JUL logger name |
| `void setLevel(int)` / `boolean isEnabled(int)` | `TRACE`, `DEBUG`, `INFO`, `OFF` mapped to FINEST, FINE, INFO, OFF |
| `void log(int level, Supplier<String>)` | written through `SwtLog` (message computed on the caller, published by the log thread) |
| `void keep(int capacity)` / `String[] events()` | a ring of the last records as "LEVEL message", oldest first; 0 forgets it |

## Contract

- A level change takes effect at once; a disabled level does not compute the message.
- The ring holds the last N records in publication order, including SWT's own records of the same name.
- No synchronous I/O on the calling thread: a handler that blocks does not block the writer.

## DOOP and donors

One fixed `String[]` ring per kept log with a modular index (no per-record object beyond the message). Donors
(`FOSS_REUSE_DECISION.tsv`): `SwtLog` (first-party, reused as it is) and the JDK's `MemoryHandler` (circular buffer of
records, GPL-2.0 with the Classpath exception: pattern only). Algorithm: `ALGORITHM_CATALOGUE.tsv` ring buffer.

## Status (2026-10-31)

| gate | result |
|---|---|
| JUnit, headless | `Test_M3Log` 5/5 (widget-free: no widget mode, no official-jar differential applies) |
| coverage (JaCoCo 0.8.15) | lines 39/39, methods 16/16; branches 28/30, the other 2 are `SWT.error` probe artifacts: every reachable branch covered |

Open: GTK and Cocoa run the same common code (not run there).
