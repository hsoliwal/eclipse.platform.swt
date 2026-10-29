// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.internal.render.RowPaint;
import org.junit.jupiter.api.Test;

public class Test_org_eclipse_swt_internal_render_RowPaintRetention {
	private static ImageData pixels() {
		return new ImageData(2, 2, 24, new PaletteData(0xff0000, 0xff00, 0xff));
	}

	@Test public void shrinkReleasesInactivePayloadWithoutReplacingLanes() {
		RowPaint row = new RowPaint();
		row.cells(8);
		String[] texts = row.text;
		ImageData[] images = row.imageData;
		int[] geometry = row.cellX;
		ImageData payload = pixels();
		for (int i = 0; i < 8; i++) {
			row.text[i] = "row-" + i;
			row.imageData[i] = payload;
			row.cellX[i] = i + 100;
		}
		row.cells(2);
		assertSame(texts, row.text);
		assertSame(images, row.imageData);
		assertSame(geometry, row.cellX);
		assertEquals(8, row.text.length);
		for (int i = 0; i < 8; i++) {
			assertNull(row.text[i], "stale text at " + i);
			assertNull(row.imageData[i], "stale pixels at " + i);
			assertNull(row.image[i]);
			assertEquals(i + 100, row.cellX[i]);
		}
		assertEquals(2, payload.width); // caller's pixels remain owned and usable
		for (int i = 0; i < 2; i++) {
			assertEquals(-1, row.cellFont[i]);
			assertEquals(-1, row.cellForeground[i]);
			assertEquals(-1, row.cellBackground[i]);
		}
	}

	@Test public void repeatedGrowShrinkAndZeroReleaseAllPreviousActiveReferences() {
		RowPaint row = new RowPaint();
		java.util.Random random = new java.util.Random(0x29_10_26L);
		ImageData pixels = pixels();
		for (int round = 0; round < 2048; round++) {
			int count = random.nextInt(129);
			row.cells(count);
			assertEquals(count, row.cells);
			for (int i = 0; i < row.text.length; i++) {
				assertNull(row.text[i]);
				assertNull(row.imageData[i]);
			}
			for (int i = 0; i < count; i++) {
				row.text[i] = "frame-" + round;
				row.imageData[i] = pixels;
			}
		}
		row.cells(0);
		for (int i = 0; i < row.text.length; i++) {
			assertNull(row.text[i]);
			assertNull(row.imageData[i]);
		}
	}

	@Test public void snapshotReadReleasesOlderWideRowAndPreservesActiveSerialization() {
		RowPaint source = new RowPaint(); source.cells(1);
		source.text[0] = "世界 e\u0301 \ud83d\ude42";
		source.imageData[0] = pixels();
		source.cellFont[0] = 0x123456789abcdef0L;
		source.textWidth[0] = 41;
		int[] ints = new int[RowPaint.keyLength(1) + 6];
		String[] texts = new String[3]; ImageData[] images = new ImageData[3];
		int size = source.write(ints, 3, texts, images, 1);
		RowPaint target = new RowPaint(); target.cells(9);
		for (int i = 0; i < 9; i++) { target.text[i] = "old"; target.imageData[i] = pixels(); }
		target.read(ints, 3, texts, images, 1);
		assertEquals(1, target.cells);
		assertSame(source.imageData[0], target.imageData[0]);
		assertSame(source.text[0], target.text[0]);
		for (int i = 1; i < 9; i++) { assertNull(target.text[i]); assertNull(target.imageData[i]); }
		int[] copy = new int[ints.length];
		assertEquals(size, target.write(copy, 3, new String[3], new ImageData[3], 1));
		assertArrayEquals(ints, copy);
	}

	@Test public void negativeLegacyCountIsNotNewlyRejected() {
		RowPaint row = new RowPaint(); row.cells(3); row.text[2] = "preserved";
		row.cells(-1);
		assertEquals(-1, row.cells);
		assertEquals("preserved", row.text[2]);
	}
}
