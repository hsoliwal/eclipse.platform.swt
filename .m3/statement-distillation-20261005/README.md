# Executed SWT Java statement distillation

Base: `b3ae2d485ed706d0c63c3f21b35fcbf40f97b2e4`.
Recipe implementation and proof crate: https://github.com/hsoliwal/com.synexia/pull/8957

All 2,462 application Java files were processed serially through the existing block-normalization
and statement-composition recipes. This produces 371,027 executable atoms and 1,304 actual Java
postimages. All three tracked M3 tooling Java files add 77 atoms without source changes.

Every source passed exact reconstruction, reparse and fixed-point replay. Independent javac body
equivalence and atom census cover all 2,462 application files. The 22-module compile passed.
The rebuilt GTK/Win32/Cocoa Java fragments preserve all executable classfile content after removing
debug metadata: 708 / 1,029 / 836 classes respectively. This includes their method/API/JNI descriptors.

`statement-atoms.tsv.gz` stores every atom, its parent, kind, pattern and UTF-16 range. Literal
payload is shared with the exact delivered source. `all-files.tsv` seals the before/after source
and original canonical JSON packet for every file. `RestoreAtoms` in the linked canonical recipe
crate reconstructs all 2,465 original packets byte for byte; the restoration receipt is included.
This is an executable source composition, not just an inventory count or a list of method names.

The asset catalogue retains all 412 PNGs, including deliberately invalid/truncated image fixtures.
All 170 tracked native C/header/library payloads are byte exact; built native binaries are excluded.
Existing APIs, style atoms, examples, tests, resources, build definitions and workflows are retained.

## Gate status

The native GTK reactor is under validation. Windows/Cocoa native execution, strict canonical
repository admission, full canonical reactor and repository-owned offline dependency closure are
not claimed here. Strict-toolchain compilation currently stops at the existing missing closing
parenthesis in `ChallengeSupersetCatalog.java`; the canonical PR preserves that diagnostic.
Native payload preservation does not establish complete native semantic atomization or optimization.
These generated sources remain review candidates until the required promotion gates pass.

Candidate synthesis follows the fan-in invariant: every distinct ordered composition is retained
as a new recipe candidate with source, contract and parent lineage. Actual OpenRewrite scan/visit
phases must be verified; the serial driver deliberately completes normalization before scanning
the final source into packets.
