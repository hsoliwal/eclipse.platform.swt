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
#include "MayaCompositionTelemetry.h"
#include "MayaSurfaceBudget.h"
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
// Each SWT Display/UI thread owns its native objects. Partition live lookups
// by that thread, but issue tokens globally: two Displays cannot race inside
// unordered_map or accidentally resolve one another's handle. Never move an
// existing COM object to a different thread to work around SWT ownership.
thread_local swt_maya::TokenRegistry<Composition, 0x31> liveCompositions;
thread_local swt_maya::TokenRegistry<Layer, 0x32> liveLayers;

struct Layer {
    jlong token = 0;
    Composition *owner = nullptr;
    std::uint64_t seenFrame = 0;
    swt_maya::CommittedLayerProperties committedProperties;
    swt_maya::SceneLayerState committedScene;
    bool sceneMayBeActive = false;
    IDCompositionVisual *visual = nullptr;
    IDCompositionSurface *surface = nullptr;
    IDCompositionEffectGroup *effect = nullptr;
    int width = 0, height = 0;
    bool initialized = false;
    std::uint64_t accountedSurfaceBytes = 0; // logical BGRA bytes, not GPU residency
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
    swt_maya::CompositionTelemetry telemetry;
    swt_maya::SurfaceBudget surfaceBudget; // zero cap means opt-out
    // Native copies of the active JNI prefix of a legacy _nApply frame, reused across ordinary UI-thread
    // frames so a warm frame allocates nothing. A nested _nApply must not replace an outer frame's prefix.
    std::vector<jlong> frameOrder;
    std::vector<jfloat> frameProperties;
    int activeApplyDepth = 0;
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
    if (SUCCEEDED(status) && valid) {
        composition->telemetry.deviceProbe(false);
        return S_OK;
    }
    const HRESULT reason = composition->d3d
            ? composition->d3d->GetDeviceRemovedReason() : S_OK;
    const HRESULT normalized = static_cast<HRESULT>(swt_maya::compositionHealth(
            static_cast<std::int32_t>(status), valid != FALSE,
            static_cast<std::int32_t>(reason)));
    composition->telemetry.deviceProbe(FAILED(normalized));
    return normalized;
}

void destroy(Layer *layer) {
    if (layer->token != 0 && layer->owner) layer->owner->telemetry.retiredLayer();
    // Each layer owns one logical reservation; return it even if a COM or
    // registry operation failed before this layer received a native handle.
    if (layer->owner && layer->accountedSurfaceBytes) {
        layer->owner->surfaceBudget.release(layer->accountedSurfaceBytes);
        layer->accountedSurfaceBytes = 0;
    }
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
    std::uint64_t reservedBytes = 0;
    if (!c->surfaceBudget.reserve(width, height, reservedBytes)) {
        destroy(layer); // rejected before any COM surface/visual allocation
        return 0;
    }
    layer->accountedSurfaceBytes = reservedBytes;
    bool ok = SUCCEEDED(c->device->CreateSurface(width, height, DXGI_FORMAT_B8G8R8A8_UNORM, DXGI_ALPHA_MODE_PREMULTIPLIED,
                      &layer->surface))
           && SUCCEEDED(c->device->CreateVisual(&layer->visual))
           // Pin source-pixel sampling instead of inheriting a future root policy.
           && SUCCEEDED(layer->visual->SetBitmapInterpolationMode(
                   DCOMPOSITION_BITMAP_INTERPOLATION_MODE_NEAREST_NEIGHBOR))
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
        c->telemetry.createdLayer();
        return layer->token;
    } catch (const std::bad_alloc &) {
        // push_back or registry insertion may fail independently. Retire
        // only a successful append and release every acquired COM object.
        if (!c->layers.empty() && c->layers.back() == layer) c->layers.pop_back();
        destroy(layer);
        return 0;
    }
}

