/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
// MayaComposition: layers composited by the window system, above a window's own pixels (its backing store). Z order,
// offset, opacity and visibility are properties of system objects: changing them repaints nothing.
//
// - Windows: DirectComposition (a D3D11 device, a composition device, a topmost target for the window, one visual per
//   layer with a premultiplied BGRA surface and an effect group for opacity).
// - Elsewhere this file compiles to the same exports answering "not supported" (0), so every maya build exports the
//   same names; Android (ASurfaceControl) and Cocoa/UIKit (CALayer) take their place here.
// - One _nApply per frame sets every layer's order, offset, opacity and visibility and commits once.
// - Arrays are copied in (Get*ArrayRegion, no critical section), so the GC is never held while the system works.
#include <jni.h>
#include <cstdint>
#include <vector>
#include "MayaNativeHandleRegistry.h"
#include "MayaCompositionChecks.h"
#include "MayaCompositionDelta.h"
#include "MayaCompositionDirty.h"
#include "MayaDeviceHealth.h"
#include "MayaFrameDirect.h"
#include "MayaFrameView.h"
#include <cstring>
#include <limits>
#include <new>

#ifdef _WIN32
#include <windows.h>
#include <d3d11.h>
#include <dxgi.h>
#include <dcomp.h>

namespace {

template <class T> void release(T *&p) {
    if (p) {
        p->Release();
        p = nullptr;
    }
}

struct Composition;
struct Layer;
// Stable typed handles, not allocator addresses: a freed native object's
// pointer can be reused, but its retired Java token never can.
swt_maya::TokenRegistry<Composition, 0x31> liveCompositions;
swt_maya::TokenRegistry<Layer, 0x32> liveLayers;

struct Layer {
    jlong token = 0;
    Composition *owner = nullptr;
    std::uint64_t seenFrame = 0;
    swt_maya::CommittedLayerProperties committedProperties;
    IDCompositionVisual *visual = nullptr;
    IDCompositionSurface *surface = nullptr;
    IDCompositionEffectGroup *effect = nullptr;
    int width = 0, height = 0;
    bool initialized = false;
};

struct Composition {
    jlong token = 0;
    ID3D11Device *d3d = nullptr;
    ID3D11DeviceContext *context = nullptr;
    IDCompositionDevice *device = nullptr;
    IDCompositionTarget *target = nullptr;
    IDCompositionVisual *root = nullptr;
    std::uint64_t frameSequence = 0;
    std::vector<Layer *> layers;
    swt_maya::CompositionFrameDelta<Layer> frameDelta;
    bool pendingSurfaceCommit = false;
};
Composition *getComposition(jlong handle) {
    return liveCompositions.get(static_cast<std::uint64_t>(handle));
}
Layer *getLayer(jlong handle) {
    return liveLayers.get(static_cast<std::uint64_t>(handle));
}

/*
 * The OS compositor and its D3D11 device must be recreated together after
 * DXGI removal/reset. A successful no-op frame still needs this check: merely
 * comparing Z/property caches cannot establish that retained visuals exist.
 * The Java LayeredViewport owns the software fallback/recovery boundary.
 */
HRESULT checkNativeDevice(Composition *composition) noexcept {
    BOOL valid = FALSE;
    HRESULT status = composition->device->CheckDeviceState(&valid);
    if (SUCCEEDED(status) && valid) return S_OK;
    const HRESULT reason = composition->d3d
            ? composition->d3d->GetDeviceRemovedReason() : S_OK;
    return static_cast<HRESULT>(swt_maya::compositionHealth(
            static_cast<std::int32_t>(status), valid != FALSE,
            static_cast<std::int32_t>(reason)));
}

void destroy(Layer *layer) {
    liveLayers.retire(static_cast<std::uint64_t>(layer->token), layer);
    release(layer->effect);
    release(layer->surface);
    release(layer->visual);
    delete layer;
}

void destroy(Composition *c) {
    liveCompositions.retire(static_cast<std::uint64_t>(c->token), c);
    for (Layer *layer : c->layers) destroy(layer);
    c->layers.clear();
    if (c->target) {
        c->target->SetRoot(nullptr);
        if (c->device) c->device->Commit();
    }
    release(c->root);
    release(c->target);
    release(c->device);
    release(c->context);
    release(c->d3d);
    delete c;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nCreate
  (JNIEnv *, jclass, jlong hwnd) {
    if (!hwnd) return 0;
    Composition *c = new (std::nothrow) Composition();
    if (!c) return 0;
    IDXGIDevice *dxgi = nullptr;
    bool ok = SUCCEEDED(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, D3D11_CREATE_DEVICE_BGRA_SUPPORT,
                      nullptr, 0, D3D11_SDK_VERSION, &c->d3d, nullptr, &c->context))
           && SUCCEEDED(c->d3d->QueryInterface(__uuidof(IDXGIDevice), reinterpret_cast<void **>(&dxgi)))
           && SUCCEEDED(DCompositionCreateDevice(dxgi, __uuidof(IDCompositionDevice), reinterpret_cast<void **>(&c->device)))
           && SUCCEEDED(c->device->CreateTargetForHwnd(reinterpret_cast<HWND>(hwnd), TRUE, &c->target))
           && SUCCEEDED(c->device->CreateVisual(&c->root))
           && SUCCEEDED(c->target->SetRoot(c->root))
           && SUCCEEDED(c->device->Commit());
    release(dxgi);
    if (!ok) {
        destroy(c);
        return 0;
    }
    try {
        const std::uint64_t token = liveCompositions.admit(c);
        if (token == 0) {
            destroy(c);
            return 0;
        }
        c->token = static_cast<jlong>(token);
        return c->token;
    } catch (const std::bad_alloc &) {
        destroy(c);
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nAddLayer
  (JNIEnv *, jclass, jlong composition, jint width, jint height) {
    Composition *c = getComposition(composition);
    if (!c || !swt_maya::validPixelSize(width, height) ||
            FAILED(checkNativeDevice(c))) return 0;
    Layer *layer = new (std::nothrow) Layer();
    if (!layer) return 0;
    layer->owner = c;
    layer->width = width;
    layer->height = height;
    bool ok = SUCCEEDED(c->device->CreateSurface(width, height, DXGI_FORMAT_B8G8R8A8_UNORM, DXGI_ALPHA_MODE_PREMULTIPLIED,
                      &layer->surface))
           && SUCCEEDED(c->device->CreateVisual(&layer->visual))
           && SUCCEEDED(layer->visual->SetContent(layer->surface))
           && SUCCEEDED(c->device->CreateEffectGroup(&layer->effect))
           && SUCCEEDED(layer->visual->SetEffect(layer->effect));
    if (!ok) {
        destroy(layer);
        return 0;
    }
    try {
        c->layers.push_back(layer);
        const std::uint64_t token = liveLayers.admit(layer);
        if (token == 0) {
            c->layers.pop_back();
            destroy(layer);
            return 0;
        }
        layer->token = static_cast<jlong>(token);
        return layer->token;
    } catch (const std::bad_alloc &) {
        // push_back or registry insertion may fail independently. Retire
        // only a successful append and release every acquired COM object.
        if (!c->layers.empty() && c->layers.back() == layer) c->layers.pop_back();
        destroy(layer);
        return 0;
    }
}

// the layer's pixels: width * height premultiplied ARGB ints, row by row (an int's bytes in memory are B, G, R, A,
// which is DXGI_FORMAT_B8G8R8A8); returns 0, or the failing HRESULT
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nUpload
  (JNIEnv *env, jclass, jlong layerHandle, jintArray pixels) {
    Layer *layer = getLayer(layerHandle);
    if (!layer || !pixels) return E_POINTER;
    const HRESULT health = checkNativeDevice(layer->owner);
    if (FAILED(health)) return health;
    jsize n = env->GetArrayLength(pixels);
    if (!swt_maya::validPixelBuffer(layer->width, layer->height, n)) return E_INVALIDARG;
    const jsize count = static_cast<jsize>(static_cast<std::uint64_t>(layer->width) * layer->height);
    std::vector<jint> copy(static_cast<size_t>(count));
    env->GetIntArrayRegion(pixels, 0, count, copy.data());
    if (env->ExceptionCheck()) return E_FAIL;
    ID3D11Texture2D *texture = nullptr;
    POINT at;
    HRESULT hr = layer->surface->BeginDraw(nullptr, __uuidof(ID3D11Texture2D), reinterpret_cast<void **>(&texture), &at);
    if (FAILED(hr)) return hr;
    D3D11_BOX box = { static_cast<UINT>(at.x), static_cast<UINT>(at.y), 0, static_cast<UINT>(at.x + layer->width),
                      static_cast<UINT>(at.y + layer->height), 1 };
    layer->owner->context->UpdateSubresource(texture, 0, &box, copy.data(), static_cast<UINT>(layer->width) * 4, 0);
    release(texture);
    const HRESULT finished = layer->surface->EndDraw();
    if (SUCCEEDED(finished)) {
        layer->initialized = true;
        layer->owner->pendingSurfaceCommit = true;
    }
    return finished;
}

extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nUploadDirty
  (JNIEnv *env, jclass, jlong layerHandle, jintArray packed, jintArray rectangles, jint count) {
    Layer *layer = getLayer(layerHandle);
    if (!layer || !packed || !rectangles) return E_POINTER;
    const HRESULT health = checkNativeDevice(layer->owner);
    if (FAILED(health)) return health;
    const jsize rectangleLength = env->GetArrayLength(rectangles);
    if (count < 0 || count > swt_maya::MAX_DIRTY_RECTS || count > rectangleLength / 4) return E_INVALIDARG;
    try {
        std::vector<jint> rects(static_cast<size_t>(count) * 4);
        if (count > 0) env->GetIntArrayRegion(rectangles, 0, count * 4, rects.data());
        if (env->ExceptionCheck()) return E_FAIL;
        const jsize pixelLength = env->GetArrayLength(packed);
        if (!swt_maya::validPackedDirtyFrame(layer->width, layer->height, pixelLength,
                rects.data(), count, rectangleLength)) return E_INVALIDARG;
        if (count == 0) return S_OK;
        if (!layer->initialized) return E_INVALIDARG; // first draw must cover the entire surface

        std::vector<jint> scratch;
        jsize offset = 0;
        for (jint i = 0; i < count; ++i) {
            const jint *r = rects.data() + static_cast<size_t>(i) * 4;
            const size_t area = swt_maya::dirtyArea(r);
            scratch.resize(area);
            env->GetIntArrayRegion(packed, offset, static_cast<jsize>(area), scratch.data());
            if (env->ExceptionCheck()) return E_FAIL;
            offset += static_cast<jsize>(area);
            RECT update = { r[0], r[1], r[0] + r[2], r[1] + r[3] };
            POINT at = {};
            ID3D11Texture2D *texture = nullptr;
            HRESULT hr = layer->surface->BeginDraw(&update, __uuidof(ID3D11Texture2D),
                    reinterpret_cast<void **>(&texture), &at);
            if (FAILED(hr)) return hr;
            // The returned offset is not necessarily the requested rectangle's origin.
            if (at.x < 0 || at.y < 0 ||
                    static_cast<std::uint64_t>(at.x) + r[2] > std::numeric_limits<UINT>::max() ||
                    static_cast<std::uint64_t>(at.y) + r[3] > std::numeric_limits<UINT>::max()) {
                release(texture);
                layer->surface->EndDraw();
                return E_INVALIDARG;
            }
            D3D11_BOX box = { static_cast<UINT>(at.x), static_cast<UINT>(at.y), 0,
                    static_cast<UINT>(at.x) + static_cast<UINT>(r[2]),
                    static_cast<UINT>(at.y) + static_cast<UINT>(r[3]), 1 };
            layer->owner->context->UpdateSubresource(texture, 0, &box, scratch.data(),
                    static_cast<UINT>(r[2]) * 4, 0);
            release(texture);
            hr = layer->surface->EndDraw();
            if (SUCCEEDED(hr)) layer->owner->pendingSurfaceCommit = true;
            if (FAILED(hr)) return hr; // later failures may have changed prior patches
        }
        return S_OK;
    } catch (const std::bad_alloc &) {
        return E_OUTOFMEMORY;
    }
}

// A single validated DirectComposition transaction for both legacy JNI heap
// arrays and versioned packed direct-buffer frames. The two views yield the
// same Values in Z order; neither one stages new JNI arrays to call the other.
// All handles are checked against composition ownership before COM mutation.
namespace {
inline Layer *lookupFrameLayer(std::uint64_t bits) noexcept {
    static_assert(sizeof(jlong) == sizeof(std::uint64_t),
            "Maya requires 64-bit opaque JNI handles");
    jlong signedHandle = 0;
    std::memcpy(&signedHandle, &bits, sizeof(bits));
    return getLayer(signedHandle);
}

inline void invalidateFrameCache(Composition *c) noexcept {
    // After a failed or interrupted native transaction, the cached scene
    // never becomes authority until a subsequent successful Commit.
    c->frameDelta.invalidate();
    for (Layer *layer : c->layers) layer->committedProperties.invalidate();
}

template<class View>
jint applyNativeFrame(Composition *c, const View &view) noexcept {
    const std::size_t count = view.size();
    if (count > static_cast<std::size_t>(std::numeric_limits<jint>::max() / 4))
        return E_INVALIDARG;
    const HRESULT health = checkNativeDevice(c);
    if (FAILED(health)) {
        // Never treat a cached no-op frame as valid after DXGI reset.
        invalidateFrameCache(c);
        return health;
    }
    try {
        // Validation precedes frameDelta.begin, RemoveAllVisuals and all
        // property writes. Duplicate/foreign/stale handles are rejected.
        std::uint64_t generation = ++c->frameSequence;
        if (generation == 0) {
            for (Layer *layer : c->layers) layer->seenFrame = 0;
            generation = ++c->frameSequence;
        }
        for (std::size_t i = 0; i < count; i++) {
            const auto value = view.at(i);
            Layer *layer = lookupFrameLayer(value.handle);
            if (!layer || layer->owner != c || layer->seenFrame == generation ||
                    !swt_maya::validLayerProperties(value.x, value.y,
                            value.opacity, value.visible)) return E_INVALIDARG;
            layer->seenFrame = generation;
        }

        // The native OS tree is retained. The requested order is staged
        // without mutating the committed order; accept() swaps only after
        // successful Commit and cannot allocate.
        c->frameDelta.begin();
        for (std::size_t i = 0; i < count; i++) {
            const auto value = view.at(i);
            if (value.visible != 0.0f) {
                c->frameDelta.includeVisible(lookupFrameLayer(value.handle));
            }
        }
        const bool reorder = c->frameDelta.requiresRebuild();
        bool changed = reorder || c->pendingSurfaceCommit;
        HRESULT hr = reorder ? c->root->RemoveAllVisuals() : S_OK;
        for (std::size_t i = 0; SUCCEEDED(hr) && i < count; i++) {
            const auto value = view.at(i);
            Layer *layer = lookupFrameLayer(value.handle);
            if (!layer->committedProperties.sameX(value.x)) {
                changed = true;
                hr = layer->visual->SetOffsetX(value.x);
            }
            if (SUCCEEDED(hr) && !layer->committedProperties.sameY(value.y)) {
                changed = true;
                hr = layer->visual->SetOffsetY(value.y);
            }
            if (SUCCEEDED(hr) && !layer->committedProperties.sameOpacity(value.opacity)) {
                changed = true;
                hr = layer->effect->SetOpacity(value.opacity);
            }
            // Without a reference, insertAbove FALSE adds above the last
            // sibling; the order is bottom-to-top in BOTH frame formats.
            if (SUCCEEDED(hr) && reorder && value.visible != 0.0f)
                hr = c->root->AddVisual(layer->visual, FALSE, nullptr);
        }

        // A pending successful EndDraw is a changed native surface even
        // when Z, offsets and opacity are unchanged (merged dirty upload).
        if (SUCCEEDED(hr) && changed) hr = c->device->Commit();
        if (SUCCEEDED(hr)) {
            if (changed) c->pendingSurfaceCommit = false;
            if (reorder) c->frameDelta.accept();
            if (changed) {
                for (std::size_t i = 0; i < count; i++) {
                    const auto value = view.at(i);
                    Layer *layer = lookupFrameLayer(value.handle);
                    layer->committedProperties.accept(
                            value.x, value.y, value.opacity);
                }
            }
        } else {
            invalidateFrameCache(c);
        }
        return hr;
    } catch (const std::bad_alloc &) {
        invalidateFrameCache(c);
        return E_OUTOFMEMORY;
    } catch (...) {
        invalidateFrameCache(c);
        return E_FAIL;
    }
}
} // namespace

// Original seven-symbol JNI contract remains untouched. JNI array regions
// are copied before entering DirectComposition, as on current master.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply
  (JNIEnv *env, jclass, jlong composition, jlongArray layers, jfloatArray props, jint count) {
    Composition *c = getComposition(composition);
    if (!c || !layers || !props) return E_POINTER;
    if (!swt_maya::validFrameArrays(count, env->GetArrayLength(layers), env->GetArrayLength(props)))
        return E_INVALIDARG;
    try {
        std::vector<jlong> order(static_cast<std::size_t>(count));
        std::vector<jfloat> properties(static_cast<std::size_t>(count) * 4);
        if (count) env->GetLongArrayRegion(layers, 0, count, order.data());
        if (env->ExceptionCheck()) return E_FAIL;
        if (count) env->GetFloatArrayRegion(props, 0, count * 4, properties.data());
        if (env->ExceptionCheck()) return E_FAIL;
        return applyNativeFrame(c, swt_maya_frame::LegacyView<jlong, jfloat>{
                order.data(), properties.data(), static_cast<std::size_t>(count)});
    } catch (const std::bad_alloc &) {
        return E_OUTOFMEMORY;
    } catch (...) {
        return E_FAIL;
    }
}

// The packed frame has already been decoded transactionally on the JNI
// stack. No NewLongArray / NewFloatArray / Set*ArrayRegion round trip.
namespace swt_maya_frame {
jint applyDecodedFrame(jlong composition, const Frame &frame) noexcept {
    Composition *c = getComposition(composition);
    if (!c) return E_POINTER;
    return applyNativeFrame(c, PackedView{frame});
}
}

extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nRemoveLayer
  (JNIEnv *, jclass, jlong composition, jlong layerHandle) {
    Composition *c = getComposition(composition);
    Layer *layer = getLayer(layerHandle);
    if (!c || !layer || layer->owner != c) return;
    for (size_t i = 0; i < c->layers.size(); i++) {
        if (c->layers[i] == layer) {
            c->layers.erase(c->layers.begin() + i);
            c->root->RemoveVisual(layer->visual);
            c->device->Commit();
            // The next frame rebuilds membership and reapplies retained properties.
            // A failed RemoveVisual/Commit must not make cached state authoritative.
            c->frameDelta.invalidate();
            for (Layer *remaining : c->layers) remaining->committedProperties.invalidate();
            destroy(layer);
            return;
        }
    }
}

extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nWaitForCommit
  (JNIEnv *, jclass, jlong composition) {
    Composition *c = getComposition(composition);
    if (!c) return E_POINTER;
    const HRESULT before = checkNativeDevice(c);
    if (FAILED(before)) return before;
    const HRESULT waited = c->device->WaitForCommitCompletion();
    return SUCCEEDED(waited) ? checkNativeDevice(c) : waited;
}

extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nDispose
  (JNIEnv *, jclass, jlong composition) {
    Composition *c = getComposition(composition);
    if (c) destroy(c);
}

#else // not yet on this platform: the same exports, answering "not supported"

namespace swt_maya_frame {
jint applyDecodedFrame(jlong, const Frame &) noexcept { return -1; }
}


extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nCreate
  (JNIEnv *, jclass, jlong) { return 0; }
extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nAddLayer
  (JNIEnv *, jclass, jlong, jint, jint) { return 0; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nUpload
  (JNIEnv *, jclass, jlong, jintArray) { return -1; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nUploadDirty
  (JNIEnv *, jclass, jlong, jintArray, jintArray, jint) { return -1; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply
  (JNIEnv *, jclass, jlong, jlongArray, jfloatArray, jint) { return -1; }
extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nRemoveLayer
  (JNIEnv *, jclass, jlong, jlong) {}
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nWaitForCommit
  (JNIEnv *, jclass, jlong) { return -1; }
extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nDispose
  (JNIEnv *, jclass, jlong) {}

#endif
