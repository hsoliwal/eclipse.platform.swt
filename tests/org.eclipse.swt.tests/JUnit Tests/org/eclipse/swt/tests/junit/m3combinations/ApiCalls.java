/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3combinations;

import java.util.*;
import java.util.List;
import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;

/**
 * One call per shipped enhanced API row (the <code>api</code> column), answering what the call returned. A row whose
 * id has no call here answers <code>NO_CALL</code> and is NOT_RUN (a new leaf adds its call). Widget-free calls use the
 * subject's texts; widget calls dispatch on the subject's widget class (the matrix only pairs a row with the widgets
 * its surface names).
 */
final class ApiCalls {
	static final Map<String, Function<Subject, String>> CALLS = new LinkedHashMap<> ();
	static {
		CALLS.put ("none", s -> "none");
		CALLS.put ("M3-RANGE-SELECT", ApiCalls::rangeSelect);
		CALLS.put ("M3-VISIBLE-RANGE", ApiCalls::visibleRange);
		CALLS.put ("M3-SCROLL-INFO", s -> pick (s, t -> M3Scrolling.getScrollInfo (t), l -> M3Scrolling.getScrollInfo (l), r -> M3Scrolling.getScrollInfo (r)).toString ());
		CALLS.put ("M3-SCROLL-TO-ALIGNED", ApiCalls::scrollToAligned);
		CALLS.put ("M3-TEXT-SEGMENTS", s -> M3TextSegments.words (s.sampleText (), Locale.GERMAN).toString ());
		CALLS.put ("M3-TOKEN-CURSOR", ApiCalls::tokenCursor);
		CALLS.put ("M3-MODE-QUERY", ModeConsistency::answer);
		CALLS.put ("M3-EMPTINESS", ApiCalls::emptiness);
		CALLS.put ("M3-ROW-MATH", ApiCalls::rowMath);
		CALLS.put ("M3-RESIDENCY", s -> pick (s, t -> M3Residency.residentRange (t), l -> M3Residency.residentRange (l), r -> M3Residency.residentRange (r)).toString ());
		CALLS.put ("M3-BULK-UPDATES", ApiCalls::bulkUpdates);
		CALLS.put ("M3-PREFETCH", ApiCalls::prefetch);
		CALLS.put ("M3-DIFF-APPLY", ApiCalls::diffApply);
		CALLS.put ("M3-VISIBILITY-OBSERVER", ApiCalls::visibility);
		CALLS.put ("M3-PROXY-MODEL", ApiCalls::proxyModel);
		CALLS.put ("M3-COLUMN-SORT", ApiCalls::columnSort);
		CALLS.put ("M3-COLUMN-FILTER", ApiCalls::columnFilter);
		CALLS.put ("M3-SCROLL-RESTORE", ApiCalls::scrollRestore);
		CALLS.put ("M3-ESTIMATED-HEIGHTS", ApiCalls::estimatedHeights);
		CALLS.put ("M3-LOCALE-COLLATION", ApiCalls::collation);
		CALLS.put ("M3-TYPE-AHEAD", s -> "index " + pick (s, t -> (long) M3TypeAhead.indexOf (t, "item 00000", 0, true, true), l -> (long) M3TypeAhead.indexOf (l, "item 00000", 0, true, true), r -> M3TypeAhead.indexOf (r, "item 00000", 0, true, true)));
		CALLS.put ("M3-CHECK-TRISTATE", ApiCalls::checkState);
	}

	private ApiCalls () {
	}

	/** True when the row's API has a call. */
	static boolean has (String api) {
		return CALLS.containsKey (api);
	}

	/** Calls the API on the subject. */
	static String call (String api, Subject subject) {
		return api + " " + CALLS.get (api).apply (subject);
	}

	/** Applies the Table, List or Tree variant of a call to the subject's widget. */
	static <R> R pick (Subject s, Function<Table, R> table, Function<org.eclipse.swt.widgets.List, R> list, Function<Tree, R> tree) {
		Widget w = s.widget ();
		if (w instanceof Table t) return table.apply (t);
		if (w instanceof org.eclipse.swt.widgets.List l) return list.apply (l);
		if (w instanceof Tree r) return tree.apply (r);
		throw new IllegalArgumentException ("no Table, List or Tree variant for " + w);
	}

	static String rangeSelect (Subject s) {
		M3ItemRange [] ranges = pick (s, M3ItemSelection::getSelectionRanges, M3ItemSelection::getSelectionRanges, r -> M3ItemSelection.getSelectionRanges (r, null));
		return Arrays.toString (ranges) + " count " + M3ItemRange.countOf (ranges);
	}