// Extension features are useful only for a live composition on this backend.
// This is independent of the versioned packed-frame transport ABI.
extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nCapabilities
  (JNIEnv *, jclass, jlong composition) {
    return getComposition(composition)
        ? static_cast<jlong>(swt_maya::CAP_AFFINE_2D | swt_maya::CAP_LOCAL_RECT_CLIP
                          | swt_maya::CAP_DIRTY_UPLOAD) : 0;
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
    // Refuse a 4-byte D3D11 pitch overflow before allocating the native
    // full-image staging vector or calling DirectComposition BeginDraw.
    if (!swt_maya::validD3dUploadBox(0, 0, layer->width, layer->height))
        return E_INVALIDARG;
    const jsize count = static_cast<jsize>(static_cast<std::uint64_t>(layer->width) * layer->height);
    try {
        // Copy before touching COM. No pinned Java heap is held during the GPU update.
        std::vector<jint> copy(static_cast<size_t>(count));
        env->GetIntArrayRegion(pixels, 0, count, copy.data());
        if (env->ExceptionCheck()) return E_FAIL;
        ID3D11Texture2D *texture = nullptr;
        POINT at = {};
        HRESULT hr = layer->surface->BeginDraw(nullptr, __uuidof(ID3D11Texture2D),
                reinterpret_cast<void **>(&texture), &at);
        if (FAILED(hr)) return hr;
        // DirectComposition can return a different update offset. Avoid signed
        // overflow for the D3D11_BOX right/bottom and for the UINT byte pitch.
        if (!swt_maya::validD3dUploadBox(at.x, at.y, layer->width, layer->height)) {
            release(texture);
            layer->surface->EndDraw();
            return E_INVALIDARG;
        }
        const UINT x = static_cast<UINT>(at.x), y = static_cast<UINT>(at.y);
        D3D11_BOX box = { x, y, 0, x + static_cast<UINT>(layer->width),
                y + static_cast<UINT>(layer->height), 1 };
        layer->owner->context->UpdateSubresource(texture, 0, &box, copy.data(),
                static_cast<UINT>(static_cast<std::uint64_t>(layer->width) * sizeof(jint)), 0);
        release(texture);
        const HRESULT finished = layer->surface->EndDraw();
        if (SUCCEEDED(finished)) {
            layer->initialized = true;
            layer->owner->pendingSurfaceCommit = true;
            layer->owner->telemetry.fullUpload(static_cast<std::uint64_t>(count));
        }
        return finished;
    } catch (const std::bad_alloc &) {
        return E_OUTOFMEMORY;
    } catch (...) {
        return E_FAIL; // no C++ exception crosses JNI
    }
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

        // Stage the complete *validated* prefix in one JNI array copy, before
        // the first BeginDraw. A subsequent JNI exception cannot leave a batch
        // half-written to native surfaces. No Java heap is pinned during COM.
        const std::size_t pixelCount = swt_maya::packedDirtyPixelCount(rects.data(), count);
        // Preserve SWT #554: exactly one complete JNI pixel copy BEFORE
        // any DirectComposition BeginDraw. Only small, non-reentrant batches
        // reuse per-thread storage; large/nested calls borrow a private copy.
        static thread_local swt_maya::DirtyPixelScratch<jint> scratch;
        swt_maya::DirtyPixelScratch<jint>::Lease copy(scratch, count, pixelCount);
        env->GetIntArrayRegion(packed, 0, static_cast<jsize>(pixelCount), copy.data());
        if (env->ExceptionCheck()) return E_FAIL;
        std::size_t offset = 0;
        for (jint i = 0; i < count; ++i) {
            const jint *r = rects.data() + static_cast<size_t>(i) * 4;
            const std::size_t area = swt_maya::dirtyArea(r);
            const jint *regionPixels = copy.data() + offset;
            offset += area;
            RECT update = { r[0], r[1], r[0] + r[2], r[1] + r[3] };
            POINT at = {};
            ID3D11Texture2D *texture = nullptr;
            HRESULT hr = layer->surface->BeginDraw(&update, __uuidof(ID3D11Texture2D),
                    reinterpret_cast<void **>(&texture), &at);
            if (FAILED(hr)) return hr;
            // The returned offset is not necessarily the requested rectangle's origin.
            if (!swt_maya::validD3dUploadBox(at.x, at.y, r[2], r[3])) {
                release(texture);
                layer->surface->EndDraw();
                return E_INVALIDARG;
            }
            D3D11_BOX box = { static_cast<UINT>(at.x), static_cast<UINT>(at.y), 0,
                    static_cast<UINT>(at.x) + static_cast<UINT>(r[2]),
                    static_cast<UINT>(at.y) + static_cast<UINT>(r[3]), 1 };
            layer->owner->context->UpdateSubresource(texture, 0, &box, regionPixels,
                    static_cast<UINT>(r[2]) * 4, 0);
            release(texture);
            hr = layer->surface->EndDraw();
            if (SUCCEEDED(hr)) {
                layer->owner->pendingSurfaceCommit = true;
                layer->owner->telemetry.dirtyPatch(static_cast<std::uint64_t>(area));
            }
            if (FAILED(hr)) return hr; // later failures may have changed prior patches
        }
        return S_OK;
    } catch (const std::bad_alloc &) {
        return E_OUTOFMEMORY;
    } catch (...) {
        return E_FAIL; // fail closed, never unwind a C++ exception over JNI
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
    for (Layer *layer : c->layers) {
        layer->committedProperties.invalidate();
        layer->committedScene.invalidate();
    }
}

