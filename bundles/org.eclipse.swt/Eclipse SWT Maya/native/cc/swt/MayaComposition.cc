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
#include "MayaCompositionDelta.h"
#include "MayaCompositionScene.h"

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
    swt_maya::CommittedLayerProperties committedProperties;
    swt_maya::SceneLayerState committedScene;
    bool sceneMayBeActive = false; // includes failed native scene mutations
    IDCompositionVisual *visual = nullptr;
    IDCompositionSurface *surface = nullptr;
    IDCompositionEffectGroup *effect = nullptr;
    int width = 0, height = 0;
};

struct Composition {
    ID3D11Device *d3d = nullptr;
    ID3D11DeviceContext *context = nullptr;
    IDCompositionDevice *device = nullptr;
    IDCompositionTarget *target = nullptr;
    IDCompositionVisual *root = nullptr;
    std::uint64_t frameSequence = 0;
    bool surfaceDirty = false; // successful EndDraw awaits a successful Commit
    std::vector<Layer *> layers;
    swt_maya::CompositionFrameDelta<Layer> frameDelta;
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
    hr = layer->surface->EndDraw();
    if (SUCCEEDED(hr)) layer->owner->surfaceDirty = true;
    return hr;
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
    // The OS retains the previous visual tree. Build only a candidate visible order
    // after validating all handles/properties; the last successful Commit owns truth.
    c->frameDelta.begin();
    for (jint i = 0; i < count; i++) {
        if (p[static_cast<size_t>(i) * 4 + 3] != 0.0f) {
            c->frameDelta.includeVisible(getLayer(order[i]));
        }
    }
    const bool reorder = c->frameDelta.requiresRebuild();
    bool changed = reorder;
    HRESULT hr = reorder ? c->root->RemoveAllVisuals() : S_OK;
    for (jint i = 0; SUCCEEDED(hr) && i < count; i++) {
        Layer *layer = getLayer(order[i]);
        const jfloat *q = &p[static_cast<size_t>(i) * 4];
        // A property-only frame never detaches and rebuilds the visual tree.
        if (!layer->committedProperties.sameX(q[0])) {
            changed = true;
            hr = layer->visual->SetOffsetX(q[0]);
        }
        if (SUCCEEDED(hr) && !layer->committedProperties.sameY(q[1])) {
            changed = true;
            hr = layer->visual->SetOffsetY(q[1]);
        }
        if (SUCCEEDED(hr) && !layer->committedProperties.sameOpacity(q[2])) {
            changed = true;
            hr = layer->effect->SetOpacity(q[2]);
        }
        // The original seven-method ABI must not inherit opt-in affine state.
        if (SUCCEEDED(hr) && layer->sceneMayBeActive) {
            changed = true;
            const D2D_MATRIX_3X2_F identity = {1, 0, 0, 1, 0, 0};
            hr = layer->visual->SetTransform(identity);
            if (SUCCEEDED(hr)) hr = layer->visual->SetClip(static_cast<IDCompositionClip *>(nullptr));
        }
        // Without a reference, insertAbove FALSE adds above the last sibling.
        if (SUCCEEDED(hr) && reorder && q[3] != 0.0f)
            hr = c->root->AddVisual(layer->visual, FALSE, nullptr);
    }
    // DirectComposition queues commands atomically only at Commit. A no-op frame
    // needs neither an OS call nor a new transaction.
    if (SUCCEEDED(hr) && (changed || c->surfaceDirty)) hr = c->device->Commit();
    if (SUCCEEDED(hr)) {
        if (reorder) c->frameDelta.accept(); // noexcept: no allocation after Commit
        if (changed) {
            for (jint i = 0; i < count; i++) {
                Layer *layer = getLayer(order[i]);
                const jfloat *q = &p[static_cast<size_t>(i) * 4];
                layer->committedProperties.accept(q[0], q[1], q[2]);
                layer->sceneMayBeActive = false;
                layer->committedScene.invalidate();
            }
        }
        c->surfaceDirty = false;
    } else {
        // A failed pending native transaction cannot become the cache authority.
        // The next call must replay all visual order and property assignments.
        c->frameDelta.invalidate();
        for (Layer *layer : c->layers) {
            layer->committedProperties.invalidate();
            layer->committedScene.invalidate();
        }
    }
    return hr;
}

