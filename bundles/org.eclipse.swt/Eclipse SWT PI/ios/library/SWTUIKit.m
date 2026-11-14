/* Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Hand-written UIKit/CoreGraphics implementation, not SWT's generated os.c.
 * Its JNI declarations and event constants are generated from ios/OS.java by
 * javac -h. No SDL/OpenJDK Mobile/desktop Cocoa implementation is copied here.
 */
#import <UIKit/UIKit.h>
#import <CoreText/CoreText.h>
#import <QuartzCore/QuartzCore.h>
#include <limits.h>
#include <math.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "SWTUIKit.h"
#include "org_eclipse_swt_internal_ios_OS.h"

#define JNI(name) Java_org_eclipse_swt_internal_ios_OS_##name
#define SWT(name) org_eclipse_swt_internal_ios_OS_##name
#define JNI_ARGS JNIEnv *env, jclass clazz __attribute__((unused))

@class SWTUIKitView;
@class SWTUIKitImage;
@class SWTUIKitGraphics;

/* The handle owner uses parallel C arrays, without boxed keys or entry objects.
 * Strong slots start zeroed and are explicitly set to nil before old storage is
 * freed. Arrays retain their high-water capacity/generations across disconnects,
 * reuse vacant slots, and never make a disposed handle valid again. */
typedef struct {
	id __strong *values;
	uint32_t *generation;
	uint32_t length, capacity;
	uint8_t kind;
} SWTHandleRegistry;

typedef enum { SWTRegistryOK, SWTRegistryMemory, SWTRegistryCapacity, SWTRegistryGeneration } SWTRegistryError;
#define SWT_SLOT_MASK UINT32_C(0x0fffffff)

static uint32_t swtRegistryIndex(SWTHandleRegistry *registry, jlong handle) {
	uint64_t token = (uint64_t)handle;
	uint32_t slot = (uint32_t)(token & SWT_SLOT_MASK);
	if (handle <= 0 || (token >> 60) != registry->kind || slot == 0 || slot > registry->length) return UINT32_MAX;
	uint32_t index = slot - 1;
	if (registry->values[index] == nil || registry->generation[index] != (uint32_t)(token >> 28)) return UINT32_MAX;
	return index;
}

static id swtRegistryGet(SWTHandleRegistry *registry, jlong handle) {
	uint32_t index = swtRegistryIndex(registry, handle);
	return index == UINT32_MAX ? nil : registry->values[index];
}

static jlong swtRegistryAdd(SWTHandleRegistry *registry, id value, SWTRegistryError *error) {
	*error = SWTRegistryOK;
	if (value == nil) { *error = SWTRegistryMemory; return 0; }
	uint32_t index = 0;
	while (index < registry->length && registry->values[index] != nil) index++;
	if (index == registry->capacity) {
		if (registry->capacity == SWT_SLOT_MASK) { *error = SWTRegistryCapacity; return 0; }
		uint32_t capacity = registry->capacity == 0 ? 32 : (uint32_t)MIN((uint64_t)registry->capacity * 2, (uint64_t)SWT_SLOT_MASK);
		id __strong *values = (id __strong *)calloc(capacity, sizeof(id));
		uint32_t *generation = calloc(capacity, sizeof(uint32_t));
		if (values == NULL || generation == NULL) {
			free(values); free(generation); *error = SWTRegistryMemory; return 0;
		}
		for (uint32_t i = 0; i < registry->length; i++) {
			values[i] = registry->values[i];
			registry->values[i] = nil;
			generation[i] = registry->generation[i];
		}
		free(registry->values); free(registry->generation);
		registry->values = values; registry->generation = generation; registry->capacity = capacity;
	}
	if (registry->generation[index] == UINT32_MAX) { *error = SWTRegistryGeneration; return 0; }
	registry->generation[index]++;
	registry->values[index] = value;
	if (index == registry->length) registry->length++;
	uint64_t token = ((uint64_t)registry->kind << 60) | ((uint64_t)registry->generation[index] << 28) | (index + UINT64_C(1));
	return (jlong)token;
}

static void swtRegistryRemove(SWTHandleRegistry *registry, jlong handle) {
	uint32_t index = swtRegistryIndex(registry, handle);
	if (index != UINT32_MAX) registry->values[index] = nil;
}

static void swtRegistryClear(SWTHandleRegistry *registry) {
	for (uint32_t i = 0; i < registry->length; i++) registry->values[i] = nil;
}

static JavaVM *swtVM;
/* All UIKit/sink fields below are accessed only on UIKit's main thread. */
static __weak UIView *swtRoot;
static BOOL swtHostTerminated;
static SWTHandleRegistry swtViews = {.kind = 1};
static jobject swtSink;
static jmethodID swtInput, swtResize, swtPaint, swtClose, swtLifecycle, swtAccessibility, swtTerminate;
static id swtActiveObserver, swtInactiveObserver, swtTerminationObserver;
/* Images and graphics belong to the connected Java Display thread. */
static SWTHandleRegistry swtImages = {.kind = 2};
static SWTHandleRegistry swtGraphics = {.kind = 3};
static pthread_t swtDisplayThread;
static pthread_mutex_t swtOwnerLock = PTHREAD_MUTEX_INITIALIZER;
/* Reserve connection ownership before touching non-atomic worker state. */
static _Atomic int swtConnectionState; /* 0 disconnected, 1 connecting, 2 connected */
/* A callback exception is kept until the Display's checkFailure consumes it. */
static pthread_mutex_t swtFailureLock = PTHREAD_MUTEX_INITIALIZER;
static jthrowable swtCallbackFailure;
static BOOL swtCallbackBridgeFailure;

static void swtThrow(JNIEnv *env, const char *type, const char *message) {
	if ((*env)->ExceptionCheck(env)) return;
	jclass cls = (*env)->FindClass(env, type);
	if (cls != NULL) {
		(*env)->ThrowNew(env, cls, message);
		(*env)->DeleteLocalRef(env, cls);
	}
}

static BOOL swtOnDisplayThread(JNIEnv *env) {
	pthread_mutex_lock(&swtOwnerLock);
	BOOL owner = atomic_load_explicit(&swtConnectionState, memory_order_acquire) == 2 && pthread_equal(swtDisplayThread, pthread_self());
	pthread_mutex_unlock(&swtOwnerLock);
	if (!owner) {
		swtThrow(env, "java/lang/IllegalStateException", "Native resource belongs to the connected SWT Display thread");
		return NO;
	}
	return YES;
}

/* JNI is never invoked with the calling worker's JNIEnv inside this block. */
static BOOL swtUI(JNIEnv *env, void (^operation)(void)) {
	__block NSString *failure = nil;
	void (^guarded)(void) = ^{
		@try { operation(); }
		@catch (NSException *exception) { failure = exception.reason ?: @"UIKit operation failed"; }
	};
	if (NSThread.isMainThread) guarded(); else dispatch_sync(dispatch_get_main_queue(), guarded);
	if (failure != nil) {
		swtThrow(env, "java/lang/IllegalStateException", failure.UTF8String);
		return NO;
	}
	return YES;
}

static NSString *swtString(JNIEnv *env, jstring value) {
	if (value == NULL) { swtThrow(env, "java/lang/NullPointerException", "text"); return nil; }
	jsize length = (*env)->GetStringLength(env, value);
	const jchar *chars = (*env)->GetStringChars(env, value, NULL);
	if (chars == NULL) return nil;
	NSString *text = [[NSString alloc] initWithCharacters:(const unichar *)chars length:(NSUInteger)length];
	(*env)->ReleaseStringChars(env, value, chars);
	if (text == nil) swtThrow(env, "java/lang/OutOfMemoryError", "UTF-16 text allocation failed");
	return text;
}

static jintArray swtInts(JNIEnv *env, const jint *values, jsize count) {
	jintArray result = (*env)->NewIntArray(env, count);
	if (result != NULL) (*env)->SetIntArrayRegion(env, result, 0, count, values);
	return result;
}