template<class View>
jint applyNativeFrame(Composition *c, const View &view) noexcept {
    c->telemetry.attemptFrame();
    const std::size_t count = view.size();
    if (count > static_cast<std::size_t>(std::numeric_limits<jint>::max() / 4)) {
        c->telemetry.rejectFrame();
        return E_INVALIDARG;
    }
    const HRESULT health = checkNativeDevice(c);
    if (FAILED(health)) {
        // Never treat a cached no-op frame as valid after DXGI reset.
        invalidateFrameCache(c);
        c->telemetry.rejectFrame();
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
                            value.opacity, value.visible) ||
                    (value.scene && !swt_maya::validSceneLayer(value.scene, value.sceneFlags))) {
                c->telemetry.rejectFrame();
                return E_INVALIDARG;
            }
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
        using ZPlan = swt_maya::CompositionFrameDelta<Layer>::ZPlan;
        const ZPlan plan = c->frameDelta.planZOrder();
        const bool reorder = plan != ZPlan::Unchanged;
        const bool fullZ = plan == ZPlan::Full;
        const bool sparseZ = plan == ZPlan::Sparse;
        bool changed = reorder || c->pendingSurfaceCommit;
        HRESULT hr = fullZ ? c->root->RemoveAllVisuals() : S_OK;
        if (sparseZ) {
            // Remove moved/departed visuals only; unchanged siblings stay attached.
            for (Layer *old : c->frameDelta.zRemovals()) {
                if (FAILED(hr)) break;
                hr = c->root->RemoveVisual(old->visual);
            }
        }
        const auto &insertions = c->frameDelta.zInsertions();
        std::size_t insertionIndex = 0;
        for (std::size_t i = 0; SUCCEEDED(hr) && i < count; i++) {
            const auto value = view.at(i);
            Layer *layer = lookupFrameLayer(value.handle);
            // A scene's physical client origin is in its matrix translation.
            // Native visual offsets precede the matrix and must remain zero.
            const float nativeX = value.scene ? 0.0f : value.x;
            const float nativeY = value.scene ? 0.0f : value.y;
            if (!layer->committedProperties.sameX(nativeX)) {
                changed = true;
                hr = layer->visual->SetOffsetX(nativeX);
            }
            if (SUCCEEDED(hr) && !layer->committedProperties.sameY(nativeY)) {
                changed = true;
                hr = layer->visual->SetOffsetY(nativeY);
            }
            if (SUCCEEDED(hr) && !layer->committedProperties.sameOpacity(value.opacity)) {
                changed = true;
                hr = layer->effect->SetOpacity(value.opacity);
            }
            if (value.scene) {
                const float *q = value.scene;
                if (SUCCEEDED(hr) && !layer->committedScene.sameMatrix(q)) {
                    changed = true;
                    // A failed COM operation may still leave pending state.
                    // Retain the reset obligation until a successful legacy frame.
                    layer->sceneMayBeActive = true;
                    const auto mapped = swt_maya::sceneMatrix(q);
                    const D2D_MATRIX_3X2_F matrix = {
                            mapped[0], mapped[1], mapped[2], mapped[3], mapped[4], mapped[5]};
                    hr = layer->visual->SetTransform(matrix);
                }
                if (SUCCEEDED(hr) && !layer->committedScene.sameClip(q, value.sceneFlags)) {
                    changed = true;
                    layer->sceneMayBeActive = true;
                    if ((value.sceneFlags & swt_maya::SCENE_CLIP) != 0) {
                        const D2D_RECT_F clip = {q[10], q[11], q[12], q[13]};
                        hr = layer->visual->SetClip(clip);
                    } else {
                        hr = layer->visual->SetClip(static_cast<IDCompositionClip *>(nullptr));
                    }
                }
            } else if (SUCCEEDED(hr) && layer->sceneMayBeActive) {
                // Both legacy arrays and packed v1 use this same transaction.
                // Neither format can represent affine/clip state inherited from a scene.
                changed = true;
                const D2D_MATRIX_3X2_F identity = {1, 0, 0, 1, 0, 0};
                hr = layer->visual->SetTransform(identity);
                if (SUCCEEDED(hr)) hr = layer->visual->SetClip(static_cast<IDCompositionClip *>(nullptr));
            }
            // Attach only moved/new visuals for a sparse frame; each lower
            // sibling has already survived or been inserted earlier in Z order.
            if (SUCCEEDED(hr) && value.visible != 0.0f) {
                if (fullZ) {
                    hr = c->root->AddVisual(layer->visual, FALSE, nullptr);
                } else if (sparseZ && insertionIndex < insertions.size()
                        && insertions[insertionIndex].layer == layer) {
                    Layer *lower = insertions[insertionIndex].lowerSibling;
                    hr = c->root->AddVisual(layer->visual,
                            lower == nullptr ? TRUE : FALSE,
                            lower == nullptr ? nullptr : lower->visual);
                    ++insertionIndex;
                }
            }
        }
        if (SUCCEEDED(hr) && sparseZ && insertionIndex != insertions.size()) hr = E_FAIL;

        // A pending successful EndDraw is a changed native surface even
        // when Z, offsets and opacity are unchanged (merged dirty upload).
        bool failedCommit = false;
        if (SUCCEEDED(hr) && changed) {
            hr = c->device->Commit();
            failedCommit = FAILED(hr);
            if (failedCommit) c->telemetry.failedCommit();
        }
        if (SUCCEEDED(hr)) {
            if (changed) c->telemetry.committedFrame(reorder);
            else c->telemetry.noopFrame();
            if (changed) c->pendingSurfaceCommit = false;
            if (reorder) c->frameDelta.accept();
            if (changed) {
                for (std::size_t i = 0; i < count; i++) {
                    const auto value = view.at(i);
                    Layer *layer = lookupFrameLayer(value.handle);
                    layer->committedProperties.accept(
                            value.scene ? 0.0f : value.x, value.scene ? 0.0f : value.y, value.opacity);
                    if (value.scene) {
                        layer->committedScene.accept(value.scene, value.sceneFlags);
                        layer->sceneMayBeActive = true;
                    } else {
                        layer->committedScene.invalidate();
                        layer->sceneMayBeActive = false;
                    }
                }
            }
        } else {
            if (!failedCommit) c->telemetry.rejectFrame();
            invalidateFrameCache(c);
        }
        return hr;
    } catch (const std::bad_alloc &) {
        c->telemetry.rejectFrame();
        invalidateFrameCache(c);
        return E_OUTOFMEMORY;
    } catch (...) {
        c->telemetry.rejectFrame();
        invalidateFrameCache(c);
        return E_FAIL;
    }
}
} // namespace

