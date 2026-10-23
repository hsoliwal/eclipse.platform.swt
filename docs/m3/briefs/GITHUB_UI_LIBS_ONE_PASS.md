# Brief: one pass over every SWT, Swing and AWT library on GitHub (superset for our viewport and custom widgets)

User, 2026-10-20: "would like to consider the superset and one pass of all the SWT/Swing/AWT libs on GitHub, and even perhaps search for viewport UI, and enhance our viewport widgets or custom widgets"; "even use LeetCode and HackerRank for algos and enhance ourselves or M3JDK". Earlier: "search GitHub once".

## Reuse first (do not redo)

- PR/issue harvest: hsoliwal/com.synexia branch codex/m3-ui-donor-atomize-20261017, docs/M3-SCALE/runs/20261017-ui-donor-atomize/GITHUB_PR_HARVEST.tsv (2,911 rows) and the 77 atomized donors in the same run.
- Widget concepts: S:/m3-scratch/worktrees/nebula-widgets/docs/m3/custom-widgets/WIDGET_MATRIX.tsv (69 concepts) and WIDGET_PLATFORM_MATRIX.tsv.
- Algorithm catalogues: the ALGORITHM_CATALOGUE.tsv files under docs/m3/* on the SWT, platform.ui and M3jdk21 branches, and com.synexia's CAT-algorithm-catalogue (185 rows).
Load these first. A repo already atomized is marked KNOWN, not re-read.

## The one pass (CPU and the GitHub API; one sweep, cached)

1. Search repositories with gh api search/repositories (30 requests/minute: throttle, cache every page under S:/m3-scratch/github-ui-libs/cache):
   - topics swt, swing, awt, javafx-free Java UI, eclipse-plugin with widgets, nebula, rcp;
   - keywords: virtual table/tree/list, viewport, lazy tree, large table, data grid, tree table, outline view, docking, ribbon, chart widget, gantt, calendar, code editor, terminal, hex editor, swing look and feel, swing components, swingx, jide, glazed lists, nattable, flatlaf, darklaf, weblaf, substance, radiance.
   Keep Java repos with at least 20 stars or a recent push.
2. For each repo, read metadata and the tree listing only (no full clones): license, stars, last push, toolkit, top-level widget classes (from file names), and virtualization/viewport technique, found by grepping the raw files of at most 3 key classes.
3. Classify each repo: ADAPT (EPL/Apache/MIT/BSD: code may be adapted with headers kept), PATTERN_ONLY (LGPL/GPL/unknown: behaviour and API only), or SKIP (no fit).
4. Output in a new run dir docs/M3-SCALE/runs/20261020-github-ui-libs/ on a com.synexia branch codex/m3-github-ui-libs-20261020 (push with K:/m3-work/agents/donor-atomize/run/api_push.py if git push hangs up):
   - UI_LIBS.tsv: repo, toolkit, license, route, stars, last_push, widgets, viewport_technique, notes.
   - WIDGET_GAPS.tsv: widget concepts not yet in WIDGET_MATRIX.tsv, with the donors that have them.
   - VIEWPORT_TECHNIQUES.tsv: each distinct technique (row-height caches, virtual flows, sparse models, cell recycling, frozen panes, incremental layout), the libraries that use it, whether our viewport has it, and what to add.
   - ALGO_CATALOGUE_ADDITIONS.tsv: for each technique and hot operation, the matching LeetCode/HackerRank/GfG problem family (for example Fenwick/prefix sums, order-statistic tree, interval merge, sliding window, two pointers, LIS for moves, k-way merge, union-find, bitset rank/select, segment tree with lazy propagation), its complexity, and the target owner: SWT viewport, custom widget, or M3JDK m3/collections.
   - SUMMARY.md: the top 20 enhancements ranked by expected gain.
5. Hand-off: send each ranked enhancement to its owner agent (custom-widgets a899e848a1edd035a, viewport core aeba9473d82661f83, Tree a56862a3f7f3ea02d, Table a9cb7083090852321, Swing a4f3571116e413c4d, collections a4a54cbc5baf005fc), one message per owner, citing the TSV rows.

## Machine rules

- No code changes in this leaf (research and catalogue only).
- Never merge PRs; no Co-Authored-By or "Generated with" trailers.
- GitHub API calls are read-only.
- Scratch only under S:/m3-scratch/github-ui-libs. Never write to G:, D: or E:.
- Commit and push after each output file.

## Eclipse Corner (user, 2026-10-20: "Eclipse Corner also may have some tricks and tips")

Also read the Eclipse Corner articles index (eclipse.org/articles) once, using WebFetch with no GitHub API cost, and extract tips relevant to the programme: virtual tables and trees, jobs and the UI thread ("On the Job"), progress monitors, decorators, label providers, SWT images and resources, and performance. Output ECLIPSE_CORNER_TIPS.tsv (article, tip, programme rule it supports or contradicts, owner) in the same run dir, and hand each tip to its owner.
