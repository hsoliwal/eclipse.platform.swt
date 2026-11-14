/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.mobile;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable paint result. Widget state remains exclusively in SWT's Control/Composite owners.
 */
public final class MobileFrame {
	public static final int SAVE = 1,
			RESTORE = 2,
			TRANSLATE = 3,
			CLIP = 4,
			FILL_RECT = 5,
			DRAW_RECT = 6,
			FILL_OVAL = 7,
			DRAW_OVAL = 8,
			DRAW_LINE = 9,
			DRAW_TEXT = 10,
			FILL_ROUND_RECT = 11,
			DRAW_ROUND_RECT = 12,
			DRAW_ARC = 13,
			FILL_ARC = 14;
	public static final int STRIDE = 8;
	private final int width, height;
	private final int[] operations, colors, fontSizes, fontStyles, antialiases;
	private final float[] lineWidths, coordinates;
	private final String[] texts, fonts;

	private MobileFrame(Builder b, int width, int height) {
		if (width < 0 || height < 0) throw new IllegalArgumentException("Negative frame bounds");
		this.width = width;
		this.height = height;
		operations = Arrays.copyOf(b.operations, b.size);
		colors = Arrays.copyOf(b.colors, b.size);
		lineWidths = Arrays.copyOf(b.lineWidths, b.size);
		coordinates = Arrays.copyOf(b.coordinates, Math.multiplyExact(b.size, STRIDE));
		texts = Arrays.copyOf(b.texts, b.size);
		fonts = Arrays.copyOf(b.fonts, b.size);
		fontSizes = Arrays.copyOf(b.fontSizes, b.size);
		fontStyles = Arrays.copyOf(b.fontStyles, b.size);
		antialiases = Arrays.copyOf(b.antialiases, b.size);
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int size() {
		return operations.length;
	}

	public int operation(int i) {
		return operations[i];
	}

	public int color(int i) {
		return colors[i];
	}

	public float lineWidth(int i) {
		return lineWidths[i];
	}

	public float coordinate(int i, int component) {
		Objects.checkIndex(i, size());
		Objects.checkIndex(component, STRIDE);
		return coordinates[i * STRIDE + component];
	}

	public String text(int i) {
		return texts[i];
	}

	public String font(int i) {
		return fonts[i];
	}

	/** Logical pixel height, not FontData point height. */
	public int fontSize(int i) {
		return fontSizes[i];
	}

	public int fontStyle(int i) {
		return fontStyles[i];
	}

	public int antialias(int i) {
		return antialiases[i];
	}

	/** Defensive snapshots for the JNI boundary. */
	public int[] operations() {
		return operations.clone();
	}

	public int[] colors() {
		return colors.clone();
	}

	public float[] lineWidths() {
		return lineWidths.clone();
	}

	public float[] coordinates() {
		return coordinates.clone();
	}

	public String[] texts() {
		return texts.clone();
	}

	public String[] fonts() {
		return fonts.clone();
	}

	public int[] fontSizes() {
		return fontSizes.clone();
	}

	public int[] fontStyles() {
		return fontStyles.clone();
	}

	public int[] antialiases() {
		return antialiases.clone();
	}

	/** Display-thread builder; publishing copies the occupied primitive lanes. */
	public static final class Builder {
		private int size, depth;
		private int antialias = -1;
		private int[] operations = new int[32],
				colors = new int[32],
				fontSizes = new int[32],
				fontStyles = new int[32];
		private int[] antialiases = new int[32];
		private float[] lineWidths = new float[32], coordinates = new float[32 * STRIDE];
		private String[] texts = new String[32], fonts = new String[32];

		private int append(
				int op,
				int color,
				float lineWidth,
				float a,
				float b,
				float c,
				float d,
				float e,
				float f) {
			if (size == operations.length) {
				int n = Math.addExact(size, Math.max(32, size >>> 1));
				operations = Arrays.copyOf(operations, n);
				colors = Arrays.copyOf(colors, n);
				fontSizes = Arrays.copyOf(fontSizes, n);
				fontStyles = Arrays.copyOf(fontStyles, n);
				antialiases = Arrays.copyOf(antialiases, n);
				lineWidths = Arrays.copyOf(lineWidths, n);
				coordinates = Arrays.copyOf(coordinates, Math.multiplyExact(n, STRIDE));
				texts = Arrays.copyOf(texts, n);
				fonts = Arrays.copyOf(fonts, n);
			}
			int i = size++, j = i * STRIDE;
			operations[i] = op;
			colors[i] = color;
			lineWidths[i] = lineWidth;
			antialiases[i] = antialias;
			coordinates[j] = a;
			coordinates[j + 1] = b;
			coordinates[j + 2] = c;
			coordinates[j + 3] = d;
			coordinates[j + 4] = e;
			coordinates[j + 5] = f;
			return i;
		}

		public void save() {
			append(SAVE, 0, 0, 0, 0, 0, 0, 0, 0);
			depth++;
		}

		public void restore() {
			if (depth == 0) throw new IllegalStateException("Unbalanced graphics restore");
			append(RESTORE, 0, 0, 0, 0, 0, 0, 0, 0);
			depth--;
		}

		public void translate(int x, int y) {
			append(TRANSLATE, 0, 0, x, y, 0, 0, 0, 0);
		}

		public void setAntialias(int value) {
			antialias = value;
		}

		public void clip(int x, int y, int w, int h) {
			append(CLIP, 0, 0, x, y, Math.max(0, w), Math.max(0, h), 0, 0);
		}

		public void shape(
				int operation,
				int argb,
				float width,
				float x,
				float y,
				float w,
				float h,
				float a,
				float b) {
			if (operation < FILL_RECT || operation > FILL_ARC || operation == DRAW_TEXT)
				throw new IllegalArgumentException("Unknown shape operation");
			append(operation, argb, width, x, y, w, h, a, b);
		}

		public void text(String text, int x, int y, int argb, String font, int pixels, int style) {
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(font, "font");
			if (pixels <= 0) throw new IllegalArgumentException("Font pixel size must be positive");
			int i = append(DRAW_TEXT, argb, 0, x, y, 0, 0, 0, 0);
			texts[i] = text;
			fonts[i] = font;
			fontSizes[i] = pixels;
			fontStyles[i] = style;
		}

		public void append(MobileFrame frame) {
			for (int i = 0; i < frame.size(); i++) {
				int op = frame.operation(i);
				if (op == SAVE) depth++;
				if (op == RESTORE && --depth < 0)
					throw new IllegalStateException("Unbalanced appended frame");
				int j =
						append(
								op,
								frame.color(i),
								frame.lineWidth(i),
								frame.coordinate(i, 0),
								frame.coordinate(i, 1),
								frame.coordinate(i, 2),
								frame.coordinate(i, 3),
								frame.coordinate(i, 4),
								frame.coordinate(i, 5));
				texts[j] = frame.text(i);
				fonts[j] = frame.font(i);
				fontSizes[j] = frame.fontSize(i);
				fontStyles[j] = frame.fontStyle(i);
				antialiases[j] = frame.antialias(i);
			}
		}

		public MobileFrame build(int width, int height) {
			if (depth != 0) throw new IllegalStateException("Unbalanced graphics save");
			return new MobileFrame(this, width, height);
		}
	}
}
