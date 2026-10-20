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
