/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.mobile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.mobile.MobileRuntime;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;

/** Public List contracts with sparse high indices; these do not qualify a native OS. */
public final class MobileListContractTest {

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static void drain(Display display) {
		for (int i = 0; i < 10000; i++) if (!display.readAndDispatch()) return;
		throw new AssertionError("Event queue did not quiesce");
	}

	public static void main(String[] args) {
		testHighIndices();
		if (args.length == 0) {
			testStructuralAndSelectionOracle();
			testSetDataReentrancy();
		}
		System.out.println("PASS: original mobile List contracts; max heap " + Runtime.getRuntime().maxMemory());
	}

	static void testBoundedHeapProcess() throws Exception {
		String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
		Process process = new ProcessBuilder(
				Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-Xmx64m", "-Djava.awt.headless=true", "-cp", classPath,
				MobileListContractTest.class.getName(), "high-index").redirectErrorStream(true).start();
		try {
			require(process.waitFor(30, TimeUnit.SECONDS), "Sparse high-index operations must complete promptly");
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			require(process.exitValue() == 0, "Billion-row List must work with a 64 MiB Java heap:\n" + output);
		} finally {
			if (process.isAlive()) process.destroyForcibly();
		}
	}

	static void testHighIndices() {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			List list = new List(shell, SWT.MULTI | SWT.VIRTUAL);
			list.setBounds(0, 0, 180, 80);
			AtomicInteger requested = new AtomicInteger(), selections = new AtomicInteger();
			list.addListener(SWT.Selection, e -> selections.incrementAndGet());
			list.addListener(SWT.SetData, e -> {
				requested.incrementAndGet();
				require(list.getItem(e.index).isEmpty(), "Same-row SetData reentry is suppressed");
				list.setItem(e.index, "row-" + e.index);
			});
			int billion = 1_000_000_000;
			list.setItemCount(billion);
			require(list.getItem(billion - 1).equals("row-999999999"), "Resolve a high row on a small heap");
			require(requested.get() == 1, "Only the requested row is resolved");
			list.select(billion - 1);
			require(Arrays.equals(list.getSelectionIndices(), new int[] {billion - 1}), "Sparse high selection");
			list.setTopIndex(billion - 10);
			shell.open();
			drain(display);
			require(requested.get() <= 12, "Painting resolves only the visible window");
			list.selectAll();
			require(list.getSelectionCount() == billion && list.getSelectionIndex() == 0,
					"Select-all counts and first-index queries do not enumerate rows");
			list.deselect(10, billion - 11);
			require(list.getSelectionCount() == 20 && list.isSelected(billion - 1) && !list.isSelected(10),
					"Large deselection preserves both boundary runs");
			int[] expected = new int[20];
			for (int i = 0; i < 10; i++) { expected[i] = i; expected[i + 10] = billion - 10 + i; }
			require(Arrays.equals(list.getSelectionIndices(), expected), "Complement selection enumerates gaps in order");
			list.deselect(0, billion - 2);
			require(list.getSelectionIndex() == billion - 1 && list.getSelectionCount() == 1,
					"First selected index skips a billion-row exclusion");
			list.selectAll();
			int before = requested.get();
			list.add("inserted", billion / 2);
			require(list.getItemCount() == billion + 1 && list.getSelectionCount() == billion,
					"Insert shifts a complemented selection without selecting the new row");
			require(!list.isSelected(billion / 2) && list.isSelected(billion), "Inserted selection hole");
			require(list.getItem(billion).equals("row-999999999") && list.getFocusIndex() == billion,
					"Sparse text and focus move with their original row");
			require(requested.get() == before, "Structural edits do not resolve uncached rows");
			list.remove(100, billion - 100);
			require(list.getItemCount() == 200 && list.getSelectionCount() == 200,
					"Removing a large middle range merges the retained selection");
			require(list.getItem(199).equals("row-999999999") && list.getFocusIndex() == 199,
					"Middle removal shifts sparse cached text and focus");
			list.setItemCount(Integer.MAX_VALUE);
			require(list.getSelectionCount() == 200 && !list.isSelected(200), "Growth leaves new rows unselected");
			list.select(Integer.MAX_VALUE - 1);
			require(list.isSelected(Integer.MAX_VALUE - 1), "Last legal SWT index is selectable");
			list.selectAll();
			require(list.getSelectionCount() == Integer.MAX_VALUE, "Maximum count does not overflow");
			list.deselect(0, Integer.MAX_VALUE - 2);
			require(Arrays.equals(list.getSelectionIndices(), new int[] {Integer.MAX_VALUE - 1}),
					"Exclusive range endpoints retain the final legal row");
			list.remove(0, Integer.MAX_VALUE - 1);
			require(list.getItemCount() == 0 && list.getSelectionIndex() == -1, "Full sparse removal");
			list.add("only");
			require(list.getItem(0).equals("only") && list.getSelectionCount() == 0, "Empty owner can be reused");
			require(selections.get() == 0, "Programmatic selection and structure changes stay silent");
		} finally {
			display.dispose();
			MobileRuntime.uninstall(platform);
		}
		require(platform.controls.isEmpty() && platform.images.isEmpty(), "List disposal releases its native peers/images");
	}

	static void testStructuralAndSelectionOracle() {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			List list = new List(new Shell(display), SWT.MULTI);
			ArrayList<String> texts = new ArrayList<>();
			ArrayList<Boolean> selected = new ArrayList<>();
			AtomicInteger events = new AtomicInteger();
			list.addListener(SWT.Selection, e -> events.incrementAndGet());
			Random random = new Random(20261115);
			for (int step = 0; step < 1200; step++) {
				int size = texts.size();
				switch (random.nextInt(13)) {
					case 0 -> {
						int index = random.nextInt(size + 1);
						String value = "insert-" + step;
						list.add(value, index); texts.add(index, value); selected.add(index, false);
					}
					case 1 -> {
						if (size == 0) break;
						int start = random.nextInt(size), end = start + random.nextInt(size - start);
						list.remove(start, end); texts.subList(start, end + 1).clear(); selected.subList(start, end + 1).clear();
					}
					case 2 -> {
						int next = random.nextInt(65);
						list.setItemCount(next);
						while (texts.size() < next) { texts.add(""); selected.add(false); }
						if (next < size) { texts.subList(next, size).clear(); selected.subList(next, size).clear(); }
					}
					case 3 -> {
						if (size == 0) break;
						int index = random.nextInt(size); String value = "set-" + step;
						list.setItem(index, value); texts.set(index, value);
					}
					case 4 -> {
						if (size == 0) break;
						int index = random.nextInt(size); list.clear(index); texts.set(index, "");
					}
					case 5 -> { list.clearAll(); Collections.fill(texts, ""); }
					case 6 -> {
						int index = random.nextInt(size + 8) - 4; list.select(index);
						if (index >= 0 && index < size) selected.set(index, true);
					}
					case 7, 8 -> {
						int start = random.nextInt(size + 8) - 4, end = random.nextInt(size + 8) - 4;
						boolean value = random.nextBoolean();
						if (value) list.select(start, end); else list.deselect(start, end);
						for (int i = Math.max(0, start); i <= Math.min(size - 1, end); i++) selected.set(i, value);
					}
					case 9 -> { list.selectAll(); Collections.fill(selected, true); }
					case 10 -> { list.deselectAll(); Collections.fill(selected, false); }
					case 11 -> {
						int[] indices = new int[random.nextInt(5)];
						for (int i = 0; i < indices.length; i++) indices[i] = random.nextInt(size + 8) - 4;
						list.setSelection(indices); Collections.fill(selected, false);
						for (int index : indices) if (index >= 0 && index < size) selected.set(index, true);
					}
					case 12 -> {
						if (size == 0) break;
						int[] indices = {random.nextInt(size), random.nextInt(size), random.nextInt(size)};
						list.remove(indices);
						int[] distinct = Arrays.stream(indices).distinct().sorted().toArray();
						for (int i = distinct.length - 1; i >= 0; i--) { texts.remove(distinct[i]); selected.remove(distinct[i]); }
					}
				}
				require(Arrays.equals(list.getItems(), texts.toArray(String[]::new)), "Text oracle at step " + step);
				int[] expected = java.util.stream.IntStream.range(0, selected.size()).filter(selected::get).toArray();
				require(Arrays.equals(list.getSelectionIndices(), expected), "Selection oracle at step " + step);
				require(list.getSelectionCount() == expected.length, "Selection cardinality at step " + step);
				require(list.getSelectionIndex() == (expected.length == 0 ? -1 : expected[0]), "First selection at step " + step);
				for (int i = 0; i < selected.size(); i++) require(list.isSelected(i) == selected.get(i), "Selection membership");
			}
			require(events.get() == 0, "Oracle programmatic operations do not send selection events");
		} finally {
			display.dispose();
			MobileRuntime.uninstall(platform);
		}
	}

	static void testSetDataReentrancy() {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			List shrinking = new List(shell, SWT.VIRTUAL);
			shrinking.setItemCount(Integer.MAX_VALUE);
			shrinking.addListener(SWT.SetData, e -> shrinking.setItemCount(1));
			require(shrinking.getItem(Integer.MAX_VALUE - 1).isEmpty(), "SetData may shrink past the active row");
			List clearing = new List(shell, SWT.VIRTUAL);
			clearing.setItemCount(Integer.MAX_VALUE);
			clearing.addListener(SWT.SetData, e -> clearing.removeAll());
			require(clearing.getItem(Integer.MAX_VALUE - 1).isEmpty(), "SetData may remove all rows");
			List retry = new List(shell, SWT.VIRTUAL);
			retry.setItemCount(Integer.MAX_VALUE);
			AtomicInteger attempts = new AtomicInteger();
			retry.addListener(SWT.SetData, e -> {
				if (attempts.incrementAndGet() == 1) throw new IllegalStateException("first callback");
				retry.setItem(e.index, "retry");
			});
			try { retry.getItem(Integer.MAX_VALUE - 1); throw new AssertionError("Expected callback exception"); }
			catch (IllegalStateException expected) { require(expected.getMessage().equals("first callback"), "Original failure propagates"); }
			require(retry.getItem(Integer.MAX_VALUE - 1).equals("retry") && attempts.get() == 2,
					"Failed SetData releases the active-row guard");
			List selection = new List(shell, SWT.VIRTUAL | SWT.MULTI);
			selection.setItemCount(3); selection.selectAll();
			selection.addListener(SWT.SetData, e -> {
				selection.setItem(e.index, "selected-" + e.index);
				if (e.index == 0) selection.deselect(1, 2);
			});
			require(Arrays.equals(selection.getSelection(), new String[] {"selected-0"}),
					"Selection text traversal observes deselection inside SetData");
			List extending = new List(shell, SWT.VIRTUAL | SWT.MULTI);
			extending.setItemCount(3); extending.select(0);
			extending.addListener(SWT.SetData, e -> {
				extending.setItem(e.index, "extended-" + e.index);
				if (e.index == 0) extending.select(2);
			});
			require(Arrays.equals(extending.getSelection(), new String[] {"extended-0", "extended-2"}),
					"Selection text traversal can grow after SetData selects later rows");
			List disposed = new List(shell, SWT.VIRTUAL);
			disposed.setItemCount(Integer.MAX_VALUE);
			disposed.addListener(SWT.SetData, e -> disposed.dispose());
			require(disposed.getItem(Integer.MAX_VALUE - 1).isEmpty() && disposed.isDisposed(), "SetData may dispose its owner");
		} finally {
			display.dispose();
			MobileRuntime.uninstall(platform);
		}
	}
}
