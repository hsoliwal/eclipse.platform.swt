/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.cocoa;

public class CATransaction extends NSObject {

public static double animationDuration() {
	return OS.objc_msgSend_fpret(OS.class_CATransaction, OS.sel_animationDuration);
}

public static void begin() {
	OS.objc_msgSend(OS.class_CATransaction, OS.sel_begin);
}

public static void commit() {
	OS.objc_msgSend(OS.class_CATransaction, OS.sel_commit);
}

public static boolean disableActions() {
	return OS.objc_msgSend_bool(OS.class_CATransaction, OS.sel_disableActions);
}

public static void setAnimationDuration(double duration) {
	OS.objc_msgSend(OS.class_CATransaction, OS.sel_setAnimationDuration_, duration);
}

public static void setDisableActions(boolean disable) {
	OS.objc_msgSend(OS.class_CATransaction, OS.sel_setDisableActions_, disable);
}

}
