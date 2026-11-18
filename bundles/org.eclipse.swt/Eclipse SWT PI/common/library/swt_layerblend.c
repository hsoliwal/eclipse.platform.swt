/* Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 * Optional Java JNI bulk blend kernel. Never edits SWT-generated JNI sources. */
#include <jni.h>
#include <stdint.h>

static inline uint32_t mul255(uint32_t x, uint32_t y) {
    return (x * y + 127u) / 255u;
}

/* Reuse the same premultiplied kernel for ordinary and masked JNI paths. */
static inline uint32_t blend_pixel(uint32_t s, uint32_t d, uint32_t opacity, jint mode) {
    uint32_t sa = mul255(s >> 24, opacity);
    uint32_t sr = mul255((s >> 16) & 255u, opacity), sg = mul255((s >> 8) & 255u, opacity), sb = mul255(s & 255u, opacity);
    uint32_t da = d >> 24, dr = (d >> 16) & 255u, dg = (d >> 8) & 255u, db = d & 255u;
    uint32_t a = sa + mul255(da, 255u-sa), r, g, b;
    if (mode == 0) {
        r = sr + mul255(dr,255u-sa); g = sg + mul255(dg,255u-sa); b = sb + mul255(db,255u-sa);
    } else if (mode == 1) {
        r = mul255(sr,255u-da)+mul255(dr,255u-sa)+mul255(sr,dr);
        g = mul255(sg,255u-da)+mul255(dg,255u-sa)+mul255(sg,dg);
        b = mul255(sb,255u-da)+mul255(db,255u-sa)+mul255(sb,db);
    } else {
        r = sr+dr-mul255(sr,dr); g = sg+dg-mul255(sg,dg); b = sb+db-mul255(sb,db);
    }
    if (r > a) r = a;
    if (g > a) g = a;
    if (b > a) b = a;
    return (a << 24) | (r << 16) | (g << 8) | b;
}
static int validate(JNIEnv* env, jintArray destination, jint destinationOffset,
        jintArray source, jint sourceOffset, jint length, jint opacity, jint mode,
        jbyteArray mask, jint maskOffset) {
    if (!validate(env, destination, destinationOffset, source, sourceOffset,
            length, opacity, mode, NULL, 0)) return;
    jint* src = (*env)->GetIntArrayElements(env, source, NULL);
    if (src == NULL) return;
    jint* dst = (*env)->GetIntArrayElements(env, destination, NULL);
    if (dst == NULL) {
        (*env)->ReleaseIntArrayElements(env, source, src, JNI_ABORT);
        return;
    }
    for (jint i = 0; i < length; ++i) {
        dst[destinationOffset+i] = (jint)blend_pixel(
                (uint32_t)src[sourceOffset+i], (uint32_t)dst[destinationOffset+i],
                (uint32_t)opacity, mode);
    }
    (*env)->ReleaseIntArrayElements(env, destination, dst, 0);
    (*env)->ReleaseIntArrayElements(env, source, src, JNI_ABORT);
}

/* Optional 8-bit alpha mask; the existing unmasked JNI symbol is unchanged. */
JNIEXPORT void JNICALL
Java_org_eclipse_swt_internal_render_LayerArgbCompositor_blendMaskedNative(
        JNIEnv* env, jclass clazz, jintArray destination, jint destinationOffset,
        jintArray source, jint sourceOffset, jbyteArray mask, jint maskOffset,
        jint length, jint opacity, jint mode) {
    (void)clazz;
    if (mask == NULL) {
        jclass bad = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        if (bad != NULL) (*env)->ThrowNew(env, bad, "mask required");
        return;
    }
    if (!validate(env, destination, destinationOffset, source, sourceOffset,
            length, opacity, mode, mask, maskOffset)) return;
    jint* src = (*env)->GetIntArrayElements(env, source, NULL);
    if (src == NULL) return;
    jint* dst = (*env)->GetIntArrayElements(env, destination, NULL);
    if (dst == NULL) {
        (*env)->ReleaseIntArrayElements(env, source, src, JNI_ABORT);
        return;
    }
    jbyte* coverage = (*env)->GetByteArrayElements(env, mask, NULL);
    if (coverage == NULL) {
        (*env)->ReleaseIntArrayElements(env, destination, dst, JNI_ABORT);
        (*env)->ReleaseIntArrayElements(env, source, src, JNI_ABORT);
        return;
    }
    for (jint i = 0; i < length; ++i) {
        uint32_t effective = mul255((uint32_t)opacity, (uint8_t)coverage[maskOffset+i]);
        if (effective != 0) dst[destinationOffset+i] = (jint)blend_pixel(
                (uint32_t)src[sourceOffset+i], (uint32_t)dst[destinationOffset+i],
                effective, mode);
    }
    (*env)->ReleaseByteArrayElements(env, mask, coverage, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, destination, dst, 0);
    (*env)->ReleaseIntArrayElements(env, source, src, JNI_ABORT);
}
