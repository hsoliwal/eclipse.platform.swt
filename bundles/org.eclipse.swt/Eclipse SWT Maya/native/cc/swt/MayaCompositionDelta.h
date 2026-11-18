/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_COMPOSITION_DELTA_H
#define SWT_MAYA_COMPOSITION_DELTA_H

#include <cstddef>
#include <vector>

// The platform-independent, composition-owned state of an admitted native frame.
// Pure C++17: the Windows owner uses this after JNI handle/property preflight.
// No JNI types, OS handles, widgets or rendering methods escape this header.
namespace swt_maya {

struct CommittedLayerProperties {
    float x = 0.0f;
    float y = 0.0f;
    float opacity = 1.0f;
    bool valid = false;

    bool sameX(float value) const noexcept { return valid && x == value; }
    bool sameY(float value) const noexcept { return valid && y == value; }
    bool sameOpacity(float value) const noexcept { return valid && opacity == value; }

    void accept(float newX, float newY, float newOpacity) noexcept {
        x = newX;
        y = newY;
        opacity = newOpacity;
        valid = true;
    }

    void invalidate() noexcept { valid = false; }
};

template <class LayerType>
class CompositionFrameDelta {
    // Both vectors retain their allocation between transactions. Only the
    // successfully committed visible order is authoritative.
    std::vector<LayerType *> committed_;
    std::vector<LayerType *> requested_;
    bool valid_ = false;

public:
    void begin() noexcept { requested_.clear(); }

    void includeVisible(LayerType *layer) { requested_.push_back(layer); }

    bool requiresRebuild() const noexcept {
        if (!valid_ || committed_.size() != requested_.size()) return true;
        for (std::size_t i = 0; i < committed_.size(); ++i) {
            if (committed_[i] != requested_[i]) return true;
        }
        return false;
    }

    // No allocation or exception is possible *after* the compositor commits.
    // The previous committed buffer becomes the scratch for the next request.
    void accept() noexcept {
        committed_.swap(requested_);
        valid_ = true;
    }

    // Fail closed after COM/Commit/removal failure. No stale layer pointer
    // is dereferenced: the next admitted frame must rebuild its full order.
    void invalidate() noexcept { valid_ = false; }
};

} // namespace swt_maya
#endif