static void swtCaptureFailure(JNIEnv *env) {
	if (!(*env)->ExceptionCheck(env)) return;
	jthrowable local = (*env)->ExceptionOccurred(env);
	(*env)->ExceptionClear(env);
	jthrowable global = (jthrowable)(*env)->NewGlobalRef(env, local);
	(*env)->DeleteLocalRef(env, local);
	BOOL allocationFailed = global == NULL;
	if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
	pthread_mutex_lock(&swtFailureLock);
	if (swtCallbackFailure == NULL && !swtCallbackBridgeFailure) {
		swtCallbackFailure = global;
		swtCallbackBridgeFailure = allocationFailed;
		global = NULL;
	}
	pthread_mutex_unlock(&swtFailureLock);
	if (global != NULL) (*env)->DeleteGlobalRef(env, global);
}

/* UIKit must never wait for an application listener. EventSink only enqueues. */
static void swtCallback(void (^call)(JNIEnv *, jobject)) {
	if (swtSink == NULL || swtVM == NULL) return;
	pthread_mutex_lock(&swtFailureLock);
	BOOL failed = swtCallbackFailure != NULL || swtCallbackBridgeFailure;
	pthread_mutex_unlock(&swtFailureLock);
	if (failed) return;
	JNIEnv *env = NULL;
	BOOL attached = NO;
	jint status = (*swtVM)->GetEnv(swtVM, (void **)&env, JNI_VERSION_1_8);
	if (status == JNI_EDETACHED) {
		status = (*swtVM)->AttachCurrentThreadAsDaemon(swtVM, (void **)&env, NULL);
		attached = status == JNI_OK;
	}
	if (status != JNI_OK || env == NULL) {
		pthread_mutex_lock(&swtFailureLock);
		swtCallbackBridgeFailure = YES;
		pthread_mutex_unlock(&swtFailureLock);
		return;
	}
	if ((*env)->PushLocalFrame(env, 16) == 0) {
		call(env, swtSink);
		swtCaptureFailure(env);
		(*env)->PopLocalFrame(env, NULL);
	} else {
		swtCaptureFailure(env);
	}
	if (attached) (*swtVM)->DetachCurrentThread(swtVM);
}

static jint swtTime(NSTimeInterval seconds) {
	/* SWT event time is a wrapping 32-bit millisecond value. */
	return (jint)(uint32_t)(uint64_t)(seconds * 1000.0);
}

static void swtSendInput(jlong peer, jint type, CGPoint point, jint button,
	jint keyCode, jchar character, jint state, jint count, NSTimeInterval time) {
	swtCallback(^(JNIEnv *env, jobject sink) {
		(*env)->CallVoidMethod(env, sink, swtInput, peer, type, (jint)floor(point.x),
			(jint)floor(point.y), button, keyCode, character, state, count, swtTime(time));
	});
}

@interface SWTUIKitView : UIView
@property(nonatomic) jlong peer;
@property(nonatomic) jlong owner;
@property(nonatomic) jint kind;
@property(nonatomic) jint style;
@property(nonatomic) BOOL disposed;
@property(nonatomic, strong) UITouch *primaryTouch;
@property(nonatomic) BOOL enabled;
@property(nonatomic) CGImageRef snapshot;
@end

@implementation SWTUIKitView
- (instancetype)initWithFrame:(CGRect)frame {
	self = [super initWithFrame:frame];
	if (self) {
		self.opaque = NO;
		self.clipsToBounds = YES;
		self.contentMode = UIViewContentModeRedraw;
		self.multipleTouchEnabled = YES;
		self.enabled = YES;
		self.hidden = YES;
	}
	return self;
}
- (void)dealloc { if (_snapshot != NULL) CGImageRelease(_snapshot); }
- (void)setSnapshot:(CGImageRef)value {
	if (value != NULL) CGImageRetain(value);
	if (_snapshot != NULL) CGImageRelease(_snapshot);
	_snapshot = value;
	[self setNeedsDisplay];
}
- (void)drawRect:(CGRect)rect {
	(void)rect;
	if (_disposed || _snapshot == NULL) return;
	CGContextRef gc = UIGraphicsGetCurrentContext();
	CGContextSaveGState(gc);
	CGContextTranslateCTM(gc, 0, self.bounds.size.height);
	CGContextScaleCTM(gc, 1, -1);
	CGContextDrawImage(gc, self.bounds, _snapshot);
	CGContextRestoreGState(gc);
	/* Present must not schedule another SWT Paint event. */
}
- (void)layoutSubviews {
	[super layoutSubviews];
	if (_disposed || _peer == 0) return;
	jlong peer = _peer;
	CGSize size = self.bounds.size;
	swtCallback(^(JNIEnv *env, jobject sink) {
		(*env)->CallVoidMethod(env, sink, swtResize, peer, (jint)size.width, (jint)size.height);
	});
	swtCallback(^(JNIEnv *env, jobject sink) { (*env)->CallVoidMethod(env, sink, swtPaint, peer); });
}
- (void)didMoveToWindow {
	[super didMoveToWindow];
	if (_disposed || _peer == 0 || self.window == nil) return;
	jlong peer = _peer;
	swtCallback(^(JNIEnv *env, jobject sink) { (*env)->CallVoidMethod(env, sink, swtPaint, peer); });
}
- (BOOL)canBecomeFirstResponder { return !_disposed && _enabled && (_style & SWT(NO_FOCUS)) == 0; }
- (BOOL)becomeFirstResponder {
	BOOL was = self.isFirstResponder;
	BOOL result = [super becomeFirstResponder];
	if (result && !was && !_disposed) swtSendInput(_peer, SWT(FOCUS_IN), CGPointZero, 0, 0, 0, 0, 0, NSProcessInfo.processInfo.systemUptime);
	return result;
}
- (BOOL)resignFirstResponder {
	BOOL was = self.isFirstResponder;
	BOOL result = [super resignFirstResponder];
	if (result && was && !_disposed) swtSendInput(_peer, SWT(FOCUS_OUT), CGPointZero, 0, 0, 0, 0, 0, NSProcessInfo.processInfo.systemUptime);
	return result;
}
- (void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
	if (_disposed || !_enabled || _primaryTouch != nil) return;
	_primaryTouch = touches.anyObject;
	if (_primaryTouch == nil) return;
	[self becomeFirstResponder];
	CGPoint point = [_primaryTouch locationInView:self];
	swtSendInput(_peer, SWT(MOUSE_ENTER), point, 0, 0, 0, 0, 0, event.timestamp);
	swtSendInput(_peer, SWT(MOUSE_DOWN), point, 1, 0, 0, 0, (jint)_primaryTouch.tapCount, event.timestamp);
}
- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
	if (!_disposed && _primaryTouch != nil && [touches containsObject:_primaryTouch]) {
		swtSendInput(_peer, SWT(MOUSE_MOVE), [_primaryTouch locationInView:self], 0, 0, 0, SWT(BUTTON1), 0, event.timestamp);
	}
}
- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
	if (_disposed || _primaryTouch == nil || ![touches containsObject:_primaryTouch]) return;
	CGPoint point = [_primaryTouch locationInView:self];
	jint count = (jint)_primaryTouch.tapCount;
	_primaryTouch = nil;
	swtSendInput(_peer, SWT(MOUSE_UP), point, 1, 0, 0, SWT(BUTTON1), count, event.timestamp);
	swtSendInput(_peer, SWT(MOUSE_EXIT), point, 0, 0, 0, 0, 0, event.timestamp);
}
- (void)touchesCancelled:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
	if (_disposed || _primaryTouch == nil || ![touches containsObject:_primaryTouch]) return;
	CGPoint point = [_primaryTouch locationInView:self];
	_primaryTouch = nil;
	/* A cancelled gesture must not activate an original widget's MouseUp handler. */
	swtSendInput(_peer, SWT(MOUSE_EXIT), point, 0, 0, 0, 0, 0, event.timestamp);
}
- (void)sendPresses:(NSSet<UIPress *> *)presses type:(jint)type event:(UIPressesEvent *)event {
	if (_disposed || !_enabled) return;
	if (@available(iOS 13.4, *)) {
		for (UIPress *press in presses) {
			UIKey *key = press.key;
			if (key == nil) continue;
			UIKeyModifierFlags modifiers = key.modifierFlags;
			jint state = ((modifiers & UIKeyModifierShift) ? SWT(SHIFT) : 0)
				| ((modifiers & UIKeyModifierControl) ? SWT(CTRL) : 0)
				| ((modifiers & UIKeyModifierAlternate) ? SWT(ALT) : 0)
				| ((modifiers & UIKeyModifierCommand) ? SWT(COMMAND) : 0);
			jint code = 0;
			switch (key.keyCode) {
				case UIKeyboardHIDUsageKeyboardUpArrow: code = SWT(ARROW_UP); break;
				case UIKeyboardHIDUsageKeyboardDownArrow: code = SWT(ARROW_DOWN); break;
				case UIKeyboardHIDUsageKeyboardLeftArrow: code = SWT(ARROW_LEFT); break;
				case UIKeyboardHIDUsageKeyboardRightArrow: code = SWT(ARROW_RIGHT); break;
				case UIKeyboardHIDUsageKeyboardPageUp: code = SWT(PAGE_UP); break;
				case UIKeyboardHIDUsageKeyboardPageDown: code = SWT(PAGE_DOWN); break;
				case UIKeyboardHIDUsageKeyboardHome: code = SWT(HOME); break;
				case UIKeyboardHIDUsageKeyboardEnd: code = SWT(END); break;
				case UIKeyboardHIDUsageKeyboardInsert: code = SWT(INSERT); break;
				case UIKeyboardHIDUsageKeyboardDeleteOrBackspace: code = '\b'; break;
				case UIKeyboardHIDUsageKeyboardReturnOrEnter: code = '\r'; break;
				case UIKeyboardHIDUsageKeyboardEscape: code = 0x1b; break;
				case UIKeyboardHIDUsageKeyboardTab: code = '\t'; break;
				case UIKeyboardHIDUsageKeyboardDeleteForward: code = 0x7f; break;
				default: if (key.charactersIgnoringModifiers.length != 0) code = [key.charactersIgnoringModifiers characterAtIndex:0]; break;
			}
			jchar character = key.characters.length == 0 ? 0 : [key.characters characterAtIndex:0];
			swtSendInput(_peer, type, CGPointZero, 0, code, character, state, 1, event.timestamp);
		}
	}
}
- (void)pressesBegan:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event { [self sendPresses:presses type:SWT(KEY_DOWN) event:event]; }
- (void)pressesEnded:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event { [self sendPresses:presses type:SWT(KEY_UP) event:event]; }
- (void)pressesCancelled:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event { [self sendPresses:presses type:SWT(KEY_UP) event:event]; }
- (BOOL)accessibilityActivate {
	if (_disposed || !_enabled) return NO;
	jlong peer = _peer;
	swtCallback(^(JNIEnv *env, jobject sink) { (*env)->CallVoidMethod(env, sink, swtAccessibility, peer, (jint)SWT(ACTION_ACTIVATE)); });
	return YES;
}
@end

