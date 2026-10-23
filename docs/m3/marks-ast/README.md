# Marks from the compiler's AST (pass seven) and the inline anchors (annotate)

Rule 33 (no second AST): `M3MarksPlugin` is a javac plugin that runs inside the JDK21 gate compile of the bundle
(`-processorpath <crate jar> -Xplugin:"M3Marks <crate-dir> <out.tsv>"`) and writes one row per site from the
trees javac already attributed: calls and constructors matching the compiled METHOD_PATTERNS, lambdas, anonymous
and named implementations of the listener types, loops (M32), String concatenation (M33) and methods whose body
exceeds 15 lines (ML, METHOD_SPLIT). No source is re-parsed.

| Bundle | AST marks | Differential against the grep marks of passes three and four |
| --- | --- | --- |
| org.eclipse.ui.ide | 4319 | BOTH 1993, AST only 1184, grep only 3673 |

Grep-only rows are regex over-matches or templates the compiled patterns do not cover yet (the catalogue
feedback, MOP); AST-only rows are regex under-matches (multi-line calls, supertype declarations) and the new
AST-only kinds (ML). Files: `ui.ide/ui.ide.marks.tsv` (template, target, kind, path, line, matched),
`ui.ide/ui.ide.DIFFERENTIAL.tsv` (template, path, line, BOTH/AST/GREP), `ui.ide/ui.ide.tags.tsv`.

The anchors in this branch (`tag_sites.awk`, one per line and template, appended at the end of the marked
line, fixed point) have the CNPCDA form `//FLLDCIM: MARK — <template> <target> <kind>`: the agent that fixes
a file reads what needs fixing in the file itself (user 2026-10-20: "keep building TODO or comments as tags of
what needs fixing"). Anchors are append-only; they survive every refactoring pass.
