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

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Runtime proof of a custom widget on Initiative 31 (SWT on Swing): runs a snippet or example main class, which keeps
 * its own event loop, and after a settle time writes a screenshot of every visible shell (the Swing window printed to
 * an image), counts the distinct colours drawn, appends one row
 * <code>widget class status shells colours seconds detail</code> to the output file and halts.
 * <ul>
 * <li>AVAILABLE: a shell is shown with at least three colours and no exception was thrown or printed;</li>
 * <li>EXCEPTION: an uncaught exception on any thread, or a stack trace printed (the first one is the detail);</li>
 * <li>BLANK: shells are shown but hold fewer than three colours;</li>
 * <li>NO_SHELL: main returned, or no display or shell exists after the settle time;</li>
 * <li>TIMEOUT: the SWT thread did not run the probe within 20 seconds (a hang or a busy loop).</li>
 * </ul>
 * A main class written <code>tab:</code><i>class</i> is a Nebula example tab (<code>AbstractExampleTab</code>): it is
 * created in a shell of its own, parameters and control, as the Nebula examples view shows it.
 * Usage: <code>WidgetSmoke &lt;out.tsv&gt; &lt;shot-dir&gt; &lt;widget&gt; &lt;main-class&gt; [settle-seconds]</code>
 */
public final class WidgetSmoke {
	private static final Logger LOG = Logger.getLogger ("m3.i31.harness.WidgetSmoke");
	private static final AtomicReference<String> PROBLEM = new AtomicReference<> ();
	private static final AtomicBoolean WRITTEN = new AtomicBoolean ();

	private WidgetSmoke () {
	}

	public static void main (String [] args) {
		Path out = Paths.get (args [0]);
		Path shots = Paths.get (args [1]);
		String widget = args [2];
		String main = args [3];
		long settle = args.length > 4 ? Long.parseLong (args [4]) : 4;
		long start = System.nanoTime ();
		Thread.setDefaultUncaughtExceptionHandler ((thread, e) -> {
			problem (thread.getName () + ": " + summary (e));
			LOG.log (Level.SEVERE, "uncaught on " + thread.getName (), e);
		});
		System.setErr (new PrintStream (new ProblemTee (System.err), true));
		Thread swt = Thread.currentThread ();
		Thread watcher = new Thread (() -> watch (out, shots, widget, main, settle, swt, start), "smoke watcher");
		watcher.setDaemon (true);
		watcher.start ();
		try {
			if (main.startsWith ("tab:")) {
				runTab (main.substring (4));
			} else {
				Class.forName (main).getMethod ("main", String [].class).invoke (null, (Object) new String [0]);
			}
		} catch (InvocationTargetException e) {
			problem ("main: " + summary (e.getCause ()));
			LOG.log (Level.SEVERE, "main threw", e.getCause ());
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			problem ("main: " + summary (e));
			LOG.log (Level.SEVERE, "main could not run", e);
		}
		finish (out, widget, main, PROBLEM.get () != null ? "EXCEPTION" : "NO_SHELL", 0, 0, start);
	}

	/** Shows a Nebula example tab (parameters and control) in a shell, with an event loop until the shell closes. */
	private static void runTab (String className) throws ReflectiveOperationException {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText (simple (className));
		shell.setLayout (new FillLayout ());
		Object tab = Class.forName (className).getConstructor ().newInstance ();
		tab.getClass ().getMethod ("create", Composite.class).invoke (tab, shell);
		shell.setSize (900, 700);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
	}