static SWTUIKitView *swtView(jlong peer) {
	SWTUIKitView *view = swtRegistryGet(&swtViews, peer);
	if (peer <= 0 || view == nil || view.disposed) {
		[NSException raise:NSInvalidArgumentException format:@"SWT UIKit peer is disposed or unknown"];
	}
	return view;
}

void SWTUIKitSetRootView(UIView *view) {
	if (!NSThread.isMainThread || swtSink != NULL) {
		[NSException raise:NSInternalInconsistencyException format:@"Install the SWT root on main before connecting its Display"];
	}
	swtRoot = view;
	swtHostTerminated = NO;
}

void SWTUIKitHostTerminated(void) {
	if (!NSThread.isMainThread) [NSException raise:NSInternalInconsistencyException format:@"Host destruction must run on main"];
	swtHostTerminated = YES;
	swtCallback(^(JNIEnv *env, jobject sink) { (*env)->CallVoidMethod(env, sink, swtTerminate); });
}

JNIEXPORT void JNICALL JNI(checkFailure)(JNI_ARGS) {
	@autoreleasepool {
		pthread_mutex_lock(&swtFailureLock);
		jthrowable failure = swtCallbackFailure;
		BOOL bridgeFailure = swtCallbackBridgeFailure;
		swtCallbackFailure = NULL;
		swtCallbackBridgeFailure = NO;
		pthread_mutex_unlock(&swtFailureLock);
		if (failure != NULL) {
			(*env)->Throw(env, failure);
			(*env)->DeleteGlobalRef(env, failure);
		} else if (bridgeFailure) {
			swtThrow(env, "java/lang/IllegalStateException", "Unable to attach UIKit callback thread or preserve callback exception");
		}
	}
}

JNIEXPORT void JNICALL JNI(connect)(JNI_ARGS, jobject sink) {
	@autoreleasepool {
		if (sink == NULL) { swtThrow(env, "java/lang/NullPointerException", "sink"); return; }
		if (swtVM == NULL) { swtThrow(env, "java/lang/IllegalStateException", "Register the SWT iOS native owner before connecting a Display"); return; }
		if (NSThread.isMainThread) { swtThrow(env, "java/lang/IllegalStateException", "SWT Display must run on the Java application worker, not UIKit main"); return; }
		jclass type = (*env)->GetObjectClass(env, sink);
		if (type == NULL) return;
		jmethodID input = (*env)->GetMethodID(env, type, "input", "(JIIIIICIII)V");
		jmethodID resize = input == NULL ? NULL : (*env)->GetMethodID(env, type, "resize", "(JII)V");
		jmethodID paint = resize == NULL ? NULL : (*env)->GetMethodID(env, type, "paint", "(J)V");
		jmethodID close = paint == NULL ? NULL : (*env)->GetMethodID(env, type, "close", "(J)V");
		jmethodID lifecycle = close == NULL ? NULL : (*env)->GetMethodID(env, type, "lifecycle", "(Z)V");
		jmethodID accessibility = lifecycle == NULL ? NULL : (*env)->GetMethodID(env, type, "accessibility", "(JI)V");
		jmethodID terminate = accessibility == NULL ? NULL : (*env)->GetMethodID(env, type, "terminate", "()V");
		(*env)->DeleteLocalRef(env, type);
		if (terminate == NULL) return;
		jobject retained = (*env)->NewGlobalRef(env, sink);
		if (retained == NULL) return;
		int disconnected = 0;
		if (!atomic_compare_exchange_strong_explicit(&swtConnectionState, &disconnected, 1,
			memory_order_acq_rel, memory_order_acquire)) {
			(*env)->DeleteGlobalRef(env, retained);
			swtThrow(env, "java/lang/IllegalStateException", "SWT Display already connected or connecting"); return;
		}
		BOOL success = swtUI(env, ^{
			if (swtRoot == nil || swtRoot.window.windowScene == nil || swtHostTerminated || swtSink != NULL) [NSException raise:NSInternalInconsistencyException format:@"Install one live UIKit scene root before connecting SWT"];
			swtInput = input; swtResize = resize; swtPaint = paint; swtClose = close;
			swtLifecycle = lifecycle; swtAccessibility = accessibility; swtTerminate = terminate;
			NSNotificationCenter *center = NSNotificationCenter.defaultCenter;
			@try {
				swtActiveObserver = [center addObserverForName:UISceneDidActivateNotification object:nil queue:NSOperationQueue.mainQueue usingBlock:^(NSNotification *note) {
					if (note.object != swtRoot.window.windowScene) return;
					swtCallback(^(JNIEnv *callbackEnv, jobject callbackSink) { (*callbackEnv)->CallVoidMethod(callbackEnv, callbackSink, swtLifecycle, JNI_TRUE); });
				}];
				swtInactiveObserver = [center addObserverForName:UISceneWillDeactivateNotification object:nil queue:NSOperationQueue.mainQueue usingBlock:^(NSNotification *note) {
					if (note.object != swtRoot.window.windowScene) return;
					swtCallback(^(JNIEnv *callbackEnv, jobject callbackSink) { (*callbackEnv)->CallVoidMethod(callbackEnv, callbackSink, swtLifecycle, JNI_FALSE); });
				}];
				swtTerminationObserver = [center addObserverForName:UIApplicationWillTerminateNotification object:nil queue:NSOperationQueue.mainQueue usingBlock:^(NSNotification *note) {
					(void)note;
					SWTUIKitHostTerminated();
				}];
				/* Publish only after the complete observer set has been installed. */
				swtSink = retained;
				jboolean active = swtRoot.window.windowScene.activationState == UISceneActivationStateForegroundActive ? JNI_TRUE : JNI_FALSE;
				swtCallback(^(JNIEnv *callbackEnv, jobject callbackSink) { (*callbackEnv)->CallVoidMethod(callbackEnv, callbackSink, swtLifecycle, active); });
			} @catch (NSException *exception) {
				if (swtSink == retained) swtSink = NULL;
				if (swtActiveObserver != nil) [center removeObserver:swtActiveObserver];
				if (swtInactiveObserver != nil) [center removeObserver:swtInactiveObserver];
				if (swtTerminationObserver != nil) [center removeObserver:swtTerminationObserver];
				swtActiveObserver = swtInactiveObserver = swtTerminationObserver = nil;
				swtRegistryClear(&swtViews);
				@throw exception;
			}
		});
		if (!success) {
			(*env)->DeleteGlobalRef(env, retained);
			atomic_store_explicit(&swtConnectionState, 0, memory_order_release); return;
		}
		pthread_mutex_lock(&swtOwnerLock);
		swtDisplayThread = pthread_self();
		atomic_store_explicit(&swtConnectionState, 2, memory_order_release);
		pthread_mutex_unlock(&swtOwnerLock);
	}
}