// One counted JNI frame for affine transforms and local rectangular clips.
// The original _nApply remains available and resets this opt-in state on use.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApplyScene
  (JNIEnv *env, jclass, jlong composition, jlongArray layers, jfloatArray packed, jintArray flags, jint count) {
    Composition *c = getComposition(composition);
    if (!c || !layers || !packed || !flags) return E_POINTER;
    if (!swt_maya::validSceneArrays(count, env->GetArrayLength(layers),
            env->GetArrayLength(packed), env->GetArrayLength(flags))
            || static_cast<size_t>(count) > c->layers.size()) return E_INVALIDARG;
    std::vector<jlong> order(static_cast<size_t>(count));
    std::vector<jfloat> p(static_cast<size_t>(count) * swt_maya::SCENE_FLOATS);
    std::vector<jint> opts(static_cast<size_t>(count));
    if (count != 0) {
        env->GetLongArrayRegion(layers, 0, count, order.data());
        if (env->ExceptionCheck()) return E_FAIL;
        env->GetFloatArrayRegion(packed, 0, count * swt_maya::SCENE_FLOATS, p.data());
        if (env->ExceptionCheck()) return E_FAIL;
        env->GetIntArrayRegion(flags, 0, count, opts.data());
        if (env->ExceptionCheck()) return E_FAIL;
    }
    // Validate the complete frame before changing any visible OS state.
    std::uint64_t generation = ++c->frameSequence;
    if (generation == 0) {
        for (Layer *layer : c->layers) layer->seenFrame = 0;
        generation = ++c->frameSequence;
    }
    for (jint i = 0; i < count; ++i) {
        Layer *layer = getLayer(order[static_cast<size_t>(i)]);
        const jfloat *q = &p[static_cast<size_t>(i) * swt_maya::SCENE_FLOATS];
        if (!layer || layer->owner != c || layer->seenFrame == generation ||
                !swt_maya::validSceneLayer(q, opts[static_cast<size_t>(i)])) return E_INVALIDARG;
        layer->seenFrame = generation;
    }
    c->frameDelta.begin();
    for (jint i = 0; i < count; ++i) {
        if (p[static_cast<size_t>(i) * swt_maya::SCENE_FLOATS + 3] != 0.0f)
            c->frameDelta.includeVisible(getLayer(order[static_cast<size_t>(i)]));
    }
    const bool reorder = c->frameDelta.requiresRebuild();
    bool changed = reorder;
    HRESULT hr = reorder ? c->root->RemoveAllVisuals() : S_OK;
    for (jint i = 0; SUCCEEDED(hr) && i < count; ++i) {
        Layer *layer = getLayer(order[static_cast<size_t>(i)]);
        const jfloat *q = &p[static_cast<size_t>(i) * swt_maya::SCENE_FLOATS];
        const int option = opts[static_cast<size_t>(i)];
        if (!layer->committedProperties.sameX(q[0])) {
            changed = true;
            hr = layer->visual->SetOffsetX(q[0]);
        }
        if (SUCCEEDED(hr) && !layer->committedProperties.sameY(q[1])) {
            changed = true;
            hr = layer->visual->SetOffsetY(q[1]);
        }
        if (SUCCEEDED(hr) && !layer->committedProperties.sameOpacity(q[2])) {
            changed = true;
            hr = layer->effect->SetOpacity(q[2]);
        }
        if (SUCCEEDED(hr) && !layer->committedScene.sameMatrix(q)) {
            changed = true;
            layer->sceneMayBeActive = true;
            const D2D_MATRIX_3X2_F matrix = {q[4], q[5], q[6], q[7], q[8], q[9]};
            hr = layer->visual->SetTransform(matrix);
        }
        if (SUCCEEDED(hr) && !layer->committedScene.sameClip(q, option)) {
            changed = true;
            layer->sceneMayBeActive = true;
            if ((option & swt_maya::SCENE_CLIP) != 0) {
                const D2D_RECT_F clip = {q[10], q[11], q[12], q[13]};
                hr = layer->visual->SetClip(clip);
            } else {
                hr = layer->visual->SetClip(static_cast<IDCompositionClip *>(nullptr));
            }
        }
        if (SUCCEEDED(hr) && reorder && q[3] != 0.0f)
            hr = c->root->AddVisual(layer->visual, FALSE, nullptr);
    }
    if (SUCCEEDED(hr) && (changed || c->surfaceDirty)) hr = c->device->Commit();
    if (SUCCEEDED(hr)) {
        if (reorder) c->frameDelta.accept();
        if (changed) {
            for (jint i = 0; i < count; ++i) {
                Layer *layer = getLayer(order[static_cast<size_t>(i)]);
                const jfloat *q = &p[static_cast<size_t>(i) * swt_maya::SCENE_FLOATS];
                layer->committedProperties.accept(q[0], q[1], q[2]);
                layer->committedScene.accept(q, opts[static_cast<size_t>(i)]);
                layer->sceneMayBeActive = true;
            }
        }
        c->surfaceDirty = false;
    } else {
        c->frameDelta.invalidate();
        for (Layer *layer : c->layers) {
            layer->committedProperties.invalidate();
            layer->committedScene.invalidate();
        }
        // Preserve a successfully ended pixel upload until the next Commit.
    }
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
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply
  (JNIEnv *, jclass, jlong, jlongArray, jfloatArray, jint) { return -1; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApplyScene
  (JNIEnv *, jclass, jlong, jlongArray, jfloatArray, jintArray, jint) { return -1; }
extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nRemoveLayer
  (JNIEnv *, jclass, jlong, jlong) {}
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nWaitForCommit
  (JNIEnv *, jclass, jlong) { return -1; }
extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nDispose
  (JNIEnv *, jclass, jlong) {}

#endif
