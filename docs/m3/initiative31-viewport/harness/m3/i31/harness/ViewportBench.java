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
package m3.i31.harness;

import java.io.*;
import java.lang.management.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;

import javax.swing.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Optimization-pass measurements of the Initiative 31 Tree, Table and List (one widget and size per JVM, the mode
 * from <code>-Dswt.widget.mode</code>): wall time (median of the repetitions), bytes allocated on the SWT and the
 * Swing threads, heap retained after a full collection, and the items that exist as Swing peers (materialized
 * items with their Swing nodes). Appends tab-separated rows
 * <code>widget mode size operation median_ms min_ms allocated_bytes retained_bytes peers setdata renders paints events</code>
 * (the last four per repetition: SetData events, renderer uses, paint passes, Swing model events) to the output file; an
 * operation past <code>-Dbench.limit</code> seconds leaves a TIMEOUT row and stops the run.
 * <p>
 * Usage: <code>ViewportBench &lt;out-file&gt; &lt;Tree|Table|List&gt; &lt;size&gt; &lt;repetitions&gt;</code>
 * </p>
 */
public final class ViewportBench {
	private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean ();
	private static final Logger LOG = Logger.getLogger ("m3.i31.harness.ViewportBench");

	private final Display display;
	private final String widget;
	private final int size;
	private final int repetitions;
	private final String mode = System.getProperty ("swt.widget.mode", "viewport");
	private final Path out;
	private Thread swing;
	private Shell shell;
	private Control control;
	/** The operation being measured and its start (wall clock), read by the watchdog. */
	private volatile String running;
	private volatile long since;
	/** SetData events so far (SWT thread). */
	private long setData;

	private ViewportBench (Path out, Display display, String widget, int size, int repetitions) {
		this.out = out;
		this.display = display;
		this.widget = widget;
		this.size = size;
		this.repetitions = repetitions;
	}

	public static void main (String [] args) throws Exception {
		Display display = new Display ();
		ViewportBench bench = new ViewportBench (Paths.get (args [0]), display, args [1], Integer.parseInt (args [2]), Integer.parseInt (args [3]));
		try {
			bench.run ();
		} finally {
			display.dispose ();
		}
		System.exit (0);
	}

	/** One measurement: the operation runs, then the queue is drained so Swing has painted. */
	private interface Operation {
		void run (int repetition);
	}

	private void run () {
		watch ();
		Thread [] edt = new Thread [1];
		SwingUtilities.invokeLater (() -> edt [0] = Thread.currentThread ());
		pump ();
		swing = edt [0];
		switch (widget) {
			case "Tree": tree (); break;
			case "Table": table (); break;
			default: list (); break;
		}
	}

	private void open () {
		shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setSize (420, 320);
	}

	private void tree () {
		measure ("fill", r -> {
			if (control != null) shell.dispose ();
			open ();
			Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
			control = tree;
			tree.addListener (SWT.SetData, e -> {
				setData++;
				((TreeItem) e.item).setText ("Item " + e.index);
			});
			tree.setItemCount (size);
			shell.open ();
		});
		retained ("filled");
		Tree tree = (Tree) control;
		viewportDeltas (i -> tree.setItemCount (tree.getItemCount () + 1));
		measure ("scroll", r -> tree.setTopItem (tree.getItem ((int) ((long) size * (r + 1) / (repetitions + 2)))));
		measure ("append100", r -> tree.setItemCount (tree.getItemCount () + 100));
		int children = Math.min (size, 100_000);
		measure ("expand" + children, r -> {
			TreeItem parent = tree.getItem (r);
			parent.setItemCount (children);
			parent.setExpanded (true);
		});
		measure ("collapse" + children, r -> tree.getItem (r).setExpanded (false));
		measure ("clear", r -> tree.setItemCount (size), r -> tree.setItemCount (0));
	}

	private void table () {
		measure ("fill", r -> {
			if (control != null) shell.dispose ();
			open ();
			Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
			control = table;
			table.addListener (SWT.SetData, e -> {
				setData++;
				((TableItem) e.item).setText ("Row " + e.index);
			});
			table.setItemCount (size);
			shell.open ();
		});
		retained ("filled");
		Table table = (Table) control;
		viewportDeltas (i -> table.setItemCount (table.getItemCount () + 1));
		measure ("scroll", r -> table.setTopIndex ((int) ((long) size * (r + 1) / (repetitions + 2))));
		measure ("append100", r -> table.setItemCount (table.getItemCount () + 100));
		measure ("append10000", r -> table.setItemCount (table.getItemCount () + 10_000));
		measure ("removeAll", r -> table.setItemCount (size), r -> table.removeAll ());
		measure ("clear", r -> table.setItemCount (size), r -> table.setItemCount (0));
	}

