/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_COMPOSITION_DELTA_H
#define SWT_MAYA_COMPOSITION_DELTA_H

#include <array>
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
    // Existing full rebuild remains the fallback. Sparse preserves the largest
    // stable sibling subsequence, moving only the remaining native visuals.
    enum class ZPlan { Unchanged, Full, Sparse };
    struct ZInsertion {
        LayerType *layer;
        LayerType *lowerSibling; // null means bottom of the parent stack
    };

private:
    std::vector<LayerType *> zRemovals_;
    std::vector<ZInsertion> zInsertions_;
    static constexpr std::size_t SPARSE_MAX = 64;

public:
    // JNI preflight must validate all handles/duplicate membership first.
    // Small scenes use bounded O(N^2) identity search plus LIS; large scenes
    // retain the original nonquadratic full-tree fallback.
    ZPlan planZOrder() {
        zRemovals_.clear();
        zInsertions_.clear();
        if (!requiresRebuild()) return ZPlan::Unchanged;
        if (!valid_ || committed_.size() > SPARSE_MAX || requested_.size() > SPARSE_MAX)
            return ZPlan::Full;

        std::array<int, SPARSE_MAX> rank{};
        std::array<int, SPARSE_MAX> predecessor{};
        std::array<int, SPARSE_MAX> tails{};
        std::array<unsigned char, SPARSE_MAX> keep{};
        int length = 0;
        for (std::size_t i = 0; i < requested_.size(); ++i) {
            int prior = -1;
            for (std::size_t j = 0; j < committed_.size(); ++j) {
                if (committed_[j] == requested_[i]) {
                    prior = static_cast<int>(j);
                    break;
                }
            }
            rank[i] = prior;
            predecessor[i] = -1;
            if (prior < 0) continue;
            int lo = 0, hi = length;
            while (lo < hi) {
                int mid = lo + (hi - lo) / 2;
                if (rank[static_cast<std::size_t>(tails[static_cast<std::size_t>(mid)])] < prior)
                    lo = mid + 1;
                else
                    hi = mid;
            }
            if (lo > 0) predecessor[i] = tails[static_cast<std::size_t>(lo - 1)];
            tails[static_cast<std::size_t>(lo)] = static_cast<int>(i);
            if (lo == length) ++length;
        }
        if (length > 0) {
            int i = tails[static_cast<std::size_t>(length - 1)];
            while (i >= 0) {
                keep[static_cast<std::size_t>(i)] = 1;
                i = predecessor[static_cast<std::size_t>(i)];
            }
        }
        for (LayerType *old : committed_) {
            bool retained = false;
            for (std::size_t i = 0; i < requested_.size(); ++i) {
                if (requested_[i] == old && keep[i] != 0) {
                    retained = true;
                    break;
                }
            }
            if (!retained) zRemovals_.push_back(old);
        }
        for (std::size_t i = 0; i < requested_.size(); ++i) {
            if (keep[i] == 0)
                zInsertions_.push_back({requested_[i], i == 0 ? nullptr : requested_[i - 1]});
        }
        // Original RemoveAllVisuals + N AddVisual wins when most slots moved.
        if (zRemovals_.size() + zInsertions_.size() >= requested_.size() + 1) {
            zRemovals_.clear();
            zInsertions_.clear();
            return ZPlan::Full;
        }
        return ZPlan::Sparse;
    }

    const std::vector<LayerType *> &zRemovals() const noexcept { return zRemovals_; }
    const std::vector<ZInsertion> &zInsertions() const noexcept { return zInsertions_; }

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
