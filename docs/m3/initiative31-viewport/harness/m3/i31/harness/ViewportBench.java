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
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Optimization-pass measurements of the Initiative 31 Tree, Table and List (one widget and size per JVM, the mode
 * from <code>-Dswt.widget.mode</code>): wall time (median of the repetitions), bytes allocated on the SWT and the
 * Swing threads, heap retained after a full collection, and the items that exist as Swing peers (materialized
 * items with their Swing nodes). Appends tab-separated rows
 * <code>widget mode size operation median_ms min_ms allocated_bytes retained_bytes peers</code> to the output file; an
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
			tree.addListener (SWT.SetData, e -> ((TreeItem) e.item).setText ("Item " + e.index));
			tree.setItemCount (size);
			shell.open ();
		});
		retained ("filled");
		Tree tree = (Tree) control;
		measure ("scroll", r -> tree.setTopItem (tree.getItem ((int) ((long) size * (r + 1) / (repetitions + 2)))));
		measure ("append100", r -> tree.setItemCount (tree.getItemCount () + 100));
		int children = Math.min (size, 100_000);
		measure ("expand" + children, r -> {
			TreeItem parent = tree.getItem (r);
			parent.setItemCount (children);
			parent.setExpanded (true);
		});
		measure ("collapse" + children, r -> tree.getItem (r).setExpanded (false));
		measure ("clear", r -> {
			if (r > 0) tree.setItemCount (size);
			tree.setItemCount (0);
		});
	}

	private void table () {
		measure ("fill", r -> {
			if (control != null) shell.dispose ();
			open ();
			Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
			control = table;
			table.addListener (SWT.SetData, e -> ((TableItem) e.item).setText ("Row " + e.index));
			table.setItemCount (size);
			shell.open ();
		});
		retained ("filled");
		Table table = (Table) control;
		measure ("scroll", r -> table.setTopIndex ((int) ((long) size * (r + 1) / (repetitions + 2))));
		measure ("append100", r -> table.setItemCount (table.getItemCount () + 100));
		measure ("append10000", r -> table.setItemCount (table.getItemCount () + 10_000));
		measure ("clear", r -> {
			if (r > 0) table.setItemCount (size);
			table.setItemCount (0);
		});
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
		measure ("scroll", r -> list.setTopIndex ((int) ((long) size * (r + 1) / (repetitions + 2))));
		measure ("showSelection", r -> {
			list.setSelection ((int) ((long) size * (r + 1) / (repetitions + 3)));
			list.showSelection ();
		});
		measure ("append100", r -> {
			for (int i = 0; i < 100; i++) list.add ("a" + i);
		});
		measure ("clear", r -> {
			if (r > 0) list.setItems (items);
			list.removeAll ();
		});
	}

	private void measure (String operation, Operation body) {
		long [] millis = new long [repetitions];
		long allocated = 0;
		for (int r = 0; r < repetitions; r++) {
			long before = allocated ();
			running = operation + "#" + r;
			since = System.currentTimeMillis ();
			long start = System.nanoTime ();
			body.run (r);
			pump ();
			millis [r] = (System.nanoTime () - start) / 1_000_000;
			allocated += allocated () - before;
			running = null;
		}
		long [] sorted = millis.clone ();
		Arrays.sort (sorted);
		row (operation, sorted [sorted.length / 2], sorted [0], allocated / repetitions, -1);
	}

	private void retained (String state) {
		pump ();
		for (int i = 0; i < 3; i++) System.gc ();
		MemoryUsage heap = ManagementFactory.getMemoryMXBean ().getHeapMemoryUsage ();
		row ("retained-" + state, -1, -1, -1, heap.getUsed ());
	}

	private void row (String operation, long median, long min, long allocated, long retained) {
		write (widget + "\t" + mode + "\t" + size + "\t" + operation + "\t" + median + "\t" + min + "\t" + allocated + "\t" + retained + "\t" + peers ());
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
				write (widget + "\t" + mode + "\t" + size + "\t" + operation + "\tTIMEOUT\t" + limit + "\t-1\t-1\t-1");
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
