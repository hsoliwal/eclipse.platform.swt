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
package org.eclipse.swt.snippets.m3.gallery;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/**
 * Runs an example's <code>main</code> in this JVM and closes it again: a watcher thread finds the Display the example
 * creates, prints every open shell into a PNG after a delay and disposes the shells, so the example's event loop ends.
 * No example needs a hook for this. A result is <code>PASS</code> (main returned), <code>FAIL</code> (main threw, or the
 * snapshot failed) or <code>TIMEOUT</code> (no Display, or main did not return).
 * <p>
 * Usage: <code>M3ExampleRunner &lt;class&gt; [args...]</code> runs one example; <code>M3ExampleRunner --batch &lt;label&gt;
 * &lt;results.tsv&gt; &lt;snapshot-dir&gt; [first-entry]</code> runs every gallery entry (this JVM's
 * <code>-Dswt.widget.mode</code> applies to all of them) and appends one TSV line per entry; it exits with 3 after a
 * TIMEOUT whose example did not return, so that a script resumes at the next entry in a fresh JVM.
 * </p>
 */
public final class M3ExampleRunner {
	/** Columns of a result line. */
	public static final String HEADER = "label\tentry\tclass\targs\tstatus\tmillis\tsnapshot\tdetail";
	/** Delay before the shells are printed and closed (<code>-Dm3.runner.delay</code>, ms). */
	public static final int DELAY = Integer.getInteger ("m3.runner.delay", 1500);
	/** Time an example may take to open its first shell and return (<code>-Dm3.runner.timeout</code>, ms). */
	public static final int TIMEOUT = Integer.getInteger ("m3.runner.timeout", 60000);

	/** The outcome of one run. */
	public record Result (String className, String args, String status, long millis, String snapshot, String detail) {
		/** One TSV line (tabs and line breaks of the detail are flattened). */
		public String tsv (String label, int entry) {
			return String.join ("\t", label, String.valueOf (entry), className, args.isEmpty () ? "-" : args, status, String.valueOf (millis), snapshot, detail.replaceAll ("[\t\r\n]+", " | "));
		}
	}

	private M3ExampleRunner () {
	}

	public static void main (String [] args) throws Exception {
		if (args.length == 0) {
			System.out.println ("usage: M3ExampleRunner <class> [args...] | --batch <label> <results.tsv> <snapshot-dir> [first-entry]");
			return;
		}
		if ("--batch".equals (args [0])) {
			System.exit (batch (args [1], Paths.get (args [2]), Paths.get (args [3]), args.length > 4 ? Integer.parseInt (args [4]) : 0));
		}
		onBlocked = detail -> {
			System.out.println ("TIMEOUT " + detail);
			System.exit (3);
		};
		Result result = run (args [0], Arrays.copyOfRange (args, 1, args.length), null);
		System.out.println (result.tsv ("single", 0));
		System.exit ("PASS".equals (result.status ()) ? 0 : 1);
	}

	/**
	 * Runs every gallery entry from <code>first</code> and answers 0. An example whose main does not return blocks this
	 * thread: the watcher then appends its TIMEOUT line and ends the JVM with 3 (see {@link #onBlocked}).
	 */
	public static int batch (String label, Path results, Path snapshots, int first) throws IOException {
		Files.createDirectories (snapshots);
		if (!Files.exists (results)) append (results, HEADER);
		for (int i = first; i < M3GalleryIndex.SIZE; i++) {
			String [] entry = M3GalleryIndex.ENTRIES [i];
			Path png = snapshots.resolve (String.format ("%s-%03d-%s.png", label, i, simpleName (entry [1])));
			int index = i;
			onBlocked = detail -> halt (results, new Result (entry [1], entry [2], "TIMEOUT", -1, "-", detail).tsv (label, index));
			append (results, run (entry [1], M3GalleryModel.args (entry [2]), png).tsv (label, i));
		}
		return 0;
	}

	/** Called by the watcher when an example's main does not return after its shells were closed. */
	static volatile java.util.function.Consumer<String> onBlocked = detail -> {
	};

