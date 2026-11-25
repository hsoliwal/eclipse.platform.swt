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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyledTextLineSpacingProvider;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Height extent of StyledText's exact mode (variable line heights below the
 * viewport threshold): line pixels, line indexes, the top pixel and the
 * whole-document extent come from prefix sums over the renderer's height lane
 * instead of a loop over every line, and the retained layouts are bounded by the
 * display. Every answer must equal a brute-force loop over the same height lane
 * after random edits, re-wraps, indents and scrolls.
 */
public class Test_org_eclipse_swt_custom_StyledText_HeightExtent {
	private static final int UNMEASURED = -1;
	private Shell shell;

	@AfterEach
	public void tearDown() {
		if (shell != null) {
			shell.dispose();
		}
	}

	/* ------------------------------------------------------------ store level */

	/** The store's extent queries against loops over its width and height arrays, under random size writes and edits. */
	@ParameterizedTest(name = "seed={0}")
	@ValueSource(longs = { 1, 2, 3 })
	public void test_storeExtentMatchesBruteForceUnderRandomSizeWrites(long seed) throws Exception {
		Class<?> type = Class.forName("org.eclipse.swt.custom.StyledTextLineStore");
		Constructor<?> constructor = type.getDeclaredConstructor();
		constructor.setAccessible(true);
		Object store = constructor.newInstance();
		Method reset = method(type, "reset", int.class);
		Method setSize = method(type, "setSize", int.class, int.class, int.class, int.class);
		Method setHeight = method(type, "setHeight", int.class, int.class);
		Method resetSizes = method(type, "resetSizes", int.class, int.class);
		Method shiftSizes = method(type, "shiftSizes", int.class, int.class, int.class);
		Method covers = method(type, "extentCovers", int.class);
		Method heightSum = method(type, "heightSum", int.class, int.class);
		Method noHeightCount = method(type, "noHeightCount", int.class, int.class);
		Method nextUnmeasured = method(type, "nextUnmeasured", int.class, int.class, boolean.class);
		Method previousUnmeasured = method(type, "previousUnmeasured", int.class, int.class);
		Method firstExceeding = method(type, "firstExceeding", int.class, int.class, long.class);
		Method lastExceeding = method(type, "lastExceeding", int.class, int.class, long.class);
		Field lineCountField = field(type, "lineCount");
		Random random = new Random(seed);
		int lineCount = 3000;
		reset.invoke(store, lineCount);
		for (int step = 0; step < 1500; step++) {
			int op = random.nextInt(10);
			if (op < 4) {
				setSize.invoke(store, random.nextInt(lineCount), random.nextInt(500), 1 + random.nextInt(60), 0);
			} else if (op < 6) {
				setHeight.invoke(store, random.nextInt(lineCount), 1 + random.nextInt(60));
			} else if (op == 6) {
				int start = random.nextInt(lineCount);
				int count = random.nextBoolean() ? random.nextInt(5) : random.nextInt(lineCount - start + 1);
				resetSizes.invoke(store, start, start + count);
			} else if (op == 7) {
				// fill a run, as a measuring pass does
				int start = random.nextInt(lineCount);
				for (int i = start, end = Math.min(lineCount, start + random.nextInt(400)); i < end; i++) {
					setSize.invoke(store, i, random.nextInt(500), 1 + random.nextInt(60), 0);
				}
			} else {
				// an edit: shiftSizes is called before the line count is updated
				int start = random.nextInt(lineCount);
				int replace = Math.min(lineCount - 1 - start, random.nextInt(4));
				int inserted = random.nextInt(4);
				shiftSizes.invoke(store, start, replace, inserted);
				lineCount += inserted - replace;
				lineCountField.setInt(store, lineCount);
			}
			if (step % 5 != 0) {
				continue;
			}
			assertTrue((Boolean) covers.invoke(store, lineCount), "exact mode covers every line");
			int[] heights = (int[]) field(type, "height").get(store);
			int[] widths = (int[]) field(type, "width").get(store);
			for (int q = 0; q < 12; q++) {
				int from = random.nextInt(lineCount + 1);
				int to = from + random.nextInt(lineCount - from + 1);
				if (random.nextInt(4) == 0) {
					to = Math.min(lineCount, from + random.nextInt(70)); // short ranges take the direct path
				}
				String at = "seed " + seed + " step " + step + " [" + from + ", " + to + ")";
				long sum = 0;
				int unmeasured = 0, firstNoHeight = to, firstNoWidth = to, lastNoHeight = from - 1;
				for (int i = from; i < to; i++) {
					if (heights[i] == UNMEASURED) {
						unmeasured++;
						firstNoHeight = Math.min(firstNoHeight, i);
						lastNoHeight = i;
					} else {
						sum += heights[i];
					}
					if (widths[i] == UNMEASURED) {
						firstNoWidth = Math.min(firstNoWidth, i);
					}
				}
				assertEquals(sum, (long) (Long) heightSum.invoke(store, from, to), at + " height sum");
				assertEquals(unmeasured, (int) (Integer) noHeightCount.invoke(store, from, to), at + " lines without a height");
				assertEquals(firstNoHeight, (int) (Integer) nextUnmeasured.invoke(store, from, to, false), at + " next without a height");
				assertEquals(firstNoWidth, (int) (Integer) nextUnmeasured.invoke(store, from, to, true), at + " next without a width");
				assertEquals(lastNoHeight, (int) (Integer) previousUnmeasured.invoke(store, from, to), at + " previous without a height");
				long rest = sum == 0 ? random.nextInt(5) - 1 : (long) (random.nextDouble() * (sum + 20)) - 1;
				assertEquals(bruteFirstExceeding(heights, from, to, rest), (int) (Integer) firstExceeding.invoke(store, from, to, rest), at + " first exceeding " + rest);
				assertEquals(bruteLastExceeding(heights, from, to, rest), (int) (Integer) lastExceeding.invoke(store, from, to, rest), at + " last exceeding " + rest);
			}
		}
	}

