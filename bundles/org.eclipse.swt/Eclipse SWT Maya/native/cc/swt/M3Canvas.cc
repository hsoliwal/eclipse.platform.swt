/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
// M3Canvas: maya's canvas layer above the platform's lowest common denominator (a window and its pixels). A whole org.eclipse.swt.internal.gpu.OpStream drawn on a canvas in one JNI call. It draws the
// same pixels as OpStreamReplay, which makes one call per command (and more per paint and shader).
//
// - The stream is the packed int stream of OpStream: a header int op | (length << 8), then length int arguments.
// - Paints are built here from (PaintKeys mask, ARGB), the way GlPlaneBackend.paint builds them.
// - Text stays cached on the Java side: the caller resolves each DRAW_TEXT and GLYPH_RUN to an SkTextBlob pointer, in
//   stream order, in blobs (0 draws nothing).
// - Arrays are copied in (GetIntArrayRegion, no critical section), so the GC is never held while Skia draws.
#include <jni.h>
#include <cstdint>
#include <vector>
#include "SkCanvas.h"
#include "SkGradientShader.h"
#include "SkMatrix.h"
#include "SkPaint.h"
#include "SkPath.h"
#include "SkTextBlob.h"

namespace {

// OpStream commands and flags (org.eclipse.swt.internal.gpu.OpStream)
const int CLIP = 1, CLIP_RESET = 2, FILL_RECT = 3, DRAW_RECT = 4, DRAW_LINE = 5, DRAW_POINT = 6, FILL_OVAL = 7,
          DRAW_OVAL = 8, FILL_GRADIENT = 9, DRAW_TEXT = 10, FILL_POLY = 11, DRAW_POLY = 12, GLYPH_RUN = 14,
          CLIP_PUSH = 15, CLIP_POP = 16, TRANSFORM = 17, OPEN = 0x40;
// PaintKeys mask fields (org.eclipse.swt.internal.gpu.PaintKeys)
const int KIND_STROKE = 1, KIND_TEXT = 2, CAP_SHIFT = 5, JOIN_SHIFT = 7, WIDTH_SHIFT = 12;

// the paint of a (PaintKeys mask, ARGB) key, as GlPlaneBackend.paint makes it
SkPaint paintOf(int mask, int argb) {
    SkPaint paint;
    paint.setColor(static_cast<SkColor>(argb));
    paint.setAntiAlias(false);
    int kind = mask & 3;
    if (kind == KIND_STROKE) {
        float width = ((static_cast<uint32_t>(mask) >> WIDTH_SHIFT) & 0xFFFF) / 16.0f;
        paint.setStyle(SkPaint::kStroke_Style);
        paint.setStrokeWidth(width <= 1.0f ? 0.0f : width);
        int cap = (mask >> CAP_SHIFT) & 3, join = (mask >> JOIN_SHIFT) & 3;
        paint.setStrokeCap(cap == 2 ? SkPaint::kRound_Cap : cap == 3 ? SkPaint::kSquare_Cap : SkPaint::kButt_Cap);
        paint.setStrokeJoin(join == 2 ? SkPaint::kRound_Join : join == 3 ? SkPaint::kBevel_Join : SkPaint::kMiter_Join);
    } else if (kind == KIND_TEXT) {
        paint.setAntiAlias(true);
    }
    return paint;
}

int fillMask(int mask) {
    return (mask & ~3);
}

float bitsToFloat(int bits) {
    union { int i; float f; } u;
    u.i = bits;
    return u.f;
}

// SWT/GDI+ {m11, m12, m21, m22, dx, dy} as Skia's row-major 3x3 matrix
void applyTransform(SkCanvas* canvas, const jint* a) {
    canvas->setMatrix(SkMatrix::MakeAll(bitsToFloat(a[0]), bitsToFloat(a[2]), bitsToFloat(a[4]),
                                        bitsToFloat(a[1]), bitsToFloat(a[3]), bitsToFloat(a[5]), 0, 0, 1));
}

// width and height made non-negative by moving the origin, as GDI does
void normalize(int& x, int& y, int& w, int& h) {
    if (w < 0) { x += w; w = -w; }
    if (h < 0) { y += h; h = -h; }
}

void gradient(SkCanvas* canvas, const jint* a) {
    int x = a[0], y = a[1], w = a[2], h = a[3];
    normalize(x, y, w, h);
    bool vertical = a[6] != 0;
    SkPoint pts[2] {SkPoint::Make(x, y), SkPoint::Make(vertical ? x : x + w, vertical ? y + h : y)};
    SkColor colors[2] {static_cast<SkColor>(a[4]), static_cast<SkColor>(a[5])};
    SkPaint paint;
    paint.setAntiAlias(true); // a default Skija Paint, as OpStreamReplay makes it
    paint.setShader(SkGradientShader::MakeLinear(pts, colors, nullptr, 2, SkTileMode::kClamp,
                                                 SkGradientShader::kInterpolateColorsInPremul_Flag, nullptr));
    canvas->drawRect(SkRect::MakeLTRB(x, y, x + w, y + h), paint);
}

void poly(SkCanvas* canvas, const jint* a, int op) {
    int count = a[0];
    if (count < 2) return;
    const jint* p = a + 3;
    float offset = (op & ~OPEN) == DRAW_POLY ? .5f : 0.f;
    SkPath path;
    path.moveTo(p[0] + offset, p[1] + offset);
    for (int k = 1; k < count; k++) path.lineTo(p[2 * k] + offset, p[2 * k + 1] + offset);
    if ((op & OPEN) == 0) path.close();
    canvas->drawPath(path, paintOf(a[2], a[1]));
}

SkTextBlob* blobAt(const std::vector<jlong>& blobs, int& next) {
    jlong ptr = next < static_cast<int>(blobs.size()) ? blobs[next] : 0;
    next++;
    return reinterpret_cast<SkTextBlob*>(static_cast<uintptr_t>(ptr));
}

// replays size ints of ops; returns the number of commands drawn
int replay(SkCanvas* canvas, const jint* a, int n, const std::vector<jlong>& blobs, int textMask) {
    int clipSave = -1, transformAt = -1, next = 0, drawn = 0;
    for (int i = 0; i < n;) {
        int header = a[i], op = header & 0xFF, o = i + 1;
        i = o + static_cast<int>(static_cast<uint32_t>(header) >> 8);
        if (i > n) break;
        const jint* b = a + o;
        drawn++;
        switch (op & ~OPEN) {
            case CLIP:
                if (clipSave >= 0) {
                    canvas->restoreToCount(clipSave);
                    if (transformAt >= 0) applyTransform(canvas, a + transformAt);
                }
                clipSave = canvas->save();
                canvas->clipRect(SkRect::MakeLTRB(b[0], b[1], b[0] + b[2], b[1] + b[3]), SkClipOp::kIntersect, false);
                break;
            case CLIP_RESET:
                if (clipSave >= 0) {
                    canvas->restoreToCount(clipSave);
                    if (transformAt >= 0) applyTransform(canvas, a + transformAt);
                }
                clipSave = -1;
                break;
            case TRANSFORM:
                transformAt = o;
                applyTransform(canvas, b);
                break;
            case FILL_RECT: {
                int x = b[0], y = b[1], w = b[2], h = b[3];
                normalize(x, y, w, h);
                canvas->drawRect(SkRect::MakeLTRB(x, y, x + w, y + h), paintOf(b[5], b[4]));
                break;
            }
            case DRAW_RECT: {
                int x = b[0], y = b[1], w = b[2], h = b[3];
                normalize(x, y, w, h);
                canvas->drawRect(SkRect::MakeLTRB(x + .5f, y + .5f, x + w + .5f, y + h + .5f), paintOf(b[5], b[4]));
                break;
            }
            case DRAW_LINE: {
                int mask = b[5], argb = b[4];
                canvas->drawLine(b[0] + .5f, b[1] + .5f, b[2] + .5f, b[3] + .5f, paintOf(mask, argb));
                float width = ((static_cast<uint32_t>(mask) >> WIDTH_SHIFT) & 0xFFFF) / 16.0f;
                if (width <= 1.0f) {
                    canvas->drawRect(SkRect::MakeLTRB(b[2], b[3], b[2] + 1, b[3] + 1), paintOf(fillMask(mask), argb));
                }
                break;
            }
            case DRAW_POINT:
                canvas->drawRect(SkRect::MakeLTRB(b[0], b[1], b[0] + 1, b[1] + 1), paintOf(fillMask(b[5]), b[4]));
                break;
            case FILL_OVAL:
                canvas->drawOval(SkRect::MakeLTRB(b[0], b[1], b[0] + b[2], b[1] + b[3]), paintOf(b[5], b[4]));
                break;
            case DRAW_OVAL:
                canvas->drawOval(SkRect::MakeLTRB(b[0] + .5f, b[1] + .5f, b[0] + b[2] + .5f, b[1] + b[3] + .5f), paintOf(b[5], b[4]));
                break;
            case FILL_GRADIENT:
                gradient(canvas, b);
                break;
            case DRAW_TEXT: {
                SkTextBlob* blob = blobAt(blobs, next);
                int x = b[4], y = b[5];
                if ((b[8] & 1) != 0) canvas->drawRect(SkRect::MakeLTRB(x, y, x + b[9], y + b[10]), paintOf(0, b[7]));
                if (blob != nullptr) canvas->drawTextBlob(blob, x, y + b[11], paintOf(textMask, b[6]));
                break;
            }
            case CLIP_PUSH:
                canvas->save();
                canvas->clipRect(SkRect::MakeLTRB(b[0], b[1], b[0] + b[2], b[1] + b[3]), SkClipOp::kIntersect, false);
                break;
            case CLIP_POP:
                canvas->restore();
                if (transformAt >= 0) applyTransform(canvas, a + transformAt);
                break;
            case GLYPH_RUN: {
                SkTextBlob* blob = blobAt(blobs, next);
                if (blob != nullptr) canvas->drawTextBlob(blob, b[1], b[2], paintOf(textMask, b[3]));
                break;
            }
            case FILL_POLY:
            case DRAW_POLY:
                poly(canvas, b, op);
                break;
            default:
                drawn--;
                break;
        }
    }
    if (clipSave >= 0) canvas->restoreToCount(clipSave);
    return drawn;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_M3Canvas__1nReplay
  (JNIEnv* env, jclass, jlong canvasPtr, jintArray opsArray, jint size, jlongArray blobsArray, jint blobCount, jint textMask) {
    SkCanvas* canvas = reinterpret_cast<SkCanvas*>(static_cast<uintptr_t>(canvasPtr));
    if (canvas == nullptr || opsArray == nullptr || size <= 0) return 0;
    jint length = env->GetArrayLength(opsArray);
    if (size > length) size = length;
    thread_local std::vector<jint> ops;
    thread_local std::vector<jlong> blobs;
    ops.resize(size);
    env->GetIntArrayRegion(opsArray, 0, size, ops.data());
    int count = blobsArray == nullptr ? 0 : blobCount;
    if (count > 0 && count > env->GetArrayLength(blobsArray)) count = env->GetArrayLength(blobsArray);
    blobs.resize(count > 0 ? count : 0);
    if (count > 0) env->GetLongArrayRegion(blobsArray, 0, count, blobs.data());
    return replay(canvas, ops.data(), size, blobs, textMask);
}
