/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
#ifndef SWT_MOBILE_IOS_H
#define SWT_MOBILE_IOS_H

#include <jni.h>
#import <UIKit/UIKit.h>

/**
 * Attach a JavaVM supplied by the embedding application and its root UIView.
 * Call on UIKit's main thread after VM creation and class-path configuration,
 * before starting IOSPlatform.start(application). The VM must outlive attachment.
 * SWT registers its native OS methods; it does not create or ship an iOS VM.
 * One live root host is supported per attachment. Reattachment requires detach.
 * On failure, Java exceptions are returned through NSError rather than left pending.
 */
FOUNDATION_EXPORT BOOL SWTIOSAttach(JavaVM *vm, UIView *container, NSError **error);

/**
 * Close the root host, invalidate queued frames and delete every JNI global ref.
 * Call on UIKit's main thread before destroying the supplied VM/container. Native
 * callbacks enqueue Display.close and never synchronously wait for its thread.
 * Detach is not a Java-thread join: allow the SWT application thread to terminate
 * before destroying the VM. Do not synchronously await Java work from UIKit.
 */
FOUNDATION_EXPORT void SWTIOSDetach(void);

#endif
