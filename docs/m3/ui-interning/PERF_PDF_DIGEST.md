# Digest: "Java Performance: Common Things" (H. Soliwal, 2010)

Source: `C:/Users/hsoliwal/Desktop/Performance_Tuning_For_Java.pdf` (read only), sha256
`6463569011631186591c3080948e39b598a285d20d739e0ab5e8592bcb83dd74`. The document is the user's own, published
under the New BSD License. Only short quotes appear here; everything else is a paraphrase.

## How the digest was made (CPU only, no LLM in the extraction)

1. `pdftotext -enc UTF-8 -layout` gave 1,358 lines (text sha256 `5ffe272a...6efa`).
2. `awk` split the text at the 20 upper-case section headings into 21 leaf files: intro, the "memory or CPU" gate, and
   the 19 technique sections.
3. Map pass: the canonical `E:/tools/cpu_multipass_summarize.py --target 4 --chunk 40` ran once per section.
4. Reduce pass: the same summarizer ran over the whole text (`--target 25`), output sha256 `713d3ab8...b78c`.
5. A reviewer read every pass-1 extract against its section and assigned each technique to the tracks below.
   That mapping is the only judgement in this digest; the per-section extracts are reproducible byte for byte.

Scratch outputs (regenerable): `S:/m3-scratch/ui-interning/pdf/{sec8,p1,pass2_global.txt}`.

## The organizing rule

