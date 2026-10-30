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

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.*;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.tests.junit.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.opentest4j.*;

/**
 * The combination matrix: one parameterized case per row of <code>COMBINATION_MATRIX.tsv</code>. A row is a six-step
 * script of the recorded differential harness (create and fill, feature switch, theme and language, operation,
 * enhanced API call, dispose; the probe is the subject's state), so the reference JVM
 * (<code>-Dswt.widget.mode=original -Dswt.viewport.record=true</code>) records every row and each mode's JVM compares
 * its rows with that recording (mode-neutral answers). Every row also asserts no exception except the documented ones
 * and no leak (tracked device objects, shells and children back to their count; a first-use cache is told apart by a
 * repeat). <code>-Dm3.combinations.env=headless|display</code> selects the rows (headless rows need no Display);
 * <code>-Dm3.combinations.from=&lt;row&gt;</code> resumes after a killed JVM.
 */
@SuppressWarnings ("restriction")
public class Test_M3CombinationMatrix {
	static final String ENV = System.getProperty ("m3.combinations.env", "headless");
	static final boolean RECORD = Boolean.getBoolean ("swt.viewport.record");
	static final String RECORDING = System.getProperty ("swt.viewport.recording");
	static final boolean RESUME = Boolean.getBoolean ("m3.combinations.resume");
	static final int ROW_LIMIT = Integer.getInteger ("m3.combinations.rowLimit", 900);
	static final int STEPS = 6;
	static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor (r -> {
		Thread thread = new Thread (r, "m3 combination row watchdog");
		thread.setDaemon (true);
		return thread;
	});
	static Display display;
	static Shell shell;

	/**
	 * The rows this JVM runs: the reference runs every row of its environment, a mode JVM the rows of its mode; rows of
	 * 10^5 items last; with <code>-Dm3.combinations.resume=true</code> the rows this mode and role already ran are skipped.
	 */
	static Stream<CombinationRow> rows () throws IOException {
		String mode = System.getProperty ("swt.widget.mode", "viewport");
		Set<String> done = RESUME ? ResultLog.done (mode, RECORD ? "record" : "compare") : Set.of ();
		return CombinationRow.load (CombinationRow.matrixFile ()).stream ().filter (r -> r.env ().equals (ENV)).filter (r -> RECORD || r.mode ().equals (mode))
			.filter (r -> !done.contains (r.id ())).sorted (Comparator.comparingInt ((CombinationRow r) -> r.count () >= 100_000 ? 1 : 0).thenComparing (CombinationRow::id));
	}

	@BeforeAll
	static void open () {
		if (!"display".equals (ENV)) return;
		DeviceData data = new DeviceData ();
		data.tracking = true;
		display = new Display (data);
		shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setSize (400, 300);
		shell.open ();
	}

	@AfterAll
	static void close () {
		if (display != null) display.dispose ();
	}

	@ParameterizedTest (name = "{0}")
	@MethodSource ("rows")
	void row (CombinationRow row) throws Throwable {
		long start = System.nanoTime ();
		String [] outcome = {"-", "-"};
		Throwable failure = null;
		ScheduledFuture<?> guard = guard (row, Thread.currentThread (), start);
		try {
			execute (row, outcome);
		} catch (Throwable t) {
			failure = t;
			throw t;
		} finally {
			guard.cancel (false);
			cleanUp ();
			String status = failure == null ? "PASS" : failure instanceof TestAbortedException ? "NOT_RUN" : "FAIL";
			ResultLog.write (row, RECORD ? "record" : "compare", status, outcome [0], outcome [1], (System.nanoTime () - start) / 1_000_000, failure);
		}
	}

	/**
	 * A row that runs longer than <code>-Dm3.combinations.rowLimit</code> seconds (default 900) cannot be stopped on the UI
	 * thread: the watchdog records it as TIMEOUT with the row thread's stack and halts the JVM (exit 3); the leg resumes.
	 */
	static ScheduledFuture<?> guard (CombinationRow row, Thread runner, long start) {
		return WATCHDOG.schedule (() -> {
			AssertionError timeout = new AssertionError ("row exceeded " + ROW_LIMIT + " s; the stack is the row thread's at that moment");
			timeout.setStackTrace (runner.getStackTrace ());
			ResultLog.write (row, RECORD ? "record" : "compare", "TIMEOUT", "-", "-", (System.nanoTime () - start) / 1_000_000, timeout);
			Runtime.getRuntime ().halt (3);
		}, ROW_LIMIT, TimeUnit.SECONDS);
	}