JNIEXPORT void JNICALL JNI(disconnect)(JNI_ARGS) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return;
		__block jobject retained = NULL;
		swtUI(env, ^{
			/* Silence callbacks before removing views; no delayed event can use old refs. */
			retained = swtSink; swtSink = NULL;
			NSNotificationCenter *center = NSNotificationCenter.defaultCenter;
			if (swtActiveObserver != nil) [center removeObserver:swtActiveObserver];
			if (swtInactiveObserver != nil) [center removeObserver:swtInactiveObserver];
			if (swtTerminationObserver != nil) [center removeObserver:swtTerminationObserver];
			swtActiveObserver = swtInactiveObserver = swtTerminationObserver = nil;
			for (uint32_t i = 0; i < swtViews.length; i++) {
				SWTUIKitView *view = swtViews.values[i];
				if (view == nil) continue;
				view.disposed = YES; view.primaryTouch = nil; view.snapshot = NULL;
				[view removeFromSuperview];
			}
			swtRegistryClear(&swtViews);
		});
		if (retained != NULL) (*env)->DeleteGlobalRef(env, retained);
		swtRegistryClear(&swtGraphics); swtRegistryClear(&swtImages);
		atomic_store_explicit(&swtConnectionState, 0, memory_order_release);
	}
}

JNIEXPORT jintArray JNICALL JNI(displayBounds)(JNI_ARGS) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return NULL;
		__block CGRect bounds = CGRectZero;
		if (!swtUI(env, ^{ bounds = swtRoot.bounds; })) return NULL;
		jint values[] = {0, 0, (jint)floor(bounds.size.width), (jint)floor(bounds.size.height)};
		return swtInts(env, values, 4);
	}
}

JNIEXPORT jintArray JNICALL JNI(dpi)(JNI_ARGS) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return NULL;
		/* SWT uses logical points here. This is nominal CoreText DPI, not physical panel DPI. */
		jint values[] = {72, 72};
		return swtInts(env, values, 2);
	}
}

JNIEXPORT jlong JNICALL JNI(createControl)(JNI_ARGS, jint kind, jlong parent, jint style) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return 0;
		if ((kind != SWT(SHELL) && kind != SWT(COMPOSITE) && kind != SWT(CANVAS)) || (kind != SWT(SHELL) && parent == 0)) { swtThrow(env, "java/lang/IllegalArgumentException", "Unsupported peer kind or missing parent"); return 0; }
		__block jlong peer = 0;
		swtUI(env, ^{
			SWTUIKitView *owner = parent == 0 ? nil : swtView(parent);
			if (kind == SWT(SHELL) && owner != nil && owner.kind != SWT(SHELL)) {
				[NSException raise:NSInvalidArgumentException format:@"A Shell owner must be a Shell"];
			}
			/* An owned Shell is a window in display coordinates, not a clipped
			 * child control in its owner's coordinate system. */
			UIView *host = kind == SWT(SHELL) ? swtRoot : owner;
			if (host == nil) [NSException raise:NSInternalInconsistencyException format:@"UIKit root is unavailable"];
			SWTUIKitView *view = [[SWTUIKitView alloc] initWithFrame:CGRectZero];
			view.kind = kind; view.owner = parent; view.style = style;
			SWTRegistryError error;
			peer = swtRegistryAdd(&swtViews, view, &error);
			if (peer == 0) [NSException raise:error == SWTRegistryMemory ? NSMallocException : NSInternalInconsistencyException format:@"Cannot allocate SWT UIKit peer registry slot (%u)", (unsigned)error];
			view.peer = peer;
			@try { [host addSubview:view]; }
			@catch (NSException *exception) {
				view.disposed = YES;
				swtRegistryRemove(&swtViews, peer);
				@throw exception;
			}
		});
		return (*env)->ExceptionCheck(env) ? 0 : peer;
	}
}

JNIEXPORT void JNICALL JNI(disposeControl)(JNI_ARGS, jlong peer) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return;
		swtUI(env, ^{
			SWTUIKitView *view = swtView(peer);
			/* Widget/Composite disposes child owners first. Refuse an orphaning call. */
			for (UIView *child in view.subviews) if ([child isKindOfClass:SWTUIKitView.class] && !((SWTUIKitView *)child).disposed) {
				[NSException raise:NSInternalInconsistencyException format:@"Dispose SWT child peers before their parent"];
			}
			if (view.kind == SWT(SHELL)) for (uint32_t i = 0; i < swtViews.length; i++) {
				SWTUIKitView *owned = swtViews.values[i];
				if (owned != nil && !owned.disposed && owned.owner == peer) {
					[NSException raise:NSInternalInconsistencyException format:@"Dispose owned SWT Shell peers before their owner"];
				}
			}
			view.disposed = YES; view.primaryTouch = nil; view.snapshot = NULL;
			[view resignFirstResponder]; [view removeFromSuperview]; swtRegistryRemove(&swtViews, peer);
		});
	}
}

