package org.eclipse.swt.tests.junit.raster;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.canvasext.FontProperties;
import org.eclipse.swt.internal.canvasext.IDpiScaler;
import org.eclipse.swt.internal.canvasext.raster.IRasterImage;
import org.eclipse.swt.internal.canvasext.raster.IRasterResources;

public class RasterResourcesDouble implements IRasterResources {

	public Color background;
	public Color foreground;
	public Font font;
	public io.github.humbleui.skija.Font m3Font;
	public Image cachedImage;
	public IRasterImage cachedM3Image;
	public IRasterImage cachedTextImage;
	public String[] textSplits;
	public Point textExtent;
	public FontData fontData;
	public IDpiScaler scaler;

	public RasterResourcesDouble(int zoom) {
		this.scaler = () -> zoom;
	}

	public RasterResourcesDouble() {
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
	public io.github.humbleui.skija.Font getM3Font() {
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
	public void resetBaseColors() {
		// TODO Auto-generated method stub

	}

	@Override
	public void cacheTextImage(String text, FontProperties fontProperties, boolean transparent, int background,
			int foreground, boolean antiAlias, IRasterImage m3Image) {
		// TODO Auto-generated method stub

	}

	@Override
	public IRasterImage getTextImage(String text, FontProperties fontProperties, boolean transparent, int background,
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