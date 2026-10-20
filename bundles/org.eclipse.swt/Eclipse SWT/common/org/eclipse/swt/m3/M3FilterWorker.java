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
 *     Hitesh Soliwal (Synexia) - debounce with a superseding check adapted from x86core jsonviewer
 *         TreeFilterControl; cancellable model-side work and one asyncExec back to the UI adapted
 *         from TV_5.0 treeviewer TreeViewerUpdater, TreeViewerWork and ViewerFilterUI
 *         (docs/m3/sort-filter/PROVENANCE.tsv)
 *******************************************************************************/
package org.eclipse.swt.m3;

import java.util.*;
import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.widgets.*;

/*
 * The asynchronous side of an M3ColumnFilter.
 *
 * - Debounce: every criterion change increments the filter's generation (an int) and re-arms one
 *   preallocated timer runnable; when it fires it returns unless the armed generation is still
 *   the current one, so a superseded change never runs and nothing is allocated per keystroke.
 * - Model work off the UI thread: one daemon thread takes the newest request (a snapshot of the
 *   criteria), compiles nothing (matches were compiled once when the change was recorded),
 *   evaluates only the changed criteria over the source rows, folds the masks word by word,
 *   applies the tree ancestor rule, compacts the view and lets a following M3ColumnSort sort the
 *   result into spare lanes. It compares the generation every CHECK rows and drops the work as
 *   soon as a newer generation exists.
 * - One asyncExec back: the result is applied on the UI thread only when its generation is still
 *   current; applying swaps lanes and fires one RESET, so a bound widget refreshes its window.
 */
final class M3FilterWorker implements Runnable {

	static final int CHECK = 4096;
	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.m3.M3ColumnFilter"); //$NON-NLS-1$

	private final M3ColumnFilter filter;
	private final Display display;
	private final Object lock = new Object ();
	private final Runnable debounced = this::debounced, applied = this::applied;
	int debounce;
	private int armed;
	private volatile boolean stopped;
	private Thread thread;
	/* the request (guarded by lock) */
	private int requested = -1, taken = -1;
	private M3FilterCriterion [] requestCriteria, requestCurrent;
	private boolean [] requestDirty;
	private IntUnaryOperator requestParents;
	private int requestSize;
	private M3CellText text;
	/* the result, published to the UI thread by asyncExec */
	private int resultGeneration = -1, resultSize;
	private M3FilterCriterion [] resultCriteria;
	private long [] resultMatched, resultKept;
	private int [] resultRows, resultRank;
	private RuntimeException failure;

	M3FilterWorker (M3ColumnFilter filter, Display display) {
		this.filter = filter;
		this.display = display;
	}

	/* UI thread: (re)arms the debounce timer for the current generation */
	void schedule () {
		armed = filter.generation;
		display.timerExec (debounce, debounced);
	}

	private void debounced () {
		if (armed != filter.generation || stopped) return;
		submit ();
	}

	/* UI thread: hands the newest criteria to the worker thread */
	void submit () {
		synchronized (lock) {
			requestCriteria = filter.pendingCriteria ().clone ();
			requestCurrent = filter.appliedCriteria ().clone ();
			requestDirty = filter.dirtyCriteria ().clone ();
			requestParents = filter.parents ();
			requestSize = filter.sourceSize ();
			text = filter.cellText ();
			requested = filter.generation;
			if (thread == null) {
				thread = new Thread (this, "M3ColumnFilter worker"); //$NON-NLS-1$
				thread.setDaemon (true);
				thread.start ();
			}
			lock.notifyAll ();
		}
	}

	/* UI thread: stops the worker thread after its current step */
	void stop () {
		synchronized (lock) {
			stopped = true;
			lock.notifyAll ();
		}
		display.timerExec (-1, debounced);
	}

