package org.eclipse.swt.tests.junit.m3gc;

import java.util.Arrays;
import java.util.Objects;

import jdk.m3.graphics.M3Font;
import jdk.m3.graphics.M3Paint;

public class MethodCall {

	public final String className;
	public final String methodName;
	public final Object[] params;

	public MethodCall(String className, String methodName, Object... params) {
		this.className = className;
		this.methodName = methodName;

		for (final Object p : params) {
			if (p instanceof M3Paint pa) {
				params[Arrays.asList(params).indexOf(p)] = PaintData.getData(pa);
			}

			if (p instanceof M3Font f) {
				params[Arrays.asList(params).indexOf(p)] = ExtractedFontData.getData(f);
			}

		}

		this.params = params;
	}

	@Override
	public String toString() {
		return "MethodCall [className=" + className + ", methodName=" + methodName + ", params="
				+ Arrays.toString(params) + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.deepHashCode(params);
		result = prime * result + Objects.hash(className, methodName);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final MethodCall other = (MethodCall) obj;
		return Objects.equals(className, other.className) && Objects.equals(methodName, other.methodName)
				&& Arrays.deepEquals(params, other.params);
	}

	public static MethodCall get(String className, String methodName, Object... params) {
		return new MethodCall(className, methodName, params);
	}

}