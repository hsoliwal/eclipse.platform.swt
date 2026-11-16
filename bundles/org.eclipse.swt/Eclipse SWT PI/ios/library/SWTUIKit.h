/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
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
