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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 7)
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
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.CBanner;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.internal.WidgetMode;
import org.eclipse.swt.internal.canvasext.IExternalCanvasFactory;
import org.eclipse.swt.internal.render.m3.gc.M3CanvasFactory;
import org.eclipse.swt.ole.win32.OleClientSite;
import org.eclipse.swt.ole.win32.OleControlSite;
import org.eclipse.swt.opengl.GLCanvas;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Decorations;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Headless (no Display): the leaf-7 decision of the M3 factory for a composite that paints itself (pattern
 * COMPOSITE_PAINT_HOOK v1). In the <code>m3</code> and <code>gpu</code> modes every composite class of the fork, an
 * application's and Nebula's gets the M3 handler (the composite asks once, at its first painted <code>WM_PAINT</code>; a
 * pure container never asks: CONTAINER_OPT_OUT, probed on a Display); a Canvas is never decided here (its constructor hook
 * decides); an embedded, OLE, Browser, transparent or mirrored composite stays stock; the other modes never. The class decides
 * through the existing selector (<code>swt.widget.mode.&lt;SimpleName&gt;</code>, most derived first, up to Composite, then
 * the global property). Classes only: no widget is constructed.
 */
public class Test_org_eclipse_swt_m3gc_CompositeCoverage {
	/** An application composite (never constructed). */
	static class AppComposite extends Composite {
		AppComposite(Composite parent, int style) {
			super(parent, style);
		}
	}

	static final List<Class<?>> COVERED = List.of(Composite.class, CTabFolder.class, CBanner.class, ViewForm.class,
			SashForm.class, ScrolledComposite.class, CCombo.class, AppComposite.class);
	static final List<Class<?>> CANVASES = List.of(Canvas.class, StyledText.class, CLabel.class, GLCanvas.class,
			Decorations.class, Shell.class);
	static final int[] STYLES = { SWT.NONE, SWT.BORDER, SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED, SWT.CLOSE | SWT.FLAT,
			SWT.NO_MERGE_PAINTS | SWT.NO_REDRAW_RESIZE, SWT.M3 };

	/* the properties a test changed, with their previous values (restored after each test: the P04 lesson, L5-37) */
	private final Map<String, String> saved = new HashMap<>();

	static WidgetMode drawn(WidgetMode mode) {
		return mode == WidgetMode.M3 || mode == WidgetMode.GPU ? mode : null;
	}

