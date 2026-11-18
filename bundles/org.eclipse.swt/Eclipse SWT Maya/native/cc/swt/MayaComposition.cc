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
#include <unordered_set>
#include "MayaCompositionChecks.h"
#include "MayaCompositionDirty.h"
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
// O(1) live-handle membership: never dereference an arbitrary jlong.
std::unordered_set<Composition *> liveCompositions;
std::unordered_set<Layer *> liveLayers;

struct Layer {
    Composition *owner = nullptr;
    std::uint64_t seenFrame = 0;
    IDCompositionVisual *visual = nullptr;
    IDCompositionSurface *surface = nullptr;
    IDCompositionEffectGroup *effect = nullptr;
    int width = 0, height = 0;
    bool initialized = false; // nonvirtual DirectComposition surfaces require an initial full draw
};

struct Composition {
    ID3D11Device *d3d = nullptr;
    ID3D11DeviceContext *context = nullptr;
    IDCompositionDevice *device = nullptr;
    IDCompositionTarget *target = nullptr;
    IDCompositionVisual *root = nullptr;
    std::uint64_t frameSequence = 0;
    std::vector<Layer *> layers;
};
Composition *getComposition(jlong handle) {
    Composition *c = reinterpret_cast<Composition *>(handle);
    return liveCompositions.find(c) == liveCompositions.end() ? nullptr : c;
}
Layer *getLayer(jlong handle) {
    Layer *l = reinterpret_cast<Layer *>(handle);
    return liveLayers.find(l) == liveLayers.end() ? nullptr : l;
}

void destroy(Layer *layer) {
    liveLayers.erase(layer);
    release(layer->effect);
    release(layer->surface);
    release(layer->visual);
    delete layer;
}

void destroy(Composition *c) {
    liveCompositions.erase(c);
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
    Composition *c = new Composition();
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
    liveCompositions.insert(c);
    return reinterpret_cast<jlong>(c);
}

extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nAddLayer
  (JNIEnv *, jclass, jlong composition, jint width, jint height) {
    Composition *c = getComposition(composition);
    if (!c || !swt_maya::validPixelSize(width, height)) return 0;
    Layer *layer = new Layer();
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
    c->layers.push_back(layer);
    liveLayers.insert(layer);
    return reinterpret_cast<jlong>(layer);
}

// the layer's pixels: width * height premultiplied ARGB ints, row by row (an int's bytes in memory are B, G, R, A,
// which is DXGI_FORMAT_B8G8R8A8); returns 0, or the failing HRESULT
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nUpload
  (JNIEnv *env, jclass, jlong layerHandle, jintArray pixels) {
    Layer *layer = getLayer(layerHandle);
    if (!layer || !pixels) return E_POINTER;
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
    HRESULT completed = layer->surface->EndDraw();
    if (SUCCEEDED(completed)) layer->initialized = true;
    return completed;
}


// Packed, concatenated premultiplied ARGB patches, each x,y,w,h in layer-local pixels.
// All input regions are validated before touching the compositor. Each patch gets
// BeginDraw/EndDraw with the returned updateOffset; the caller's _nApply commits
// the updated surface after this call. No JNI pin is held while GPU work runs.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nUploadDirty
  (JNIEnv *env, jclass, jlong layerHandle, jintArray packed, jintArray rectangles, jint count) {
    Layer *layer = getLayer(layerHandle);
    if (!layer || !packed || !rectangles) return E_POINTER;
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
            if (FAILED(hr)) return hr; // later failures may have changed prior patches
        }
        return S_OK;
    } catch (const std::bad_alloc &) {
        return E_OUTOFMEMORY;
    }
}

// one frame: layers in Z order, bottom to top, with x, y, opacity (0..1) and visible (0 or 1) per layer in props;
// hidden layers are detached; one Commit. Returns 0, or the failing HRESULT.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply
  (JNIEnv *env, jclass, jlong composition, jlongArray layers, jfloatArray props, jint count) {
    Composition *c = getComposition(composition);
    if (!c || !layers || !props) return E_POINTER;
    if (!swt_maya::validFrameArrays(count, env->GetArrayLength(layers), env->GetArrayLength(props)))
        return E_INVALIDARG;
    std::vector<jlong> order(static_cast<size_t>(count));
    std::vector<jfloat> p(static_cast<size_t>(count) * 4);
    if (count != 0) env->GetLongArrayRegion(layers, 0, count, order.data());
    if (env->ExceptionCheck()) return E_FAIL;
    if (count != 0) env->GetFloatArrayRegion(props, 0, count * 4, p.data());
    if (env->ExceptionCheck()) return E_FAIL;
    // Validate every handle and property before modifying the visible tree.
    std::uint64_t generation = ++c->frameSequence;
    if (generation == 0) {
        for (Layer *layer : c->layers) layer->seenFrame = 0;
        generation = ++c->frameSequence;
    }
    for (jint i = 0; i < count; i++) {
        Layer *layer = getLayer(order[i]);
        const jfloat *q = &p[static_cast<size_t>(i) * 4];
        if (!layer || layer->owner != c || layer->seenFrame == generation ||
                !swt_maya::validLayerProperties(q[0], q[1], q[2], q[3]))
            return E_INVALIDARG;
        layer->seenFrame = generation;
    }
    HRESULT hr = c->root->RemoveAllVisuals();
    for (jint i = 0; SUCCEEDED(hr) && i < count; i++) {
        Layer *layer = getLayer(order[i]);
        const jfloat *q = &p[static_cast<size_t>(i) * 4];
        hr = layer->visual->SetOffsetX(q[0]);
        if (SUCCEEDED(hr)) hr = layer->visual->SetOffsetY(q[1]);
        if (SUCCEEDED(hr)) hr = layer->effect->SetOpacity(q[2]);
        // with no reference visual, insertAbove FALSE adds on top of every sibling (TRUE would add at the bottom)
        if (SUCCEEDED(hr) && q[3] != 0.0f) hr = c->root->AddVisual(layer->visual, FALSE, nullptr);
    }
    if (SUCCEEDED(hr)) hr = c->device->Commit();
    return hr;
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
            destroy(layer);
            return;
        }
    }
}

extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nWaitForCommit
  (JNIEnv *, jclass, jlong composition) {
    Composition *c = getComposition(composition);
    return c ? c->device->WaitForCommitCompletion() : E_POINTER;
}

extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nDispose
  (JNIEnv *, jclass, jlong composition) {
    Composition *c = getComposition(composition);
    if (c) destroy(c);
}

#else // not yet on this platform: the same exports, answering "not supported"

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