	/** Runs the row through the harness, then the leak check; outcome = {theme/language answer, leak delta}. */
	static void execute (CombinationRow row, String [] outcome) throws IOException {
		Assumptions.assumeTrue (ApiCalls.has (row.api ()), "NO_CALL: no call for " + row.api () + " in ApiCalls");
		Map<Object, Boolean> before = census ();
		Subject subject = Subject.of (row.widget ());
		ViewportDifferential<Subject> harness = ViewportDifferential.of ("m3comb-" + row.id (), 0, script (row, subject, outcome)).seeds (1).steps (STEPS).widget (row.widget ())
			.probe ("state", Subject::state).afterStep (s -> drain ());
		harness.compareWithRecording (subject, RECORD, RECORDING);
		outcome [1] = leaks (row, before);
	}

	/** The six steps of a row, in order. */
	static ViewportDifferential.Ops<Subject> script (CombinationRow row, Subject subject, String [] outcome) {
		List<ViewportDifferential.Op<Subject>> steps = List.of (s -> create (row, s), s -> feature (row, s), s -> outcome [0] = themeLanguage (row, s), s -> s.operate (row.op (), row.count ()),
			s -> api (row, s), s -> s.disposed () ? "already disposed" : s.dispose ());
		String [] names = {"create", "feature", "theme-language", "op", "api", "dispose"};
		int [] next = {0};
		return (random, log) -> {
			log.append (names [next [0]]).append (' ');
			return steps.get (next [0]++);
		};
	}

	static String create (CombinationRow row, Subject s) {
		s.create (shell, row.styleBits ());
		if (shell != null) shell.layout (true, true);
		s.setCount (row.count ());
		return "created " + row.widget () + " " + row.style ();
	}

	static String feature (CombinationRow row, Subject s) {
		if ("none".equals (row.feature ())) return "feature none";
		int id = M3Features.id (row.feature ()).orElseThrow (() -> new AssertionError ("no feature " + row.feature ()));
		M3Features.set (s.widget (), id, true);
		return "feature " + row.feature () + " on=" + M3Features.isOn (s.widget (), id);
	}

	static String themeLanguage (CombinationRow row, Subject s) {
		return ThemeLanguage.apply ("M3Theme", row.theme (), s.widget ()) + "; " + ThemeLanguage.apply ("M3Language", row.language (), s.widget ());
	}

	/** The API call; a contract exception (SWT.error with a documented code) is an answer, everything else fails. */
	static String api (CombinationRow row, Subject s) {
		try {
			return ApiCalls.call (row.api (), s);
		} catch (RuntimeException e) {
			if (ApiCalls.contract (e)) return row.api () + " threw " + e.getClass ().getSimpleName () + " " + e.getMessage ();
			throw e;
		}
	}

	static void drain () {
		if (display == null) return;
		while (display.readAndDispatch ()) {
			/* run pending events and paints */
		}
	}

	/** Tracked device objects, shells and the shell's children, by identity. */
	static Map<Object, Boolean> census () {
		Map<Object, Boolean> live = new IdentityHashMap<> ();
		if (display == null) return live;
		drain ();
		for (Object o : display.getDeviceData ().objects) live.put (o, Boolean.TRUE);
		for (Shell s : display.getShells ()) live.put (s, Boolean.TRUE);
		for (Control c : shell.getChildren ()) live.put (c, Boolean.TRUE);
		return live;
	}

	/** New live objects after the row; a positive count is measured again after a repeat of the row (first-use caches). */
	static String leaks (CombinationRow row, Map<Object, Boolean> before) {
		if (display == null) return "-";
		List<String> grown = grown (before);
		if (grown.isEmpty ()) return "0";
		Map<Object, Boolean> again = census ();
		Subject repeat = Subject.of (row.widget ());
		ViewportDifferential.Ops<Subject> ops = script (row, repeat, new String [2]);
		for (int i = 0; i < STEPS; i++) ops.next (null, new StringBuilder ()).apply (repeat);
		List<String> leaked = grown (again);
		assertTrue (leaked.isEmpty (), "leak after a repeat of the row: " + leaked + " (first run: " + grown + ")");
		return "0 (first-use " + grown.size () + ")";
	}

	static List<String> grown (Map<Object, Boolean> before) {
		Map<Object, Boolean> after = census ();
		return after.keySet ().stream ().filter (o -> !before.containsKey (o)).map (o -> o.getClass ().getSimpleName ()).sorted ().collect (Collectors.toList ());
	}

	/** Disposes what a failed row left in the shell. */
	static void cleanUp () {
		if (shell == null || shell.isDisposed ()) return;
		for (Control c : shell.getChildren ()) c.dispose ();
		drain ();
	}
}
