# M3 behaviours: built once, attachable to any widget, delivered to every view

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

User, 2026-10-20: "who knows, you might find input to enhance all APIs even further, maybe even new widgets", "widget
decorators are very, very important .. autocomplete", "what else is used so commonly", then the list: bulk selection
manipulators (move a tree node left or under a child, move the selection up or down), decorators with tooltips,
expand all / expand to level, collapse all / collapse to level, scroll to first / last selected, validators,
formatters, constrainers, numeric and string formats, disablers / enablers. Brief rule 29.

A behaviour is a first-class, composable artifact (the BOP/AnBOP layers of CLAUDE.md). It is implemented once over
the lane model, with parallel SWT and Swing (M3JDK) APIs. It attaches to any widget or viewer, is declared per view in
its `.m3view` spec, and is never re-implemented per widget. Every behaviour is delta-only (rule 24) and works at 10^6
rows with native work O(window).

## Catalogue (usage = files using the stock API in the 12 Eclipse forks, WIDGET_API_USAGE.tsv)

| Behaviour | What it does | Stock evidence (files) | Owner |
|---|---|---|---|
| Decorator + tooltip | Overlay icons/badges on controls and rows (error, warning, info, required, custom); hover tooltip per decoration; decoration ids as lanes, no per-row image objects | IDecoration 56, ControlDecoration 48, FieldDecorationRegistry 44, ToolTip 53 | custom-widgets (SWT), Swing |
| Autocomplete | Content proposals for text/combo/cells; proposals from a filter lane over interned labels; popup is a viewport list | ContentProposalAdapter + providers 63 | custom-widgets, sort-filter |
| Validator | Per field/cell validation with severity, message and decoration; batch validation over lanes; drives enablers | IInputValidator 38, IValidator, ControlDecorationSupport | enhanced-api |
| Formatter | Display formats for numbers, dates, sizes and strings, applied at paint from typed lanes (no String per cell until needed) | (StyledString 162 for styled text) | enhanced-api |
| Constrainer | Input masks and constraints (numeric only, range, length, pattern), verified on VerifyEvent and paste | Spinner 43, VerifyListener uses | enhanced-api |
| Enabler / disabler | Declarative enablement of controls and actions from selection, validation and state lanes (one evaluation per change, not per control) | MenuManager 279, action enablement | jface |
| Bulk selection mover | Move the selection up/down, indent under the previous sibling, outdent to the parent, as one M3Patch move run; multi-range selections; undoable | (custom code across views) | Tree and Table tracks |
| Expand/collapse | Expand all, expand to level N, collapse all, collapse to level N: topology-lane operations with native work O(window), no per-node items | AbstractTreeViewer expandToLevel/collapseToLevel | Tree track + jface |
| Scroll to selection | Scroll to first, last, next or previous selected item; selection is a range set, so first/last are O(1) | (custom code) | viewport core |
| Type-ahead find | Incremental find over interned labels, wrap, highlight matches | (custom code) | jface |
| Sort / filter / filter row | Multi-column sort, column filters, quick filter box | ViewerComparator 276, ViewerFilter 288, PatternFilter 92, FilteredTree 57 | sort-filter |
| Context menu | Menu contributions per row/selection, built lazily | MenuManager 279, IMenuListener 77 | jface |
| Busy / empty-state overlay | Busy indicator, progress, "no items" and "filtered out" placeholders drawn as overlay planes | BusyIndicator 227, ProgressBar 44 | render-modes |
| Styled cells and highlighters | Styled segments, striping, match highlighting, rules by lane value | StyledString 162, StyledCellLabelProvider 120 | render-modes + custom-widgets |
| Inline cell editing | Editors activated on cells, with validator + formatter + constrainer | TextCellEditor 39, EditingSupport, cell editors 130 total | jface |
| Drag and drop / reorder | DnD of selection ranges; reorder as move runs | DnD 149 | Tree and Table tracks |
| Sections / expandable panes | Forms-style sections and expandable composites with PartVisible-driven lazy content | FormToolkit 275, Section 190, ExpandableComposite 96 | custom-widgets |
| Prompt / clear / history | Placeholder text, clear button and MRU history for text fields | (SwingX PromptSupport pattern) | custom-widgets |
| Copy / export | Copy selection or all rows as text/TSV from lanes | (custom code) | jface |
| Persisted state | Sort, filters, widths, expansion and selection saved and restored | (memento code) | jface |

