package org.eclipse.swt.internal.render.m3.gc;

import org.eclipse.swt.internal.canvasext.*;


public class Logger {

	public static void logException(Throwable t) {
		t.printStackTrace(System.err);
	}

}