JNIEXPORT void JNICALL JNI(setBounds)(JNI_ARGS, jlong peer, jint x, jint y, jint width, jint height) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return;
		if (width < 0 || height < 0) { swtThrow(env, "java/lang/IllegalArgumentException", "Negative bounds"); return; }
		swtUI(env, ^{
			SWTUIKitView *view = swtView(peer);
			BOOL fillViewport = view.kind == SWT(SHELL) && x == 0 && y == 0
				&& width == (jint)floor(swtRoot.bounds.size.width) && height == (jint)floor(swtRoot.bounds.size.height);
			view.autoresizingMask = fillViewport ? UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight : UIViewAutoresizingNone;
			view.frame = CGRectMake(x, y, width, height);
		});
	}
}
JNIEXPORT void JNICALL JNI(setVisible)(JNI_ARGS, jlong peer, jboolean visible) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return;
		swtUI(env, ^{ SWTUIKitView *view = swtView(peer); view.hidden = !visible; if (visible) [view setNeedsLayout]; });
	}
}
JNIEXPORT void JNICALL JNI(setEnabled)(JNI_ARGS, jlong peer, jboolean enabled) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return;
		swtUI(env, ^{
			SWTUIKitView *view = swtView(peer); view.enabled = enabled; view.userInteractionEnabled = enabled;
			if (enabled) view.accessibilityTraits &= ~UIAccessibilityTraitNotEnabled; else view.accessibilityTraits |= UIAccessibilityTraitNotEnabled;
			if (!enabled) { view.primaryTouch = nil; [view resignFirstResponder]; }
		});
	}
}
JNIEXPORT void JNICALL JNI(setTitle)(JNI_ARGS, jlong peer, jstring title) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return;
		NSString *value = swtString(env, title); if (value == nil) return;
		swtUI(env, ^{ swtView(peer).accessibilityLabel = value; });
	}
}
JNIEXPORT jboolean JNICALL JNI(requestFocus)(JNI_ARGS, jlong peer) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return JNI_FALSE;
		__block BOOL focused = NO;
		swtUI(env, ^{ focused = [swtView(peer) becomeFirstResponder]; });
		return focused ? JNI_TRUE : JNI_FALSE;
	}
}
JNIEXPORT void JNICALL JNI(setAccessibility)(JNI_ARGS, jlong peer, jstring label, jint role, jint state) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return;
		if (role == SWT(ROLE_SLIDER) || role == SWT(ROLE_SPINBUTTON)) {
			swtThrow(env, "java/lang/UnsupportedOperationException", "Adjustable accessibility actions require the corresponding original SWT widget integration"); return;
		}
		NSString *value = swtString(env, label); if (value == nil) return;
		swtUI(env, ^{
			SWTUIKitView *view = swtView(peer);
			/* Roles/states are the unchanged ACC constants, not a new widget model. */
			UIAccessibilityTraits traits = UIAccessibilityTraitNone;
			if (role == SWT(ROLE_PUSHBUTTON) || role == SWT(ROLE_CHECKBUTTON) || role == SWT(ROLE_RADIOBUTTON)) traits |= UIAccessibilityTraitButton;
			if (role == SWT(ROLE_LABEL)) traits |= UIAccessibilityTraitStaticText;
			if ((state & (SWT(STATE_SELECTED) | SWT(STATE_CHECKED))) != 0) traits |= UIAccessibilityTraitSelected;
			if ((state & SWT(STATE_DISABLED)) != 0 || !view.enabled) traits |= UIAccessibilityTraitNotEnabled;
			view.isAccessibilityElement = role != 0 || value.length != 0;
			view.accessibilityLabel = value;
			view.accessibilityTraits = traits;
			UIAccessibilityPostNotification(UIAccessibilityLayoutChangedNotification, nil);
		});
	}
}

/* CoreGraphics image and GC implementation follows. */

@interface SWTUIKitImage : NSObject
@property(nonatomic) jlong peer;
@property(nonatomic) size_t width;
@property(nonatomic) size_t height;
@property(nonatomic) CGContextRef context;
@property(nonatomic) jlong activeGraphics;
@property(nonatomic) BOOL disposed;
@end
@implementation SWTUIKitImage
- (void)dealloc {
	if (_context != NULL) CGContextRelease(_context);
}
@end

@interface SWTUIKitGraphics : NSObject
@property(nonatomic, strong) SWTUIKitImage *image;
@property(nonatomic) uint32_t foreground;
@property(nonatomic) uint32_t background;
@property(nonatomic) CGFloat alpha;
@property(nonatomic) CGFloat lineWidth;
@property(nonatomic) BOOL antialias;
@property(nonatomic) CGAffineTransform transform;
@property(nonatomic) BOOL clipped;
@property(nonatomic) CGRect clip;
@property(nonatomic) CTFontRef font;
@end
@implementation SWTUIKitGraphics
- (void)dealloc { if (_font != NULL) CFRelease(_font); }
@end

static SWTUIKitImage *swtImage(JNIEnv *env, jlong handle) {
	if (!swtOnDisplayThread(env)) return nil;
	SWTUIKitImage *image = swtRegistryGet(&swtImages, handle);
	if (handle <= 0 || image == nil || image.disposed) {
		swtThrow(env, "java/lang/IllegalStateException", "SWT image is disposed or unknown"); return nil;
	}
	return image;
}
static SWTUIKitGraphics *swtGC(JNIEnv *env, jlong handle) {
	if (!swtOnDisplayThread(env)) return nil;
	SWTUIKitGraphics *graphics = swtRegistryGet(&swtGraphics, handle);
	if (handle <= 0 || graphics == nil || graphics.image.disposed) {
		swtThrow(env, "java/lang/IllegalStateException", "SWT GC or its image is disposed or unknown"); return nil;
	}
	return graphics;
}

/* UIKit-compatible BGRA storage is premultiplied; Java's ARGB ingress is straight. */
static uint32_t swtPremultiply(uint32_t color) {
	uint32_t alpha = color >> 24;
	uint32_t red = (((color >> 16) & 255) * alpha + 127) / 255;
	uint32_t green = (((color >> 8) & 255) * alpha + 127) / 255;
	uint32_t blue = ((color & 255) * alpha + 127) / 255;
	return (alpha << 24) | (red << 16) | (green << 8) | blue;
}
static uint32_t swtUnpremultiply(uint32_t color) {
	uint32_t alpha = color >> 24;
	if (alpha == 0) return 0;
	uint32_t red = (((color >> 16) & 255) * 255 + alpha / 2) / alpha;
	uint32_t green = (((color >> 8) & 255) * 255 + alpha / 2) / alpha;
	uint32_t blue = ((color & 255) * 255 + alpha / 2) / alpha;
	return (alpha << 24) | (MIN(red, 255) << 16) | (MIN(green, 255) << 8) | MIN(blue, 255);
}

JNIEXPORT jlong JNICALL JNI(createImage)(JNI_ARGS, jint width, jint height, jintArray argb) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return 0;
		if (width <= 0 || height <= 0 || (uint64_t)width * (uint64_t)height > INT_MAX
			|| (size_t)width > SIZE_MAX / 4 / (size_t)height) {
			swtThrow(env, "java/lang/IllegalArgumentException", "Invalid or overflowing image dimensions"); return 0;
		}
		jsize count = width * height;
		if (argb != NULL && (*env)->GetArrayLength(env, argb) != count) {
			swtThrow(env, "java/lang/IllegalArgumentException", "Pixel array does not match the image dimensions"); return 0;
		}
		SWTUIKitImage *image = [SWTUIKitImage new];
		image.width = (size_t)width; image.height = (size_t)height;
		CGColorSpaceRef colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
		if (colorSpace == NULL) { swtThrow(env, "java/lang/OutOfMemoryError", "sRGB color space"); return 0; }
		/* CoreGraphics owns the allocation and any copy-on-write snapshot storage. */
		image.context = CGBitmapContextCreate(NULL, (size_t)width, (size_t)height, 8,
			(size_t)width * 4, colorSpace, kCGBitmapByteOrder32Little | kCGImageAlphaPremultipliedFirst);
		CGColorSpaceRelease(colorSpace);
		if (image.context == NULL) { swtThrow(env, "java/lang/OutOfMemoryError", "CoreGraphics bitmap context"); return 0; }
		uint32_t *pixels = CGBitmapContextGetData(image.context);
		if (pixels == NULL) { swtThrow(env, "java/lang/OutOfMemoryError", "Image pixels"); return 0; }
		if (argb != NULL) {
			/* Initialize before publishing or taking any snapshot; later mutations
			 * go through CoreGraphics so its copy-on-write ownership stays intact. */
			(*env)->GetIntArrayRegion(env, argb, 0, count, (jint *)pixels);
			if ((*env)->ExceptionCheck(env)) return 0;
			for (jsize i = 0; i < count; i++) pixels[i] = swtPremultiply(pixels[i]);
		} else {
			memset(pixels, 0, (size_t)count * sizeof(uint32_t));
		}
		/* Original SWT coordinates have y increasing downward. */
		CGContextTranslateCTM(image.context, 0, height);
		CGContextScaleCTM(image.context, 1, -1);
		SWTRegistryError error;
		image.peer = swtRegistryAdd(&swtImages, image, &error);
		if (image.peer == 0) { swtThrow(env, error == SWTRegistryMemory ? "java/lang/OutOfMemoryError" : "java/lang/IllegalStateException", "Cannot allocate SWT image registry slot"); return 0; }
		return image.peer;
	}
}

