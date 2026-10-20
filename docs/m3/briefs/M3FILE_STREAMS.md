# Brief: M3File streams (a completed directory read like a zip, as a stream of entries with filter and transformer lambdas)

User, 2026-10-20 (said four times): "New file API streams: read a completed directory as a stream, like a zip file, with
file and folder entries, taking filter and transformer lambdas, with entry M3File". User correction 2026-10-20: "M3File, please correct everywhere" (the entry type is M3File, extending the existing M3jdk21 M3File owner). Also: "make all UI in Eclipse first",
so this track is scheduled after the UI tracks or runs alongside without taking their resources.

## Shape (M3JDK, inside the JDK per feedback-m3jdk21-native-integration; plus a JDK-neutral copy for tools)

- `M3File`: one entry, like a ZipEntry. It has a path id (interned segments, like M3TreePathTrie), parent id, kind bits
  (FILE, DIRECTORY, SYMLINK, HIDDEN), size, mtime, and a lazy content handle (bytes, a CharSequence or a mapped
  M3String). Entries are rows in lanes; the M3File object is a flyweight cursor reused while streaming, and is only
  materialized when the caller keeps it.
- `M3Files.stream(Path root)` returns an `M3FileStream`: an ordered stream of entries, folders before their children,
  like reading a zip.
  - `.filter(Predicate<M3File>)` and `.filterDirs(Predicate<M3File>)`: pruning a directory skips its subtree.
  - `.map(Function<M3File, R>)` and `.transform(M3FileTransformer)`: content transformers, the content read lazily and
    once.
  - `.collect(...)`, `.forEach(...)`, `.toZip(OutputStream)`, `.toLanes()` (a columnar snapshot of the whole tree).
  - Parallel by subtree when asked, ordered output otherwise.
- The same API reads a zip/jar (`M3Files.stream(zipPath)`) and a git tree (`M3Files.stream(repo, commit)`, through a
  `git cat-file --batch` stream). A completed directory, a zip and a commit are one abstraction.
- "Completed" means a snapshot: entries come from one consistent listing, with an option to verify mtimes at the end;
  changes during the stream are reported, not silently mixed.
- DOOP: no File/Path object per entry unless asked; names are interned; sizes and mtimes are long lanes.

## Donors (m3-foss-donor-first)

java.nio.file.Files.walk/find and FileVisitor (JDK, ours), java.util.zip (JDK), Apache Commons Compress archive streams
(Apache-2.0: ADAPT), jimfs (Apache-2.0: pattern for in-memory trees), JGit TreeWalk with tree filters (EDL/BSD:
ADAPT), and the existing M3jdk21 M3File (jdk.m3.parser JMOD, M3jdk21 #1288): reuse it, do not fork a second owner.

## First consumer

The UI-file marking stream (C:/m3-superset/tools/mark_ui_files_stream.sh) is today a shell + awk stream over
`git cat-file --batch`. Port it to M3Files once the API exists; the outputs must stay byte-identical. That is the
differential.

## Proof

- Tests against Files.walk and ZipInputStream on the same trees: same entries, same order rules, same content.
- Benchmarks: entries/s and allocations per entry, before (Files.walk + Path objects) and after.
- Recipe-first: the receiver lands in M3jdk21 via a Synexia crate, with lineage rows.

## Requirements added 2026-10-20 (user notes)

1. **Default methods** carry most of the API: `M3File`, `M3FileStream` and `M3FileTransformer` are interfaces whose
   default methods give filter/map/visit/delta behaviour, so providers implement only a few primitives.
2. **Progress on both directions**: every input and output stream takes a sub-progress monitor (Eclipse
   `SubMonitor` shape: split by entries and bytes, cancellation checked per entry).
3. **Shadow streams**: secondary readers that advance as and when the main stream is read (no second read of the
   bytes), for layered parsing: a hash shadow, a line/token shadow, a structure shadow (M3AST) over one pass.
4. **Layered traversal**: the directory walk is layered the same way: the main traversal feeds shadow traversals
   (type classifier, hash, structure, delta) in one pass; the traversal order is a declared parameter.
5. **Change detection**: an M3File records content hash and structured hash (M3AST hash) and, where the provider
   has change attributes (modified time, length, file key), uses them first; hashes only when attributes differ or
   are missing. Providers with their own change mechanism (resource deltas, watch services) plug in as sources.
6. **Small by interning**: names, extensions and path segments are interned ids (TokenInterner), so an M3File is a
   few ints and longs; DOOP lanes hold the metadata of all entries, objects only on request.
7. **Deltas downstream**: two snapshots produce an M3File delta (added, removed, changed with the attribute or hash
   that changed), which downstream consumers apply instead of rescanning.
8. **DOOP, no file-system mirror**: only what the consumer declares is kept: file type (.txt, .json, .bin, ...),
   traversal order, the attributes it asked for. Never a whole file-system object graph.

Donors to inventory first (m3-foss-donor-first): Apache Commons IO `TeeInputStream`, `ObservableInputStream`,
`ProxyInputStream` (Apache-2.0) for shadow streams; Eclipse `SubMonitor` (EPL-2.0) for progress; Eclipse
`IResourceDelta`/`IResourceDeltaVisitor` and `java.nio.file.WatchService` as change sources; JGit `WorkingTreeIterator`
and `DirCache` (EDL-1.0) for attribute-first change detection with content-hash fallback.
