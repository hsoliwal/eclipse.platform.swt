/* SPDX-License-Identifier: EPL-2.0 */
#ifndef M3_ANDROID_BITMAP_TEST_ONLY_H
#define M3_ANDROID_BITMAP_TEST_ONLY_H
#include <jni.h>
#include <stdint.h>
/* Test-only subset of the public NDK AndroidBitmap contract, never packaged. */
typedef struct AndroidBitmapInfo {
    uint32_t width;
    uint32_t height;
    uint32_t stride;
    int32_t format;
    uint32_t flags;
} AndroidBitmapInfo;
#define ANDROID_BITMAP_RESULT_SUCCESS 0
#define ANDROID_BITMAP_RESULT_BAD_PARAMETER (-1)
#define ANDROID_BITMAP_FORMAT_RGBA_8888 1
int AndroidBitmap_getInfo(JNIEnv *env, jobject bitmap, AndroidBitmapInfo *info);
int AndroidBitmap_lockPixels(JNIEnv *env, jobject bitmap, void **pixels);
int AndroidBitmap_unlockPixels(JNIEnv *env, jobject bitmap);
#endif
