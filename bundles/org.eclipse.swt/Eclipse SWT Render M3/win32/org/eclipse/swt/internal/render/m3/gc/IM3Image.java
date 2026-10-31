package org.eclipse.swt.internal.render.m3.gc;

import jdk.m3.graphics.M3FilterTileMode;
import jdk.m3.graphics.M3Shader;

public interface IM3Image extends AutoCloseable {

	boolean isClosed();

	int getHeight();

	int getWidth();

	M3Shader makeShader(M3FilterTileMode repeat);

	jdk.m3.graphics.M3Image getM3Image();

	@Override
	void close();
}
