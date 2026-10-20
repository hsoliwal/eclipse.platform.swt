# Eclipse-wide marks (every fork, like the Problems view)

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

User, 2026-10-20: "mark all of Eclipse just like the Problems view; I have forked the projects; mechanical pass",
"mark all Eclipse UI files", "mark all files using a stream", "scaffold everything", "all Eclipse files".
All mechanical (git grep, git cat-file --batch streams, awk); re-runnable with the scripts in docs/m3/tools.

| File | What | Rows |
|---|---|---|
| ALL_FILES.tsv | every tracked non-test Java file of the 12 forks, with a UI flag (one streamed read per file) | 40,139 |
| UI_FILE_MARKS.tsv | every UI file: kind (VIEW, EDITOR, DIALOG, WIZARD, PREFERENCE, FORM_SECTION, PROVIDER, ACTION, WIDGET, UI_OTHER), viewer and widget counts, rebuild/thread/IO/map signals, behaviour families used, needs, source-adapter guess, permalink | 11,639 |
| SPEC_SCAFFOLD.tsv | a `.m3view` spec skeleton for every UI file that creates a viewer (view id, source adapter guess, presentation, behaviours, footer, view bar) | 381 |
| ECLIPSE_MARKS.tsv | every marked site from both censuses (correction sites and UI-thread sites) with a branch status: OPEN, FILE_CHANGED_ON_BRANCH (a track branch already touches the file), OPEN_NO_BRANCH_YET (fork with no track branch) | 6,968 |
| FILE_ROLLUP.tsv | one row per marked file: kind, site counts, model-listener count, needs, spec scaffold, branch status; sorted by model-listener rebuilds then sites | 2,800 |

Problems view instance: docs/m3/markers-view/PROBLEMS_VIEW_MARKS.tsv on the JFace branch (74 sites, status against
that branch). The same format applies to every file here.

Needs across UI files: THREAD 921, VIEWBAR 651, DELTA 389, TEMPLATE 381, FOOTER 381, DECORATOR 230, SCROLL_CHECKPOINTS 158,
DOOP 148, SPLIT_PANEL 86, TREE_DIALOG_CANDIDATE 80, AUTOCOMPLETE 40. Spec scaffolds with a guessed change source: 49 of 381
(observables 20, resources 5, markers 4, EMF 4, document 4, PDE model 2, search 1); the other 332 say source=TODO and
need the file visit (docs/m3/file-queue/VISIT_PROTOCOL.md).

Fix order (user, 2026-10-20: "fix the Problems view, and likewise fix all of platform.ui, and then JDT"): Problems view
(JFace branch), then platform.ui by FILE_ROLLUP order, then eclipse.jdt.ui and eclipse.jdt.debug.