## Rules

- One implementation per behaviour, SWT and Swing names parallel; Swing implementations live in M3JDK.
- Attached by API (`M3Behaviours.attach(widget, ...)`) or by `.m3view` spec (`behaviours=decorations, autocomplete,
  validate, expandToLevel, mover`).
- Each behaviour has a differential where a stock equivalent exists (expandToLevel, ControlDecoration, content
  proposals), a ledger row at 10^5/10^6, and an example.
- File visitors (VISIT_PROTOCOL.md) record new behaviour candidates they find in the code. New candidates are added here first, with an owner.

## Web paradigms (user, 2026-10-20: "what else, man .. web has the most paradigms")

The web kits carry most of today's interaction paradigms. Two sources, both already harvested:
- **New widgets:** the 69-kit web universe ranked 29 widget gaps for SWT/Nebula. See hsoliwal/com.synexia develop,
  docs/M3-SCALE/runs/20261013-all-widget-viewport/universe/ULTIMATE_KIT.md. In rank order: card, avatar, icon,
  skeleton, drawer, autocomplete, kbd, kanban, bottom sheet, empty state, affix, pull-to-refresh, swipe actions,
  highlight, FAB, OTP input, tag input, stat/KPI, video, cascader, infinite scroll, hover card, tour, QR, markdown,
  virtual list, date range, sparkline, ribbon. The custom-widgets track builds them as viewport widgets, SWT and Swing,
  in the matrix.
- **Behaviour paradigms:** these attach to any widget or view, like the catalogue above.

| Paradigm | What it does | Owner |
|---|---|---|
| Debounced instant search / faceted filters / saved views | type-to-filter on a filter lane; facet counts from lanes; named filter+sort sets | sort-filter |
| Command palette | Ctrl+K: fuzzy search over commands, views, files and settings in one viewport list (Quick Access done right) | platform-ui-surfaces |
| Toast / snackbar with Undo | non-modal notifications, with undo for bulk operations (moves, deletes) | custom-widgets |
| Optimistic update + skeleton loading | show the patch at once and reconcile when the model confirms; skeleton rows while a window loads | viewport core + render-modes |
| Inline edit (click-to-edit) and dirty guard | edit in place with validator/formatter; warn on unsaved changes | jface |
| Chips / tag input / multi-select with search | packed tag-id arrays, filter lane for options | custom-widgets |
| Drag handles, sortable lists, cross-list drag (kanban) | reorder as move runs, drop between lists | Tree and Table tracks |
| Hover card / popover / tour callouts | overlay planes anchored to row or node bounds | custom-widgets |
| Empty, error and loading states | standard overlay messages per view | render-modes |
| Sticky / affix headers, sticky group rows | a sticky bit on rows, drawn at a fixed viewport position | viewport core |
| Scrollable tabs with an overflow menu, resizable split panels | CTabFolder/SashForm enrichment | all-widgets |
| Keyboard shortcuts overlay, roving focus, focus trap | accessible keyboard model per view | all-widgets |
| Grid power features (AG Grid style) | row grouping and aggregation, pivot, range selection, fill handle, clipboard paste, column pinning, master/detail | nebula-grid + sort-filter |
| Selection paradigms | shift-range, ctrl-toggle, checkbox column, "select all matching the filter" as a range set | viewport core |
| Touch paradigms | pull-to-refresh, swipe actions, long-press menus, pinch zoom (needed for "Eclipse on a tablet") | custom-widgets + RWT |
| Responsive layout and theme tokens | breakpoints for container sizes, dark/light, density | all-widgets (L&F) |
| Motion | animated expand/collapse, insert/remove transitions on the drawn planes, never blocking the model | render-modes |

Each paradigm is a behaviour or widget with an owner, a spec key (`behaviours=` in `.m3view`), a differential where a
stock equivalent exists, and an example. The GitHub one-pass harvest adds any web paradigm it finds with an owner.