	private void list () {
		String [] items = new String [size];
		Arrays.setAll (items, i -> "Line " + i);
		measure ("fill", r -> {
			if (control != null) shell.dispose ();
			open ();
			List list = new List (shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
			control = list;
			list.setItems (items);
			shell.open ();
		});
		retained ("filled");
		List list = (List) control;
		viewportDeltas (i -> list.add ("h" + i));
		measure ("scroll", r -> list.setTopIndex ((int) ((long) size * (r + 1) / (repetitions + 2))));
		measure ("showSelection", r -> {
			list.setSelection ((int) ((long) size * (r + 1) / (repetitions + 3)));
			list.showSelection ();
		});
		measure ("append100", r -> {
			for (int i = 0; i < 100; i++) list.add ("a" + i);
		});
		measure ("setItems", r -> list.setItems (items));
		measure ("clear", r -> list.setItems (items), r -> list.removeAll ());
	}

	/** Shell sizes of the resize sweep: grow, shrink below the start, grow again, back to the start. */
	private static final int [] [] SIZES = {{420, 640}, {840, 960}, {300, 240}, {640, 480}, {420, 320}};
	/** Deltas applied while the control is hidden (brief rule 24, hidden viewports). */
	private static final int HIDDEN_DELTAS = 10_000;

	/**
	 * Viewport deltas (brief rule 24): a resize sweep, a layout pass without a size change and the hidden gate. The
	 * counters of each row show whether only the rows entering the window were asked for and painted.
	 *
	 * @param delta one model change of the widget (the hidden gate applies it {@link #HIDDEN_DELTAS} times)
	 */
	private void viewportDeltas (Operation delta) {
		countRenders ();
		measure ("resize", r -> {
			int [] next = SIZES [r % SIZES.length];
			shell.setSize (next [0], next [1]);
		});
		measure ("layout", r -> shell.layout (true, true));
		hiddenGate (delta);
	}

	/**
	 * The hidden gate: hide the control, apply the deltas, show it. Row <code>hidden</code>: the time and the counters
	 * while hidden (SetData, renders and paint passes must be 0); row <code>shown</code>: the time and the counters of
	 * the show (one paint pass).
	 */
	private void hiddenGate (Operation delta) {
		control.setVisible (false);
		pump ();
		long [] before = counters ();
		long allocated = allocated ();
		long start = System.nanoTime ();
		running = "hidden";
		since = System.currentTimeMillis ();
		for (int i = 0; i < HIDDEN_DELTAS; i++) delta.run (i);
		pump ();
		long hidden = (System.nanoTime () - start) / 1_000_000;
		long [] whileHidden = minus (counters (), before);
		row ("hidden" + HIDDEN_DELTAS, hidden, hidden, allocated () - allocated, -1, whileHidden);
		before = counters ();
		allocated = allocated ();
		start = System.nanoTime ();
		running = "shown";
		since = System.currentTimeMillis ();
		control.setVisible (true);
		pump ();
		long shown = (System.nanoTime () - start) / 1_000_000;
		running = null;
		row ("shown" + HIDDEN_DELTAS, shown, shown, allocated () - allocated, -1, minus (counters (), before));
	}

	/** SetData events, renderer uses, paint passes with renderer uses, Swing model events: so far. */
	private long [] counters () {
		return new long [] {setData, CountingPane.USES.get (), CountingPane.PASSES.get (), CountingPane.EVENTS.get ()};
	}

	private static long [] minus (long [] after, long [] before) {
		long [] delta = new long [after.length];
		Arrays.setAll (delta, i -> after [i] - before [i]);
		return delta;
	}

	/**
	 * Counts renderer uses, paint passes and Swing model events: the renderer pane of every Swing list, table and
	 * tree under the control is replaced, on the Swing thread, by a {@link CountingPane}, their models get a counting
	 * listener, and a {@link CountingRepaints} numbers the paint cycles (needs
	 * <code>--add-opens java.desktop/javax.swing.plaf.basic</code>).
	 */
	private void countRenders () {
		Container root = control.handle;
		SwingUtilities.invokeLater (() -> {
			if (!(RepaintManager.currentManager (root) instanceof CountingRepaints)) RepaintManager.setCurrentManager (new CountingRepaints ());
			swapPanes (root);
		});
		pump ();
	}

	private static void swapPanes (Container container) {
		if (container instanceof JList || container instanceof JTable || container instanceof JTree) {
			try {
				Object ui = container.getClass ().getMethod ("getUI").invoke (container);
				Field pane = null;
				for (Class<?> c = ui.getClass (); c != null && pane == null; c = c.getSuperclass ()) {
					for (Field f : c.getDeclaredFields ()) if (f.getName ().equals ("rendererPane")) pane = f;
				}
				if (pane != null) {
					pane.setAccessible (true);
					CellRendererPane old = (CellRendererPane) pane.get (ui);
					if (old != null && !(old instanceof CountingPane)) {
						CountingPane counting = new CountingPane ();
						container.remove (old);
						container.add (counting);
						pane.set (ui, counting);
					}
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOG.log (Level.WARNING, "renderer pane of " + container.getClass ().getName () + " not counted", e);
			}
			if (container instanceof JTable) ((JTable) container).getModel ().addTableModelListener (e -> CountingPane.EVENTS.incrementAndGet ());
			if (container instanceof JList) ((JList<?>) container).getModel ().addListDataListener (new CountingListData ());
		}
		for (Component child : container.getComponents ()) {
			if (child instanceof Container) swapPanes ((Container) child);
		}
	}

	/** Counts the events of a list model. */
	static final class CountingListData implements javax.swing.event.ListDataListener {
		@Override
		public void intervalAdded (javax.swing.event.ListDataEvent e) {
			CountingPane.EVENTS.incrementAndGet ();
		}

		@Override
		public void intervalRemoved (javax.swing.event.ListDataEvent e) {
			CountingPane.EVENTS.incrementAndGet ();
		}

		@Override
		public void contentsChanged (javax.swing.event.ListDataEvent e) {
			CountingPane.EVENTS.incrementAndGet ();
		}
	}

	/** A repaint manager that numbers its paint cycles. */
	static final class CountingRepaints extends RepaintManager {
		static final AtomicLong CYCLES = new AtomicLong ();

		@Override
		public void paintDirtyRegions () {
			CYCLES.incrementAndGet ();
			super.paintDirtyRegions ();
		}
	}

	/**
	 * A renderer pane that counts each renderer it paints or takes in (to measure it), and the paint cycles in which
	 * it painted (paint passes of the control).
	 */
	static final class CountingPane extends CellRendererPane {
		private static final long serialVersionUID = 1L;
		static final AtomicLong USES = new AtomicLong ();
		static final AtomicLong PASSES = new AtomicLong ();
		static final AtomicLong EVENTS = new AtomicLong ();
		private static long lastCycle = -1;

		@Override
		public void paintComponent (Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
			USES.incrementAndGet ();
			long cycle = CountingRepaints.CYCLES.get ();
			if (cycle != lastCycle) {
				lastCycle = cycle;
				PASSES.incrementAndGet ();
			}
			super.paintComponent (g, c, p, x, y, w, h, shouldValidate);
		}

		@Override
		protected void addImpl (Component x, Object constraints, int index) {
			USES.incrementAndGet ();
			super.addImpl (x, constraints, index);
		}
	}

	private void measure (String operation, Operation body) {
		measure (operation, null, body);
	}

	/** Measures <code>body</code>; <code>setup</code> (may be null) runs before each repetition, outside the numbers. */
	private void measure (String operation, Operation setup, Operation body) {
		long [] millis = new long [repetitions];
		long allocated = 0;
		long [] counted = new long [4];
		for (int r = 0; r < repetitions; r++) {
			if (setup != null) {
				setup.run (r);
				pump ();
			}
			long [] before = counters ();
			long bytes = allocated ();
			running = operation + "#" + r;
			since = System.currentTimeMillis ();
			long start = System.nanoTime ();
			body.run (r);
			pump ();
			millis [r] = (System.nanoTime () - start) / 1_000_000;
			allocated += allocated () - bytes;
			long [] delta = minus (counters (), before);
			for (int i = 0; i < counted.length; i++) counted [i] += delta [i];
			running = null;
		}
		long [] sorted = millis.clone ();
		Arrays.sort (sorted);
		for (int i = 0; i < counted.length; i++) counted [i] /= repetitions;
		row (operation, sorted [sorted.length / 2], sorted [0], allocated / repetitions, -1, counted);
	}

	private void retained (String state) {
		pump ();
		for (int i = 0; i < 3; i++) System.gc ();
		MemoryUsage heap = ManagementFactory.getMemoryMXBean ().getHeapMemoryUsage ();
		row ("retained-" + state, -1, -1, -1, heap.getUsed (), new long [] {-1, -1, -1, -1});
	}

	/** One row; <code>counted</code>: SetData events, renderer uses, paint passes, Swing model events. */
	private void row (String operation, long median, long min, long allocated, long retained, long [] counted) {
		write (widget + "\t" + mode + "\t" + size + "\t" + operation + "\t" + median + "\t" + min + "\t" + allocated + "\t" + retained + "\t" + peers ()
			+ "\t" + counted [0] + "\t" + counted [1] + "\t" + counted [2] + "\t" + counted [3]);
	}

	private synchronized void write (String line) {
		try {
			// one row at a time, so a run stopped by its time limit keeps what it measured
			Files.write (out, java.util.List.of (line), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new UncheckedIOException (e);
		}
	}

	/** Bytes allocated so far by the SWT thread and the Swing thread. */
	private long allocated () {
		return THREADS.getThreadAllocatedBytes (Thread.currentThread ().getId ()) + THREADS.getThreadAllocatedBytes (swing.getId ());
	}

	/** Items that exist with a Swing peer: the materialized items of the tree or table (by reflection). */
	private long peers () {
		try {
			if (control instanceof Tree) {
				Object viewport = field (control, "viewport");
				if (viewport != null) return (Integer) invoke (field (field (viewport, "roots"), "items"), "size");
				return ((java.util.List<?>) field (control, "itemList")).size ();
			}
			if (control instanceof Table) {
				return ((java.util.List<?>) field (control, "itemList")).stream ().filter (o -> o != null).count ();
			}
			return 0;
		} catch (ReflectiveOperationException e) {
			return -1;
		}
	}

	private static Object field (Object target, String name) throws ReflectiveOperationException {
		for (Class<?> c = target.getClass (); c != null; c = c.getSuperclass ()) {
			try {
				Field f = c.getDeclaredField (name);
				f.setAccessible (true);
				return f.get (target);
			} catch (NoSuchFieldException e) {
				// next superclass
			}
		}
		throw new NoSuchFieldException (name);
	}

	private static Object invoke (Object target, String name) throws ReflectiveOperationException {
		Method m = target.getClass ().getDeclaredMethod (name);
		m.setAccessible (true);
		return m.invoke (target);
	}

	/**
	 * Drains the SWT queue until Swing has run everything queued before. The SWT thread never blocks on Swing: the
	 * Swing thread can be waiting for an SWT exclusive section, which only <code>readAndDispatch</code> grants.
	 */
	private void pump () {
		for (int round = 0; round < 2; round++) {
			AtomicBoolean reached = new AtomicBoolean ();
			SwingUtilities.invokeLater (() -> reached.set (true));
			while (!reached.get ()) {
				if (!display.readAndDispatch ()) LockSupport.parkNanos (200_000);
			}
			for (int i = 0; i < 1_000_000 && display.readAndDispatch (); i++) {
				// drain what Swing posted back
			}
		}
	}

	/**
	 * Stops an operation that runs past <code>-Dbench.limit</code> seconds (default 240): appends a TIMEOUT row (median
	 * = the limit in ms), logs every thread's stack and halts, so the desktop slot is freed and the ledger keeps a "no".
	 */
	private void watch () {
		long limit = Long.getLong ("bench.limit", 240) * 1000;
		Thread watchdog = new Thread (() -> {
			while (true) {
				LockSupport.parkNanos (1_000_000_000L);
				String operation = running;
				if (operation == null || System.currentTimeMillis () - since < limit) continue;
				write (widget + "\t" + mode + "\t" + size + "\t" + operation + "\tTIMEOUT\t" + limit + "\t-1\t-1\t-1\t-1\t-1\t-1\t-1");
				StringBuilder stacks = new StringBuilder ();
				Thread.getAllStackTraces ().forEach ((t, frames) -> {
					stacks.append ('"').append (t.getName ()).append ("\"\n");
					for (StackTraceElement frame : frames) stacks.append ("\tat ").append (frame).append ('\n');
				});
				LOG.log (Level.SEVERE, operation + " ran past " + limit + " ms; thread stacks:\n" + stacks);
				for (Handler handler : Logger.getLogger ("").getHandlers ()) handler.flush ();
				Runtime.getRuntime ().halt (3);
			}
		}, "bench watchdog");
		watchdog.setDaemon (true);
		watchdog.start ();
	}
}