"FIRST, ESTABLISH WHETHER YOU HAVE A MEMORY OR A CPU PROBLEM." The book splits every technique into a memory half and
a CPU-time half. Two more rules from the introduction carry over unchanged: measure before tuning ("Use profilers to
identify bottlenecks"), and change one thing at a time. Both are already programme law: oracles before mirroring, and
the recorded differential.

## Techniques, mapped to our tracks

Tracks: **VP** = viewport mediators (SWT Tree/Table/List, Nebula Grid), **COL** = M3 collections (`m3/collections`),
**SW** = M3JDK Swing/AWT DOOP caches, **RM** = render modes (custom/m3/gpu drawn planes). **UI-INT** = this leaf
(label pool, text extents, path trie).

| # | Section | Technique (paraphrase) | Where it lands | Status |
|---|---|---|---|---|
| 1 | Divide and conquer | Store a node's index as segments shared with its ancestors; reuse common segments through a pool rather than one full path string per node. The book's `Path(name, parent)` class with `StringPool.addorGet(name)` is a path trie with pooled names. | **UI-INT** `M3TreePathTrie` (one int per path, prefixes shared) + `M3LabelPool` (pooled segment names). | Built in this leaf |
| 2 | Divide and conquer | "Interned string: I am not sure if, how and when they are garbage collected." | **UI-INT**: no `String.intern()`; a per-model pool with refcounts and bounded LRU of released labels, so lifetime is explicit (TIL law). | Built in this leaf |
| 3 | Divide and conquer | Linear child lookup by name (`getChild` loops over children). | **UI-INT**: child lookup is one hash probe on the (parent path id, segment) edge key. | Built in this leaf |
| 4 | Strings | Many strings cost per-object overhead; char arrays are cheaper; StringBuilder in loops. | **UI-INT**: label text lives in one char arena; Strings are lazy facades made only when an API asks. **COL**: M3String composite (no flatten) is the JDK-level form. | Built in this leaf |
| 5 | Strings | Convert primitive data held in strings to primitives. | **VP/UI-INT**: rows keep `int` label ids, extents are packed `long`s; sizes and dates in Table columns stay numbers until painted. | Built (labels); adoption row for numbers |
| 6 | Primitives | Primitives and arrays over collections; "Do not get tempted to use readymade structures"; the 24-byte `Entry` per list element. | **COL** law: arrays + offsets + masks, no Entry objects. Every class in this leaf follows it. | Programme law |
| 7 | Fields vs. locals | A parameter promoted to a field holds heap when unused; scope variables tightly; lazy initialization. | **VP**: facades hold no state the lanes do not hold (DOOP). **UI-INT**: String facades and extents are created on demand. | Programme law |
| 8 | Proxies | A light descriptor answers most questions; the heavy object loads on demand (Eclipse plug-in XML). | **VP**: TreeItem/TableItem are facades over lanes; **SW**: TreePath/Rectangle only on API demand (M3 layout caches). | Done in VP/SW |
| 9 | Flyweight & pooling | Share immutable objects; pool only when the pool is cheaper than creation. | **UI-INT**: the label pool is a flyweight store for text; Event objects are reused from an LRU (brief rule 7). | Built in this leaf |
| 10 | Caching | Cache what is costly to regenerate and used often; tune memory against CPU; LRU/MRU control; "If memory is limited, it may be a sensible to not cache at all, particularly strings." | **UI-INT** `M3TextExtentCache`: (label id, font id) to packed extent, bounded, invalidated on font/DPI change; strings are not cached as objects, only as arena chars. | Built in this leaf |
| 11 | Paging | Consumers take pages; the client sees only a portion, so do not process everything up front (`PagedList`). | **VP**: the resident window is the page; **COL** `M3WindowView`. | Done in VP |
| 12 | Minimize GC | Reuse objects (JFace reuses TreeItems); no allocation in loops; propagate deltas, not rebuilds (Eclipse resource deltas); simple int-token protocols. | **VP/JFace**: ONE model, delta processing end to end (memory `feedback-one-model-delta-processing`). **UI-INT**: label ids are the int tokens; a refresh re-interns into the same pool and keeps path ids, so expansion survives without rebuild. | Built (ids); JFace adoption row |
| 13 | Filters | Pre-filter before creating end objects (the Markers view pre-filters by resource and type). | **VP** sort/filter permutation index + bitmask; **UI-INT**: FilteredTree ancestor marking runs over path ids, never over item objects. | Built (marking) |
| 14 | Misc. (memory) | Pre-size collections; null out references (leak size); clone for binary copies. | **COL**: growth policies (`M3LaneGrowth`); **UI-INT**: released ids clear their String facade slot at once. | Built in this leaf |
| 15 | Misc. (CPU) | Arrays and primitives are fastest; choose the right structure; profile; buffered I/O; avoid synthetic accessors on hot paths. | Rule 21 catalogue: every structure choice is recorded with its category and measured speed. | Programme law |
| 16 | Use the information available | Decide whether an event touches you before reacting; keep cheap by-products (a stale flag). | **UI-INT**: `String.hashCode()` is cached by the JDK, so the pool keys on it instead of rehashing; the extent cache keeps width and height from one measurement. **VP**: deltas outside the window change extents only. | Built in this leaf |
| 17 | Loops | Hoist invariants; locals over fields; no allocation inside loops; combine loops; nest loops by cost; know the API (the `addAllChildPath` fix); turn costly expressions into lookups. | **UI-INT**: widest-row computation measures each distinct label once (lookup per row). **RM**: per-frame paint loops hoist font/colour state. | Built (widest) |
| 18 | Loops | `saveVisible()/restoreVisible()` called per item inside `doStep`: hoist the bracket out of the per-item loop. | **VP** mediator phases already bracket a whole rebuild (capture, plan, commit, restore focus once). | Done in VP |
| 19 | Threading | Too many threads thrash context switches; too few starve; use pools. | **RM** gpu frame pacing; **COL** model jobs off the UI thread (brief rule 5). | Existing tracks |
| 20 | Synchronization | Synchronize only what is shared (Vector's needless locks). | **UI-INT**: the pool is owned by one model (UI thread), so it has no locks; concurrent use goes through the model owner. | Built in this leaf |
| 21 | Deadlocks / locking schemes | Acquire locks in one order; combine optimistic and pessimistic locking; lock-compatibility tables. | **COL** concurrent lanes; **SW** EDT confinement. | Existing tracks |

## What this leaf takes from the book

- The book already sketches the tree path trie with pooled names (technique 1) and names the open question about
  `String.intern()` lifetime (technique 2). `M3TreePathTrie` + `M3LabelPool` answer both in lanes: one `int` per path,
  shared prefixes, pooled names, explicit refcounts, and bounded eviction of released labels.
- Caching text extents (technique 10) with the book's own warning about strings: only ints and packed longs are
  cached, never a String per row.
- Delta propagation (technique 12): a refresh reuses path ids and label ids, so expansion and selection survive a full
  rebuild without any per-row object.

Short quotes used: 5 (all under 20 words). No code from the book is copied.
