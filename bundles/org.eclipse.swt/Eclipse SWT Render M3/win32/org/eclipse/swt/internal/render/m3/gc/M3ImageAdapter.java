package org.eclipse.swt.internal.render.m3.gc;

import jdk.m3.graphics.M3FilterTileMode;
import jdk.m3.graphics.M3Image;
import jdk.m3.graphics.M3Shader;

public class M3ImageAdapter implements IM3Image {

	public M3ImageAdapter(M3Image m3Image) {
		this.m3Image = m3Image;
	}

	private final M3Image m3Image;

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
	public M3Image getM3Image() {
		return m3Image;
	}

	@Override
	public M3Shader makeShader(M3FilterTileMode repeat) {
		return m3Image.makeShader(repeat);
	}

}