JNIEXPORT void JNICALL JNI(disposeImage)(JNI_ARGS, jlong handle) {
	@autoreleasepool {
		SWTUIKitImage *image = swtImage(env, handle); if (image == nil) return;
		image.disposed = YES;
		swtRegistryRemove(&swtImages, handle);
		/* An outstanding GC retains only the allocation until close; all draws refuse. */
	}
}

JNIEXPORT jintArray JNICALL JNI(imagePixels)(JNI_ARGS, jlong handle) {
	@autoreleasepool {
		SWTUIKitImage *image = swtImage(env, handle); if (image == nil) return NULL;
		jsize count = (jsize)(image.width * image.height);
		jintArray result = (*env)->NewIntArray(env, count);
		if (result == NULL) return NULL;
		uint32_t *pixels = CGBitmapContextGetData(image.context);
		jint block[512];
		for (jsize start = 0; start < count;) {
			jsize length = MIN((jsize)512, count - start);
			for (jsize i = 0; i < length; i++) block[i] = (jint)swtUnpremultiply(pixels[start + i]);
			(*env)->SetIntArrayRegion(env, result, start, length, block);
			if ((*env)->ExceptionCheck(env)) return NULL;
			start += length;
		}
		return result;
	}
}

JNIEXPORT void JNICALL JNI(present)(JNI_ARGS, jlong peer, jlong handle) {
	@autoreleasepool {
		SWTUIKitImage *image = swtImage(env, handle); if (image == nil) return;
		/* CGBitmapContextCreateImage defines an immutable snapshot, including when
		 * CoreGraphics implements it using copy-on-write. Never expose mutable pixels. */
		CGImageRef snapshot = CGBitmapContextCreateImage(image.context);
		if (snapshot == NULL) { swtThrow(env, "java/lang/OutOfMemoryError", "Image snapshot"); return; }
		swtUI(env, ^{ swtView(peer).snapshot = snapshot; });
		CGImageRelease(snapshot);
	}
}

JNIEXPORT jlong JNICALL JNI(createGraphics)(JNI_ARGS, jlong handle) {
	@autoreleasepool {
		SWTUIKitImage *image = swtImage(env, handle); if (image == nil) return 0;
		if (image.activeGraphics != 0) { swtThrow(env, "java/lang/IllegalStateException", "Image already has an active GC"); return 0; }
		SWTUIKitGraphics *gc = [SWTUIKitGraphics new];
		gc.image = image; gc.foreground = 0xff000000; gc.background = 0xffffffff;
		gc.alpha = 1; gc.lineWidth = 1; gc.antialias = YES; gc.transform = CGAffineTransformIdentity;
		gc.font = CTFontCreateWithName(CFSTR("Helvetica"), 12, NULL);
		if (gc.font == NULL) { swtThrow(env, "java/lang/OutOfMemoryError", "CoreText font"); return 0; }
		SWTRegistryError error;
		jlong peer = swtRegistryAdd(&swtGraphics, gc, &error);
		if (peer == 0) { swtThrow(env, error == SWTRegistryMemory ? "java/lang/OutOfMemoryError" : "java/lang/IllegalStateException", "Cannot allocate SWT GC registry slot"); return 0; }
		image.activeGraphics = peer;
		return peer;
	}
}

JNIEXPORT void JNICALL JNI(disposeGraphics)(JNI_ARGS, jlong handle) {
	@autoreleasepool {
		if (!swtOnDisplayThread(env)) return;
		SWTUIKitGraphics *gc = swtRegistryGet(&swtGraphics, handle);
		if (gc == nil) { swtThrow(env, "java/lang/IllegalStateException", "GC is disposed or unknown"); return; }
		if (gc.image.activeGraphics == handle) gc.image.activeGraphics = 0;
		swtRegistryRemove(&swtGraphics, handle);
	}
}
JNIEXPORT void JNICALL JNI(setForeground)(JNI_ARGS, jlong handle, jint argb) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc != nil) gc.foreground = (uint32_t)argb;
	}
}
JNIEXPORT void JNICALL JNI(setBackground)(JNI_ARGS, jlong handle, jint argb) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc != nil) gc.background = (uint32_t)argb;
	}
}
JNIEXPORT void JNICALL JNI(setAlpha)(JNI_ARGS, jlong handle, jint alpha) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return;
		if (alpha < 0 || alpha > 255) { swtThrow(env, "java/lang/IllegalArgumentException", "alpha outside 0..255"); return; }
		gc.alpha = alpha / 255.0;
	}
}
JNIEXPORT void JNICALL JNI(setLineWidth)(JNI_ARGS, jlong handle, jfloat width) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return;
		if (!isfinite(width) || width < 0) { swtThrow(env, "java/lang/IllegalArgumentException", "Invalid line width"); return; }
		gc.lineWidth = width == 0 ? 1 : width;
	}
}
JNIEXPORT void JNICALL JNI(setAntialias)(JNI_ARGS, jlong handle, jboolean enabled) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc != nil) gc.antialias = enabled;
	}
}
JNIEXPORT void JNICALL JNI(setFont)(JNI_ARGS, jlong handle, jstring family, jfloat size, jint style) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return;
		if (!isfinite(size) || size <= 0) { swtThrow(env, "java/lang/IllegalArgumentException", "Invalid font size"); return; }
		NSString *name = swtString(env, family); if (name == nil) return;
		CTFontRef font = CTFontCreateWithName((__bridge CFStringRef)name, size, NULL);
		if (font == NULL) { swtThrow(env, "java/lang/OutOfMemoryError", "CoreText font"); return; }
		CTFontSymbolicTraits traits = ((style & 1) ? kCTFontBoldTrait : 0) | ((style & 2) ? kCTFontItalicTrait : 0);
		if (traits != 0) {
			CTFontRef variant = CTFontCreateCopyWithSymbolicTraits(font, size, NULL, traits, kCTFontBoldTrait | kCTFontItalicTrait);
			if (variant != NULL) { CFRelease(font); font = variant; }
		}
		double height = ceil(CTFontGetAscent(font)) + ceil(CTFontGetDescent(font)) + ceil(CTFontGetLeading(font));
		if (!isfinite(height) || height < 0 || height > INT_MAX) {
			CFRelease(font); swtThrow(env, "java/lang/IllegalArgumentException", "Font metrics exceed SWT integer coordinates"); return;
		}
		CTFontRef old = gc.font; gc.font = font; if (old != NULL) CFRelease(old);
	}
}
JNIEXPORT void JNICALL JNI(setTransform)(JNI_ARGS, jlong handle, jfloatArray matrix) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return;
		if (matrix == NULL) { gc.transform = CGAffineTransformIdentity; return; }
		if ((*env)->GetArrayLength(env, matrix) != 6) { swtThrow(env, "java/lang/IllegalArgumentException", "Affine matrix needs six values"); return; }
		jfloat values[6]; (*env)->GetFloatArrayRegion(env, matrix, 0, 6, values);
		if ((*env)->ExceptionCheck(env)) return;
		for (int i = 0; i < 6; i++) if (!isfinite(values[i])) { swtThrow(env, "java/lang/IllegalArgumentException", "Non-finite transform"); return; }
		gc.transform = CGAffineTransformMake(values[0], values[1], values[2], values[3], values[4], values[5]);
	}
}
JNIEXPORT void JNICALL JNI(setClipping)(JNI_ARGS, jlong handle, jboolean enabled, jint x, jint y, jint width, jint height) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return;
		if (enabled && (width < 0 || height < 0)) { swtThrow(env, "java/lang/IllegalArgumentException", "Negative clip dimensions"); return; }
		gc.clipped = enabled; gc.clip = CGRectMake(x, y, width, height);
	}
}

