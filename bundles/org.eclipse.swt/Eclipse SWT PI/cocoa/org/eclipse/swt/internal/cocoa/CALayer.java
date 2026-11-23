/*******************************************************************************
 * Copyright (c) 2000, 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *    Tobias Melcher - native layer border bindings
 *******************************************************************************/
package org.eclipse.swt.internal.cocoa;

public class CALayer extends NSObject {

public CALayer() {
	super();
}

public CALayer(long id) {
	super(id);
}

public CALayer(id id) {
	super(id);
}

public void addSublayer(CALayer layer) {
	OS.objc_msgSend(this.id, OS.sel_addSublayer_, layer != null ? layer.id : 0);
}

public NSPoint anchorPoint() {
	NSPoint result = new NSPoint();
	OS.objc_msgSend_stret(result, this.id, OS.sel_anchorPoint);
	return result;
}

public long /*CGColorRef*/ borderColor() {
	return OS.objc_msgSend(this.id, OS.sel_borderColor);
}

public double borderWidth() {
	return OS.objc_msgSend_fpret(this.id, OS.sel_borderWidth);
}

public NSRect bounds() {
	NSRect result = new NSRect();
	OS.objc_msgSend_stret(result, this.id, OS.sel_bounds);
	return result;
}

public id contents() {
	long result = OS.objc_msgSend(this.id, OS.sel_contents);
	return result != 0 ? new id(result) : null;
}

public double contentsScale() {
	return OS.objc_msgSend_fpret(this.id, OS.sel_contentsScale);
}

public double cornerRadius() {
	return OS.objc_msgSend_fpret(this.id, OS.sel_cornerRadius);
}

public NSRect frame() {
	NSRect result = new NSRect();
	OS.objc_msgSend_stret(result, this.id, OS.sel_frame);
	return result;
}

public boolean isHidden() {
	return OS.objc_msgSend_bool(this.id, OS.sel_isHidden);
}

public CALayer mask() {
	long result = OS.objc_msgSend(this.id, OS.sel_mask);
	return result != 0 ? new CALayer(result) : null;
}

public boolean masksToBounds() {
	return OS.objc_msgSend_bool(this.id, OS.sel_masksToBounds);
}

public NSPoint position() {
	NSPoint result = new NSPoint();
	OS.objc_msgSend_stret(result, this.id, OS.sel_position);
	return result;
}

public void removeFromSuperlayer() {
	OS.objc_msgSend(this.id, OS.sel_removeFromSuperlayer);
}

public void setAnchorPoint(NSPoint anchorPoint) {
	OS.objc_msgSend(this.id, OS.sel_setAnchorPoint_, anchorPoint);
}

public void setBorderColor(long /*CGColorRef*/ color) {
	OS.objc_msgSend(this.id, OS.sel_setBorderColor_, color);
}

public void setBorderWidth(double width) {
	OS.objc_msgSend(this.id, OS.sel_setBorderWidth_, width);
}

public void setBounds(NSRect bounds) {
	OS.objc_msgSend(this.id, OS.sel_setBounds_, bounds);
}

public void setContents(id contents) {
	OS.objc_msgSend(this.id, OS.sel_setContents_, contents != null ? contents.id : 0);
}

public void setContentsScale(double scale) {
	OS.objc_msgSend(this.id, OS.sel_setContentsScale_, scale);
}

public void setCornerRadius(double cornerRadius) {
	OS.objc_msgSend(this.id, OS.sel_setCornerRadius_, cornerRadius);
}

public void setFrame(NSRect frame) {
	OS.objc_msgSend(this.id, OS.sel_setFrame_, frame);
}

public void setHidden(boolean hidden) {
	OS.objc_msgSend(this.id, OS.sel_setHidden_, hidden);
}

public void setMask(CALayer mask) {
	OS.objc_msgSend(this.id, OS.sel_setMask_, mask != null ? mask.id : 0);
}

public void setMasksToBounds(boolean masksToBounds) {
	OS.objc_msgSend(this.id, OS.sel_setMasksToBounds_, masksToBounds);
}

public void setPosition(NSPoint position) {
	OS.objc_msgSend(this.id, OS.sel_setPosition_, position);
}

public void setZPosition(double position) {
	OS.objc_msgSend(this.id, OS.sel_setZPosition_, position);
}

public NSArray sublayers() {
	long result = OS.objc_msgSend(this.id, OS.sel_sublayers);
	return result != 0 ? new NSArray(result) : null;
}

public CALayer superlayer() {
	long result = OS.objc_msgSend(this.id, OS.sel_superlayer);
	return result != 0 ? new CALayer(result) : null;
}

public double zPosition() {
	return OS.objc_msgSend_fpret(this.id, OS.sel_zPosition);
}

}