	static void append (Path file, String line) throws IOException {
		Files.writeString (file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	/**
	 * Records the blocked entry and ends the JVM with 3 (the script resumes at the next entry); System.exit runs the
	 * shutdown hooks (for example a coverage agent writing its data), which need no UI thread.
	 */
	static void halt (Path results, String line) {
		try {
			append (results, line);
		} catch (IOException e) {
			e.printStackTrace ();
		}
		System.exit (3);
	}

	static String simpleName (String className) {
		return className.substring (className.lastIndexOf ('.') + 1);
	}

	/** Runs one example on this thread; <code>png</code> receives the first shell's pixels (null: no file). */
	public static Result run (String className, String [] args, Path png) {
		long start = System.nanoTime ();
		AtomicReference<String> snapshot = new AtomicReference<> ("-"), problem = new AtomicReference<> ("");
		Thread watcher = watch (Thread.currentThread (), png, snapshot, problem);
		String failure = invoke (className, args);
		watcher.interrupt ();
		join (watcher);
		long millis = (System.nanoTime () - start) / 1_000_000;
		disposeLeftover ();
		String detail = failure != null ? failure : problem.get ();
		String status = failure != null ? "FAIL" : detail.startsWith ("TIMEOUT") ? "TIMEOUT" : detail.isEmpty () ? "PASS" : "FAIL";
		return new Result (className, String.join (" ", args), status, millis, snapshot.get (), detail.replaceFirst ("^TIMEOUT ", ""));
	}

	/** Calls the example's main; null when it returned, else the failure with its stack. */
	static String invoke (String className, String [] args) {
		try {
			Method main = Class.forName (className).getMethod ("main", String [].class);
			main.invoke (null, (Object) args);
			return null;
		} catch (InvocationTargetException e) {
			return stack (e.getCause ());
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return stack (e);
		}
	}

	/** The throwable and its frames, one line each. */
	public static String stack (Throwable t) {
		StringWriter out = new StringWriter ();
		t.printStackTrace (new PrintWriter (out));
		return out.toString ().trim ();
	}

	/** A Display the example left undisposed would make the next example's Display fail. */
	static void disposeLeftover () {
		Display display = Display.findDisplay (Thread.currentThread ());
		if (display != null && !display.isDisposed ()) display.dispose ();
	}

	/** Starts the watcher: wait for the example's Display, then close it after the delay; give up after the timeout. */
	static Thread watch (Thread ui, Path png, AtomicReference<String> snapshot, AtomicReference<String> problem) {
		Thread watcher = new Thread (() -> supervise (ui, png, snapshot, problem), "M3ExampleRunner watcher");
		watcher.setDaemon (true);
		watcher.start ();
		return watcher;
	}

	/** The watcher's work: a late shell is still closed (TIMEOUT noted); an example that never ends is reported blocked. */
	static void supervise (Thread ui, Path png, AtomicReference<String> snapshot, AtomicReference<String> problem) {
		Display display = awaitDisplay (ui);
		if (display == null && Thread.currentThread ().isInterrupted ()) return;
		if (display == null) {
			problem.set ("TIMEOUT no shell within " + TIMEOUT + " ms");
			display = awaitDisplay (ui);
			if (display == null && !Thread.currentThread ().isInterrupted ()) onBlocked.accept ("main did not open a shell in " + 2 * TIMEOUT + " ms" + where (ui));
			if (display == null) return;
		}
		close (display, png, snapshot, problem);
		if (sleep (TIMEOUT)) onBlocked.accept ("main did not return " + TIMEOUT + " ms after its shells were closed" + where (ui));
	}

	/** Where the example's thread is: its top frames, for the TIMEOUT detail. */
	static String where (Thread ui) {
		StringBuilder out = new StringBuilder ("; example thread at");
		Arrays.stream (ui.getStackTrace ()).limit (12).forEach (frame -> out.append ("\n\tat ").append (frame));
		return out.toString ();
	}

	static void join (Thread thread) {
		try {
			thread.join (2000);
		} catch (InterruptedException e) {
			Thread.currentThread ().interrupt ();
		}
	}

	/** The Display of the example's thread, once it exists and has a shell; null after the timeout or an interrupt. */
	static Display awaitDisplay (Thread ui) {
		for (long end = System.nanoTime () + TIMEOUT * 1_000_000L; System.nanoTime () < end; ) {
			Display display = Display.findDisplay (ui);
			if (display != null && !display.isDisposed () && hasShell (display)) return display;
			if (!sleep (50)) return null;
		}
		return null;
	}

	/**
	 * True when the display has an open shell, asked on its own thread without blocking the watcher: an example busy on
	 * its UI thread does not answer within the poll and counts as having no shell yet (the timeout then applies).
	 */
	static boolean hasShell (Display display) {
		java.util.concurrent.CompletableFuture<Boolean> open = new java.util.concurrent.CompletableFuture<> ();
		try {
			display.asyncExec (() -> open.complete (display.getShells ().length > 0));
			return open.get (1, java.util.concurrent.TimeUnit.SECONDS);
		} catch (SWTException | java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread ().interrupt ();
			return false;
		}
	}

	/** Sleeps; false when interrupted (the interrupt flag stays set). */
	static boolean sleep (long millis) {
		try {
			Thread.sleep (millis);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread ().interrupt ();
			return false;
		}
	}

	/** After the delay, on the UI thread: print the first shell, then dispose every shell. */
	static void close (Display display, Path png, AtomicReference<String> snapshot, AtomicReference<String> problem) {
		try {
			display.asyncExec (() -> display.timerExec (DELAY, () -> {
				try {
					if (png != null) snapshot.set (snapshot (display, png));
				} catch (RuntimeException | SWTError e) {
					problem.set ("snapshot failed: " + stack (e));
				}
				for (Shell shell : display.getShells ()) shell.dispose ();
			}));
		} catch (SWTException e) {
			/* the example disposed its Display first: nothing to close */
		}
	}

	/** Prints the first visible shell into a PNG; answers the file name. */
	static String snapshot (Display display, Path png) {
		Shell shell = Arrays.stream (display.getShells ()).filter (Shell::isVisible).findFirst ().orElse (display.getShells () [0]);
		Point size = shell.getSize ();
		Image image = new Image (display, Math.max (1, size.x), Math.max (1, size.y));
		GC gc = new GC (image);
		try {
			shell.print (gc);
		} finally {
			gc.dispose ();
		}
		ImageLoader loader = new ImageLoader ();
		loader.data = new ImageData [] {image.getImageData ()};
		image.dispose ();
		loader.save (png.toString (), SWT.IMAGE_PNG);
		return png.getFileName ().toString ();
	}
}
