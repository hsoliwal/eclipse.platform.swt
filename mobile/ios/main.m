/* Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 */
#import <UIKit/UIKit.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include "SWTUIKit.h"

/* The application target links a supplied, hash-verified iOS JVM closure.
 * This file neither downloads a runtime nor turns a desktop JVM into one. */
static __weak UIView *swtApplicationRoot;

static void swtStartupFailure(NSString *reason) {
	NSLog(@"SWT iOS application failure: %@", reason);
	dispatch_async(dispatch_get_main_queue(), ^{
		UIView *root = swtApplicationRoot;
		if (root == nil) return;
		UILabel *label = [[UILabel alloc] initWithFrame:root.bounds];
		label.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
		label.numberOfLines = 0;
		label.textAlignment = NSTextAlignmentCenter;
		label.text = @"Unable to start application.\nSee the application log for details.";
		label.accessibilityIdentifier = @"swt.startup.failure";
		label.backgroundColor = UIColor.systemBackgroundColor;
		label.textColor = UIColor.labelColor;
		[root addSubview:label];
		UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, label);
	});
}

static jstring swtJavaString(JNIEnv *env, NSString *value) {
	NSUInteger length = value.length;
	if (length > INT32_MAX) return NULL;
	jchar *characters = length == 0 ? NULL : malloc(length * sizeof(jchar));
	if (length != 0 && characters == NULL) return NULL;
	if (length != 0) [value getCharacters:(unichar *)characters range:NSMakeRange(0, length)];
	jstring result = (*env)->NewString(env, characters, (jsize)length);
	free(characters);
	return result;
}

/* UIKit owns main. The JVM and the application's normal SWT event loop are
 * confined to this dedicated worker; UIKit never joins the worker. */
static void swtRunJava(void) {
	@autoreleasepool {
		NSBundle *bundle = NSBundle.mainBundle;
		NSString *resources = bundle.resourcePath;
		NSString *mainClass = [bundle objectForInfoDictionaryKey:@"SWTJavaMainClass"];
		if (![mainClass isKindOfClass:NSString.class] || mainClass.length == 0) {
			swtStartupFailure(@"SWTJavaMainClass is missing from the application Info.plist"); return;
		}
		NSString *classPath = [NSString stringWithFormat:@"-Djava.class.path=%@/swt-mobile.jar:%@/application.jar", resources, resources];
		NSString *javaHome = [@"-Djava.home=" stringByAppendingString:[resources stringByAppendingPathComponent:@"runtime"]];
		NSString *libraryPath = [@"-Djava.library.path=" stringByAppendingString:[resources stringByAppendingPathComponent:@"runtime/lib"]];
		JavaVMOption options[] = {
			{(char *)classPath.UTF8String, NULL},
			{(char *)javaHome.UTF8String, NULL},
			{(char *)libraryPath.UTF8String, NULL},
			{"-Xint", NULL}
		};
		JavaVMInitArgs configuration = {
			.version = JNI_VERSION_1_8,
			.nOptions = (jint)(sizeof(options) / sizeof(options[0])),
			.options = options,
			.ignoreUnrecognized = JNI_FALSE
		};
		JavaVM *vm = NULL;
		JNIEnv *env = NULL;
		jint result = JNI_CreateJavaVM(&vm, (void **)&env, &configuration);
		if (result != JNI_OK || vm == NULL || env == NULL) {
			swtStartupFailure([NSString stringWithFormat:@"JNI_CreateJavaVM failed (%d); the supplied runtime must support iOS and -Xint", result]); return;
		}
		BOOL success = SWTUIKitRegister(vm, env) == JNI_OK;
		jclass platform = success ? (*env)->FindClass(env, "org/eclipse/swt/internal/ios/IOSPlatform") : NULL;
		jmethodID launch = platform == NULL ? NULL : (*env)->GetStaticMethodID(env, platform, "launch", "(Ljava/lang/String;[Ljava/lang/String;)V");
		jclass strings = launch == NULL ? NULL : (*env)->FindClass(env, "java/lang/String");
		NSMutableArray<NSString *> *arguments = [NSMutableArray array];
		for (NSString *argument in NSProcessInfo.processInfo.arguments) {
			if ([argument hasPrefix:@"--swt-arg="]) [arguments addObject:[argument substringFromIndex:10]];
		}
		jobjectArray javaArguments = strings == NULL ? NULL : (*env)->NewObjectArray(env, (jsize)arguments.count, strings, NULL);
		jstring javaMain = javaArguments == NULL ? NULL : swtJavaString(env, mainClass);
		if (javaMain != NULL) {
			for (NSUInteger i = 0; i < arguments.count && !(*env)->ExceptionCheck(env); i++) {
				jstring argument = swtJavaString(env, arguments[i]);
				if (argument == NULL) { success = NO; break; }
				(*env)->SetObjectArrayElement(env, javaArguments, (jsize)i, argument);
				(*env)->DeleteLocalRef(env, argument);
			}
			if (success && !(*env)->ExceptionCheck(env)) (*env)->CallStaticVoidMethod(env, platform, launch, javaMain, javaArguments);
		} else {
			success = NO;
		}
		if ((*env)->ExceptionCheck(env)) {
			(*env)->ExceptionDescribe(env);
			(*env)->ExceptionClear(env);
			success = NO;
		}
		if (javaMain != NULL) (*env)->DeleteLocalRef(env, javaMain);
		if (javaArguments != NULL) (*env)->DeleteLocalRef(env, javaArguments);
		if (strings != NULL) (*env)->DeleteLocalRef(env, strings);
		if (platform != NULL) (*env)->DeleteLocalRef(env, platform);
		if (!success) swtStartupFailure(@"The supplied runtime or Java application failed; the original exception is in the log");
		/* launch() disposes the live Display before this shutdown. DestroyJavaVM
 		 * may wait for application threads, exclusively on this worker. */
		jint destroyed = (*vm)->DestroyJavaVM(vm);
		if (destroyed != JNI_OK) swtStartupFailure([NSString stringWithFormat:@"DestroyJavaVM failed (%d)", destroyed]);
	}
}

