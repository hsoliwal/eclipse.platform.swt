package org.eclipse.swt.internal.canvasext.raster;

import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Shader;

public interface IRasterImage extends AutoCloseable {

	boolean isClosed();

	int getHeight();

	int getWidth();

	Shader makeShader(FilterTileMode repeat);

	io.github.humbleui.skija.Image getM3Image();

	@Override
	void close();
}