## Donors first: most behaviours are already implemented (user, 2026-10-20: "that's why I said look at web UI and/or custom UI, you will get a lot already implemented")

Every behaviour starts from an existing implementation (m3-foss-donor-first): inventory the donor (repo, commit, paths,
licence), prove the contract fits, attribute, and write only the gap on our lane model. Licence routes:
- ADAPT: MIT, Apache-2.0, BSD and EPL donors; code may be adapted with notices kept.
- PATTERN_ONLY: LGPL, GPL and commercial donors; behaviour and API only.
Rows are in FOSS_REUSE_DECISION.tsv next to this file. Each owner fills in the commit and paths before reuse.

| Behaviour | Web donors (route) | Java / custom UI donors (route) |
|---|---|---|
| Decorator + tooltip | Radix Tooltip/HoverCard, MUI Badge (MIT: ADAPT) | JFace ControlDecoration/FieldDecorationRegistry (EPL: ADAPT), ControlsFX decoration (BSD-3: ADAPT), IntelliJ ComponentValidator (Apache: ADAPT) |
| Autocomplete | downshift, react-select, MUI Autocomplete (MIT: ADAPT) | JFace ContentProposalAdapter (EPL: ADAPT), ControlsFX AutoCompletionBinding (BSD-3: ADAPT), SwingX AutoCompleteDecorator (LGPL: PATTERN_ONLY) |
| Validator | Zod, React Hook Form (MIT: ADAPT) | ControlsFX ValidationSupport (BSD-3: ADAPT), JFace data-binding validators (EPL: ADAPT), IntelliJ ValidationInfo (Apache: ADAPT) |
| Formatter / constrainer | imask (MIT), Cleave.js (Apache-2.0), ECMA-402 Intl formats (spec) | java.text / java.time formats (JDK), Nebula FormattedText (EPL: ADAPT) |
| Enabler / disabler | MUI disabled state, form libraries' dependent fields (MIT) | Eclipse core expressions enabledWhen (EPL: ADAPT), JFace action enablement (EPL) |
| Bulk selection mover (up/down/indent/outdent) | react-arborist, react-complex-tree, dnd-kit sortable (MIT: ADAPT) | IntelliJ TreeUtil and move actions (Apache: ADAPT) |
| Expand/collapse to level | Ant Design Tree, react-arborist (MIT) | JFace AbstractTreeViewer.expandToLevel/collapseToLevel (EPL: ADAPT, stock reference), IntelliJ TreeUtil.expand (Apache) |
| Scroll to first/last selected | TanStack Virtual scrollToIndex (MIT) | IntelliJ ScrollingUtil (Apache: ADAPT) |
| Type-ahead find | WAI-ARIA tree/listbox typeahead pattern (W3C), Radix (MIT) | IntelliJ SpeedSearch (Apache: ADAPT) |
| Sort / filter / filter row / grouping | TanStack Table (MIT: ADAPT), AG Grid Community (MIT: ADAPT; Enterprise grouping/pivot: PATTERN_ONLY) | NatTable (EPL: ADAPT), Glazed Lists (LGPL/MPL: PATTERN_ONLY), TV_5.0 and x86core (user-owned) |
| Command palette | cmdk, kbar (MIT: ADAPT) | Eclipse Quick Access (EPL), IntelliJ Search Everywhere / GotoAction (Apache: ADAPT) |
| Toast with Undo | Sonner, react-hot-toast (MIT: ADAPT) | ControlsFX NotificationPane/Notifications (BSD-3: ADAPT), IntelliJ balloon notifications (Apache) |
| Chips / tag input / multi-select | MUI Chip, react-select multi (MIT) | ControlsFX CheckComboBox (BSD-3), Nebula widgets (EPL) |
| Drag reorder / kanban | dnd-kit, react-beautiful-dnd (MIT/Apache-2.0: ADAPT) | JFace ViewerDropAdapter (EPL) |
| Hover card / popover / tour | Radix, Floating UI (MIT: ADAPT), Shepherd (MIT) | ControlsFX PopOver (BSD-3: ADAPT) |
| Skeleton / empty / loading states | MUI Skeleton, Mantine (MIT) | IntelliJ StatusText ("Nothing to show") (Apache: ADAPT) |
| Sticky headers / pinned columns | TanStack Table, AG Grid Community (MIT) | NatTable freeze/pinning (EPL), nebula#696 (EPL) |
| Grid power features | AG Grid Community (MIT; Enterprise: PATTERN_ONLY), TanStack Table (MIT) | NatTable (EPL: ADAPT) |
| Touch paradigms | Hammer.js, use-gesture (MIT) | JavaFX gesture events (GPL+CPE: PATTERN_ONLY) |
| Motion | Framer Motion (MIT), FLIP technique | IntelliJ animators (Apache) |
| Busy / progress | MUI Progress (MIT) | JFace ProgressMonitorPart (EPL), SwingX JXBusyLabel (LGPL: PATTERN_ONLY) |
| Translucent progress overlay over a widget (user, 2026-10-20) | MUI Backdrop + CircularProgress, Mantine LoadingOverlay (MIT: ADAPT) | IntelliJ LoadingDecorator / JBLoadingPanel (Apache: ADAPT); javax.swing.JLayer + LayerUI (JDK, ours in M3JDK: ADAPT in place); SwingX JXBusyLabel (LGPL: PATTERN_ONLY). Drawn on an overlay plane above the viewport while its model window loads; input passes through or is blocked per spec; never blocks the UI thread (rule 28) |
| Breadcrumbs (user, 2026-10-20) | MUI Breadcrumbs, Ant Design Breadcrumb with overflow (MIT: ADAPT) | JDT JavaEditorBreadcrumb (EPL: ADAPT, in our jdt.ui fork), e4 breadcrumb viewer (EPL). A path bar over the tree's topology lanes (TreePath = interned segment ids), with drop-down children per segment from the same viewport list, and overflow collapsing |
| Toolbar scroll carets and path bars (user, 2026-10-20: "scroll carets on toolbar .. path bars") | MUI Tabs scrollButtons, Ant Design overflow menus (MIT: ADAPT) | SWT CTabFolder/CoolBar chevrons (EPL: ADAPT, in our fork), IntelliJ ActionToolbar overflow (Apache: ADAPT). A ToolBar or path bar whose items exceed its width shows scroll carets plus a chevron menu of hidden items; items laid out as a viewport over the item lane, so only visible items are native |
| Diff viewer (user, 2026-10-20) | Monaco diff editor, diff2html, react-diff-viewer (MIT: ADAPT); Myers/patience/histogram diff (algorithms) | Eclipse Compare TextMergeViewer (EPL: ADAPT, in our eclipse.platform fork), IntelliJ diff (Apache: ADAPT), java-diff-utils (Apache-2.0: ADAPT). Side-by-side and unified views as one viewport over hunk lanes (line ranges, kinds), with virtualized lines, incremental re-diff of the edited region only, hunk navigation (next/previous change), and folding of unchanged runs. Owners: platform-views (Compare) with the StyledText track |
| Text folding (user, 2026-10-20) | Monaco/VS Code folding ranges (MIT: ADAPT) | JFace Text ProjectionViewer/ProjectionAnnotationModel (EPL: ADAPT, in our platform.ui fork), IntelliJ FoldingModel (Apache: ADAPT). Fold regions as interval lanes over line offsets (Fenwick for visible-line mapping), folding is a viewport delta (rule 24: lines leaving the window, extents updated), regions update from document deltas, no re-scan of the document |
| Popup find (find/replace overlay) (user, 2026-10-20) | VS Code find widget (MIT: ADAPT) | Eclipse FindReplaceOverlay (EPL: ADAPT, in our platform.ui fork), IntelliJ EditorSearchSession (Apache: ADAPT). An overlay plane over the text or tree viewport: incremental search on a model thread (superseding generation), match ranges as lanes, highlight only matches in the window, counts "n of m", next/previous, replace all as one patch; also for trees and tables (find in view) |

