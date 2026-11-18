/* Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 * Optional Java JNI bulk blend kernel. Never edits SWT-generated JNI sources. */
#include <jni.h>
#include <math.h>
#include <stdint.h>

static inline uint32_t mul255(uint32_t x, uint32_t y) {
    return (x * y + 127u) / 255u;
}

/* These W3C separable equations match LayerArgbCompositor's Java fallback. */
static inline double hard_light(double backdrop, double source) {
    return source <= 0.5 ? backdrop * 2.0 * source :
            backdrop + (2.0 * source - 1.0) - backdrop * (2.0 * source - 1.0);
}

static inline double soft_light(double backdrop, double source) {
    if (source <= 0.5) return backdrop - (1.0 - 2.0 * source) * backdrop * (1.0 - backdrop);
    double d = backdrop <= 0.25 ? ((16.0 * backdrop - 12.0) * backdrop + 4.0) * backdrop : sqrt(backdrop);
    return backdrop + (2.0 * source - 1.0) * (d - backdrop);
}

static double blend_separable(jint mode, double backdrop, double source) {
    switch (mode) {
        case 3: return hard_light(source, backdrop); /* overlay */
        case 4: return fmin(backdrop, source);
        case 5: return fmax(backdrop, source);
        case 6: return backdrop == 0.0 ? 0.0 : source >= 1.0 ? 1.0 : fmin(1.0, backdrop / (1.0 - source));
        case 7: return backdrop >= 1.0 ? 1.0 : source <= 0.0 ? 0.0 : 1.0 - fmin(1.0, (1.0 - backdrop) / source);
        case 8: return hard_light(backdrop, source);
        case 9: return soft_light(backdrop, source);
        case 10: return fabs(backdrop - source);
        case 11: return backdrop + source - 2.0 * backdrop * source;
        default: return source;
    }
}

static inline uint32_t separable(jint mode, uint32_t sc, uint32_t sa, uint32_t dc, uint32_t da) {
    uint32_t base = mul255(sc, 255u - da) + mul255(dc, 255u - sa);
    if (sa == 0u || da == 0u) return base;
    double source = fmin(1.0, (double)sc / sa);
    double backdrop = fmin(1.0, (double)dc / da);
    return base + (uint32_t)floor((double)sa * da * blend_separable(mode, backdrop, source) / 255.0 + 0.5);
}

/* Optional capability query: legacy libraries lack this export and support modes 0..2. */
JNIEXPORT jint JNICALL
Java_org_eclipse_swt_internal_render_LayerArgbCompositor_nativeModeCount(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return 12;
}

JNIEXPORT void JNICALL
Java_org_eclipse_swt_internal_render_LayerArgbCompositor_blendNative(
        JNIEnv* env, jclass clazz, jintArray destination, jint destinationOffset,
        jintArray source, jint sourceOffset, jint length, jint opacity, jint mode) {
    (void)clazz;
    if (destination == NULL || source == NULL || length < 0 ||
            destinationOffset < 0 || sourceOffset < 0 || opacity < 0 || opacity > 255 ||
            mode < 0 || mode > 11 ||
            (*env)->IsSameObject(env, destination, source) ||
            (jlong)destinationOffset + length > (*env)->GetArrayLength(env, destination) ||
            (jlong)sourceOffset + length > (*env)->GetArrayLength(env, source)) {
        jclass bad = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        if (bad != NULL) (*env)->ThrowNew(env, bad, "bad native layer blend arguments");
        return;
    }
    jint* src = (*env)->GetIntArrayElements(env, source, NULL);
    if (src == NULL) return;
    jint* dst = (*env)->GetIntArrayElements(env, destination, NULL);
    if (dst == NULL) {
        (*env)->ReleaseIntArrayElements(env, source, src, JNI_ABORT);
        return;
    }
    for (jint i = 0; i < length; ++i) {
        uint32_t s = (uint32_t)src[sourceOffset+i], d = (uint32_t)dst[destinationOffset+i];
        uint32_t sa = mul255(s >> 24, (uint32_t)opacity);
        uint32_t sr = mul255((s >> 16) & 255u, (uint32_t)opacity);
        uint32_t sg = mul255((s >> 8) & 255u, (uint32_t)opacity);
        uint32_t sb = mul255(s & 255u, (uint32_t)opacity);
        uint32_t da = d >> 24, dr = (d >> 16) & 255u, dg = (d >> 8) & 255u, db = d & 255u;
        uint32_t a = sa + mul255(da, 255u-sa), r, g, b;
        if (mode == 0) {
            r = sr + mul255(dr,255u-sa);
            g = sg + mul255(dg,255u-sa);
            b = sb + mul255(db,255u-sa);
        } else if (mode == 1) {
            r = mul255(sr,255u-da)+mul255(dr,255u-sa)+mul255(sr,dr);
            g = mul255(sg,255u-da)+mul255(dg,255u-sa)+mul255(sg,dg);
            b = mul255(sb,255u-da)+mul255(db,255u-sa)+mul255(sb,db);
        } else if (mode == 2) {
            r = sr+dr-mul255(sr,dr);
            g = sg+dg-mul255(sg,dg);
            b = sb+db-mul255(sb,db);
        } else {
            r = separable(mode,sr,sa,dr,da);
            g = separable(mode,sg,sa,dg,da);
            b = separable(mode,sb,sa,db,da);
        }
        if (r > a) r = a;
        if (g > a) g = a;
        if (b > a) b = a;
        dst[destinationOffset+i]=(jint)((a<<24)|(r<<16)|(g<<8)|b);
    }
    (*env)->ReleaseIntArrayElements(env, destination, dst, 0);
    (*env)->ReleaseIntArrayElements(env, source, src, JNI_ABORT);
}