// Original seven-symbol JNI contract remains untouched. JNI array regions
// are copied before entering DirectComposition, into per-composition scratch
// that is reused across frames (a nested call copies into its own locals).
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply
  (JNIEnv *env, jclass, jlong composition, jlongArray layers, jfloatArray props, jint count) {
    Composition *c = getComposition(composition);
    if (!c || !layers || !props) return E_POINTER;
    if (!swt_maya::validFrameArrays(count, env->GetArrayLength(layers), env->GetArrayLength(props)))
        return E_INVALIDARG;
    try {
        // Only the counted prefix is copied; spare capacity in either array never reaches a frame.
        std::vector<jlong> nestedOrder;
        std::vector<jfloat> nestedProperties;
        const bool nested = c->activeApplyDepth != 0;
        std::vector<jlong> &order = nested ? nestedOrder : c->frameOrder;
        std::vector<jfloat> &properties = nested ? nestedProperties : c->frameProperties;
        order.resize(static_cast<std::size_t>(count));
        properties.resize(static_cast<std::size_t>(count) * 4);
        if (count) env->GetLongArrayRegion(layers, 0, count, order.data());
        if (env->ExceptionCheck()) return E_FAIL;
        if (count) env->GetFloatArrayRegion(props, 0, count * 4, properties.data());
        if (env->ExceptionCheck()) return E_FAIL;
        struct ApplyDepth {
            Composition *composition;
            explicit ApplyDepth(Composition *owner) noexcept : composition(owner) { ++composition->activeApplyDepth; }
            ~ApplyDepth() { --composition->activeApplyDepth; }
        } depth{c};
        return applyNativeFrame(c, swt_maya_frame::LegacyView<jlong, jfloat>{
                order.data(), properties.data(), static_cast<std::size_t>(count)});
    } catch (const std::bad_alloc &) {
        return E_OUTOFMEMORY;
    } catch (...) {
        return E_FAIL;
    }
}

