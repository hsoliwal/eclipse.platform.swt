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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 5)
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.Java2DCanvas;
import org.eclipse.swt.custom.AnimatedProgress;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.TableCursor;
import org.eclipse.swt.custom.TreeCursor;
import org.eclipse.swt.internal.WidgetMode;
import org.eclipse.swt.internal.canvasext.IExternalCanvasFactory;
import org.eclipse.swt.internal.render.m3.gc.M3CanvasFactory;
import org.eclipse.swt.opengl.GLCanvas;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Decorations;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Headless (no Display): the leaf-5 activation matrix of the M3 canvas factory, mode x Canvas subclass x style (pattern
 * M3-CANVAS-ACTIVATION v2). In the <code>m3</code> and <code>gpu</code> modes every Canvas subclass of the fork (and an
 * application's) gets the M3 handler with or without <code>SWT.M3</code>; a GLCanvas or an <code>SWT.EMBEDDED</code> host
 * only with the bit; a Decorations (Shell) never; the other modes never. The class decides through the existing selector
 * (<code>swt.widget.mode.&lt;SimpleName&gt;</code>, most derived first, up to Canvas, then the global property). Classes
 * only: no widget is constructed.
 */
@SuppressWarnings("deprecation") // AnimatedProgress is a deprecated Canvas subclass of the fork, still covered
public class Test_org_eclipse_swt_m3gc_CanvasCoverage {
	/** An application canvas and an application editor (never constructed). */
	static class AppCanvas extends Canvas {
		AppCanvas(Composite parent, int style) {
			super(parent, style);
		}
	}

	static class AppText extends StyledText {
		AppText(Composite parent, int style) {
			super(parent, style);
		}
	}

	static final List<Class<?>> COVERED = List.of(Canvas.class, StyledText.class, CLabel.class, TableCursor.class,
			TreeCursor.class, AnimatedProgress.class, Java2DCanvas.class, AppCanvas.class, AppText.class);
	static final int[] STYLES = { SWT.NONE, SWT.M3, SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED, SWT.M3 | SWT.BORDER,
			SWT.NO_MERGE_PAINTS | SWT.NO_REDRAW_RESIZE };

	/* the properties a test changed, with their previous values (restored after each test: the P04 lesson) */
	private final Map<String, String> saved = new HashMap<>();

	static WidgetMode drawn(WidgetMode mode) {
		return mode == WidgetMode.M3 || mode == WidgetMode.GPU ? mode : null;
	}

	static String key(String widget) {
		return widget == null ? WidgetMode.PROPERTY : WidgetMode.PROPERTY + "." + widget;
	}

	static boolean noModeProperty() {
		return System.getProperties().stringPropertyNames().stream()
				.noneMatch(k -> k.equals(WidgetMode.PROPERTY) || k.startsWith(WidgetMode.PROPERTY + "."));
	}

	void set(String widget, String value) {
		// containsKey, not putIfAbsent: an absent property is saved as null, which putIfAbsent would overwrite (L5-37)
		if (!saved.containsKey(key(widget))) {
			saved.put(key(widget), System.getProperty(key(widget)));
		}
		if (value == null) {
			System.clearProperty(key(widget));
		} else {
			System.setProperty(key(widget), value);
		}
	}

	@AfterEach
	void restore() {
		saved.forEach((k, v) -> {
			if (v == null) {
				System.clearProperty(k);
			} else {
				System.setProperty(k, v);
			}
		});
		saved.clear();
	}

	@Test
	public void testEveryCanvasSubclassInADrawnMode() {
		List<String> wrong = new ArrayList<>();
		for (WidgetMode mode : WidgetMode.values()) {
			for (Class<?> type : COVERED) {
				for (int style : STYLES) {
					if (drawn(mode) != M3CanvasFactory.backend(mode, type, style)) {
						wrong.add(mode + " " + type.getSimpleName() + " 0x" + Integer.toHexString(style));
					}
				}
			}
		}
		assertEquals(List.of(), wrong, "mode x subclass x style cells that miss the leaf-5 contract");
	}

	@Test
	public void testForeignCanvasesNeedTheStyleBit() {
		for (WidgetMode mode : WidgetMode.values()) {
			assertNull(M3CanvasFactory.backend(mode, GLCanvas.class, SWT.NONE), mode + " GLCanvas without SWT.M3");
			assertNull(M3CanvasFactory.backend(mode, Canvas.class, SWT.EMBEDDED), mode + " embedded host without SWT.M3");
			assertNull(M3CanvasFactory.backend(mode, AppCanvas.class, SWT.EMBEDDED | SWT.NO_BACKGROUND), mode + " embedded subclass");
			assertEquals(drawn(mode), M3CanvasFactory.backend(mode, GLCanvas.class, SWT.M3), mode + " GLCanvas with SWT.M3");
			assertEquals(drawn(mode), M3CanvasFactory.backend(mode, Canvas.class, SWT.EMBEDDED | SWT.M3), mode + " embedded with SWT.M3");
		}
		assertTrue(M3CanvasFactory.foreign(GLCanvas.class, SWT.NONE));
		assertFalse(M3CanvasFactory.foreign(StyledText.class, SWT.NO_BACKGROUND));
	}

	@Test
	public void testDecorationsNever() {
		for (WidgetMode mode : WidgetMode.values()) {
			for (int style : STYLES) {
				assertNull(M3CanvasFactory.backend(mode, Decorations.class, style), mode + " Decorations");
				assertNull(M3CanvasFactory.backend(mode, Shell.class, style), mode + " Shell");
			}
		}
	}

	@Test
	public void testFactoryAppliesTheSelectorToTheCanvasClass() {
		List<Class<?>> asked = new ArrayList<>();
		M3CanvasFactory m3 = new M3CanvasFactory(type -> {
			asked.add(type);
			return WidgetMode.M3;
		}, c -> Test_org_eclipse_swt_m3gc_M3CanvasFactory.RASTER, c -> Test_org_eclipse_swt_m3gc_M3CanvasFactory.GPU);
		assertTrue(m3.accepts(null, SWT.NONE), "a plain canvas in m3 without the bit");
		assertSame(Test_org_eclipse_swt_m3gc_M3CanvasFactory.RASTER, m3.create(null, SWT.NONE));
		assertFalse(m3.accepts(null, SWT.EMBEDDED), "an embedded host without the bit");
		assertEquals(List.of(Canvas.class, Canvas.class, Canvas.class), asked, "the selector sees the canvas class");
		M3CanvasFactory off = new M3CanvasFactory((Class<?> type) -> WidgetMode.ORIGINAL,
				c -> Test_org_eclipse_swt_m3gc_M3CanvasFactory.RASTER, c -> Test_org_eclipse_swt_m3gc_M3CanvasFactory.GPU);
		assertFalse(off.accepts(null, SWT.M3), "original mode, even with the bit");
		assertNull(off.create(null, SWT.M3));
	}

	@Test
	public void testPerClassPropertyDecides() {
		assumeTrue(noModeProperty(), "the test JVM sets no widget mode");
		set("StyledText", "m3");
		assertEquals(WidgetMode.M3, M3CanvasFactory.mode(StyledText.class));
		assertEquals(WidgetMode.M3, M3CanvasFactory.mode(AppText.class), "a subclass inherits its superclass's property");
		assertEquals(WidgetMode.ORIGINAL, M3CanvasFactory.mode(CLabel.class), "no property: the stock path");
		assertEquals(WidgetMode.ORIGINAL, M3CanvasFactory.mode(Canvas.class));
		set("Canvas", "gpu");
		assertEquals(WidgetMode.GPU, M3CanvasFactory.mode(CLabel.class), "the Canvas property reaches every subclass");
		assertEquals(WidgetMode.GPU, M3CanvasFactory.mode(AppCanvas.class));
		assertEquals(WidgetMode.M3, M3CanvasFactory.mode(StyledText.class), "the most derived property wins");
		set("Canvas", null);
		set(null, "m3");
		set("StyledText", "original");
		assertEquals(WidgetMode.ORIGINAL, M3CanvasFactory.mode(StyledText.class), "a class opts out of a global m3");
		assertEquals(WidgetMode.ORIGINAL, M3CanvasFactory.mode(AppText.class));
		assertEquals(WidgetMode.M3, M3CanvasFactory.mode(CLabel.class), "the global property for the others");
		set("AppCanvas", "custom");
		assertEquals(WidgetMode.ORIGINAL, M3CanvasFactory.mode(AppCanvas.class), "an unsupported mode falls back (Canvas has no viewport)");
	}

	@Test
	public void testAnonymousClassesHaveNoPropertyOfTheirOwn() {
		assumeTrue(noModeProperty(), "the test JVM sets no widget mode");
		Object anonymous = new Object() {
		};
		set("Object", "m3");
		assertEquals(WidgetMode.M3, WidgetMode.of(anonymous.getClass(), Object.class, WidgetMode.M3), "the empty simple name is skipped");
	}

	@Test
	public void testAnyRequestedSeesGlobalAndPerWidgetKeys() {
		assumeTrue(noModeProperty(), "the test JVM sets no widget mode");
		assertFalse(WidgetMode.anyRequested(WidgetMode.M3, WidgetMode.GPU), "no property: the Display registers nothing");
		set(null, "custom");
		assertFalse(WidgetMode.anyRequested(WidgetMode.M3, WidgetMode.GPU), "custom is not drawn through M3");
		set("StyledText", " M3 ");
		assertTrue(WidgetMode.anyRequested(WidgetMode.M3, WidgetMode.GPU), "a per-widget key alone, trimmed, any case");
		set("StyledText", null);
		set("Tree", "gpu");
		assertTrue(WidgetMode.anyRequested(WidgetMode.M3, WidgetMode.GPU));
	}

	@Test
	public void testDefaultAcceptIsTheInitiative31Rule() {
		IExternalCanvasFactory foreign = c -> null;
		assertTrue(foreign.accepts(null, SWT.M3), "the style bit");
		assertTrue(foreign.accepts(null, SWT.M3 | SWT.BORDER));
		assertFalse(foreign.accepts(null, SWT.NONE), "no bit: a foreign factory keeps the stock path");
		assertFalse(foreign.accepts(null, SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED));
	}
}
