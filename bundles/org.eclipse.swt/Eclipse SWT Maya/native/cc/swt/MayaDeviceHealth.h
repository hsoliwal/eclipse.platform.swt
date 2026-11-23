/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_DEVICE_HEALTH_H
#define SWT_MAYA_DEVICE_HEALTH_H

#include <cstdint>

/*
 * Platform-independent result normalization for IDCompositionDevice's
 * CheckDeviceState. The Windows owner invokes this before every native frame
 * transaction, including a no-op one. No Win32/D3D types escape this header.
 * Signed 32-bit results use the HRESULT sign-bit convention.
 */
namespace swt_maya {
constexpr std::int32_t DEVICE_REMOVED = static_cast<std::int32_t>(0x887A0005u);
constexpr std::int32_t DEVICE_RESET = static_cast<std::int32_t>(0x887A0007u);

inline std::int32_t compositionHealth(std::int32_t checkResult,
        bool valid, std::int32_t removedReason) noexcept {
    // A failed CheckDeviceState is already a concrete failure code.
    if (checkResult < 0) return checkResult;
    if (valid) return 0;
    // CheckDeviceState sometimes reports an invalid composition even if the
    // D3D reason is currently S_OK; still fail closed, never publish pixels.
    return removedReason < 0 ? removedReason : DEVICE_REMOVED;
}
} // namespace swt_maya
#endif // SWT_MAYA_DEVICE_HEALTH_H
