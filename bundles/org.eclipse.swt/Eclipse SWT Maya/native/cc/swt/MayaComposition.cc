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

struct Layer {
    Composition *owner = nullptr;
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
    std::vector<Layer *> layers;
};

void destroy(Layer *layer) {
    release(layer->effect);
    release(layer->surface);
    release(layer->visual);
    delete layer;
}

void destroy(Composition *c) {
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
    return reinterpret_cast<jlong>(c);
}

extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nAddLayer
  (JNIEnv *, jclass, jlong composition, jint width, jint height) {
    Composition *c = reinterpret_cast<Composition *>(composition);
    if (!c || width <= 0 || height <= 0) return 0;
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
    return reinterpret_cast<jlong>(layer);
}

// the layer's pixels: width * height premultiplied ARGB ints, row by row (an int's bytes in memory are B, G, R, A,
// which is DXGI_FORMAT_B8G8R8A8); returns 0, or the failing HRESULT
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nUpload
  (JNIEnv *env, jclass, jlong layerHandle, jintArray pixels) {
    Layer *layer = reinterpret_cast<Layer *>(layerHandle);
    if (!layer || !pixels) return E_POINTER;
    jsize n = env->GetArrayLength(pixels);
    if (n < static_cast<jsize>(layer->width) * layer->height) return E_INVALIDARG;
    std::vector<jint> copy(static_cast<size_t>(layer->width) * layer->height);
    env->GetIntArrayRegion(pixels, 0, static_cast<jsize>(copy.size()), copy.data());
    ID3D11Texture2D *texture = nullptr;
    POINT at;
    HRESULT hr = layer->surface->BeginDraw(nullptr, __uuidof(ID3D11Texture2D), reinterpret_cast<void **>(&texture), &at);
    if (FAILED(hr)) return hr;
    D3D11_BOX box = { static_cast<UINT>(at.x), static_cast<UINT>(at.y), 0, static_cast<UINT>(at.x + layer->width),
                      static_cast<UINT>(at.y + layer->height), 1 };
    layer->owner->context->UpdateSubresource(texture, 0, &box, copy.data(), static_cast<UINT>(layer->width) * 4, 0);
    release(texture);
    return layer->surface->EndDraw();
}

// one frame: layers in Z order, bottom to top, with x, y, opacity (0..1) and visible (0 or 1) per layer in props;
// hidden layers are detached; one Commit. Returns 0, or the failing HRESULT.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply
  (JNIEnv *env, jclass, jlong composition, jlongArray layers, jfloatArray props, jint count) {
    Composition *c = reinterpret_cast<Composition *>(composition);
    if (!c || count < 0 || env->GetArrayLength(layers) < count || env->GetArrayLength(props) < count * 4) return E_INVALIDARG;
    std::vector<jlong> order(count);
    std::vector<jfloat> p(static_cast<size_t>(count) * 4);
    env->GetLongArrayRegion(layers, 0, count, order.data());
    env->GetFloatArrayRegion(props, 0, count * 4, p.data());
    HRESULT hr = c->root->RemoveAllVisuals();
    for (jint i = 0; SUCCEEDED(hr) && i < count; i++) {
        Layer *layer = reinterpret_cast<Layer *>(order[i]);
        if (!layer) continue;
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
    Composition *c = reinterpret_cast<Composition *>(composition);
    Layer *layer = reinterpret_cast<Layer *>(layerHandle);
    if (!c || !layer) return;
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
    Composition *c = reinterpret_cast<Composition *>(composition);
    return c ? c->device->WaitForCommitCompletion() : E_POINTER;
}

extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nDispose
  (JNIEnv *, jclass, jlong composition) {
    Composition *c = reinterpret_cast<Composition *>(composition);
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
extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nRemoveLayer
  (JNIEnv *, jclass, jlong, jlong) {}
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nWaitForCommit
  (JNIEnv *, jclass, jlong) { return -1; }
extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nDispose
  (JNIEnv *, jclass, jlong) {}

#endif