The GitHub one-pass harvest (brief GITHUB_UI_LIBS_ONE_PASS.md) adds donors it finds. Owners start each behaviour from the
first ADAPT donor in its row.

## Filters in depth (user, 2026-10-20: "what more .. filters")

All filters are bit lanes over the model, folded word-wide, evaluated on a model thread with a superseding generation.
A change reports ENTERED/LEFT as `old ^ new` and becomes one window patch, never a refresh. Owner: sort-filter, unless
marked otherwise.

| Filter paradigm | What it does | Donors (route) |
|---|---|---|
| Quick filter box | contains, starts-with, wildcard, regex, case, whole word; tokens with OR/NOT | TV_5.0, x86core (user-owned); JFace PatternFilter (EPL) |
| Fuzzy and camelCase matching | "NPE" finds NullPointerException; subsequence and fuzzy scores with match highlighting | JDT SearchPattern camelCase (EPL: ADAPT); fzf algorithm (MIT: ADAPT) |
| Column filters and filter row | one criterion per column, text/number/date operators, a row of inputs above the header | NatTable filter row (EPL: ADAPT); TanStack Table filterFns (MIT); AG Grid community text/number/date filters (MIT) |
| Value-list (Excel-style) filter | checkbox list of distinct values with counts, search inside, select all/none | TanStack faceted unique values (MIT: ADAPT); AG Grid set filter (enterprise: PATTERN_ONLY) |
| Range filters | numeric range slider, date presets (today, last 7 days, this month), between | Nebula range widgets (EPL); MUI Slider (MIT) |
| Faceted filters with live counts | sidebar facets (type, severity, resource, date, author), counts from lanes, combined with AND across facets and OR within | TanStack faceting (MIT: ADAPT) |
| Filter expression language | `severity:error type:java path:*/ui/* -resolved`; parsed once, compiled to lane predicates | GitHub search syntax (pattern), Kibana KQL (Elastic: PATTERN_ONLY) |
| Filter chips | active filters shown as removable chips above the view, click to edit | MUI Chip (MIT) |
| Highlight instead of hide | dim non-matching rows, keep the context, jump between matches | VS Code "filter on type" toggle (MIT) |
| Filter from a cell | context menu "filter to this value" or "exclude this value" | AG Grid / Excel pattern |
| Scope filters | selection, working set, project, open editors; set-diff on scope changes | Problems view reference (FALLBACKS_TO_DELTAS.md) |
| Tree-aware filtering | keep ancestors of matches, auto-expand to matches, counts per branch | TV_5.0 ancestor rule (user-owned); FilteredTree (EPL) |
| Saved and named filter sets | named filter+sort+column states per view, shared via preferences | Problems view filter configurations (EPL: ADAPT) |
| Refine within results | stack a new filter on the current result as a cheap AND lane | - |
| Status | "12 of 10,345 shown, 3 filters active, clear all" from lane counters | - |

