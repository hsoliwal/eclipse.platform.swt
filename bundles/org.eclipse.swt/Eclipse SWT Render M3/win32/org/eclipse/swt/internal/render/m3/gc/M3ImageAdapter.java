package org.eclipse.swt.internal.render.m3.gc;

import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Shader;

public class M3ImageAdapter implements IM3Image {

	public M3ImageAdapter(Image m3Image) {
		this.m3Image = m3Image;
	}

	private final Image m3Image;

	@Override
	public boolean isClosed() {
		return m3Image.isClosed();
	}

	@Override
	public int getHeight() {
		return m3Image.getHeight();
	}

	@Override
	public void close() {
		m3Image.close();
	}

	@Override
	public int getWidth() {
		return m3Image.getWidth();
	}

	@Override
	public Image getM3Image() {
		return m3Image;
	}

	@Override
	public Shader makeShader(FilterTileMode repeat) {
		return m3Image.makeShader(repeat);
	}

}
