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
package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.WidgetMode;
import org.eclipse.swt.internal.canvasext.IExternalCanvasHandler;
import org.eclipse.swt.internal.render.m3.gc.M3CanvasFactory;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.Test;

/**
 * Headless (no Display): the activation decision of the M3 canvas factory (GC seam, leaf 4; leaf 5 contract). A plain
 * canvas gets a handler when the widget mode of <code>Canvas</code> is <code>m3</code> or <code>gpu</code>, with or
 * without <code>SWT.M3</code> (leaf 4 required the bit: L5-02); the selector and the handler makers are stubbed, so the
 * mode maps to a handler or null. The per-class matrix is Test_org_eclipse_swt_m3gc_CanvasCoverage.
 */
public class Test_org_eclipse_swt_m3gc_M3CanvasFactory {
	/** A handler double: which maker answered is the outcome. */
	static final class HandlerDouble implements IExternalCanvasHandler {
		final String backend;

		HandlerDouble(String backend) {
			this.backend = backend;
		}

		@Override
		public Object paint(Consumer<Event> consumer, long wParam, long lParam) {
			return null;
		}

		@Override
		public void redrawTriggered() {
		}

		@Override
		public void redrawTriggered(int x, int y, int width, int height, boolean all) {
		}

		@Override
		public void scroll(int destX, int destY, int x, int y, int width, int height, boolean all) {
		}
	}

	static final HandlerDouble RASTER = new HandlerDouble("raster"), GPU = new HandlerDouble("gpu");

	static M3CanvasFactory factory(WidgetMode mode) {
		return new M3CanvasFactory(() -> mode, c -> RASTER, c -> GPU);
	}

	static WidgetMode shipped() {
		return WidgetMode.of(M3CanvasFactory.WIDGET, WidgetMode.M3, WidgetMode.GPU);
	}

	@Test
	public void testBackendNeedsADrawnModeNotTheStyleBit() {
		for (WidgetMode mode : WidgetMode.values()) {
			WidgetMode expected = mode == WidgetMode.M3 || mode == WidgetMode.GPU ? mode : null;
			// leaf 5 (L5-02): a plain canvas no longer needs SWT.M3 in a drawn mode; leaf 4 asserted null here
			assertEquals(expected, M3CanvasFactory.backend(mode, SWT.NONE), mode + " without SWT.M3");
			assertEquals(expected, M3CanvasFactory.backend(mode, SWT.BORDER | SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED), mode + " without SWT.M3");
			assertEquals(expected, M3CanvasFactory.backend(mode, SWT.M3), mode + " with SWT.M3");
			assertEquals(expected, M3CanvasFactory.backend(mode, SWT.M3 | SWT.BORDER | SWT.NO_BACKGROUND), mode + " with SWT.M3 and other bits");
		}
	}

	@Test
	public void testCreateAnswersTheSelectedHandlerOrNull() {
		assertSame(RASTER, factory(WidgetMode.M3).create(null, SWT.M3));
		assertSame(GPU, factory(WidgetMode.GPU).create(null, SWT.M3));
		assertSame(RASTER, factory(WidgetMode.M3).create(null, SWT.M3 | SWT.BORDER));
		// leaf 5 (L5-02): without the style bit too; leaf 4 asserted null for these two
		assertSame(RASTER, factory(WidgetMode.M3).create(null, SWT.NONE), "m3 mode without the style bit");
		assertSame(GPU, factory(WidgetMode.GPU).create(null, SWT.BORDER), "gpu mode without the style bit");
		assertNull(factory(WidgetMode.VIEWPORT).create(null, SWT.M3), "viewport mode");
		assertNull(factory(WidgetMode.ORIGINAL).create(null, SWT.M3), "original mode");
		assertNull(factory(WidgetMode.CUSTOM).create(null, SWT.M3), "custom mode (a GC, no M3 surface)");
	}

	@Test
	public void testShippedSelectorIsOffWithoutTheProperty() {
		assumeTrue(System.getProperty(WidgetMode.PROPERTY) == null, "the test JVM sets no global mode");
		assumeTrue(System.getProperty(WidgetMode.PROPERTY + "." + M3CanvasFactory.WIDGET) == null, "the test JVM sets no Canvas mode");
		assertEquals(WidgetMode.ORIGINAL, shipped(), "Canvas has no viewport: the fallback is the stock path");
		assertNull(M3CanvasFactory.backend(shipped(), SWT.M3), "default off even with the style bit");
		assertNull(M3CanvasFactory.backend(shipped(), SWT.NONE), "default off without it");
	}

	@Test
	public void testCanvasPropertySelectsTheMode() {
		String key = WidgetMode.PROPERTY + "." + M3CanvasFactory.WIDGET;
		String previous = System.getProperty(key);
		try {
			System.setProperty(key, "m3");
			assertEquals(WidgetMode.M3, shipped());
			assertEquals(WidgetMode.M3, M3CanvasFactory.backend(shipped(), SWT.M3));
			System.setProperty(key, "gpu");
			assertEquals(WidgetMode.GPU, M3CanvasFactory.backend(shipped(), SWT.M3));
			System.setProperty(key, "custom");
			assertEquals(WidgetMode.ORIGINAL, shipped(), "a mode the canvas does not implement falls back to the stock path");
			assertNull(M3CanvasFactory.backend(shipped(), SWT.M3));
		} finally {
			// no property leaks into the next test class (PRE_EXISTING.tsv P04 is exactly that leak)
			if (previous == null) {
				System.clearProperty(key);
			} else {
				System.setProperty(key, previous);
			}
		}
	}

	@Test
	public void testStyleBitIsTheOneTheSeamChecks() {
		assertEquals(1 << 23, SWT.M3, "I31's canvas style bit, our constant (brief rule 14)");
	}
}
