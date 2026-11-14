/* Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 */
#ifndef SWT_UIKIT_H
#define SWT_UIKIT_H

#include <jni.h>

#ifdef __OBJC__
@class UIView;
/* Called on the UIKit main thread before launching the Java application worker.
 * The application retains the root view until SWT has disconnected. */
void SWTUIKitSetRootView(UIView *view);
/* Final host/session destruction, on main. Enqueues disposal without joining
 * the Java Display worker. Temporary scene disconnection is not destruction. */
void SWTUIKitHostTerminated(void);
#endif

#ifdef __cplusplus
extern "C" {
#endif
/* Explicit static registration: call on the JVM application worker after
 * JNI_CreateJavaVM and before IOSPlatform.launch. No libjvm is bundled here. */
jint SWTUIKitRegister(JavaVM *vm, JNIEnv *env);
#ifdef __cplusplus
}
#endif
#endif