@interface SWTAppDelegate : UIResponder <UIApplicationDelegate>
@property(nonatomic, strong) UIWindow *swtWindow;
@property(nonatomic, strong) NSThread *javaThread;
@property(nonatomic, copy) NSString *sessionIdentifier;
@property(nonatomic) BOOL started;
@end

@implementation SWTAppDelegate
- (void)application:(UIApplication *)application didDiscardSceneSessions:(NSSet<UISceneSession *> *)sessions {
	(void)application;
	for (UISceneSession *session in sessions) {
		if ([session.persistentIdentifier isEqualToString:self.sessionIdentifier]) {
			SWTUIKitHostTerminated();
			/* The queued Display disposal owns peer/resource cleanup. */
			self.swtWindow = nil;
		}
	}
}
@end

@interface SWTSceneDelegate : UIResponder <UIWindowSceneDelegate>
@property(nonatomic, strong) UIWindow *window;
@end

@implementation SWTSceneDelegate
- (void)scene:(UIScene *)scene willConnectToSession:(UISceneSession *)session options:(UISceneConnectionOptions *)options {
	(void)options;
	if (![scene isKindOfClass:UIWindowScene.class]) return;
	SWTAppDelegate *application = (SWTAppDelegate *)UIApplication.sharedApplication.delegate;
	if (application.started) {
		/* UIKit may reconnect a temporarily disconnected scene. Retain the
		 * actual SWT owners/peers and JVM; never create a second JVM. */
		if (application.swtWindow != nil && [application.sessionIdentifier isEqualToString:session.persistentIdentifier]) {
			self.window = application.swtWindow;
			self.window.windowScene = (UIWindowScene *)scene;
			[self.window makeKeyAndVisible];
		} else {
			NSLog(@"SWT supports one live application scene/session per process; relaunch after final session destruction");
		}
		return;
	}
	application.started = YES;
	application.sessionIdentifier = session.persistentIdentifier;
	self.window = [[UIWindow alloc] initWithWindowScene:(UIWindowScene *)scene];
	UIViewController *controller = [UIViewController new];
	controller.view.backgroundColor = UIColor.systemBackgroundColor;
	self.window.rootViewController = controller;
	application.swtWindow = self.window;
	swtApplicationRoot = controller.view;
	SWTUIKitSetRootView(controller.view);
	[self.window makeKeyAndVisible];
	[controller.view layoutIfNeeded];
	application.javaThread = [[NSThread alloc] initWithBlock:^{ swtRunJava(); }];
	application.javaThread.name = @"SWT Java Display";
	application.javaThread.stackSize = 2 * 1024 * 1024;
	[application.javaThread start];
}
@end

int main(int argc, char **argv) {
	@autoreleasepool {
		return UIApplicationMain(argc, argv, nil, NSStringFromClass(SWTAppDelegate.class));
	}
}