static CGContextRef swtBegin(SWTUIKitGraphics *gc) {
	CGContextRef context = gc.image.context;
	CGContextSaveGState(context);
	CGContextConcatCTM(context, gc.transform);
	if (gc.clipped) CGContextClipToRect(context, gc.clip);
	CGContextSetAlpha(context, gc.alpha);
	CGContextSetLineWidth(context, gc.lineWidth);
	CGContextSetShouldAntialias(context, gc.antialias);
	uint32_t fg = gc.foreground, bg = gc.background;
	CGContextSetRGBStrokeColor(context, ((fg >> 16) & 255) / 255.0, ((fg >> 8) & 255) / 255.0, (fg & 255) / 255.0, (fg >> 24) / 255.0);
	CGContextSetRGBFillColor(context, ((bg >> 16) & 255) / 255.0, ((bg >> 8) & 255) / 255.0, (bg & 255) / 255.0, (bg >> 24) / 255.0);
	return context;
}

JNIEXPORT void JNICALL JNI(drawLine)(JNI_ARGS, jlong handle, jint x1, jint y1, jint x2, jint y2) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return;
		CGContextRef context = swtBegin(gc);
		CGContextBeginPath(context); CGContextMoveToPoint(context, x1, y1); CGContextAddLineToPoint(context, x2, y2);
		CGContextStrokePath(context); CGContextRestoreGState(context);
	}
}
JNIEXPORT void JNICALL JNI(drawShape)(JNI_ARGS, jlong handle, jint kind, jboolean fill, jint x, jint y, jint width, jint height) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return;
		if (kind < 0 || kind > 1) { swtThrow(env, "java/lang/IllegalArgumentException", "Unknown shape"); return; }
		CGRect rect = CGRectStandardize(CGRectMake(x, y, width, height));
		CGContextRef context = swtBegin(gc);
		if (kind == 0) {
			if (fill) CGContextFillRect(context, rect); else CGContextStrokeRect(context, rect);
		} else {
			if (fill) CGContextFillEllipseInRect(context, rect); else CGContextStrokeEllipseInRect(context, rect);
		}
		CGContextRestoreGState(context);
	}
}
JNIEXPORT void JNICALL JNI(drawPolygon)(JNI_ARGS, jlong handle, jintArray points, jboolean fill) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return;
		if (points == NULL) { swtThrow(env, "java/lang/NullPointerException", "points"); return; }
		jsize length = (*env)->GetArrayLength(env, points);
		if ((length & 1) != 0) { swtThrow(env, "java/lang/IllegalArgumentException", "Polygon requires coordinate pairs"); return; }
		if (length == 0) return;
		if ((size_t)length > SIZE_MAX / sizeof(jint)) { swtThrow(env, "java/lang/OutOfMemoryError", "Polygon too large"); return; }
		jint *values = malloc((size_t)length * sizeof(jint));
		if (values == NULL) { swtThrow(env, "java/lang/OutOfMemoryError", "Polygon coordinates"); return; }
		(*env)->GetIntArrayRegion(env, points, 0, length, values);
		if ((*env)->ExceptionCheck(env)) { free(values); return; }
		CGContextRef context = swtBegin(gc);
		CGContextBeginPath(context); CGContextMoveToPoint(context, values[0], values[1]);
		for (jsize i = 2; i < length; i += 2) CGContextAddLineToPoint(context, values[i], values[i + 1]);
		CGContextClosePath(context);
		if (fill) CGContextFillPath(context); else CGContextStrokePath(context);
		CGContextRestoreGState(context); free(values);
	}
}

/* Shape with CoreText, preserving UTF-16. Mnemonics are rendering metadata;
 * tabs use actual space advances and delimiters do not split surrogate pairs. */
static CGSize swtLayoutText(JNIEnv *env, SWTUIKitGraphics *gc, NSString *text, jint flags,
	void (^draw)(CTLineRef, CGFloat, CGFloat)) {
	CGFloat ascent = ceil(CTFontGetAscent(gc.font));
	CGFloat lineHeight = ascent + ceil(CTFontGetDescent(gc.font)) + ceil(CTFontGetLeading(gc.font));
	if (lineHeight < 1) lineHeight = 1;
	NSDictionary *attributes = @{(__bridge NSString *)kCTFontAttributeName: (__bridge id)gc.font,
		(__bridge NSString *)kCTForegroundColorFromContextAttributeName: @YES};
	NSAttributedString *spaceString = [[NSAttributedString alloc] initWithString:@" " attributes:attributes];
	CTLineRef space = CTLineCreateWithAttributedString((__bridge CFAttributedStringRef)spaceString);
	if (space == NULL) { swtThrow(env, "java/lang/OutOfMemoryError", "CoreText tab metrics"); return CGSizeZero; }
	CGFloat tabWidth = MAX(1.0, CTLineGetTypographicBounds(space, NULL, NULL, NULL) * 8);
	CFRelease(space);
	CGFloat x = 0, y = 0, maximum = 0;
	NSMutableString *segment = [NSMutableString string];
	NSMutableIndexSet *underlines = [NSMutableIndexSet indexSet];
	BOOL mnemonic = NO;
	NSUInteger length = text.length;
	for (NSUInteger i = 0; i <= length; i++) {
		unichar ch = i == length ? 0 : [text characterAtIndex:i];
		BOOL delimiter = (flags & SWT(DRAW_DELIMITER)) != 0 && (ch == '\r' || ch == '\n');
		BOOL tab = (flags & SWT(DRAW_TAB)) != 0 && ch == '\t';
		if (i == length || delimiter || tab) {
			NSMutableAttributedString *run = [[NSMutableAttributedString alloc] initWithString:segment attributes:attributes];
			[underlines enumerateIndexesUsingBlock:^(NSUInteger index, BOOL *stop) {
				(void)stop;
				NSUInteger count = 1;
				if (index + 1 < segment.length && CFStringIsSurrogateHighCharacter([segment characterAtIndex:index])
					&& CFStringIsSurrogateLowCharacter([segment characterAtIndex:index + 1])) count = 2;
				[run addAttribute:(__bridge NSString *)kCTUnderlineStyleAttributeName value:@(kCTUnderlineStyleSingle) range:NSMakeRange(index, count)];
			}];
			CTLineRef line = CTLineCreateWithAttributedString((__bridge CFAttributedStringRef)run);
			if (line == NULL) { swtThrow(env, "java/lang/OutOfMemoryError", "CoreText line"); return CGSizeZero; }
			CGFloat advance = CTLineGetTypographicBounds(line, NULL, NULL, NULL);
			if (draw != nil) draw(line, x, y + ascent);
			CFRelease(line);
			x += advance; maximum = MAX(maximum, x);
			[segment setString:@""]; [underlines removeAllIndexes]; mnemonic = NO;
			if (delimiter) {
				x = 0; y += lineHeight;
				if (ch == '\r' && i + 1 < length && [text characterAtIndex:i + 1] == '\n') i++;
			} else if (tab) {
				x = (floor(x / tabWidth) + 1) * tabWidth; maximum = MAX(maximum, x);
			}
			continue;
		}
		if ((flags & SWT(DRAW_MNEMONIC)) != 0 && ch == '&') {
			if (i + 1 < length && [text characterAtIndex:i + 1] == '&') { i++; }
			else { mnemonic = YES; continue; }
		}
		if (mnemonic) { [underlines addIndex:segment.length]; mnemonic = NO; }
		[segment appendString:[NSString stringWithCharacters:&ch length:1]];
	}
	return CGSizeMake(ceil(maximum), ceil(y + lineHeight));
}

