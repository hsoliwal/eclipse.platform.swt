package org.eclipse.swt.tests.junit.m3gc;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.canvasext.FontProperties;
import org.eclipse.swt.internal.canvasext.IDpiScaler;
import org.eclipse.swt.internal.render.m3.gc.IM3Image;
import org.eclipse.swt.internal.render.m3.gc.IM3Resources;

public class M3ResourcesDouble implements IM3Resources {

	public Color background;
	public Color foreground;
	public Font font;
	public jdk.m3.graphics.M3Font m3Font;
	public Image cachedImage;
	public IM3Image cachedM3Image;
	public IM3Image cachedTextImage;
	public String[] textSplits;
	public Point textExtent;
	public FontData fontData;
	public IDpiScaler scaler;

	public M3ResourcesDouble(int zoom) {
		this.scaler = () -> zoom;
	}

	public M3ResourcesDouble() {
		this(100);
	}

	@Override
	public void setBackground(Color color) {
		this.background = color;
	}

	@Override
	public void setForeground(Color color) {
		this.foreground = color;
	}

	@Override
	public Color getForeground() {
		return foreground;
	}

	@Override
	public Color getBackground() {
		return background;
	}

	@Override
	public void setFont(Font font) {
		this.font = font;
	}

	@Override
	public jdk.m3.graphics.M3Font getM3Font() {
		return m3Font;
	}

	@Override
	public IDpiScaler getScaler() {
		return scaler;
	}

	@Override
	public Font getFont() {
		return font;
	}

	@Override
	public void resetFont() {
		this.font = null;
	}

	@Override
	public void resetBaseColors() {
		// TODO Auto-generated method stub

	}

	@Override
	public void cacheTextImage(String text, FontProperties fontProperties, boolean transparent, int background,
			int foreground, boolean antiAlias, IM3Image m3Image) {
		// TODO Auto-generated method stub

	}

	@Override
	public IM3Image getTextImage(String text, FontProperties fontProperties, boolean transparent, int background,
			int foreground, boolean antialias) {
		return cachedTextImage;
	}

	@Override
	public String[] getTextSplits(String inputText, int flags) {
		return TextSplitUtil.getTextSplits(inputText, flags, null, false);
	}

	@Override
	public Point textExtent(String text, int flags) {
		return textExtent;
	}

	@Override
	public Point textExtent(String string) {
		return textExtent;
	}

	@Override
	public FontData getFontData() {
		return this.fontData;
	}

}