	private static void watch (Path out, Path shots, String widget, String main, long settle, Thread swt, long start) {
		try {
			Thread.sleep (settle * 1000);
		} catch (InterruptedException e) {
			Thread.currentThread ().interrupt ();
		}
		Display display = Display.findDisplay (swt);
		if (display == null || display.isDisposed ()) {
			finish (out, widget, main, PROBLEM.get () != null ? "EXCEPTION" : "NO_SHELL", 0, 0, start);
			return;
		}
		java.util.List<Component> windows = new ArrayList<> ();
		int [] native_ = new int [2];
		String prefix = widget.replace ('/', '_') + "-" + simple (main) + "-";
		CountDownLatch probed = new CountDownLatch (1);
		display.asyncExec (() -> {
			try {
				for (Shell shell : display.getShells ()) {
					if (!shell.isVisible ()) continue;
					Object handle = Shell.class.getField ("handle").get (shell);
					if (handle instanceof Component) {
						windows.add ((Component) handle);
					} else {
						// the control run on the native SWT: the shell prints itself (Control.print, SWT 3.4)
						native_ [1] = Math.max (native_ [1], shootNative (shell, shots.resolve (prefix + "native-" + native_ [0]++ + ".png")));
					}
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				problem ("probe: " + summary (e));
			} finally {
				probed.countDown ();
			}
		});
		boolean answered;
		try {
			answered = probed.await (20, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			answered = false;
		}
		if (!answered) {
			finish (out, widget, main, "TIMEOUT", 0, 0, start);
			return;
		}
		int colours = native_ [1];
		int index = 0;
		for (Component component : windows) {
			colours = Math.max (colours, shoot (component, shots.resolve (prefix + index++ + ".png")));
		}
		int shells = windows.size () + native_ [0];
		String status = PROBLEM.get () != null ? "EXCEPTION" : shells == 0 ? "NO_SHELL" : colours < 3 ? "BLANK" : "AVAILABLE";
		finish (out, widget, main, status, shells, colours, start);
	}

	/** On the SWT thread: prints a native shell into an image (Control.print by reflection), saves it, counts colours. */
	private static int shootNative (Shell shell, Path file) throws ReflectiveOperationException {
		org.eclipse.swt.graphics.Rectangle bounds = shell.getBounds ();
		org.eclipse.swt.graphics.Image image = new org.eclipse.swt.graphics.Image (shell.getDisplay (), Math.max (1, bounds.width), Math.max (1, bounds.height));
		org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC (image);
		try {
			Shell.class.getMethod ("print", org.eclipse.swt.graphics.GC.class).invoke (shell, gc);
		} finally {
			gc.dispose ();
		}
		org.eclipse.swt.graphics.ImageData data = image.getImageData ();
		image.dispose ();
		BitSet seen = new BitSet (1 << 24);
		int [] line = new int [data.width];
		for (int y = 0; y < data.height; y++) {
			data.getPixels (0, y, data.width, line, 0);
			for (int pixel : line) seen.set (pixel & 0xFFFFFF);
		}
		try {
			Files.createDirectories (file.getParent ());
		} catch (IOException e) {
			throw new UncheckedIOException (e);
		}
		org.eclipse.swt.graphics.ImageLoader loader = new org.eclipse.swt.graphics.ImageLoader ();
		loader.data = new org.eclipse.swt.graphics.ImageData [] {data};
		loader.save (file.toString (), org.eclipse.swt.SWT.IMAGE_PNG);
		return seen.cardinality ();
	}

	/** Prints the window of a shell into a PNG and returns the number of distinct colours. */
	private static int shoot (Component component, Path file) {
		Window window = component instanceof Window ? (Window) component : SwingUtilities.getWindowAncestor (component);
		int [] colours = new int [1];
		try {
			SwingUtilities.invokeAndWait (() -> {
				int width = Math.max (1, window.getWidth ()), height = Math.max (1, window.getHeight ());
				BufferedImage image = new BufferedImage (width, height, BufferedImage.TYPE_INT_RGB);
				Graphics2D g = image.createGraphics ();
				window.printAll (g);
				g.dispose ();
				BitSet seen = new BitSet (1 << 24);
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) seen.set (image.getRGB (x, y) & 0xFFFFFF);
				}
				colours [0] = seen.cardinality ();
				try {
					Files.createDirectories (file.getParent ());
					ImageIO.write (image, "png", file.toFile ());
				} catch (IOException e) {
					throw new UncheckedIOException (e);
				}
			});
		} catch (InterruptedException e) {
			Thread.currentThread ().interrupt ();
		} catch (java.lang.reflect.InvocationTargetException e) {
			problem ("screenshot: " + summary (e.getCause ()));
		}
		return colours [0];
	}

	private static void finish (Path out, String widget, String main, String status, int shells, int colours, long start) {
		if (!WRITTEN.compareAndSet (false, true)) return;
		long seconds = (System.nanoTime () - start) / 1_000_000_000L;
		String detail = PROBLEM.get () == null ? "" : PROBLEM.get ().replace ('\t', ' ').replace ('\n', ' ');
		String row = widget + "\t" + main + "\t" + status + "\t" + shells + "\t" + colours + "\t" + seconds + "\t" + detail;
		try {
			Files.write (out, java.util.List.of (row), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			LOG.log (Level.SEVERE, "row not written: " + row, e);
		}
		LOG.info (row);
		System.out.flush ();
		Runtime.getRuntime ().halt (0);
	}

	private static void problem (String text) {
		PROBLEM.compareAndSet (null, text.length () > 300 ? text.substring (0, 300) : text);
	}

	private static String summary (Throwable e) {
		StackTraceElement [] frames = e.getStackTrace ();
		return e + (frames.length > 0 ? " at " + frames [0] : "");
	}

	private static String simple (String className) {
		return className.substring (className.lastIndexOf ('.') + 1);
	}

	/** Passes standard error through and keeps the first line that looks like an exception (a printed stack trace). */
	private static final class ProblemTee extends OutputStream {
		private final PrintStream target;
		private final ByteArrayOutputStream line = new ByteArrayOutputStream ();

		ProblemTee (PrintStream target) {
			this.target = target;
		}

		@Override
		public synchronized void write (int b) {
			target.write (b);
			if (b != '\n') {
				if (line.size () < 1000) line.write (b);
				return;
			}
			String text = line.toString (StandardCharsets.UTF_8);
			line.reset ();
			if (!text.startsWith ("\t") && (text.contains ("Exception") || text.contains ("Error:"))) problem ("stderr: " + text.trim ());
		}

		@Override
		public void flush () {
			target.flush ();
		}
	}
}