	void set(String widget, String value) {
		String key = widget == null ? WidgetMode.PROPERTY : WidgetMode.PROPERTY + "." + widget;
		if (!saved.containsKey(key)) {
			saved.put(key, System.getProperty(key));
		}
		if (value == null) {
			System.clearProperty(key);
		} else {
			System.setProperty(key, value);
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
	public void testEveryCompositeInADrawnMode() {
		List<String> wrong = new ArrayList<>();
		for (WidgetMode mode : WidgetMode.values()) {
			for (Class<?> type : COVERED) {
				for (int style : STYLES) {
					if (drawn(mode) != M3CanvasFactory.compositeBackend(mode, type, style)) {
						wrong.add(mode + " " + type.getSimpleName() + " 0x" + Integer.toHexString(style));
					}
				}
			}
		}
		assertEquals(List.of(), wrong, "mode x composite x style cells that miss the leaf-7 contract");
	}

	@Test
	public void testCanvasesAreNeverDecidedHere() {
		for (WidgetMode mode : WidgetMode.values()) {
			for (Class<?> type : CANVASES) {
				for (int style : STYLES) {
					assertNull(M3CanvasFactory.compositeBackend(mode, type, style), mode + " " + type.getSimpleName());
				}
			}
		}
	}

	@Test
	public void testForeignTransparentAndMirroredCompositesStayStock() {
		for (WidgetMode mode : WidgetMode.values()) {
			assertNull(M3CanvasFactory.compositeBackend(mode, Composite.class, SWT.EMBEDDED), mode + " embedded host");
			assertNull(M3CanvasFactory.compositeBackend(mode, OleClientSite.class, SWT.NONE), mode + " OLE client site");
			assertNull(M3CanvasFactory.compositeBackend(mode, OleControlSite.class, SWT.NONE), mode + " OLE control site");
			assertNull(M3CanvasFactory.compositeBackend(mode, Browser.class, SWT.NONE), mode + " Browser");
			assertNull(M3CanvasFactory.compositeBackend(mode, CTabFolder.class, SWT.TRANSPARENT), mode + " transparent");
			assertNull(M3CanvasFactory.compositeBackend(mode, AppComposite.class, SWT.RIGHT_TO_LEFT), mode + " mirrored");
		}
		assertTrue(M3CanvasFactory.foreignComposite(OleControlSite.class, SWT.NONE));
		assertTrue(M3CanvasFactory.foreignComposite(CBanner.class, SWT.EMBEDDED));
		assertFalse(M3CanvasFactory.foreignComposite(CTabFolder.class, SWT.BORDER | SWT.CLOSE));
	}

	@Test
	public void testFactoryAppliesTheSelectorToTheCompositeClass() {
		List<Class<?>> asked = new ArrayList<>();
		M3CanvasFactory gpu = new M3CanvasFactory(type -> {
			asked.add(type);
			return WidgetMode.GPU;
		}, c -> Test_org_eclipse_swt_m3gc_M3CanvasFactory.RASTER, c -> Test_org_eclipse_swt_m3gc_M3CanvasFactory.GPU);
		assertTrue(gpu.acceptsComposite(null, SWT.NONE), "a plain composite in gpu");
		assertSame(Test_org_eclipse_swt_m3gc_M3CanvasFactory.GPU, gpu.createComposite(null, SWT.BORDER));
		assertFalse(gpu.acceptsComposite(null, SWT.EMBEDDED), "an embedded host");
		assertNull(gpu.createComposite(null, SWT.TRANSPARENT), "a transparent composite");
		assertEquals(List.of(Composite.class, Composite.class, Composite.class, Composite.class), asked,
				"the selector sees the composite class");
		assertSame(Test_org_eclipse_swt_m3gc_M3CanvasFactory.GPU, gpu.create(null, SWT.NONE), "the canvas path is unchanged");
		assertEquals(Canvas.class, asked.get(4), "and still asks for the canvas class");
		M3CanvasFactory off = new M3CanvasFactory((Class<?> type) -> WidgetMode.VIEWPORT,
				c -> Test_org_eclipse_swt_m3gc_M3CanvasFactory.RASTER, c -> Test_org_eclipse_swt_m3gc_M3CanvasFactory.GPU);
		assertFalse(off.acceptsComposite(null, SWT.NONE), "viewport mode");
		assertNull(off.createComposite(null, SWT.NONE));
	}

	@Test
	public void testPerClassPropertyDecidesForComposites() {
		assumeTrue(Test_org_eclipse_swt_m3gc_CanvasCoverage.noModeProperty(), "the test JVM sets no widget mode");
		set("CTabFolder", "m3");
		assertEquals(WidgetMode.M3, M3CanvasFactory.mode(CTabFolder.class));
		assertEquals(WidgetMode.ORIGINAL, M3CanvasFactory.mode(CBanner.class), "no property: the stock path");
		set("Composite", "gpu");
		assertEquals(WidgetMode.GPU, M3CanvasFactory.mode(CBanner.class), "the Composite property reaches every composite");
		assertEquals(WidgetMode.GPU, M3CanvasFactory.mode(AppComposite.class));
		assertEquals(WidgetMode.M3, M3CanvasFactory.mode(CTabFolder.class), "the most derived property wins");
		assertEquals(WidgetMode.ORIGINAL, M3CanvasFactory.mode(Canvas.class), "the Composite property stops at Canvas");
		assertEquals(WidgetMode.ORIGINAL, M3CanvasFactory.mode(StyledText.class));
		set("Canvas", "m3");
		assertEquals(WidgetMode.GPU, M3CanvasFactory.mode(ViewForm.class), "the Canvas property does not reach composites");
		assertEquals(WidgetMode.M3, M3CanvasFactory.mode(CLabel.class));
	}

	@Test
	public void testDefaultCompositeAcceptIsOff() {
		IExternalCanvasFactory foreign = c -> null;
		assertFalse(foreign.acceptsComposite(null, SWT.NONE), "a foreign factory handles canvases only");
		assertFalse(foreign.acceptsComposite(null, SWT.M3), "even with the style bit");
		assertNull(foreign.createCompositeExtension(null));
	}
}
