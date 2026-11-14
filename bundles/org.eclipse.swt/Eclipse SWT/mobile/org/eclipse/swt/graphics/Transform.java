/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import java.util.Arrays;
import org.eclipse.swt.SWT;

/** Affine transform [m11, m12, m21, m22, dx, dy], applied by the native GC. */
public final class Transform extends Resource {
	private float[] elements;
	public Transform(Device device) { this(device, 1, 0, 0, 1, 0, 0); }
	public Transform(Device device, float[] values) { super(device); if (values == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if (values.length < 6) SWT.error(SWT.ERROR_INVALID_ARGUMENT); elements = Arrays.copyOf(values, 6); validate(elements); init(); }
	public Transform(Device device, float m11, float m12, float m21, float m22, float dx, float dy) { this(device, new float[] {m11, m12, m21, m22, dx, dy}); }
	private static void validate(float[] values) { for (float value : values) if (!Float.isFinite(value)) SWT.error(SWT.ERROR_INVALID_ARGUMENT); }
	private void check() { if (isDisposed()) SWT.error(SWT.ERROR_GRAPHIC_DISPOSED); device.checkDevice(); }
	public void getElements(float[] values) { check(); if (values == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if (values.length < 6) SWT.error(SWT.ERROR_INVALID_ARGUMENT); System.arraycopy(elements, 0, values, 0, 6); }
	public void setElements(float m11, float m12, float m21, float m22, float dx, float dy) { check(); float[] next = {m11,m12,m21,m22,dx,dy}; validate(next); elements = next; }
	public void identity() { setElements(1, 0, 0, 1, 0, 0); }
	public boolean isIdentity() { check(); return Arrays.equals(elements, new float[] {1,0,0,1,0,0}); }
	public void invert() {
		check(); float[] m=elements; double determinant=(double)m[0]*m[3]-(double)m[1]*m[2];
		if (determinant == 0) SWT.error(SWT.ERROR_CANNOT_INVERT_MATRIX);
		setElements((float)(m[3]/determinant),(float)(-m[1]/determinant),(float)(-m[2]/determinant),(float)(m[0]/determinant),(float)(((double)m[2]*m[5]-(double)m[3]*m[4])/determinant),(float)(((double)m[1]*m[4]-(double)m[0]*m[5])/determinant));
	}
	public void multiply(Transform matrix) { check(); if (matrix == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if (matrix.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT); concat(matrix.elements); }
	private void concat(float[] b) { float[] a=elements; setElements(a[0]*b[0]+a[2]*b[1],a[1]*b[0]+a[3]*b[1],a[0]*b[2]+a[2]*b[3],a[1]*b[2]+a[3]*b[3],a[0]*b[4]+a[2]*b[5]+a[4],a[1]*b[4]+a[3]*b[5]+a[5]); }
	public void translate(float x,float y) { check(); concat(new float[]{1,0,0,1,x,y}); }
	public void scale(float x,float y) { check(); concat(new float[]{x,0,0,y,0,0}); }
	public void shear(float x,float y) { check(); concat(new float[]{1,y,x,1,0,0}); }
	public void rotate(float angle) { check(); double rad=Math.toRadians(angle); float c=(float)Math.cos(rad),s=(float)Math.sin(rad); concat(new float[]{c,s,-s,c,0,0}); }
	public void transform(float[] points) { check(); if(points==null) SWT.error(SWT.ERROR_NULL_ARGUMENT); float[] m=elements; for(int i=0;i+1<points.length;i+=2){float x=points[i],y=points[i+1];points[i]=m[0]*x+m[2]*y+m[4];points[i+1]=m[1]*x+m[3]*y+m[5];} }
	@Override void destroy() { elements=null; }
	@Override public boolean isDisposed() { return elements==null || device==null || device.isDisposed(); }
	@Override public String toString() { return isDisposed()?"Transform {*DISPOSED*}":"Transform "+Arrays.toString(elements); }
}