	private static int bruteFirstExceeding(int[] heights, int from, int to, long rest) {
		long sum = 0;
		for (int i = from; i < to; i++) {
			sum += heights[i] == UNMEASURED ? 0 : heights[i];
			if (sum > rest) {
				return i;
			}
		}
		return -1;
	}

	private static int bruteLastExceeding(int[] heights, int from, int to, long rest) {
		long sum = 0;
		for (int i = to - 1; i >= from; i--) {
			sum += heights[i] == UNMEASURED ? 0 : heights[i];
			if (sum > rest) {
				return i;
			}
		}
		return -1;
	}

	/* ----------------------------------------------------------- widget level */

	/**
	 * Line pixels, line indexes, line heights, the top pixel and the renderer's
	 * whole-document height against brute-force loops over the height lane, after
	 * random edits, re-wraps, vertical indents, line spacing and scrolls.
	 */
	@ParameterizedTest(name = "seed={0} {1}")
	@CsvSource({ "1, wrap", "2, indent", "3, spacing", "4, wrap" })
	public void test_linePixelIndexAndHeightMatchBruteForceUnderRandomEdits(long seed, String mode) throws Exception {
		shell = new Shell();
		StyledText t = new StyledText(shell, SWT.MULTI | SWT.V_SCROLL | (mode.equals("wrap") ? SWT.WRAP : SWT.H_SCROLL));
		shell.setBounds(40, 40, 460, 360);
		t.setBounds(0, 0, 420, 300);
		shell.open();
		Random random = new Random(seed);
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 700; i++) {
			text.append("line ").append(i);
			for (int w = 0, words = random.nextInt(6) == 0 ? 8 + random.nextInt(30) : random.nextInt(5); w < words; w++) {
				text.append(" word").append(w);
			}
			text.append(i + 1 < 700 ? "\n" : "");
		}
		t.setText(text.toString());
		if (mode.equals("indent")) {
			for (int i = 0; i < t.getLineCount(); i += 6) {
				t.setLineVerticalIndent(i, 1 + i % 4);
			}
		} else if (mode.equals("spacing")) {
			t.setLineSpacingProvider(line -> line % 3 == 0 ? Integer.valueOf(1 + line % 5) : null);
		}
		Display display = t.getDisplay();
		for (int step = 0; step < 60; step++) {
			int lineCount = t.getLineCount();
			switch (random.nextInt(9)) {
			case 0, 1 -> {
				int line = random.nextInt(lineCount);
				int offset = t.getOffsetAtLine(line) + random.nextInt(t.getLine(line).length() + 1);
				String[] inserts = { "x", "a\nb", "p\nq\nr\n", "\n", " more words that wrap at the narrow widths of the test " };
				t.replaceTextRange(offset, 0, inserts[random.nextInt(inserts.length)]);
			}
			case 2 -> {
				int line = random.nextInt(lineCount);
				int start = t.getOffsetAtLine(line);
				int endLine = Math.min(lineCount - 1, line + random.nextInt(4));
				int end = t.getOffsetAtLine(endLine) + random.nextInt(t.getLine(endLine).length() + 1);
				t.replaceTextRange(start, Math.max(0, end - start), "");
			}
			case 3 -> t.setSize(260 + random.nextInt(300), 300); // re-wraps every line
			case 4 -> t.setTopIndex(random.nextInt(lineCount));
			case 5 -> t.setTopPixel(Math.max(0, t.getTopPixel() + (random.nextBoolean() ? 1 : -1) * random.nextInt(3 * 300)));
			case 6 -> {
				if (mode.equals("indent")) {
					t.setLineVerticalIndent(random.nextInt(lineCount), random.nextInt(9));
				} else {
					t.setLineSpacing(random.nextInt(3));
				}
			}
			case 7 -> {
				while (display.readAndDispatch()) {
					// the idle measurement pass
				}
			}
			default -> t.redraw();
			}
			assertMatchesBruteForce(t, "seed " + seed + " " + mode + " step " + step);
		}
	}

	private static void assertMatchesBruteForce(StyledText t, String where) throws Exception {
		Object renderer = field(t, "renderer");
		Object store = field(renderer, "store");
		Method height = method(store.getClass(), "height", int.class);
		Method rendererHeight = method(renderer.getClass(), "getHeight");
		int lineCount = t.getLineCount();
		// the whole-document height first, while some lines may have no height yet (they are estimated)
		int wrapWidth = (Integer) method(StyledText.class, "getWrapWidth").invoke(t);
		int averageCharWidth = (Integer) field(renderer, "averageCharWidth");
		int defaultHeight = t.getLineHeight();
		int expectedExtent = 0;
		for (int i = 0; i < lineCount; i++) {
			int h = (Integer) height.invoke(store, i);
			if (h == UNMEASURED) {
				h = wrapWidth > 0 ? ((t.getLine(i).length() * averageCharWidth / wrapWidth) + 1) * defaultHeight : defaultHeight;
			}
			expectedExtent += h;
		}
		expectedExtent += t.getTopMargin() + t.getBottomMargin();
		assertEquals(expectedExtent, (int) (Integer) rendererHeight.invoke(renderer), where + ": renderer height");
		// every line measured (both calls measure the lines between the top line and the asked line)
		t.getLinePixel(lineCount);
		t.getLinePixel(0);
		int top = t.getTopIndex();
		int[] heights = new int[lineCount];
		for (int i = 0; i < lineCount; i++) {
			heights[i] = (Integer) height.invoke(store, i);
			assertNotEquals(UNMEASURED, heights[i], where + ": line " + i + " measured");
		}
		int topPixel = t.getLinePixel(top);
		int[] expected = new int[lineCount + 1];
		expected[top] = topPixel;
		for (int i = top; i < lineCount; i++) {
			expected[i + 1] = expected[i] + heights[i];
		}
		for (int i = top - 1; i >= 0; i--) {
			expected[i] = expected[i + 1] - heights[i];
		}
		for (int i = 0; i <= lineCount; i++) {
			assertEquals(expected[i], t.getLinePixel(i), where + ": pixel of line " + i);
		}
		for (int i = 0; i < lineCount; i++) {
			assertEquals(heights[i], t.getLinePixel(i + 1) - t.getLinePixel(i), where + ": height of line " + i);
			int y = expected[i] + (i % 3 == 0 ? 0 : i % 3 == 1 ? heights[i] / 2 : heights[i] - 1);
			assertEquals(i, t.getLineIndex(y), where + ": line at y " + y);
		}
		assertEquals(0, t.getLineIndex(expected[0] - 1 - heights[0] * 1000), where + ": above the first line");
		assertEquals(lineCount - 1, t.getLineIndex(expected[lineCount] + 1000), where + ": below the last line");
		long above = 0;
		for (int i = 0; i < top; i++) {
			above += heights[i];
		}
		assertEquals(above - (topPixel - t.getTopMargin()), t.getTopPixel(), where + ": top pixel");
	}

	/* -------------------------------------------------------------- residency */

	/**
	 * A StyledText sized to its whole content inside a ScrolledComposite is taller
	 * than the display: the layout slots stay bounded by a screenful of lines plus
	 * overscan (not one per line), and the lines the parent shows are the resident ones.
	 */
	@Test
	public void test_layoutSlotsOfAWidgetTallerThanTheDisplayStayBounded() throws Exception {
		shell = new Shell();
		shell.setLayout(new FillLayout());
		ScrolledComposite scrolled = new ScrolledComposite(shell, SWT.V_SCROLL);
		StyledText t = new StyledText(scrolled, SWT.MULTI);
		scrolled.setContent(t);
		Display display = t.getDisplay();
		int displayHeight = display.getClientArea().height;
		int lineHeight = t.getLineHeight();
		int lines = Math.min(18_000 / lineHeight, Math.max(400, 4 * displayHeight / lineHeight));
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < lines; i++) {
			text.append("line ").append(i).append(i + 1 < lines ? "\n" : "");
		}
		t.setText(text.toString());
		Point size = t.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		t.setSize(size);
		shell.setBounds(30, 30, 400, 300);
		shell.open();
		drain(display);
		int clientHeight = t.getClientArea().height;
		assertTrue(clientHeight > displayHeight, "the widget is taller than the display: " + clientHeight + " > " + displayHeight);
		scrolled.setOrigin(0, size.y / 2);
		t.redraw();
		drain(display);
		// the paint of the band the parent shows, sent to the paint listener: when the platform delivers
		// its own expose events (and how much of the widget they cover) is not part of what is tested
		int shownY = -t.getLocation().y;
		int shown = t.getLineIndex(shownY + 1);
		Image band = new Image(display, 200, 300);
		GC gc = new GC(band);
		Event paint = new Event();
		paint.gc = gc;
		paint.x = 0;
		paint.y = shownY;
		paint.width = 200;
		paint.height = 300;
		t.notifyListeners(SWT.Paint, paint);
		gc.dispose();
		band.dispose();
		Object renderer = field(t, "renderer");
		TextLayout[] slots = (TextLayout[]) field(renderer, "slotLayout");
		int visible = Math.min(clientHeight, displayHeight) / lineHeight + 1;
		int bound = Math.max(64, 3 * visible);
		assertTrue(slots.length <= bound, "layout slots " + slots.length + " for " + lines + " lines, bound " + bound);
		int live = 0;
		for (TextLayout layout : slots) {
			if (layout != null && !layout.isDisposed()) {
				live++;
			}
		}
		assertTrue(live <= bound, "live layouts " + live + ", bound " + bound);
		// the middle of the document is on screen: its layouts are the cached ones
		int[] slotLine = (int[]) field(renderer, "slotLine");
		boolean resident = false;
		for (int line : slotLine) {
			resident |= line >= shown && line < shown + 3;
		}
		assertTrue(resident, "a layout of the lines shown from " + shown + " is resident");
	}

	/**
	 * redraw() of a StyledText taller than the display resets every client-area line,
	 * measures the lines whose layouts are resident at once (at most one slot band)
	 * and leaves the others to the idle pass. computeSize answers the same before,
	 * just after and after the idle pass.
	 */
	@Test
	public void test_redrawOfAWidgetTallerThanTheDisplayMeasuresAtMostOneBand() throws Exception {
		StyledText t = tallWidget(null);
		Display display = t.getDisplay();
		ScrolledComposite scrolled = (ScrolledComposite) t.getParent();
		scrolled.setOrigin(0, t.getSize().y / 2);
		paintShownBand(t);
		drain(display);
		Object renderer = field(t, "renderer");
		// one slot band: the lines a display can show plus one viewport of overscan above and below
		int visible = Math.min(t.getClientArea().height, display.getClientArea().height) / t.getLineHeight() + 1;
		int band = Math.max(64, 3 * visible);
		Point before = t.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		long builds = (Long) field(renderer, "layoutBuilds");
		t.redraw();
		long synchronous = (Long) field(renderer, "layoutBuilds") - builds;
		assertTrue(synchronous <= band, "layouts built by redraw() " + synchronous + " for " + t.getLineCount() + " lines, slot band " + band);
		assertEquals(before, t.computeSize(SWT.DEFAULT, SWT.DEFAULT), "computeSize while the idle pass runs");
		drain(display);
		Object store = field(renderer, "store");
		Method width = method(store.getClass(), "width", int.class);
		for (int i = 0; i < t.getLineCount(); i++) {
			assertNotEquals(UNMEASURED, (int) (Integer) width.invoke(store, i), "line " + i + " measured by the idle pass");
		}
		assertEquals(before, t.computeSize(SWT.DEFAULT, SWT.DEFAULT), "computeSize after the idle pass");
	}

	/**
	 * With a line spacing provider, painting lines that are not resident (the parent
	 * scrolled to a band not shown before) lays out those lines only: it neither
	 * resets the sizes of the widget nor re-measures it.
	 */
	@Test
	public void test_parentScrollOfASpacingProviderWidgetDoesNotRemeasureTheWidget() throws Exception {
		StyledText t = tallWidget(line -> line % 3 == 0 ? Integer.valueOf(2) : null);
		Display display = t.getDisplay();
		ScrolledComposite scrolled = (ScrolledComposite) t.getParent();
		scrolled.setOrigin(0, t.getSize().y / 4);
		paintShownBand(t);
		drain(display); // the idle pass measures every line
		Object renderer = field(t, "renderer");
		Object store = field(renderer, "store");
		Method width = method(store.getClass(), "width", int.class);
		scrolled.setOrigin(0, t.getSize().y * 3 / 4); // a band no paint has shown
		long builds = (Long) field(renderer, "layoutBuilds");
		int painted = paintShownBand(t);
		long built = (Long) field(renderer, "layoutBuilds") - builds;
		assertTrue(built <= 2L * painted + 8, "layouts built to paint " + painted + " lines: " + built);
		for (int i = 0; i < t.getLineCount(); i++) {
			assertNotEquals(UNMEASURED, (int) (Integer) width.invoke(store, i), "line " + i + " keeps its size");
		}
	}

	/** A StyledText sized to its whole content in a ScrolledComposite, taller than the display. */
	private StyledText tallWidget(StyledTextLineSpacingProvider spacing) {
		shell = new Shell();
		shell.setLayout(new FillLayout());
		ScrolledComposite scrolled = new ScrolledComposite(shell, SWT.V_SCROLL);
		StyledText t = new StyledText(scrolled, SWT.MULTI);
		scrolled.setContent(t);
		Display display = t.getDisplay();
		int displayHeight = display.getClientArea().height;
		int lineHeight = t.getLineHeight();
		int lines = Math.min(18_000 / lineHeight, Math.max(600, 8 * displayHeight / lineHeight));
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < lines; i++) {
			text.append("line ").append(i).append(" with some words").append(i + 1 < lines ? "\n" : "");
		}
		t.setText(text.toString());
		if (spacing != null) {
			t.setLineSpacingProvider(spacing);
		}
		t.setSize(t.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		shell.setBounds(30, 30, 400, 300);
		shell.open();
		drain(display);
		assertTrue(t.getClientArea().height > displayHeight, "the widget is taller than the display");
		return t;
	}

	/** Sends the paint of the 300 pixels the parent shows to the paint listener; answers the lines painted. */
	private static int paintShownBand(StyledText t) {
		int shownY = -t.getLocation().y;
		Image band = new Image(t.getDisplay(), 200, 300);
		GC gc = new GC(band);
		Event paint = new Event();
		paint.gc = gc;
		paint.x = 0;
		paint.y = shownY;
		paint.width = 200;
		paint.height = 300;
		t.notifyListeners(SWT.Paint, paint);
		gc.dispose();
		band.dispose();
		return t.getLineIndex(shownY + 300) - t.getLineIndex(shownY) + 1;
	}

	private static void drain(Display display) {
		for (int i = 0; i < 10_000 && display.readAndDispatch(); i++) {
			// paints and the idle pass
		}
	}

	/* -------------------------------------------------------------- reflection */

	static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
		Method m = type.getDeclaredMethod(name, parameters);
		m.setAccessible(true);
		return m;
	}

	static Field field(Class<?> type, String name) throws Exception {
		Field f = type.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	static Object field(Object owner, String name) throws Exception {
		Class<?> c = owner.getClass();
		while (c != null) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(owner);
			} catch (NoSuchFieldException e) {
				c = c.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}
}
