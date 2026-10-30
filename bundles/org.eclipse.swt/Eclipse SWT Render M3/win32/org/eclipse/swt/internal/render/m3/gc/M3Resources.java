/*******************************************************************************
 * Copyright (c) 2025 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3.gc;

import java.util.HashMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.canvasext.FontProperties;
import org.eclipse.swt.internal.canvasext.IDpiScaler;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontSlant;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;

public class M3Resources implements IM3Resources {
	public static boolean USE_TEXT_IMAGE_CACHE = true;

	/** Maximum number of entries kept in the text-image LRU cache. */
	private static final int TEXT_IMAGE_CACHE_MAX = 512;

	/**
	 * LRU cache that automatically closes the evicted Skija image when the cache
	 * exceeds its capacity. Must only be accessed from the SWT UI thread.
	 */
	private static final class LruImageCache<K> extends LinkedHashMap<K, IM3Image> {
		private static final long serialVersionUID = -4958211475619287841L;
		private final int maxSize;

		LruImageCache(int maxSize) {
			super(maxSize + 1, 0.75f, /* accessOrder= */ true);
			this.maxSize = maxSize;
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<K, IM3Image> eldest) {
			if (size() > maxSize) {
				// Close the native Skija image before evicting it from the cache.
				final IM3Image image = eldest.getValue();
				// Auto-close will call image.close() at the end of this block, ensuring
				// resources are released.
				if (image != null && !image.isClosed()) {
					try {
						image.close();
					} catch (final Exception e) {
						e.printStackTrace();
					}
				}

				return true;
			}
			return false;
		}
	}

	private Color background;
	private Color foreground;
	private final Control canvas;
	private Font swtFont;
	private io.github.humbleui.skija.Font m3Font;

	// cache access only from UI thread, so no need for concurrent map
	private final Map<String, String> fontNameMapping = new HashMap<>();
	private final Set<String> unknownFonts = new HashSet<>();
	private final Map<FontProperties, io.github.humbleui.skija.Font> fontCache = new HashMap<>();
	private final LruImageCache<ImageTextKey> textImageCache = new LruImageCache<>(TEXT_IMAGE_CACHE_MAX);
	private final Map<SplitsTextCache, String[]> cachedTextSplits = new HashMap<>();

	// ---------------------------------------------------------------------------------

	private final IM3CanvasExtension m3Extension;

	public M3Resources(Control canvas, IM3CanvasExtension m3Extension) {

		this.canvas = canvas;
		this.m3Extension = m3Extension;

		if (canvas != null) {
			this.canvas.addListener(SWT.Dispose, e -> resetResources());
			this.canvas.addListener(SWT.ZoomChanged, e -> resetResources());
			this.canvas.addListener(SWT.Resize, e -> resetResources());
		}
	}

	@Override
	public void setBackground(Color color) {

		if (color == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}
		this.background = color;
	}

	@Override
	public void setForeground(Color color) {
		if (color == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}
		this.foreground = color;
	}

	@Override
	public Color getForeground() {
		if (foreground != null && !foreground.isDisposed()) {
			return foreground;
		}
		return canvas.getForeground();

	}

	@Override
	public Color getBackground() {

		if (background != null && !background.isDisposed()) {
			return background;
		}

		return canvas.getBackground();
	}

	@Override
	public void setFont(Font font) {
		if (font != null) {
			if (font.isDisposed()) {
				SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			}
		} else {
			font = getDefaultFont();
		}
		this.swtFont = font;
		this.m3Font = getM3Font(font);

	}

	private io.github.humbleui.skija.Font getM3Font(org.eclipse.swt.graphics.Font font) {
		final FontProperties props = FontProperties.getFontProperties(font);
		var cachedFont = fontCache.get(props);
		if (cachedFont != null && cachedFont.isClosed()) {
			fontCache.remove(props);
			cachedFont = null;
		}

		io.github.humbleui.skija.Font f = null;

		if (cachedFont == null) {
			f = createM3Font(props);
			fontCache.put(props, f);
			return f;
		}

		return fontCache.get(props);
	}

	private io.github.humbleui.skija.Font createM3Font(FontProperties props) {

		FontSlant slant = FontSlant.UPRIGHT;
		if (props.lfItalic != 0) {
			slant = FontSlant.ITALIC;
		}

		final FontStyle style = new FontStyle(props.lfWeight, 5, slant);
		io.github.humbleui.skija.Font m3Font = new io.github.humbleui.skija.Font(extractTypeface(props, style));

		final var rect = m3Font.measureText("T"); //$NON-NLS-1$
		final var textHeight = rect.getHeight();
		if (textHeight == 0.0) {

			props.name = Display.getDefault().getSystemFont().getFontData()[0].getName();
			m3Font = new io.github.humbleui.skija.Font(extractTypeface(props, style));
		}

		if (props.lfWidth != 0) {
			final float stretch = (float) ((props.lfWidth / 10.0) + 0.5);
			m3Font.setScaleX(stretch);
		}

		int fontSize = (props.lfHeight);
		final DpiScalerUtil util = new DpiScalerUtil(getScaler());
		fontSize = util.getZoomedFontSize(fontSize);

		m3Font.setSize(fontSize);
		m3Font.setEdging(FontEdging.SUBPIXEL_ANTI_ALIAS);
		m3Font.setSubpixel(true);
		m3Font.setHinting(FontHinting.NORMAL);
		m3Font.setAutoHintingForced(true);

		return m3Font;
	}

	private Typeface extractTypeface(FontProperties props, FontStyle style) {

		final FontMgr fm = FontMgr.getDefault();
		var name = props.name;

		if (name == null || name.isEmpty()) {
			name = Display.getDefault().getSystemFont().getFontData()[0].getName();
		}

		name = name.trim();

		if (fontNameMapping.containsKey(name)) {
			return fm.matchFamilyStyle(fontNameMapping.get(name), style);
		}

		if (unknownFonts.contains(name)) {
			return fm.matchFamilyStyle(null, style);
		}

		var bestMatch = findBestFit(name);

		if (bestMatch == null) {
			bestMatch = findBestFit(name.replaceAll("[^\\p{L}\\p{N}]+", " ")); //$NON-NLS-1$ //$NON-NLS-2$
		}

		if (bestMatch != null) {
			fontNameMapping.put(name, bestMatch);
			return fm.matchFamilyStyle(bestMatch, style);
		}

		if (SWT.getPlatform().equals("win32")) { //$NON-NLS-1$
			// arabic fonts are no longer supported on windows. Also windows falls back to
			// arial
			if (name.toLowerCase().startsWith("arabic ") || name.toLowerCase().equals("arabic")) { //$NON-NLS-1$ //$NON-NLS-2$
				fontNameMapping.put(name, "Arial"); //$NON-NLS-1$
				return fm.matchFamilyStyle("Arial", style); //$NON-NLS-1$
			}
		}
		// No matching font found, use system default
		unknownFonts.add(name);
		return fm.matchFamilyStyle(null, style);

	}

	private static String findBestFit(String name) {
		final String[] parts = name.split(" "); //$NON-NLS-1$

		for (int i = 0; i < parts.length; i++) {
			parts[i] = parts[i].trim();
			parts[i] = parts[i].toLowerCase();
		}

		final FontMgr fm = FontMgr.getDefault();
		final var count = fm.getFamiliesCount();

		String bestMatch = null;
		int bestMatchScore = 0;

		for (int i = 0; i < count; i++) {

			final var f = fm.getFamilyName(i);
			final var fLower = f.toLowerCase();

			int score = 0;
			for (final String part : parts) {
				if (fLower.contains(part)) {
					score++;
				}
			}

			if (score > bestMatchScore) {
				bestMatchScore = score;
				bestMatch = f;
			}
		}
		return bestMatch;
	}

	private org.eclipse.swt.graphics.Font getDefaultFont() {
		org.eclipse.swt.graphics.Font originalFont = canvas.getFont();

		if (originalFont == null || originalFont.isDisposed()) {
			originalFont = canvas.getDisplay().getSystemFont();
		}
		return originalFont;
	}

	@Override
	public io.github.humbleui.skija.Font getM3Font() {
		if (m3Font == null) {
			setFont(null);
		}
		return m3Font;
	}

	@Override
	public IDpiScaler getScaler() {
		return m3Extension.getScaler();
	}

	private void resetResources() {

		if (this.m3Font != null && !this.m3Font.isClosed()) {
			this.m3Font.close();
		}
		m3Font = null;

		fontCache.values().forEach(f -> {
			if (!f.isClosed()) {
				f.close();
			}
		});
		fontCache.clear();

		textImageCache.values().forEach(i -> {
			if (!i.isClosed()) {
				i.close();
			}
		});

		textImageCache.clear();
		cachedTextSplits.clear();

		fontNameMapping.clear();
		unknownFonts.clear();

	}

	@Override
	public Font getFont() {

		if (swtFont != null && !swtFont.isDisposed()) {
			return swtFont;
		}
		swtFont = getDefaultFont();

		return swtFont;
	}

	@Override
	public void resetBaseColors() {
		foreground = null;
		background = null;

	}

	@Override
	public void cacheTextImage(String text, FontProperties fontProperties, boolean transparent, int background,
			int foreground, boolean antiAlias, IM3Image m3Image) {
		final var key = new ImageTextKey(text, fontProperties, transparent, background, foreground, antiAlias);
		final var old = textImageCache.get(key);
		if (old != null && !old.isClosed()) {
			old.close();
		}
		this.textImageCache.put(key, m3Image);
	}

	@Override
	public IM3Image getTextImage(String text, FontProperties fontProperties, boolean transparent, int background,
			int foreground, boolean antialias) {
		return this.textImageCache
				.get(new ImageTextKey(text, fontProperties, transparent, background, foreground, antialias));
	}

	private static String[] splitString(String text) {
		return text.split("\r\n|\n|\r"); //$NON-NLS-1$
	}

	@Override
	public String[] getTextSplits(String inputText, int flags) {

		final boolean replaceAmpersand = (flags & SWT.DRAW_MNEMONIC) != 0;
		final boolean delimiter = (flags & SWT.DRAW_DELIMITER) != 0;
		final boolean tabulatorExpansion = (flags & SWT.DRAW_TAB) != 0;

		String[] splits = null;

		if (USE_TEXT_IMAGE_CACHE) {
			splits = cachedTextSplits
					.get(new SplitsTextCache(inputText, replaceAmpersand, delimiter, tabulatorExpansion));
		}

		String workInputText = inputText;

		if (splits == null) {
			if (tabulatorExpansion) {
				workInputText = expandTabs(workInputText, 0);
			} else {
				workInputText = workInputText.replaceAll("\\t", ""); //$NON-NLS-1$ //$NON-NLS-2$
			}

			if (replaceAmpersand) {
				workInputText = replaceMnemonics(workInputText);
			}

			// replace form feed characters with "\u240C" this is the unicode standard sign
			// for form feed.
			// unfortunately skia does not even render these form feed characters...
			workInputText = workInputText.replace("\f", "\u240C"); //$NON-NLS-1$//$NON-NLS-2$

			if (delimiter) {
				splits = splitString(workInputText);
			} else {
				splits = new String[] { removeDelimiter(workInputText) };
			}
		}

		if (USE_TEXT_IMAGE_CACHE) {
			cachedTextSplits.put(new SplitsTextCache(inputText, replaceAmpersand, delimiter, tabulatorExpansion),
					splits);
		}
		return splits;
	}

	/**
	 * Expands tab characters (\t) in the text to position-dependent spaces, so that
	 * the next character aligns to the next tab stop (every 8 average character
	 * widths by default). The expansion is based on the current x position and the
	 * average character width of the font.
	 *
	 * @param text   The input text containing tab characters
	 * @param startX The starting x position in pixels (used to calculate tab
	 *               alignment)
	 * @return The text with tabs expanded to spaces, aligned to the next tab stop
	 */
	private String expandTabs(String text, int startX) {
		final StringBuilder result = new StringBuilder();
		int currentX = 0;
		// Measure space width exactly once — it is constant for the current font.
		final int spaceWidth = textExtent(" ").x; //$NON-NLS-1$
		final float avgCharWidthF = getM3Font().getMetrics()._avgCharWidth;
		int avgCharWidth = (int) avgCharWidthF;
		if (avgCharWidth <= 0) {
			avgCharWidth = spaceWidth > 0 ? spaceWidth : 1;
		}
		final int tabSpacingPx = 8 * avgCharWidth;
		for (int i = 0; i < text.length(); i++) {
			final char ch = text.charAt(i);
			if (ch == '\t') {
				final int offsetInTab = tabSpacingPx > 0 ? (currentX - startX) % tabSpacingPx : 0;
				final int nextTabX = currentX + (tabSpacingPx - offsetInTab);
				while (currentX < nextTabX) {
					result.append(' ');
					// reuse the already-measured spaceWidth instead of calling textExtent again
					currentX += spaceWidth;
				}
			} else {
				// measureTextWidth avoids creating an intermediate String object per character
				final int charWidth = (int) getM3Font().measureTextWidth(String.valueOf(ch));
				result.append(ch);
				currentX += charWidth;
			}
		}
		return result.toString();
	}

	@Override
	public Point textExtent(String text, int flags) {
		// M3 (GC seam, leaf 6; TEXT_METRICS, pattern M3-GC-TEXT-METRICS v1, census GF28): the lines the draw draws for these flags
		// (getTextSplits: DRAW_DELIMITER lines, DRAW_TAB expansion, DRAW_MNEMONIC prefixes), the widest rounded up, one line
		// height each. The donor ignored the flags, always removed '&' and truncated
		final String[] lines = getTextSplits(text, flags);
		final io.github.humbleui.skija.Font font = getM3Font();
		final double width = Arrays.stream(lines).mapToDouble(font::measureTextWidth).max().orElse(0);
		return new Point((int) Math.ceil(width), Math.max(1, lines.length) * lineHeight());
	}

	@Override
	public Point stringExtent(String string) {
		return new Point((int) Math.ceil(getM3Font().measureTextWidth(string)), lineHeight());
	}

	/* one line as M3TextDrawing advances it: ceil |ascent| + ceil descent + Skia's line gap, rounded up (the text image height) */
	private int lineHeight() {
		final var m = getM3Font().getMetrics();
		return (int) Math.ceil(Math.ceil(Math.abs(m.getAscent())) + Math.ceil(Math.abs(m.getDescent())) + m.getLeading());
	}

	@Override
	public Point textExtent(String string) {
		return textExtent(string, SWT.NONE);
	}

	private static String removeDelimiter(String inputText) {
		return inputText.replaceAll("\r\n|\r|\n", ""); //$NON-NLS-1$//$NON-NLS-2$
	}

	public static String replaceMnemonics(String text) {
		// M3 (GC seam, leaf 6; TEXT_DRAW_FLAGS): DrawText without DT_NOPREFIX: a single '&' marks the mnemonic and is not drawn,
		// "&&" draws one '&' (the donor removed every '&'). The underline is not drawn (GF30, approximated)
		return text.replaceAll("&(&?)", "$1"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public FontData getFontData() {
		final var font = getFont();
		return font.getFontData()[0];
	}
}