	static String visibleRange (Subject s) {
		return pick (s, M3ItemSelection::getVisibleRange, M3ItemSelection::getVisibleRange, M3ItemSelection::getVisibleRange).toString ();
	}

	static String scrollToAligned (Subject s) {
		int n = s.order.length;
		if (n == 0) return "empty";
		return "top " + pick (s, t -> M3Scrolling.scrollTo (t, n / 2, M3Scrolling.CENTER), l -> M3Scrolling.scrollTo (l, n / 2, M3Scrolling.CENTER),
			r -> r.indexOf (M3Scrolling.scrollTo (r, r.getItem (n / 2), M3Scrolling.CENTER)));
	}

	static String tokenCursor (Subject s) {
		M3TokenVocabulary vocabulary = new M3TokenVocabulary (1024, true);
		String text = s.sampleText ();
		int [] tokens = M3TextSegments.words (text, Locale.ROOT).tokens (text, vocabulary, true);
		int [] needle = Arrays.copyOfRange (tokens, Math.min (1, tokens.length), Math.min (2, tokens.length));
		long mask = M3TokenMatch.presenceMask (tokens);
		return "tokens " + Arrays.toString (tokens) + " may " + M3TokenMatch.mayContain (mask, needle) + " at " + M3TokenMatch.indexOf (tokens, needle, 0);
	}

	/** Listens for emptiness crossings while the subject goes to 0 rows and back. */
	static String emptiness (Subject s) {
		StringBuilder events = new StringBuilder ();
		Listener listener = e -> events.append (e.detail == M3Emptiness.BECAME_EMPTY ? "E" : "N").append (e.count).append (' ');
		pick (s, t -> run (() -> M3Emptiness.addListener (t, listener)), l -> run (() -> M3Emptiness.addListener (l, listener)), r -> run (() -> M3Emptiness.addListener (r, listener)));
		int [] order = s.order;
		s.show (new int [0]);
		s.order = order;
		s.show (order);
		pick (s, t -> run (() -> M3Emptiness.removeListener (t, listener)), l -> run (() -> M3Emptiness.removeListener (l, listener)), r -> run (() -> M3Emptiness.removeListener (r, listener)));
		return "events [" + events.toString ().trim () + "]";
	}

	static Void run (Runnable runnable) {
		runnable.run ();
		return null;
	}

	static String rowMath (Subject s) {
		Tree tree = (Tree) s.widget ();
		TreeItem top = tree.getItemCount () == 0 ? null : tree.getTopItem ();
		return "rows " + M3RowMath.visibleRowCount (tree) + " top row " + (top == null ? -1 : M3RowMath.rowOf (top));
	}

	static String bulkUpdates (Subject s) {
		int n = s.order.length;
		Runnable edits = () -> {
			for (int i = 0; i < Math.min (3, n); i++) s.update (i, "bulk " + i);
		};
		pick (s, t -> run (() -> M3BulkUpdates.run (t, edits)), l -> run (() -> M3BulkUpdates.run (l, edits)), r -> run (() -> M3BulkUpdates.run (r, edits)));
		return s.state ();
	}

	/** Prefetch requests heard while the subject scrolls to its middle row. */
	static String prefetch (Subject s) {
		int [] heard = {0};
		Listener listener = e -> heard [0]++;
		pick (s, t -> run (() -> { M3Prefetch.setWindow (t, 5, 5); M3Prefetch.addListener (t, listener); }), l -> run (() -> { M3Prefetch.setWindow (l, 5, 5); M3Prefetch.addListener (l, listener); }),
			r -> run (() -> { M3Prefetch.setWindow (r, 5, 5); M3Prefetch.addListener (r, listener); }));
		if (s.order.length > 0) s.scrollTo (s.order.length / 2);
		Display.getCurrent ().readAndDispatch ();
		pick (s, t -> run (() -> M3Prefetch.removeListener (t, listener)), l -> run (() -> M3Prefetch.removeListener (l, listener)), r -> run (() -> M3Prefetch.removeListener (r, listener)));
		return "heard " + (heard [0] > 0 ? "some" : "none");
	}

	/** Diff from the current ids to the ids without the first row plus one new id at the end. */
	static String diffApply (Subject s) {
		long [] old = Arrays.stream (s.order).asLongStream ().toArray ();
		long [] next = new long [old.length];
		System.arraycopy (old, Math.min (1, old.length), next, 0, Math.max (0, old.length - 1));
		if (next.length > 0) next [next.length - 1] = 9_999_999;
		int [] result = pick (s, t -> M3DiffApply.apply (t, old, next, (item, id) -> item.setText (Subject.text ((int) id))), l -> M3DiffApply.apply (l, old, next, id -> Subject.text ((int) id)),
			r -> M3DiffApply.applyChildren (r, null, old, next, (item, id) -> item.setText (Subject.text ((int) id))));
		return "result " + Arrays.toString (result) + " " + s.state ();
	}

