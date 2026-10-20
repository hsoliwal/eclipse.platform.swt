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

The GitHub one-pass harvest (brief GITHUB_UI_LIBS_ONE_PASS.md) adds donors it finds. Owners start each behaviour from the
first ADAPT donor in its row.