	@Override
	public void run () {
		while (true) {
			int generation;
			M3FilterCriterion [] criteria, current;
			boolean [] dirty;
			IntUnaryOperator parents;
			int n;
			synchronized (lock) {
				while (requested == taken && !stopped) {
					try {
						lock.wait ();
					} catch (InterruptedException e) {
						stopped = true;
					}
				}
				if (stopped) return;
				generation = taken = requested;
				criteria = requestCriteria;
				current = requestCurrent;
				dirty = requestDirty;
				parents = requestParents;
				n = requestSize;
			}
			step (generation, criteria, current, dirty, parents, n);
		}
	}

	private void step (int generation, M3FilterCriterion [] criteria, M3FilterCriterion [] current, boolean [] dirty, IntUnaryOperator parents, int n) {
		long start = System.nanoTime ();
		try {
			boolean computed = compute (generation, criteria, current, dirty, parents, n);
			if (LOG.isDebugEnabled ()) {
				long micros = (System.nanoTime () - start) / 1000;
				LOG.debug (() -> "generation " + generation + (computed ? " computed over " + n + " rows in " + micros + " us" : " superseded after " + micros + " us")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			}
			if (!computed) return;
		} catch (RuntimeException e) {
			if (generation != filter.generation) return;
			synchronized (lock) {
				failure = e;
				resultGeneration = generation;
			}
		}
		post ();
	}

	private void post () {
		try {
			display.asyncExec (applied);
		} catch (SWTException e) {
			stopped = true;
		}
	}

	/* worker thread: the whole filter result for one generation, or false when a newer generation exists */
	private boolean compute (int generation, M3FilterCriterion [] criteria, M3FilterCriterion [] current, boolean [] dirty, IntUnaryOperator parents, int n) {
		int words = M3RowMask.words (n);
		for (int i = 0; i < criteria.length; i++) {
			M3FilterCriterion criterion = criteria [i];
			if (!dirty [i]) {
				criterion.mask = current [i].mask;
			} else if (criterion.narrows (current [i])) {
				/* a refining keystroke: only the rows the applied pattern kept are candidates */
				criterion.mask = Arrays.copyOf (current [i].mask, words);
				for (int word = 0; word < words; word += CHECK >> 6) {
					if (generation != filter.generation) return false;
					criterion.narrow (word, Math.min (words, word + (CHECK >> 6)), text);
				}
			} else if (criterion.isActive ()) {
				criterion.mask = new long [words];
				for (int start = 0; start < n; start += CHECK) {
					if (generation != filter.generation) return false;
					criterion.evaluate (start, Math.min (n, start + CHECK), text);
				}
			}
		}
		long [] matched = new long [words];
		M3FilterCriterion.fold (criteria, matched, n);
		long [] kept = Arrays.copyOf (matched, words);
		if (parents != null) M3FilterCriterion.ancestors (matched, kept, n, parents);
		int [] rows = new int [n], rank = new int [words];
		int size = M3RowMask.compact (kept, words, rows, rank);
		if (generation != filter.generation) return false;
		M3ColumnSort follower = filter.follower;
		if (follower != null) follower.prepare (rows, size, n, generation);
		synchronized (lock) {
			resultCriteria = criteria;
			resultMatched = matched;
			resultKept = kept;
			resultRows = rows;
			resultRank = rank;
			resultSize = size;
			failure = null;
			resultGeneration = generation;
		}
		return generation == filter.generation;
	}

	/* UI thread: applies the result if no newer generation exists */
	private void applied () {
		M3FilterCriterion [] criteria;
		long [] matched, kept;
		int [] rows, rank;
		int size, generation;
		synchronized (lock) {
			if (resultGeneration != filter.generation || stopped) return;
			if (failure != null) throw failure;
			criteria = resultCriteria;
			matched = resultMatched;
			kept = resultKept;
			rows = resultRows;
			rank = resultRank;
			size = resultSize;
			generation = resultGeneration;
		}
		filter.apply (criteria, matched, kept, rows, rank, size, generation);
		if (LOG.isDebugEnabled ()) LOG.debug (() -> "generation " + generation + " applied: " + size + " rows shown"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