	/** Visibility events heard while the subject scrolls to its middle row. */
	static String visibility (Subject s) {
		List<String> events = new ArrayList<> ();
		Listener listener = e -> events.add (e.detail + ":" + e.index + "+" + e.count);
		pick (s, t -> run (() -> M3VisibilityObserver.addListener (t, listener)), l -> run (() -> M3VisibilityObserver.addListener (l, listener)), r -> run (() -> M3VisibilityObserver.addListener (r, listener)));
		if (s.order.length > 0) s.scrollTo (s.order.length / 2);
		while (Display.getCurrent ().readAndDispatch ()) {
			/* drain */
		}
		pick (s, t -> run (() -> M3VisibilityObserver.removeListener (t, listener)), l -> run (() -> M3VisibilityObserver.removeListener (l, listener)), r -> run (() -> M3VisibilityObserver.removeListener (r, listener)));
		return "events " + (events.isEmpty () ? "none" : "some");
	}

	static String proxyModel (Subject s) {
		M3ProxyModel proxy = new M3ProxyModel (s.order.length).addFilter (row -> row % 2 == 0).refresh ();
		pick (s, t -> run (() -> proxy.bind (t, (item, row) -> item.setText (Subject.text (row)))), l -> run (() -> proxy.bind (l, Subject::text)), r -> run (() -> {
			throw new IllegalArgumentException ("no Tree binding");
		}));
		return "size " + proxy.size () + " " + s.state ();
	}

	static String columnSort (Subject s) {
		M3ColumnSort sort = new M3ColumnSort (1, s.order.length);
		sort.setCellText ((row, column) -> Subject.text (s.order.length - 1 - row));
		sort.toggle (0, false);
		Widget w = s.widget ();
		if (w instanceof Table t) sort.install (t); else sort.install ((Tree) w);
		return "keys " + sort.getSortKeyCount () + " direction " + sort.getDirection (0) + " first " + (sort.size () == 0 ? -1 : sort.map (0)) + " " + s.state ();
	}

	static String columnFilter (Subject s) {
		M3ColumnFilter filter = new M3ColumnFilter (1, s.order.length, (row, column) -> Subject.text (row));
		filter.setText (0, "1", M3TextMatch.CONTAINS);
		return "size " + filter.size () + " first " + (filter.size () == 0 ? -1 : filter.map (0)) + " active " + filter.isActive (0);
	}

	static String scrollRestore (Subject s) {
		long memento = pick (s, M3ScrollMemento::save, M3ScrollMemento::save, M3ScrollMemento::save);
		if (s.order.length > 0) s.scrollTo (0);
		boolean restored = pick (s, t -> M3ScrollMemento.restore (t, memento), l -> M3ScrollMemento.restore (l, memento), r -> M3ScrollMemento.restore (r, memento));
		return "restored " + restored + " " + s.state ();
	}

	static String estimatedHeights (Subject s) {
		M3RowExtents extents = new M3RowExtents (s.order.length, 18);
		if (s.order.length > 2) extents.setExtent (1, 40);
		return "total " + extents.totalExtent () + " rowAt(100) " + (s.order.length == 0 ? -1 : extents.rowAt (Math.min (100, extents.totalExtent () - 1))) + " visible " + extents.visibleRange (0, 200);
	}

	static String collation (Subject s) {
		String [] items = Subject.texts (Arrays.copyOf (s.order, Math.min (1000, s.order.length)));
		if (items.length > 0) items [0] = "Äpfel";
		int [] sorted = M3Collation.keys (items, Locale.GERMAN).sortedOrder ();
		return "first " + Arrays.toString (Arrays.copyOf (sorted, Math.min (5, sorted.length)));
	}

	static String checkState (Subject s) {
		Tree tree = (Tree) s.widget ();
		if (tree.getItemCount () == 0) return "empty";
		TreeItem item = tree.getItem (0);
		M3CheckState.setChecked (item, true);
		return "state " + M3CheckState.state (item) + " checked " + item.getChecked () + " dig-in " + M3CheckState.isDigIn (tree);
	}

	/** Contract exceptions (thrown by <code>SWT.error</code> with a documented code) are answers, not failures. */
	static boolean contract (Throwable t) {
		StackTraceElement [] stack = t.getStackTrace ();
		boolean fromSwtError = stack.length > 0 && SWT.class.getName ().equals (stack [0].getClassName ());
		return (t instanceof SWTException || t instanceof IllegalArgumentException) && fromSwtError;
	}
}