// Additive scene-v1 ABI. Copy only the admitted prefix, with no pinned JNI
// array retained across COM calls. All three transports share the transaction.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApplyScene
  (JNIEnv *env, jclass, jlong composition, jlongArray layers, jfloatArray packed,
   jintArray flags, jint count) {
    Composition *c = getComposition(composition);
    if (!c || !layers || !packed || !flags) return E_POINTER;
    if (!swt_maya::validSceneArrays(count, env->GetArrayLength(layers),
            env->GetArrayLength(packed), env->GetArrayLength(flags))
            || static_cast<std::size_t>(count) > c->layers.size()) return E_INVALIDARG;
    try {
        std::vector<jlong> order(static_cast<std::size_t>(count));
        std::vector<jfloat> properties(static_cast<std::size_t>(count) * swt_maya::SCENE_FLOATS);
        std::vector<jint> options(static_cast<std::size_t>(count));
        if (count) env->GetLongArrayRegion(layers, 0, count, order.data());
        if (env->ExceptionCheck()) return E_FAIL;
        if (count) env->GetFloatArrayRegion(packed, 0, count * swt_maya::SCENE_FLOATS, properties.data());
        if (env->ExceptionCheck()) return E_FAIL;
        if (count) env->GetIntArrayRegion(flags, 0, count, options.data());
        if (env->ExceptionCheck()) return E_FAIL;
        return applyNativeFrame(c, swt_maya_frame::SceneView<jlong, jfloat, jint>{
                order.data(), properties.data(), options.data(), static_cast<std::size_t>(count)});
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
            invalidateFrameCache(c);
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

// Optional per-composition logical BGRA quota. Zero disables enforcement
// without disabling byte accounting. Reductions below live reservations
// are rejected; existing COM visuals are never automatically evicted.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nSetSurfaceBudget
  (JNIEnv *, jclass, jlong composition, jlong maximumBytes) {
    Composition *c = getComposition(composition);
    if (!c) return E_POINTER;
    if (maximumBytes < 0 || !c->surfaceBudget.setLimit(
            static_cast<std::uint64_t>(maximumBytes))) return E_INVALIDARG;
    return S_OK;
}

// Explicit six-field snapshot of logical BGRA image reservations. It is not
// a measurement of resident GPU memory, pitch or driver allocation overhead.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nReadSurfaceBudget
  (JNIEnv *env, jclass, jlong composition, jlongArray output) {
    Composition *c = getComposition(composition);
    if (!c || !output) return E_POINTER;
    if (env->GetArrayLength(output) < static_cast<jsize>(swt_maya::SurfaceBudget::FIELD_COUNT))
        return E_INVALIDARG;
    const auto snapshot = c->surfaceBudget.snapshot();
    std::array<jlong, swt_maya::SurfaceBudget::FIELD_COUNT> values{};
    for (std::size_t i = 0; i < snapshot.size(); ++i)
        values[i] = static_cast<jlong>(snapshot[i]);
    env->SetLongArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
    return env->ExceptionCheck() ? E_FAIL : S_OK;
}

// A read-only explicitly requested snapshot. No widget or GPU call, no new
// native object graph and no JNI polling on normal frame submissions.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nReadMetrics
  (JNIEnv *env, jclass, jlong composition, jlongArray output) {
    Composition *c = getComposition(composition);
    if (!c || !output) return E_POINTER;
    if (env->GetArrayLength(output) < static_cast<jsize>(swt_maya::CompositionTelemetry::FIELD_COUNT))
        return E_INVALIDARG;
    const auto snapshot = c->telemetry.snapshot(
            static_cast<std::uint64_t>(c->layers.size()), c->pendingSurfaceCommit);
    std::array<jlong, swt_maya::CompositionTelemetry::FIELD_COUNT> values{};
    for (std::size_t i = 0; i < snapshot.size(); ++i)
        values[i] = static_cast<jlong>(snapshot[i]); // all are <= INT64_MAX
    env->SetLongArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
    return env->ExceptionCheck() ? E_FAIL : S_OK;
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
extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nCapabilities
  (JNIEnv *, jclass, jlong) { return 0; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nUpload
  (JNIEnv *, jclass, jlong, jintArray) { return -1; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nUploadDirty
  (JNIEnv *, jclass, jlong, jintArray, jintArray, jint) { return -1; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply
  (JNIEnv *, jclass, jlong, jlongArray, jfloatArray, jint) { return -1; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApplyScene
  (JNIEnv *, jclass, jlong, jlongArray, jfloatArray, jintArray, jint) { return -1; }
extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nRemoveLayer
  (JNIEnv *, jclass, jlong, jlong) {}
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nWaitForCommit
  (JNIEnv *, jclass, jlong) { return -1; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nReadMetrics
  (JNIEnv *, jclass, jlong, jlongArray) { return -1; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nSetSurfaceBudget
  (JNIEnv *, jclass, jlong, jlong) { return -1; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nReadSurfaceBudget
  (JNIEnv *, jclass, jlong, jlongArray) { return -1; }
extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nDispose
  (JNIEnv *, jclass, jlong) {}

#endif
