# SWT exact historical source recovery (2026-11-02)

**STATUS: historical Git-object custody, NOT widget activation.**

Synexia's 2026-10-30 audit `SWT_TIP_CONTENT.tsv` pinned at `6f54420f3d716f6ec3ba480fb45514833a73128a` classified 912 historical versions LOST. These represent 744 distinct original-path/version pairs and 742 exact Git blob objects. Their original contents have been restored as inert `.sourceblob` references under `objects/`; `INDEX.tsv` preserves original paths, SHA, historical merge and classification.

Base SWT master is `108fe6f117a8004fff3846bff8322554040b3dd8`. **No active SWT source, tests, event contract, renderer, virtual behavior, JNI or native API has been altered.**

The old audit is stale: `TreeViewport`, `ListViewport` and `RowRenderer` source paths checked on current master are present. Revalidate exact current code, moves/renames and intentional deletions before selecting a donor atom. Any restored behavior must come from canonical Synexia Maven/OpenRewrite recipe execution and independent native/Java21/SWT/JUnit/CI qualification. Public product branches may not host canonical recipe code. This is passive source history only, with draft HOLD.
