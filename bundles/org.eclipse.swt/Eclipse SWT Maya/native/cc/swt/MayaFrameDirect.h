/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_FRAME_DIRECT_H
#define SWT_MAYA_FRAME_DIRECT_H

#include "MayaFramePacket.h"
#include <jni.h>

namespace swt_maya_frame {
// Implemented by the existing MayaComposition.cc owner. Windows applies
// through the same validated transaction as legacy _nApply; other platforms
// return the historical -1 unsupported result. Does not retain 'frame'.
// The frame has already passed the versioned packet's structural validation.
jint applyDecodedFrame(jlong composition, const Frame &frame) noexcept;
}
#endif