## More behaviours (user, 2026-10-20: "what more")

| Behaviour | What it does | Donors (route) | Owner |
|---|---|---|---|
| Totals / footer row and aggregates | sum, count, min, max and average per column and per group, updated by deltas | NatTable summary row (EPL), TanStack aggregation (MIT) | sort-filter + nebula-grid |
| Conditional formatting, heatmap, sparkline, progress and rating cells | cell renderers driven by lane values and theme keys | Nebula (EPL), AG Grid community renderers (MIT) | render-modes + custom-widgets |
| Change flash | briefly highlight rows or cells that a delta changed (live data views) | AG Grid flashCells (MIT) | render-modes |
| Row details (master-detail) | expandable detail pane under a row, materialized only in the window | TanStack expanding (MIT), NatTable (EPL) | Table track |
| Tri-state check cascading | CheckboxTreeViewer grayed states as bit lanes, propagated in O(changed + depth) | JFace CheckboxTreeViewer (EPL: stock reference) | Tree track |
| Lazy children with "load more" | placeholder child with a spinner, async children, error rows | JFace DeferredTreeContentManager (EPL) | jface |
| Link with editor / outline sync | the selection follows the active editor through tree paths | Eclipse link-with-editor (EPL) | jface |
| Sticky scroll | the enclosing scopes stay pinned at the top while scrolling (trees and editors) | Eclipse 4.31 sticky scrolling (EPL: in our fork), VS Code (MIT) | jface + StyledText track |
| Code minings and inline hints | inline annotations in text without a re-layout of the whole document | JFace Text code minings (EPL: in our fork) | StyledText track |
| Zoom | Ctrl+wheel font zoom per view, as a viewport delta | Eclipse zoom commands (EPL) | all-widgets |
| Undo/redo of view edits | moves, edits and deletes as inverse patches | JFace IUndoableOperation (EPL) | enhanced-api |
| Accessibility, RTL and i18n formats | screen-reader names from lanes, RTL mirroring, locale formats | SWT Accessible (EPL: stock reference) | all-widgets |
| Print and export | visible or all rows to CSV, TSV, HTML or PDF, streamed from the lanes | NatTable export (EPL) | jface |