JNIEXPORT void JNICALL JNI(drawText)(JNI_ARGS, jlong handle, jstring value, jint x, jint y, jint flags) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return;
		NSString *text = swtString(env, value); if (text == nil) return;
		CGContextRef context = swtBegin(gc);
		if ((flags & SWT(DRAW_TRANSPARENT)) == 0) {
			CGSize extent = swtLayoutText(env, gc, text, flags, nil);
			if ((*env)->ExceptionCheck(env)) { CGContextRestoreGState(context); return; }
			CGContextFillRect(context, CGRectMake(x, y, extent.width, extent.height));
		}
		uint32_t fg = gc.foreground;
		CGContextSetRGBFillColor(context, ((fg >> 16) & 255) / 255.0, ((fg >> 8) & 255) / 255.0, (fg & 255) / 255.0, (fg >> 24) / 255.0);
		CGContextSetTextMatrix(context, CGAffineTransformMakeScale(1, -1));
		swtLayoutText(env, gc, text, flags, ^(CTLineRef line, CGFloat offsetX, CGFloat baseline) {
			CGContextSetTextPosition(context, x + offsetX, y + baseline);
			CTLineDraw(line, context);
		});
		CGContextRestoreGState(context);
	}
}
JNIEXPORT jintArray JNICALL JNI(textExtent)(JNI_ARGS, jlong handle, jstring value, jint flags) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return NULL;
		NSString *text = swtString(env, value); if (text == nil) return NULL;
		CGSize extent = swtLayoutText(env, gc, text, flags, nil);
		if ((*env)->ExceptionCheck(env)) return NULL;
		if (!isfinite(extent.width) || !isfinite(extent.height) || extent.width > INT_MAX || extent.height > INT_MAX) {
			swtThrow(env, "java/lang/IllegalArgumentException", "Text extent exceeds SWT integer coordinates"); return NULL;
		}
		jint values[] = {(jint)extent.width, (jint)extent.height};
		return swtInts(env, values, 2);
	}
}
JNIEXPORT jintArray JNICALL JNI(fontMetrics)(JNI_ARGS, jlong handle) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return NULL;
		double ascent = ceil(CTFontGetAscent(gc.font));
		double descent = ceil(CTFontGetDescent(gc.font));
		double leading = ceil(CTFontGetLeading(gc.font));
		CGSize alphabet = swtLayoutText(env, gc, @"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 0, nil);
		if ((*env)->ExceptionCheck(env)) return NULL;
		double average = ceil(alphabet.width / 52), height = ascent + descent + leading;
		if (!isfinite(ascent) || !isfinite(descent) || !isfinite(leading) || !isfinite(average)
			|| ascent < 0 || descent < 0 || leading < 0 || average < 0
			|| height > INT_MAX || average > INT_MAX) {
			swtThrow(env, "java/lang/IllegalArgumentException", "Font metrics exceed SWT integer coordinates"); return NULL;
		}
		jint values[] = {(jint)ascent, (jint)descent, (jint)average, (jint)leading, (jint)height};
		return swtInts(env, values, 5);
	}
}

JNIEXPORT void JNICALL JNI(drawImage)(JNI_ARGS, jlong handle, jlong sourceHandle,
	jint sx, jint sy, jint sw, jint sh, jint dx, jint dy, jint dw, jint dh) {
	@autoreleasepool {
		SWTUIKitGraphics *gc = swtGC(env, handle); if (gc == nil) return;
		SWTUIKitImage *source = swtImage(env, sourceHandle); if (source == nil) return;
		if (sx < 0 || sy < 0 || sw < 0 || sh < 0 || dw < 0 || dh < 0
			|| (uint64_t)(uint32_t)sx + (uint64_t)(uint32_t)sw > source.width
			|| (uint64_t)(uint32_t)sy + (uint64_t)(uint32_t)sh > source.height) {
			swtThrow(env, "java/lang/IllegalArgumentException", "Invalid source or destination image rectangle"); return;
		}
		if (sw == 0 || sh == 0 || dw == 0 || dh == 0) return;
		CGImageRef snapshot = CGBitmapContextCreateImage(source.context);
		if (snapshot == NULL) { swtThrow(env, "java/lang/OutOfMemoryError", "Source snapshot"); return; }
		CGImageRef cropped = CGImageCreateWithImageInRect(snapshot, CGRectMake(sx, sy, sw, sh));
		CGImageRelease(snapshot);
		if (cropped == NULL) { swtThrow(env, "java/lang/OutOfMemoryError", "Image crop"); return; }
		CGContextRef context = swtBegin(gc);
		CGContextTranslateCTM(context, dx, (CGFloat)dy + dh); CGContextScaleCTM(context, 1, -1);
		CGContextDrawImage(context, CGRectMake(0, 0, dw, dh), cropped);
		CGContextRestoreGState(context); CGImageRelease(cropped);
	}
}

static JNINativeMethod swtMethods[] = {
	{"connect", "(Lorg/eclipse/swt/internal/mobile/MobilePlatform$EventSink;)V", (void *)JNI(connect)},
	{"disconnect", "()V", (void *)JNI(disconnect)},
	{"checkFailure", "()V", (void *)JNI(checkFailure)},
	{"displayBounds", "()[I", (void *)JNI(displayBounds)},
	{"dpi", "()[I", (void *)JNI(dpi)},
	{"createControl", "(IJI)J", (void *)JNI(createControl)},
	{"disposeControl", "(J)V", (void *)JNI(disposeControl)},
	{"setBounds", "(JIIII)V", (void *)JNI(setBounds)},
	{"setVisible", "(JZ)V", (void *)JNI(setVisible)},
	{"setEnabled", "(JZ)V", (void *)JNI(setEnabled)},
	{"setTitle", "(JLjava/lang/String;)V", (void *)JNI(setTitle)},
	{"requestFocus", "(J)Z", (void *)JNI(requestFocus)},
	{"setAccessibility", "(JLjava/lang/String;II)V", (void *)JNI(setAccessibility)},
	{"present", "(JJ)V", (void *)JNI(present)},
	{"createImage", "(II[I)J", (void *)JNI(createImage)},
	{"disposeImage", "(J)V", (void *)JNI(disposeImage)},
	{"imagePixels", "(J)[I", (void *)JNI(imagePixels)},
	{"createGraphics", "(J)J", (void *)JNI(createGraphics)},
	{"disposeGraphics", "(J)V", (void *)JNI(disposeGraphics)},
	{"setForeground", "(JI)V", (void *)JNI(setForeground)},
	{"setBackground", "(JI)V", (void *)JNI(setBackground)},
	{"setAlpha", "(JI)V", (void *)JNI(setAlpha)},
	{"setLineWidth", "(JF)V", (void *)JNI(setLineWidth)},
	{"setAntialias", "(JZ)V", (void *)JNI(setAntialias)},
	{"setFont", "(JLjava/lang/String;FI)V", (void *)JNI(setFont)},
	{"setTransform", "(J[F)V", (void *)JNI(setTransform)},
	{"setClipping", "(JZIIII)V", (void *)JNI(setClipping)},
	{"drawLine", "(JIIII)V", (void *)JNI(drawLine)},
	{"drawShape", "(JIZIIII)V", (void *)JNI(drawShape)},
	{"drawPolygon", "(J[IZ)V", (void *)JNI(drawPolygon)},
	{"drawText", "(JLjava/lang/String;III)V", (void *)JNI(drawText)},
	{"drawImage", "(JJIIIIIIII)V", (void *)JNI(drawImage)},
	{"textExtent", "(JLjava/lang/String;I)[I", (void *)JNI(textExtent)},
	{"fontMetrics", "(J)[I", (void *)JNI(fontMetrics)}
};

jint SWTUIKitRegister(JavaVM *vm, JNIEnv *env) {
	if (vm == NULL || env == NULL || (swtVM != NULL && swtVM != vm)) return JNI_ERR;
	jclass type = (*env)->FindClass(env, "org/eclipse/swt/internal/ios/OS");
	if (type == NULL) return JNI_ERR;
	jint result = (*env)->RegisterNatives(env, type, swtMethods, (jint)(sizeof(swtMethods) / sizeof(swtMethods[0])));
	(*env)->DeleteLocalRef(env, type);
	if (result == JNI_OK) swtVM = vm;
	return result;
}

/* Optional static-library convention. The explicit bootstrap calls the same
 * registration owner, avoiding dlsym assumptions and linker dead stripping. */
JNIEXPORT jint JNICALL JNI_OnLoad_swt_ios(JavaVM *vm, void *reserved) {
	(void)reserved;
	JNIEnv *env = NULL;
	if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK) return JNI_ERR;
	return SWTUIKitRegister(vm, env) == JNI_OK ? JNI_VERSION_1_8 : JNI_ERR;
}
