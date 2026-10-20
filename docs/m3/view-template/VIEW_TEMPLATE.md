# View template: every view is the Problems view plus a spec

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

User, 2026-10-20: "make it so clean that all the other views become just templated", after "problems view is the
example of how a tree view should [work] with all its api" and "all of eclipse implemented the same way". Brief rule 27.

## Goal

The Problems view is factored into a small set of reusable parts. Converting any other view (Project Explorer, Outline,
Debug Variables, Synchronize, PDE form sections, search results, EMF editors, ...) means writing a spec, not code. A
view-specific Java class remains only for a change source that no adapter covers yet; that adapter is then added to the
template for everyone.

## The parts (owned by the JFace track, built from the Problems view)

| Part | What it is | Problems view instance |
|---|---|---|
| Lane schema | Columns and grouping keys of an element as lanes: ids, parent/child topology, int/long attributes, interned label ids, image ids, state bits | marker id, resource id, severity, type, line, category; message/location interned |
| Change-source adapter | Turns one event type into M3Patch runs on the lane schema, off the UI thread, with a superseding generation | IMarkerDelta / IResourceDelta (M3MarkerDeltas) |
| Scope and filter lanes | Bit lanes: scope set-diff, filter criteria, search text | working-set and selection scope, filters, search |
| Grouping and sorting | Permutation lanes updated incrementally | severity/type groups, comparator |
| Delta viewer | The JFace delta tree/table viewer over the public model (rule 26), PartVisible- and Viewport-listener aware (rule 25) | the markers tree |
| Counts and decorations | O(1) lane counters for titles, status line, group labels | "N errors, M warnings" |

## The spec (one per view)

A view spec is one small declarative file next to the view, `<ViewClass>.m3view`, a properties-style key=value file
parsed by the template:

```
view.id=org.eclipse.ui.views.ProblemView
element=IMarker
source=markers                 # adapter id: markers | resources | java-elements | pde-model | search-results |
                               # emf-notifications | debug-events | team-sync | observables | console-document
lanes=id:long, resource:int, severity:byte, type:int, line:int, message:label, location:label
group=severity|type|none
sort=severity desc, resource, line
filters=scope, severity, type, text
counts=severity
presentation=tree|table|flat
label=MarkerLabelProvider      # existing provider reused; strings go through the label pool
```

The template turns a spec into the view's content provider, delta wiring and viewer setup. An OpenRewrite recipe
converts the existing view class: it removes the refresh()/setInput() rebuild paths, element maps and per-element
objects listed for that view in docs/m3/correction-sites/, and binds the view to the template. The second recipe pass
must report UNCHANGED.

## Adapters needed (from the correction-sites final pass)

| Adapter id | Event | Views (sites) |
|---|---|---|
| markers / resources | IMarkerDelta, IResourceDelta | Problems, Tasks, Bookmarks, All Markers, Project Explorer, Navigator |
| java-elements | IJavaElementDelta | Package Explorer, Java Outline, breadcrumb, hierarchies |
| pde-model | IModelChangedEvent (INSERT/REMOVE/CHANGE) | all PDE form sections (28 rebuild sites) |
| search-results | ISearchResultListener elementsChanged(Object[]) | JDT, file and EGit search (8 sites) |
| emf-notifications | EMF Notification, changed resources | EMF editors (6 sites) |
| debug-events | DebugEvent sets, breakpoint and launch listeners | Variables, Expressions, Breakpoints, Launch, Memory |
| team-sync | sync info set deltas | Synchronize, Compare |
| observables | data-binding list/set/map diffs | every bound viewer (data-binding track) |
| console-document | document events | Console |

## Done means

- The Problems view itself is generated from its spec, and its differential stays at zero mismatches.
- One other view is generated from a spec with no view-specific code beyond the spec: the first proof is a PDE form
  section, then the Java Outline.
- Every later conversion is a spec plus a recipe run, recorded as one VIEW_INVENTORY row (spec path, adapter, recipe
  result, differential, delta gate, ledger numbers).

## Features every templated view gets (enrich once, every view inherits)

User, 2026-10-20: "if JFace can be enhanced, others can be enhanced; keep doing; we'll have a very rich Eclipse". Each
feature is built once in the template or JFace delta viewer, with an owner. Every view generated from a spec gets it
with no per-view code. A view that opts out says so in its spec.

| Feature | Owner | Notes |
|---|---|---|
| Multi-column sort (header clicks, sort keys, stable) | sort-filter | permutation lanes, k-way merge |
| Column filters + filter row + quick search box | sort-filter | superseding-generation filter lanes, debounced |
| Column control (show/hide/reorder/pack columns) | custom-widgets | the SwingX JXTable column-control pattern |
| Sticky group headers and group counts | jface | from the Problems view categories |
| Type-ahead find (incremental, wraps, highlights) | jface | over interned labels, no String creation |
| Copy as text/TSV, export visible or all rows | jface | streams from the lanes, no item materialization |
| Expand/collapse all, expand to level, reveal path | jface | tree topology lanes, O(window) native work |
| Lazy prefetch and release by window | enhanced-api | ViewportListener entered/left ranges |
| Hidden-view dirty rendering | all-widgets + jface | PartVisible (rule 24/25) |
| Live counts in title, status line and group rows | jface | O(1) lane counters |
| Selection as ranges (select all of 10^6 rows instantly) | viewport core | M3RangeSet |
| Row striping, highlighters, style rules | render-modes + custom-widgets | the SwingX highlighter pattern, theme keys |
| Persisted view state (sort, filters, widths, expansion) | jface | memento from the lanes |
| Keyboard navigation parity with stock (and more) | Tree/Table tracks | differential-proven |
| Large-model safety (10^6 rows smooth, no freezes) | all | ledger numbers per view |

Add each new request here first, with an owner. A feature that only one view needs still goes into the template when
it could be generic.