## Split panels: SashForm done right (user, 2026-10-20: "SashForm is a pain")

| Problem with stock SashForm | M3 split panel |
|---|---|
| weights are bare int ratios; no min/max pane sizes | per-pane min/max/preferred sizes in px or %, honoured during drag and resize |
| no collapse or maximize of a pane | collapsible panes (button or double-click on the sash), maximize/restore, remembered sizes |
| every drag step re-lays out every child | drag is a viewport delta: only the two neighbours re-layout (live), or a ghost sash with one layout on release (configurable) |
| no keyboard resizing, snapping or double-click restore | keyboard focusable sashes (arrows, Home/End), snap points, double-click restores the last size |
| nested splits are not persisted | the whole split tree (orientation, sizes, collapsed state) saved and restored as one memento |
| one direction per SashForm, nesting by hand | multi-split layout tree (rows and columns in one control) |

Donors: IntelliJ Splitter/OnePixelSplitter (Apache-2.0: ADAPT), VS Code split view and allotment (MIT: ADAPT),
react-resizable-panels (MIT: ADAPT), SwingX JXMultiSplitPane (LGPL: PATTERN_ONLY), stock SashForm (EPL: stock
reference). Owner: all-widgets (SWT, inside the toolkit per rule 25, keeping SashForm's API and adding to it), with
the Swing parallel by the Swing track (JSplitPane enrichment) and the multi-split widget by custom-widgets. Gate: a
SashForm differential (stock API answers and layouts identical when the new options are off) and a drag sweep
counting layouts per drag step.

## SVG-skinned controls (user, 2026-10-20: "SVG-painted button to my size, instead of painting it manually")

Buttons, toolbar items, badges, chips, toggles, check and radio marks, sash grips and scroll carets take their visuals
from SVG resources, scaled to the control's current size and zoom and tinted from theme keys. There is no hand-written
paint code per control.
- Each skin is an SVG with named parts (background, border, glyph, focus ring), states (normal, hover, pressed, disabled,
  focused, checked) and optional nine-slice insets, so corners keep their radius at any size.
- Rasterized through SWT's SVG seam (SVGRasterizer + org.eclipse.swt.svg/jsvg, MIT), cached in the render-modes
  GlyphCache by (skin, part, state, size, zoom, theme generation), and re-rasterized only when one of those changes,
  so a resize is a cache miss for the new size only.
- The same skins drive Swing via M3JDK, so one asset set themes SWT and Swing.
- Donors: SWT SVGRasterizer/jsvg (MIT: ADAPT), FlatLaf FlatSVGIcon colour filters (Apache-2.0: ADAPT), IntelliJ SVG icon
  loader (Apache-2.0: ADAPT), CSS SVG background/mask techniques (web pattern).
- Owner: render-modes, with all-widgets for the L&F keys. Gate: pixel comparison against the stock button in native mode
  is unchanged (skins are opt-in per L&F), skin rendering at 100/125/150/200% zoom and 10 sizes, and no paint
  allocation after the cache is warm.

## Info popups on header, body and footer; footers on trees (user, 2026-10-20: "popups for additional information on headers, on body, on footer .. dude, trees should have a footer")

| Behaviour | What it does | Donors (route) | Owner |
|---|---|---|---|
| Header info popup | hover or click on a column header: what the column means, its unit, the active sort and filter, value statistics (count, distinct, min/max, nulls) from lanes, quick actions (sort, filter, hide, pin) | AG Grid column menu (MIT community), MUI DataGrid column menu (MIT), Radix HoverCard (MIT) | custom-widgets + sort-filter |
| Body info popup | hover card or click popover on a cell or row: full untruncated value, details, related items, actions; anchored to the cell bounds on an overlay plane, delayed and dismissable, keyboard accessible | Radix HoverCard/Popover, Floating UI (MIT: ADAPT), JFace ColumnViewerToolTipSupport (EPL), ControlsFX PopOver (BSD-3: ADAPT) | custom-widgets |
| Footer info popup | click a footer cell: the aggregate breakdown (sum, count, average, min, max, per group), switch the aggregate shown, copy | AG Grid status bar aggregation (pattern), NatTable summary row (EPL) | sort-filter |
| Tree footer | trees get a footer row like tables: per-column aggregates over all, filtered or visible rows; optional per-branch subtotal rows; updated by deltas (O(changed + depth) via subtree aggregate lanes, Fenwick-style); pinned at the bottom of the viewport | NatTable summary row (EPL: ADAPT), TanStack aggregation (MIT: ADAPT) | Tree track (SWT Tree footer inside the widget, rule 25), Swing track (JTree/JXTreeTable) |
| Table footer | the same footer for Table and Grid, with the popup above | as above | Table track, nebula-grid |

### What a footer can encode (user, 2026-10-20: "footers are missing, you can encode so much info")

Every list, table, tree and grid gets an optional footer, per column and/or as one footer bar. Each item reads from lane
counters (O(1)), updates by deltas, and has a footer popup for detail.
- **Counts:** shown / total / selected ("12 of 10,345, 3 selected"), hidden by filter, collapsed descendants.
- **Selection summary:** sum, average and count of the selected cells (spreadsheet status bar), copy the summary.
- **Per-column aggregates:** sum, count, distinct, min, max, average, percent filled; per-branch subtotals in trees.
- **Column micro-charts:** sparkline, histogram or value distribution of the column, drawn from lanes.
- **State:** active sort keys and direction, active filters as chips (click to edit or clear), grouping.
- **Freshness and activity:** last update time, live-stream rate (deltas/s), loading progress (the translucent overlay's
  twin), pending or failed loads with retry.
- **Validation and problems:** error and warning counts for the rows (click to jump), as in the Problems view title.
- **Change summary:** rows added, removed or changed since the last view or save; undo hint.
- **Paging and position:** current window position ("rows 4,001–4,060"), jump-to-row field.
- **Hints:** keyboard shortcuts for the view, configurable.

Owners: Tree and Table tracks (the footer inside the widgets, one shared API), sort-filter (aggregates and filter
chips), render-modes (micro-charts), custom-widgets (footer popups). The footer content is declared in the `.m3view`
spec (`footer=counts, selection, aggregates:sum(size), sort, filters, freshness, problems`), so every templated view
gets it.

## Double header and the configure-view bar (user, 2026-10-20: "or a double header for configuration, a configure view bar")

- **Second header row (configuration row):** a row under the column headers, aligned to the columns, one cell per column.
  Each cell holds that column's controls: a filter input (the filter row), an aggregate selector for the footer, the
  sort key position, a visibility, pin or width preset, or a unit/format choice. It scrolls horizontally with the columns
  and is part of the viewport (only visible columns are materialized). The content is declared per view:
  `header2=filters|aggregates|config`. Owners: Tree and Table tracks (inside the widgets, the same API as the footer),
  sort-filter (filter and aggregate cells).
- **Configure-view bar:** a slim bar above the view with search/quick filter, group-by, column chooser (show, hide,
  reorder, pin), density (compact, normal, comfortable), presentation (tree, flat, table), saved views (named
  sort+filter+columns+grouping), export and copy, and reset to defaults. It is the same everywhere because it comes from
  the template: `viewbar=search,group,columns,density,presentation,savedViews,export`. Collapsible, keyboard accessible,
  with toolbar scroll carets when narrow. Owners: custom-widgets (the bar widget, SWT and Swing), jface (template
  binding).
- Donors: NatTable filter row and column groups (EPL: ADAPT), AG Grid floating filters and tool panels (MIT community),
  MUI DataGrid toolbar (MIT), IntelliJ view options toolbars (Apache: ADAPT).
