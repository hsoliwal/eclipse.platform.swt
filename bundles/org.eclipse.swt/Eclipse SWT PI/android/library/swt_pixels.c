/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/

/*
 * Native pixel move for the Android port (library swtpixels; link with jnigraphics).
 *
 * AndroidPixels.moveNative: locks the bitmap, moves the rows of one rectangle by (dx, dy) in place and
 * unlocks it. One JNI call per scroll instead of two framework calls per band of rows. Rows go bottom-up
 * when the pixels move down and top-down otherwise; memmove covers the overlap inside a row. Anything that
 * is not a 32-bit bitmap with both rectangles inside it is refused, and the caller then uses its Java lane.
 */

#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <android/bitmap.h>

JNIEXPORT jboolean JNICALL Java_org_eclipse_swt_internal_android_AndroidPixels_moveNative(JNIEnv *env,
		jclass clazz, jobject bitmap, jint x, jint y, jint width, jint height, jint dx, jint dy) {
	AndroidBitmapInfo info;
	void *pixels = NULL;
	int64_t targetX = (int64_t)x + dx, targetY = (int64_t)y + dy;
	size_t bytes;
	jint row;
	(void)clazz;

	if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
	if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
	/* Defensive format invariant: each RGBA pixel must fit its advertised stride. */
	if ((uint64_t)info.stride < (uint64_t)info.width * 4u) return JNI_FALSE;
	if (width <= 0 || height <= 0 || x < 0 || y < 0) return JNI_FALSE;
	if ((int64_t)x + width > (int64_t)info.width || (int64_t)y + height > (int64_t)info.height) return JNI_FALSE;
	if (targetX < 0 || targetY < 0) return JNI_FALSE;
	if (targetX + width > (int64_t)info.width || targetY + height > (int64_t)info.height) return JNI_FALSE;
	if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
	if (pixels == NULL) {
		/* A successful lock must be balanced even if the pointer is unusable. */
		AndroidBitmap_unlockPixels(env, bitmap);
		return JNI_FALSE;
	}

	bytes = (size_t)width * 4u;
	if (dy > 0) {
		for (row = height - 1; row >= 0; row--) {
			uint8_t *from = (uint8_t *)pixels + (size_t)(y + row) * info.stride + (size_t)x * 4u;
			uint8_t *to = (uint8_t *)pixels + (size_t)(targetY + row) * info.stride + (size_t)targetX * 4u;
			memmove(to, from, bytes);
		}
	} else {
		for (row = 0; row < height; row++) {
			uint8_t *from = (uint8_t *)pixels + (size_t)(y + row) * info.stride + (size_t)x * 4u;
			uint8_t *to = (uint8_t *)pixels + (size_t)(targetY + row) * info.stride + (size_t)targetX * 4u;
			memmove(to, from, bytes);
		}
	}
	AndroidBitmap_unlockPixels(env, bitmap);
	return JNI_TRUE;
}
