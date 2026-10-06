/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.io.*;
import java.lang.management.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Resource-admission tests for the viewport rewrite.
 *
 * <p>These tests intentionally separate deterministic complexity contracts from
 * diagnostic timing. A viewport is incorrect if its work or residency scales
 * with cold logical rows, even when the resulting pixels still look right.</p>
 */
public class Test_ViewportResourceBudgets {

	private static final String GTK_LOGICAL_MODEL_PROPERTY =
			"org.eclipse.swt.internal.gtk.virtualTreeLogicalNativeModel";
	private static final String RSS_LIMIT_PROPERTY =
			"org.eclipse.swt.tests.viewport.maxNativeGrowthBytes";
	private static final long DEFAULT_RSS_LIMIT = 32L * 1024L * 1024L;
	private static final int WINDOW_ROWS = 56;
	private static final int CPU_ITERATIONS = 20_000;
	private static final int ALLOCATION_ITERATIONS = 4_000;

	private Display display;
	private Shell shell;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
	}

	@AfterEach
	public void tearDown () {
		if (shell != null && !shell.isDisposed ()) shell.dispose ();
		SwtTestUtil.processEvents ();
	}

	@Test
	public void test_visibleProjectionCpuBudgetDoesNotScaleWithColdLogicalRows () throws Exception {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean ();
		assumeTrue (bean.isCurrentThreadCpuTimeSupported (), "current-thread CPU time unavailable");
		if (!bean.isThreadCpuTimeEnabled ()) bean.setThreadCpuTimeEnabled (true);

		ProjectionFixture small = projection (1_000_000, 500_000L);
		ProjectionFixture huge = projection (1_000_000_000, 500_000_000L);

		// Warm both paths before measuring so class loading/JIT is not charged to one size.
		consumeWindows (small, 2_000);
		consumeWindows (huge, 2_000);

		long smallCpu = medianCpuNanos (bean, small, CPU_ITERATIONS);
		long hugeCpu = medianCpuNanos (bean, huge, CPU_ITERATIONS);
		long baseline = Math.max (smallCpu, 1_000_000L);
		long allowance = Math.addExact (Math.multiplyExact (baseline, 4L), 5_000_000L);

		assertTrue (hugeCpu <= allowance, () ->
				"visible-window CPU scaled with cold logical rows: small=" + smallCpu
				+ "ns huge=" + hugeCpu + "ns allowance=" + allowance + "ns");
	}

	@Test
	public void test_visibleProjectionAllocationBudgetDoesNotScaleWithColdLogicalRows () throws Exception {
		Object bean = ManagementFactory.getThreadMXBean ();
		Class<?> sunThreadBean;
		try {
			sunThreadBean = Class.forName ("com.sun.management.ThreadMXBean");
		} catch (ClassNotFoundException unavailable) {
			assumeTrue (false, "HotSpot allocation counter unavailable");
			return;
		}
		assumeTrue (sunThreadBean.isInstance (bean), "HotSpot allocation counter unavailable");

		Method supported = sunThreadBean.getMethod ("isThreadAllocatedMemorySupported");
		Method enabled = sunThreadBean.getMethod ("isThreadAllocatedMemoryEnabled");
		Method setEnabled = sunThreadBean.getMethod ("setThreadAllocatedMemoryEnabled", boolean.class);
		Method allocated = sunThreadBean.getMethod ("getThreadAllocatedBytes", long.class);
		assumeTrue ((Boolean)supported.invoke (bean), "thread allocation counter unavailable");
		if (!((Boolean)enabled.invoke (bean))) setEnabled.invoke (bean, true);

		ProjectionFixture small = projection (1_000_000, 500_000L);
		ProjectionFixture huge = projection (1_000_000_000, 500_000_000L);
		consumeWindows (small, 1_000);
		consumeWindows (huge, 1_000);

		long threadId = Thread.currentThread ().threadId ();
		long beforeSmall = (Long)allocated.invoke (bean, threadId);
		consumeWindows (small, ALLOCATION_ITERATIONS);
		long smallBytes = (Long)allocated.invoke (bean, threadId) - beforeSmall;

		long beforeHuge = (Long)allocated.invoke (bean, threadId);
		consumeWindows (huge, ALLOCATION_ITERATIONS);
		long hugeBytes = (Long)allocated.invoke (bean, threadId) - beforeHuge;

		long baseline = Math.max (smallBytes, 1L);
		long allowance = Math.addExact (Math.multiplyExact (baseline, 2L), 1L * 1024L * 1024L);
		assertTrue (hugeBytes <= allowance, () ->
				"visible-window allocation scaled with cold logical rows: small=" + smallBytes
				+ " huge=" + hugeBytes + " allowance=" + allowance);
	}

	@Test
	public void test_virtualGtk3MillionRowsStayInsideNativeRssBudget () throws Exception {
		assumeTrue ("gtk".equals (SWT.getPlatform ()), "GTK-specific native residency gate");
		assumeTrue (System.getProperty ("os.name", "").toLowerCase (Locale.ROOT).contains ("linux"),
				"/proc RSS gate is Linux-specific");
		Path status = Path.of ("/proc/self/status");
		assumeTrue (Files.isReadable (status), "/proc/self/status unavailable");

		Class<?> gtk = Class.forName ("org.eclipse.swt.internal.gtk.GTK");
		Field gtk4 = gtk.getDeclaredField ("GTK4");
		gtk4.setAccessible (true);
		assumeFalse (gtk4.getBoolean (null), "bounded frontier policy is GTK3-specific");

		String previous = System.getProperty (GTK_LOGICAL_MODEL_PROPERTY);
		System.clearProperty (GTK_LOGICAL_MODEL_PROPERTY);
		try {
			shell.setLayout (new FillLayout ());
			shell.setSize (480, 320);

			Tree warm = new Tree (shell, SWT.VIRTUAL | SWT.V_SCROLL);
			warm.setItemCount (64);
			shell.open ();
			SwtTestUtil.processEvents ();

			Method residentCount = Tree.class.getDeclaredMethod ("virtualResidentChildCount", long.class);
			residentCount.setAccessible (true);
			assertEquals (64, residentCount.invoke (warm, 0L));

			forceGcAndDrain ();
			long before = residentBytes (status);

			Tree million = new Tree (shell, SWT.VIRTUAL | SWT.V_SCROLL);
			million.setItemCount (1_000_000);
			shell.layout (true, true);
			SwtTestUtil.processEvents ();

			assertEquals (1_000_000, million.getItemCount ());
			assertEquals (256, residentCount.invoke (million, 0L),
					"one million logical roots must retain one bounded native frontier");

			long after = residentBytes (status);
			long growth = Math.max (0L, after - before);
			long limit = Long.getLong (RSS_LIMIT_PROPERTY, DEFAULT_RSS_LIMIT);
			assertTrue (growth <= limit, () ->
					"GTK3 virtual Tree native/process residency regressed by " + growth
					+ " bytes for one million cold logical roots; limit=" + limit);
		} finally {
			if (previous == null) System.clearProperty (GTK_LOGICAL_MODEL_PROPERTY);
			else System.setProperty (GTK_LOGICAL_MODEL_PROPERTY, previous);
		}
	}

	private static ProjectionFixture projection (int logicalRows, long firstVisible) throws Exception {
		Class<?> topologyType = Class.forName ("org.eclipse.swt.widgets.VirtualTreeTopology");
		Constructor<?> topologyConstructor = topologyType.getDeclaredConstructor ();
		topologyConstructor.setAccessible (true);
		Object topology = topologyConstructor.newInstance ();

		Field rootField = topologyType.getDeclaredField ("ROOT");
		rootField.setAccessible (true);
		int root = rootField.getInt (null);

		Method setChildCount = topologyType.getDeclaredMethod ("setChildCount", int.class, int.class);
		Method bind = topologyType.getDeclaredMethod ("bind", int.class, int.class, int.class);
		Method flag = topologyType.getDeclaredMethod ("flag", int.class, long.class, boolean.class);
		for (Method method : new Method[] {setChildCount, bind, flag}) method.setAccessible (true);

		Class<?> stateType = Class.forName ("org.eclipse.swt.widgets.VirtualItemState");
		Field expandedField = stateType.getDeclaredField ("EXPANDED");
		expandedField.setAccessible (true);
		long expanded = expandedField.getLong (null);

		setChildCount.invoke (topology, root, logicalRows);
		for (int id = 0; id < 4; id++) {
			int coordinate = 10 + id * 1_000;
			bind.invoke (topology, id, root, coordinate);
			setChildCount.invoke (topology, id, 32);
			if ((id & 1) == 0) flag.invoke (topology, id, expanded, true);
		}

		Class<?> projectionType = Class.forName ("org.eclipse.swt.widgets.VirtualTreeVisibleProjection");
		Constructor<?> projectionConstructor = projectionType.getDeclaredConstructor (topologyType);
		projectionConstructor.setAccessible (true);
		Object projection = projectionConstructor.newInstance (topology);
		Method window = projectionType.getDeclaredMethod ("window", long.class, int.class);
		window.setAccessible (true);
		return new ProjectionFixture (projection, window, firstVisible);
	}

	private static long medianCpuNanos (
			ThreadMXBean bean, ProjectionFixture fixture, int iterations) throws Exception {
		long [] samples = new long [5];
		for (int sample = 0; sample < samples.length; sample++) {
			long before = bean.getCurrentThreadCpuTime ();
			consumeWindows (fixture, iterations);
			samples [sample] = bean.getCurrentThreadCpuTime () - before;
		}
		Arrays.sort (samples);
		return samples [samples.length / 2];
	}

	private static long consumeWindows (ProjectionFixture fixture, int iterations) throws Exception {
		long checksum = 0;
		long first = fixture.firstVisible ();
		for (int i = 0; i < iterations; i++) {
			Object [] rows = (Object [])fixture.window ().invoke (
					fixture.projection (), first + (i & 31), WINDOW_ROWS);
			checksum += rows.length;
		}
		assertEquals ((long)iterations * WINDOW_ROWS, checksum);
		return checksum;
	}

	private void forceGcAndDrain () {
		for (int i = 0; i < 2; i++) {
			System.gc ();
			SwtTestUtil.processEvents ();
		}
	}

	private static long residentBytes (Path status) throws IOException {
		for (String line : Files.readAllLines (status)) {
			if (!line.startsWith ("VmRSS:")) continue;
			String [] fields = line.trim ().split ("\\s+");
			if (fields.length < 2) break;
			return Math.multiplyExact (Long.parseLong (fields [1]), 1024L);
		}
		throw new IOException ("VmRSS missing from " + status);
	}

	private record ProjectionFixture (Object projection, Method window, long firstVisible) {
	}
}
