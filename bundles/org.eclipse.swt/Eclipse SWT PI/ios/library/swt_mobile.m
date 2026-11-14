/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *
 * Handwritten UIKit/CoreGraphics host for OS.java's declared JNI boundary.
 * This is not SWT's generated os.c and does not embed or implement a JavaVM.
 *******************************************************************************/
#import "swt_mobile.h"
#include "org_eclipse_swt_internal_ios_OS.h"
#import <CoreText/CoreText.h>
#include <limits.h>
#include <math.h>
#include <stdint.h>

enum {
	SWT_SAVE = 1, SWT_RESTORE, SWT_TRANSLATE, SWT_CLIP,
	SWT_FILL_RECT, SWT_DRAW_RECT, SWT_FILL_OVAL, SWT_DRAW_OVAL, SWT_DRAW_LINE,
	SWT_DRAW_TEXT, SWT_FILL_ROUND_RECT, SWT_DRAW_ROUND_RECT, SWT_DRAW_ARC, SWT_FILL_ARC,
	SWT_COORDINATE_STRIDE = 8
};

@class SWTMobileHost;

@interface SWTMobileFrame : NSObject
@property(nonatomic) int width;
@property(nonatomic) int height;
@property(nonatomic, copy) NSData *operations;
@property(nonatomic, copy) NSData *colors;
@property(nonatomic, copy) NSData *lineWidths;
@property(nonatomic, copy) NSData *coordinates;
@property(nonatomic, copy) NSData *fontSizes;
@property(nonatomic, copy) NSData *fontStyles;
@property(nonatomic, copy) NSData *antialiases;
@property(nonatomic, copy) NSArray *texts;
@property(nonatomic, copy) NSArray *fonts;
- (void)drawInContext:(CGContextRef)context;
@end

@interface SWTMobileView : UIView <UIKeyInput>
@property(nonatomic, weak) SWTMobileHost *host;
@property(nonatomic, strong) SWTMobileFrame *renderFrame;
@property(nonatomic, strong) UITouch *primaryTouch;
@property(nonatomic) BOOL textInputEnabled;
@end

@interface SWTMobileHost : NSObject {
@public
	JavaVM *vm;
	jobject callback;
	jmethodID resizeMethod, pointerMethod, keyMethod, repaintMethod, suspendMethod, closeMethod, failureMethod;
}
@property(nonatomic) BOOL disposed;
@property(nonatomic) BOOL frameScheduled;
@property(nonatomic, strong) SWTMobileFrame *pendingFrame;
@property(nonatomic, strong) SWTMobileView *view;
- (void)resize:(CGSize)size;
- (void)pointer:(int)action point:(CGPoint)point buttons:(int)buttons modifiers:(int)modifiers time:(double)time;
- (void)key:(int)action code:(int)code character:(unichar)character modifiers:(int)modifiers time:(double)time;
- (void)repaint;
- (void)suspend:(BOOL)suspended;
- (void)close;
- (void)background:(NSNotification *)notification;
- (void)foreground:(NSNotification *)notification;
@end

/* Registry entries are numeric lifetime tokens, never raw pointers from Java. */
static NSMutableDictionary<NSNumber *, SWTMobileHost *> *SWTMobileHosts;
static uint64_t SWTNextHandle = 1;
static JavaVM *SWTVM;
static __weak UIView *SWTContainer;

static void SWTThrow(JNIEnv *env, const char *className, const char *message) {
	if ((*env)->ExceptionCheck(env)) return;
	jclass cls = (*env)->FindClass(env, className);
	if (cls != NULL) {
		(*env)->ThrowNew(env, cls, message);
		(*env)->DeleteLocalRef(env, cls);
	}
}

static JNIEnv *SWTEnvironment(JavaVM *vm, BOOL *attached) {
	*attached = NO;
	if (vm == NULL) return NULL;
	JNIEnv *env = NULL;
	jint status = (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
	if (status == JNI_EDETACHED) {
		if ((*vm)->AttachCurrentThread(vm, (void **)&env, NULL) != JNI_OK) return NULL;
		*attached = YES;
	} else if (status != JNI_OK) {
		return NULL;
	}
	return env;
}

static NSString *SWTString(JNIEnv *env, jstring value) {
	if (value == NULL) return nil;
	jsize length = (*env)->GetStringLength(env, value);
	const jchar *chars = (*env)->GetStringChars(env, value, NULL);
	if (chars == NULL) return nil;
	NSString *result = [[NSString alloc] initWithCharacters:(const unichar *)chars length:(NSUInteger)length];
	(*env)->ReleaseStringChars(env, value, chars);
	return result;
}

static int SWTDimension(CGFloat value) {
	if (!isfinite(value) || value <= 0) return 0;
	return (int)fmin(ceil(value), INT_MAX);
}

static int SWTCoordinate(CGFloat value) {
	if (!isfinite(value)) return 0;
	return (int)fmax(INT_MIN, fmin(floor(value), INT_MAX));
}

static jint SWTTime(double seconds) {
	if (!isfinite(seconds) || seconds < 0) return 0;
	return (jint)(uint32_t)fmod(seconds * 1000.0, 4294967296.0);
}

/* SWT key events report modifier state immediately before the key transition. */
static int SWTKeyModifiers(int action, int code, int modifiers) {
	int changed;
	switch (code) {
		case -11: changed = 1; break;
		case -12: changed = 2; break;
		case -13: changed = 4; break;
		case -14: changed = 8; break;
		default: return modifiers;
	}
	return action == 0 ? modifiers & ~changed : modifiers | changed;
}

static int SWTSpecialKey(UIKeyboardHIDUsage usage) API_AVAILABLE(ios(13.4)) {
	switch (usage) {
		case UIKeyboardHIDUsageKeyboardLeftArrow: return -1;
		case UIKeyboardHIDUsageKeyboardRightArrow: return -2;
		case UIKeyboardHIDUsageKeyboardUpArrow: return -3;
		case UIKeyboardHIDUsageKeyboardDownArrow: return -4;
		case UIKeyboardHIDUsageKeyboardHome: return -5;
		case UIKeyboardHIDUsageKeyboardEnd: return -6;
		case UIKeyboardHIDUsageKeyboardPageUp: return -7;
		case UIKeyboardHIDUsageKeyboardPageDown: return -8;
		case UIKeyboardHIDUsageKeyboardDeleteForward: return -9;
		case UIKeyboardHIDUsageKeyboardInsert: return -10;
		case UIKeyboardHIDUsageKeyboardLeftShift:
		case UIKeyboardHIDUsageKeyboardRightShift: return -11;
		case UIKeyboardHIDUsageKeyboardLeftControl:
		case UIKeyboardHIDUsageKeyboardRightControl: return -12;
		case UIKeyboardHIDUsageKeyboardLeftAlt:
		case UIKeyboardHIDUsageKeyboardRightAlt: return -13;
		case UIKeyboardHIDUsageKeyboardLeftGUI:
		case UIKeyboardHIDUsageKeyboardRightGUI: return -14;
		case UIKeyboardHIDUsageKeyboardEscape: return 27;
		case UIKeyboardHIDUsageKeyboardTab: return 9;
		default: return 0;
	}
}

static void SWTOnMain(dispatch_block_t block) {
	if ([NSThread isMainThread]) block(); else dispatch_sync(dispatch_get_main_queue(), block);
}

static SWTMobileHost *SWTLookup(JNIEnv *env, jlong handle, BOOL missingIsError) {
	SWTMobileHost *host;
	@synchronized([SWTMobileHost class]) { host = SWTMobileHosts[@(handle)]; }
	if (host == nil && missingIsError) SWTThrow(env, "java/lang/IllegalStateException", "The iOS surface is detached or disposed");
	return host;
}

/* Call only nonblocking Java queue callbacks. A local ref leases the receiver. */
static void SWTInvoke(SWTMobileHost *host, void (^call)(JNIEnv *, jobject)) {
	if (host == nil) return;
	@synchronized(host) { if (host.disposed) return; }
	BOOL attached;
	JNIEnv *env = SWTEnvironment(host->vm, &attached);
	if (env == NULL) return;
	jobject receiver = NULL;
	@synchronized(host) {
		if (!host.disposed && host->callback != NULL) receiver = (*env)->NewLocalRef(env, host->callback);
	}
	if (receiver != NULL) {
		call(env, receiver);
		if ((*env)->ExceptionCheck(env)) {
			jthrowable failure = (*env)->ExceptionOccurred(env);
			(*env)->ExceptionClear(env);
			(*env)->CallVoidMethod(env, receiver, host->failureMethod, failure);
			(*env)->DeleteLocalRef(env, failure);
			if ((*env)->ExceptionCheck(env)) {
				/* A failure handler itself failed; do not carry a pending exception into UIKit. */
				(*env)->ExceptionDescribe(env);
				(*env)->ExceptionClear(env);
			}
		}
		(*env)->DeleteLocalRef(env, receiver);
	}
	if (attached) (*host->vm)->DetachCurrentThread(host->vm);
}

static CTFontRef SWTFont(NSString *name, int pixels, int style) CF_RETURNS_RETAINED {
	NSString *resolved = name.length == 0 || [name isEqualToString:@"System"] ? @"Helvetica" : name;
	CTFontRef font = CTFontCreateWithName((__bridge CFStringRef)resolved, pixels, NULL);
	CTFontSymbolicTraits traits = 0;
	if ((style & 1) != 0) traits |= kCTFontBoldTrait;
	if ((style & 2) != 0) traits |= kCTFontItalicTrait;
	if (traits != 0) {
		CTFontRef styled = CTFontCreateCopyWithSymbolicTraits(font, pixels, NULL, traits, traits);
		if (styled != NULL) { CFRelease(font); font = styled; }
	}
	return font;
}

static CGColorRef SWTColor(uint32_t argb) CF_RETURNS_RETAINED {
	CGFloat components[] = {
		((argb >> 16) & 255) / 255.0, ((argb >> 8) & 255) / 255.0,
		(argb & 255) / 255.0, ((argb >> 24) & 255) / 255.0
	};
	CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
	CGColorRef color = CGColorCreate(space, components);
	CGColorSpaceRelease(space);
	return color;
}

static CTLineRef SWTLine(NSString *text, CTFontRef font, CGColorRef color) CF_RETURNS_RETAINED {
	NSDictionary *attributes = @{
		(__bridge NSString *)kCTFontAttributeName : (__bridge id)font,
		(__bridge NSString *)kCTForegroundColorAttributeName : (__bridge id)color
	};
	NSAttributedString *line = [[NSAttributedString alloc] initWithString:text attributes:attributes];
	return CTLineCreateWithAttributedString((__bridge CFAttributedStringRef)line);
}

static void SWTDrawArc(CGContextRef context, CGRect rect, CGFloat startDegrees, CGFloat extentDegrees, BOOL fill) {
	if (rect.size.width <= 0 || rect.size.height <= 0 || extentDegrees == 0) return;
	if (fabs(extentDegrees) >= 360) {
		if (fill) CGContextFillEllipseInRect(context, rect); else CGContextStrokeEllipseInRect(context, rect);
		return;
	}
	CGFloat cx = CGRectGetMidX(rect), cy = CGRectGetMidY(rect);
	CGAffineTransform ellipse = CGAffineTransformMake(rect.size.width / 2, 0, 0, rect.size.height / 2, cx, cy);
	CGMutablePathRef path = CGPathCreateMutable();
	if (fill) CGPathMoveToPoint(path, NULL, cx, cy);
	/* SWT positive angles turn counterclockwise from the right in y-down coordinates. */
	CGFloat start = -startDegrees * (CGFloat)M_PI / 180;
	CGFloat end = -(startDegrees + extentDegrees) * (CGFloat)M_PI / 180;
	CGPathAddArc(path, &ellipse, 0, 0, 1, start, end, extentDegrees > 0);
	if (fill) CGPathCloseSubpath(path);
	CGContextAddPath(context, path);
	if (fill) CGContextFillPath(context); else CGContextStrokePath(context);
	CGPathRelease(path);
}

@implementation SWTMobileFrame
- (void)drawInContext:(CGContextRef)context {
	const jint *ops = self.operations.bytes, *colors = self.colors.bytes;
	const jint *sizes = self.fontSizes.bytes, *styles = self.fontStyles.bytes;
	const jint *antialiases = self.antialiases.bytes;
	const jfloat *widths = self.lineWidths.bytes, *coordinates = self.coordinates.bytes;
	NSUInteger count = self.operations.length / sizeof(jint);
	CGContextSaveGState(context);
	CGContextClipToRect(context, CGRectMake(0, 0, self.width, self.height));
	for (NSUInteger i = 0; i < count; i++) {
		const jfloat *p = coordinates + i * SWT_COORDINATE_STRIDE;
		CGRect rect = CGRectMake(p[0], p[1], p[2], p[3]);
		int op = ops[i];
		if (op == SWT_SAVE) { CGContextSaveGState(context); continue; }
		if (op == SWT_RESTORE) { CGContextRestoreGState(context); continue; }
		if (op == SWT_TRANSLATE) { CGContextTranslateCTM(context, p[0], p[1]); continue; }
		if (op == SWT_CLIP) { CGContextClipToRect(context, rect); continue; }
		CGColorRef color = SWTColor((uint32_t)colors[i]);
		CGContextSetShouldAntialias(context, antialiases[i] != 0);
		CGContextSetFillColorWithColor(context, color);
		CGContextSetStrokeColorWithColor(context, color);
		CGContextSetLineWidth(context, widths[i] > 0 ? widths[i] : 1);
		switch (op) {
			case SWT_FILL_RECT: CGContextFillRect(context, rect); break;
			case SWT_DRAW_RECT: CGContextStrokeRect(context, rect); break;
			case SWT_FILL_OVAL: CGContextFillEllipseInRect(context, rect); break;
			case SWT_DRAW_OVAL: CGContextStrokeEllipseInRect(context, rect); break;
			case SWT_DRAW_LINE:
				CGContextBeginPath(context);
				CGContextMoveToPoint(context, p[0], p[1]);
				CGContextAddLineToPoint(context, p[2], p[3]);
				CGContextStrokePath(context);
				break;
			case SWT_DRAW_TEXT: {
				CTFontRef font = SWTFont(self.fonts[i], sizes[i], styles[i]);
				CGFloat lineHeight = ceil(CTFontGetAscent(font) + CTFontGetDescent(font) + CTFontGetLeading(font));
				NSArray<NSString *> *lines = [self.texts[i] componentsSeparatedByString:@"\n"];
				CGFloat y = p[1];
				for (NSString *text in lines) {
				CTLineRef line = SWTLine(text, font, color);
				CGContextSaveGState(context);
				CGContextTranslateCTM(context, p[0], y + CTFontGetAscent(font));
				CGContextScaleCTM(context, 1, -1);
				CGContextSetTextMatrix(context, CGAffineTransformIdentity);
				CGContextSetTextPosition(context, 0, 0);
				CTLineDraw(line, context);
				CGContextRestoreGState(context);
				CFRelease(line);
				y += lineHeight;
				}
				CFRelease(font);
				break;
			}
			case SWT_FILL_ROUND_RECT:
			case SWT_DRAW_ROUND_RECT: {
				CGFloat rx = fmin(fabs(p[4]) / 2, fmax(0, rect.size.width) / 2);
				CGFloat ry = fmin(fabs(p[5]) / 2, fmax(0, rect.size.height) / 2);
				CGPathRef path = CGPathCreateWithRoundedRect(rect, rx, ry, NULL);
				CGContextAddPath(context, path);
				if (op == SWT_FILL_ROUND_RECT) CGContextFillPath(context); else CGContextStrokePath(context);
				CGPathRelease(path);
				break;
			}
			case SWT_DRAW_ARC: SWTDrawArc(context, rect, p[4], p[5], NO); break;
			case SWT_FILL_ARC: SWTDrawArc(context, rect, p[4], p[5], YES); break;
			default: break; /* Input validation refuses unknown operations before publication. */
		}
		CGColorRelease(color);
	}
	CGContextRestoreGState(context);
}
@end

@implementation SWTMobileHost
- (void)resize:(CGSize)size {
	SWTInvoke(self, ^(JNIEnv *env, jobject target) {
		(*env)->CallVoidMethod(env, target, self->resizeMethod, SWTDimension(size.width), SWTDimension(size.height));
	});
}
- (void)pointer:(int)action point:(CGPoint)point buttons:(int)buttons modifiers:(int)modifiers time:(double)time {
	SWTInvoke(self, ^(JNIEnv *env, jobject target) {
		(*env)->CallVoidMethod(env, target, self->pointerMethod, action, SWTCoordinate(point.x), SWTCoordinate(point.y), buttons, modifiers, SWTTime(time));
	});
}
- (void)key:(int)action code:(int)code character:(unichar)character modifiers:(int)modifiers time:(double)time {
	int state = SWTKeyModifiers(action, code, modifiers);
	SWTInvoke(self, ^(JNIEnv *env, jobject target) {
		(*env)->CallVoidMethod(env, target, self->keyMethod, action, code, (jchar)character, state, SWTTime(time));
	});
}
- (void)repaint { SWTInvoke(self, ^(JNIEnv *e, jobject t) { (*e)->CallVoidMethod(e, t, self->repaintMethod); }); }
- (void)suspend:(BOOL)value { SWTInvoke(self, ^(JNIEnv *e, jobject t) { (*e)->CallVoidMethod(e, t, self->suspendMethod, (jboolean)value); }); }
- (void)close { SWTInvoke(self, ^(JNIEnv *e, jobject t) { (*e)->CallVoidMethod(e, t, self->closeMethod); }); }
- (void)background:(NSNotification *)notification { (void)notification; [self suspend:YES]; }
- (void)foreground:(NSNotification *)notification { (void)notification; [self suspend:NO]; [self repaint]; }
@end

static int SWTModifiers(UIEvent *event) {
	if (@available(iOS 13.4, *)) {
		UIKeyModifierFlags flags = event.modifierFlags;
		return ((flags & UIKeyModifierShift) ? 1 : 0) | ((flags & UIKeyModifierControl) ? 2 : 0) |
			((flags & UIKeyModifierAlternate) ? 4 : 0) | ((flags & UIKeyModifierCommand) ? 8 : 0);
	}
	return 0;
}

@implementation SWTMobileView
- (instancetype)initWithFrame:(CGRect)frame {
	self = [super initWithFrame:frame];
	if (self != nil) { self.opaque = NO; self.multipleTouchEnabled = YES; self.contentMode = UIViewContentModeRedraw; }
	return self;
}
- (void)layoutSubviews {
	[super layoutSubviews];
	[self.host resize:self.bounds.size];
	[self.host repaint];
}
- (void)didMoveToWindow { [super didMoveToWindow]; if (self.window != nil) [self.host repaint]; }
- (void)drawRect:(CGRect)rect {
	(void)rect;
	CGContextRef context = UIGraphicsGetCurrentContext();
	if (context == NULL) return;
	CGContextClearRect(context, self.bounds);
	SWTMobileFrame *frame = self.renderFrame;
	if (frame != nil && frame.width == SWTDimension(self.bounds.size.width) && frame.height == SWTDimension(self.bounds.size.height)) {
		[frame drawInContext:context];
	}
}
- (void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
	if (self.primaryTouch != nil) return;
	self.primaryTouch = touches.anyObject;
	if (self.primaryTouch != nil) [self.host pointer:0 point:[self.primaryTouch locationInView:self] buttons:0 modifiers:SWTModifiers(event) time:event.timestamp];
}
- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
	if ([touches containsObject:self.primaryTouch]) [self.host pointer:1 point:[self.primaryTouch locationInView:self] buttons:1 modifiers:SWTModifiers(event) time:event.timestamp];
}
- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
	if ([touches containsObject:self.primaryTouch]) {
		[self.host pointer:2 point:[self.primaryTouch locationInView:self] buttons:1 modifiers:SWTModifiers(event) time:event.timestamp];
		self.primaryTouch = nil;
	}
}
- (void)touchesCancelled:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
	if ([touches containsObject:self.primaryTouch]) {
		[self.host pointer:3 point:[self.primaryTouch locationInView:self] buttons:0 modifiers:SWTModifiers(event) time:event.timestamp];
		self.primaryTouch = nil;
	}
}
- (BOOL)canBecomeFirstResponder { return self.textInputEnabled; }
- (BOOL)hasText { return YES; /* The authoritative SWT text owner receives delete intent. */ }
- (void)insertText:(NSString *)text {
	for (NSUInteger i = 0; i < text.length; i++) {
		unichar ch = [text characterAtIndex:i];
		[self.host key:0 code:0 character:ch modifiers:0 time:NSProcessInfo.processInfo.systemUptime];
		[self.host key:1 code:0 character:ch modifiers:0 time:NSProcessInfo.processInfo.systemUptime];
	}
}
- (void)deleteBackward {
	[self.host key:0 code:8 character:8 modifiers:0 time:NSProcessInfo.processInfo.systemUptime];
	[self.host key:1 code:8 character:8 modifiers:0 time:NSProcessInfo.processInfo.systemUptime];
}
- (int)specialKey:(UIKey *)key API_AVAILABLE(ios(13.4)) {
	return SWTSpecialKey(key.keyCode);
}
- (void)pressesBegan:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event {
	NSMutableSet *unhandled = [presses mutableCopy];
	if (@available(iOS 13.4, *)) {
		for (UIPress *press in presses) {
			int code = press.key == nil ? 0 : [self specialKey:press.key];
			if (code != 0) {
				[self.host key:0 code:code character:code > 0 ? (unichar)code : 0 modifiers:SWTModifiers(event) time:event.timestamp];
				[unhandled removeObject:press];
			}
		}
	}
	if (unhandled.count != 0) [super pressesBegan:unhandled withEvent:event];
}
- (void)pressesEnded:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event {
	NSMutableSet *unhandled = [presses mutableCopy];
	if (@available(iOS 13.4, *)) {
		for (UIPress *press in presses) {
			int code = press.key == nil ? 0 : [self specialKey:press.key];
			if (code != 0) {
				[self.host key:1 code:code character:code > 0 ? (unichar)code : 0 modifiers:SWTModifiers(event) time:event.timestamp];
				[unhandled removeObject:press];
			}
		}
	}
	if (unhandled.count != 0) [super pressesEnded:unhandled withEvent:event];
}
@end

static void SWTDisposeHost(JNIEnv *env, SWTMobileHost *host) {
	if (host == nil) return;
	@synchronized(host) {
		if (host.disposed) return;
		host.disposed = YES;
		host.pendingFrame = nil;
		if (host->callback != NULL) { (*env)->DeleteGlobalRef(env, host->callback); host->callback = NULL; }
	}
	dispatch_async(dispatch_get_main_queue(), ^{
		[NSNotificationCenter.defaultCenter removeObserver:host];
		[host.view resignFirstResponder];
		host.view.primaryTouch = nil;
		host.view.renderFrame = nil;
		host.view.host = nil;
		[host.view removeFromSuperview];
		host.view = nil;
	});
}

JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_ios_OS_create(JNIEnv *env, jclass cls, jobject platform) {
	@autoreleasepool {
	(void)cls;
	if (platform == NULL) { SWTThrow(env, "java/lang/NullPointerException", "platform"); return 0; }
	JavaVM *vm;
	if ((*env)->GetJavaVM(env, &vm) != JNI_OK) { SWTThrow(env, "java/lang/IllegalStateException", "Cannot locate the embedding JavaVM"); return 0; }
	SWTMobileHost *host = [SWTMobileHost new];
	host->vm = vm;
	jclass type = (*env)->GetObjectClass(env, platform);
	if (type == NULL) return 0;
#define SWT_BIND_CALLBACK(field, name, signature) \
	do { host->field = (*env)->GetMethodID(env, type, name, signature); \
		if (host->field == NULL) { (*env)->DeleteLocalRef(env, type); return 0; } } while (0)
	SWT_BIND_CALLBACK(resizeMethod, "nativeResize", "(II)V");
	SWT_BIND_CALLBACK(pointerMethod, "nativePointer", "(IIIIII)V");
	SWT_BIND_CALLBACK(keyMethod, "nativeKey", "(IICII)V");
	SWT_BIND_CALLBACK(repaintMethod, "nativeRepaint", "()V");
	SWT_BIND_CALLBACK(suspendMethod, "nativeSuspend", "(Z)V");
	SWT_BIND_CALLBACK(closeMethod, "nativeClose", "()V");
	SWT_BIND_CALLBACK(failureMethod, "nativeFailure", "(Ljava/lang/Throwable;)V");
#undef SWT_BIND_CALLBACK
	(*env)->DeleteLocalRef(env, type);
	if ((*env)->ExceptionCheck(env)) return 0;
	host->callback = (*env)->NewGlobalRef(env, platform);
	if (host->callback == NULL) return 0;
	__block jlong handle = 0;
	__block NSString *problem = nil;
	SWTOnMain(^{
		@synchronized([SWTMobileHost class]) {
			if (SWTVM != vm || SWTContainer == nil) { problem = @"Call SWTIOSAttach with this VM and a live UIView first"; return; }
			if (SWTMobileHosts.count != 0) { problem = @"An iOS root Display is already attached"; return; }
			if (SWTNextHandle > INT64_MAX) { problem = @"iOS surface lifetime tokens exhausted"; return; }
			handle = (jlong)SWTNextHandle++;
			SWTMobileView *view = [[SWTMobileView alloc] initWithFrame:SWTContainer.bounds];
			view.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
			view.host = host;
			host.view = view;
			[SWTContainer addSubview:view];
			SWTMobileHosts[@(handle)] = host;
			[NSNotificationCenter.defaultCenter addObserver:host selector:@selector(background:) name:UIApplicationDidEnterBackgroundNotification object:nil];
			[NSNotificationCenter.defaultCenter addObserver:host selector:@selector(foreground:) name:UIApplicationWillEnterForegroundNotification object:nil];
		}
		[host resize:host.view.bounds.size];
		[host suspend:UIApplication.sharedApplication.applicationState == UIApplicationStateBackground];
	});
	if (handle == 0) {
		(*env)->DeleteGlobalRef(env, host->callback); host->callback = NULL;
		SWTThrow(env, "java/lang/IllegalStateException", problem.UTF8String ?: "Cannot create UIKit surface");
	}
	return handle;
	}
}

static NSData *SWTIntLane(JNIEnv *env, jintArray array) {
	jsize count = (*env)->GetArrayLength(env, array);
	NSMutableData *bytes = [NSMutableData dataWithLength:(NSUInteger)count * sizeof(jint)];
	if (bytes == nil) { SWTThrow(env, "java/lang/OutOfMemoryError", "Native integer frame lane"); return nil; }
	if (count != 0) (*env)->GetIntArrayRegion(env, array, 0, count, bytes.mutableBytes);
	return (*env)->ExceptionCheck(env) ? nil : bytes;
}

static NSData *SWTFloatLane(JNIEnv *env, jfloatArray array) {
	jsize count = (*env)->GetArrayLength(env, array);
	NSMutableData *bytes = [NSMutableData dataWithLength:(NSUInteger)count * sizeof(jfloat)];
	if (bytes == nil) { SWTThrow(env, "java/lang/OutOfMemoryError", "Native coordinate frame lane"); return nil; }
	if (count != 0) (*env)->GetFloatArrayRegion(env, array, 0, count, bytes.mutableBytes);
	return (*env)->ExceptionCheck(env) ? nil : bytes;
}

JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_ios_OS_submit(JNIEnv *env, jclass cls, jlong handle, jint width, jint height,
	jintArray operations, jintArray colors, jfloatArray lineWidths, jfloatArray coordinates,
	jobjectArray texts, jobjectArray fonts, jintArray fontSizes, jintArray fontStyles, jintArray antialiases) {
	@autoreleasepool {
	(void)cls;
	SWTMobileHost *host = SWTLookup(env, handle, YES);
	if (host == nil) return;
	if (operations == NULL || colors == NULL || lineWidths == NULL || coordinates == NULL || texts == NULL || fonts == NULL || fontSizes == NULL || fontStyles == NULL || antialiases == NULL) {
		SWTThrow(env, "java/lang/NullPointerException", "A frame lane is null"); return;
	}
	jsize count = (*env)->GetArrayLength(env, operations);
	if (width < 0 || height < 0 || count > INT_MAX / SWT_COORDINATE_STRIDE ||
		(*env)->GetArrayLength(env, colors) != count || (*env)->GetArrayLength(env, lineWidths) != count ||
		(*env)->GetArrayLength(env, coordinates) != count * SWT_COORDINATE_STRIDE || (*env)->GetArrayLength(env, texts) != count ||
		(*env)->GetArrayLength(env, fonts) != count || (*env)->GetArrayLength(env, fontSizes) != count || (*env)->GetArrayLength(env, fontStyles) != count || (*env)->GetArrayLength(env, antialiases) != count) {
		SWTThrow(env, "java/lang/IllegalArgumentException", "Inconsistent frame lane lengths or bounds"); return;
	}
	SWTMobileFrame *frame = [SWTMobileFrame new];
	frame.width = width; frame.height = height;
	frame.operations = SWTIntLane(env, operations); if ((*env)->ExceptionCheck(env)) return;
	frame.colors = SWTIntLane(env, colors); if ((*env)->ExceptionCheck(env)) return;
	frame.lineWidths = SWTFloatLane(env, lineWidths); if ((*env)->ExceptionCheck(env)) return;
	frame.coordinates = SWTFloatLane(env, coordinates); if ((*env)->ExceptionCheck(env)) return;
	frame.fontSizes = SWTIntLane(env, fontSizes); if ((*env)->ExceptionCheck(env)) return;
	frame.fontStyles = SWTIntLane(env, fontStyles); if ((*env)->ExceptionCheck(env)) return;
	frame.antialiases = SWTIntLane(env, antialiases); if ((*env)->ExceptionCheck(env)) return;
	NSMutableArray *textValues = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
	NSMutableArray *fontValues = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
	const jint *ops = frame.operations.bytes, *sizes = frame.fontSizes.bytes;
	const jint *aa = frame.antialiases.bytes;
	const jfloat *points = frame.coordinates.bytes, *widths = frame.lineWidths.bytes;
	int depth = 0;
	for (jsize i = 0; i < count; i++) {
		if (ops[i] < SWT_SAVE || ops[i] > SWT_FILL_ARC || !isfinite(widths[i]) || widths[i] < 0 || aa[i] < -1 || aa[i] > 1) {
			SWTThrow(env, "java/lang/IllegalArgumentException", "Invalid frame operation or line width"); return;
		}
		for (int j = 0; j < SWT_COORDINATE_STRIDE; j++) {
			if (!isfinite(points[(NSUInteger)i * SWT_COORDINATE_STRIDE + j])) {
				SWTThrow(env, "java/lang/IllegalArgumentException", "A frame coordinate is not finite"); return;
			}
		}
		if (ops[i] == SWT_SAVE) depth++;
		if (ops[i] == SWT_RESTORE && --depth < 0) { SWTThrow(env, "java/lang/IllegalArgumentException", "Unbalanced frame restore"); return; }
		if (ops[i] == SWT_DRAW_TEXT) {
			jstring text = (jstring)(*env)->GetObjectArrayElement(env, texts, i);
			if ((*env)->ExceptionCheck(env)) return;
			jstring font = (jstring)(*env)->GetObjectArrayElement(env, fonts, i);
			if ((*env)->ExceptionCheck(env)) { if (text != NULL) (*env)->DeleteLocalRef(env, text); return; }
			if (text == NULL || font == NULL || sizes[i] <= 0) {
				if (text != NULL) (*env)->DeleteLocalRef(env, text);
				if (font != NULL) (*env)->DeleteLocalRef(env, font);
				SWTThrow(env, "java/lang/IllegalArgumentException", "Invalid text frame operation"); return;
			}
			NSString *textValue = SWTString(env, text);
			NSString *fontValue = (*env)->ExceptionCheck(env) ? nil : SWTString(env, font);
			(*env)->DeleteLocalRef(env, text); (*env)->DeleteLocalRef(env, font);
			if ((*env)->ExceptionCheck(env)) return;
			if (textValue == nil || fontValue == nil) { SWTThrow(env, "java/lang/OutOfMemoryError", "Native frame string"); return; }
			[textValues addObject:textValue]; [fontValues addObject:fontValue];
		} else {
			[textValues addObject:NSNull.null]; [fontValues addObject:NSNull.null];
		}
	}
	if (depth != 0) { SWTThrow(env, "java/lang/IllegalArgumentException", "Unbalanced frame save"); return; }
	frame.texts = textValues; frame.fonts = fontValues;
	@synchronized(host) {
		if (host.disposed) return;
		host.pendingFrame = frame;
		if (host.frameScheduled) return;
		host.frameScheduled = YES;
	}
	/* At most one presentation block and one pending immutable frame are retained. */
	dispatch_async(dispatch_get_main_queue(), ^{
		@synchronized(host) {
			host.frameScheduled = NO;
			if (host.disposed) return;
			host.view.renderFrame = host.pendingFrame;
			host.pendingFrame = nil;
			[host.view setNeedsDisplay];
		}
	});
	}
}

JNIEXPORT jintArray JNICALL Java_org_eclipse_swt_internal_ios_OS_measureText(JNIEnv *env, jclass cls, jstring text, jstring name, jint pixels, jint style) {
	@autoreleasepool {
	(void)cls;
	if (text == NULL || name == NULL) { SWTThrow(env, "java/lang/NullPointerException", "text/fontName"); return NULL; }
	if (pixels <= 0) { SWTThrow(env, "java/lang/IllegalArgumentException", "Font pixel size must be positive"); return NULL; }
	NSString *value = SWTString(env, text);
	if ((*env)->ExceptionCheck(env)) return NULL;
	NSString *fontName = SWTString(env, name);
	if ((*env)->ExceptionCheck(env)) return NULL;
	if (value == nil || fontName == nil) { SWTThrow(env, "java/lang/OutOfMemoryError", "Native text measurement string"); return NULL; }
	CTFontRef font = SWTFont(fontName, pixels, style);
	CGColorRef color = SWTColor(0xff000000U);
	NSArray<NSString *> *lines = [value componentsSeparatedByString:@"\n"];
	CGFloat widest = 0;
	for (NSString *textLine in lines) {
		CTLineRef line = SWTLine(textLine, font, color);
		widest = fmax(widest, (CGFloat)CTLineGetTypographicBounds(line, NULL, NULL, NULL));
		CFRelease(line);
	}
	CGFloat lineHeight = ceil(CTFontGetAscent(font) + CTFontGetDescent(font) + CTFontGetLeading(font));
	jint metrics[] = {
		SWTDimension(widest),
		SWTDimension(lineHeight * lines.count),
		SWTDimension(CTFontGetAscent(font))
	};
	CGColorRelease(color); CFRelease(font);
	jintArray result = (*env)->NewIntArray(env, 3);
	if (result != NULL) (*env)->SetIntArrayRegion(env, result, 0, 3, metrics);
	return result;
	}
}

JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_ios_OS_setVisible(JNIEnv *env, jclass cls, jlong handle, jboolean visible) {
	@autoreleasepool {
	(void)cls;
	SWTMobileHost *host = SWTLookup(env, handle, YES);
	if (host == nil) return;
	dispatch_async(dispatch_get_main_queue(), ^{
		@synchronized(host) { if (host.disposed) return; host.view.hidden = !visible; }
		if (visible) [host repaint];
	});
	}
}

JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_ios_OS_setTextInputEnabled(JNIEnv *env, jclass cls, jlong handle, jboolean enabled) {
	@autoreleasepool {
	(void)cls;
	SWTMobileHost *host = SWTLookup(env, handle, YES);
	if (host == nil) return;
	dispatch_async(dispatch_get_main_queue(), ^{
		@synchronized(host) {
			if (host.disposed) return;
			host.view.textInputEnabled = enabled;
			if (enabled) [host.view becomeFirstResponder]; else [host.view resignFirstResponder];
		}
	});
	}
}

JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_ios_OS_dispose(JNIEnv *env, jclass cls, jlong handle) {
	@autoreleasepool {
	(void)cls;
	SWTMobileHost *host;
	@synchronized([SWTMobileHost class]) {
		host = SWTMobileHosts[@(handle)];
		[SWTMobileHosts removeObjectForKey:@(handle)];
	}
	SWTDisposeHost(env, host);
	}
}

static BOOL SWTRegister(JNIEnv *env) {
	jclass type = (*env)->FindClass(env, "org/eclipse/swt/internal/ios/OS");
	if (type == NULL) return NO;
	JNINativeMethod methods[] = {
		{ "create", "(Lorg/eclipse/swt/internal/mobile/IOSPlatform;)J", (void *)Java_org_eclipse_swt_internal_ios_OS_create },
		{ "submit", "(JII[I[I[F[F[Ljava/lang/String;[Ljava/lang/String;[I[I[I)V", (void *)Java_org_eclipse_swt_internal_ios_OS_submit },
		{ "measureText", "(Ljava/lang/String;Ljava/lang/String;II)[I", (void *)Java_org_eclipse_swt_internal_ios_OS_measureText },
		{ "setVisible", "(JZ)V", (void *)Java_org_eclipse_swt_internal_ios_OS_setVisible },
		{ "setTextInputEnabled", "(JZ)V", (void *)Java_org_eclipse_swt_internal_ios_OS_setTextInputEnabled },
		{ "dispose", "(J)V", (void *)Java_org_eclipse_swt_internal_ios_OS_dispose }
	};
	jint result = (*env)->RegisterNatives(env, type, methods, (jint)(sizeof(methods) / sizeof(methods[0])));
	(*env)->DeleteLocalRef(env, type);
	return result == JNI_OK && !(*env)->ExceptionCheck(env);
}

BOOL SWTIOSAttach(JavaVM *vm, UIView *container, NSError **error) {
	NSString *problem = nil;
	if (![NSThread isMainThread]) problem = @"SWTIOSAttach must run on UIKit's main thread";
	else if (vm == NULL || container == nil) problem = @"A live JavaVM and UIView container are required";
	else {
		@synchronized([SWTMobileHost class]) {
			if (SWTVM != NULL) problem = @"SWTIOSDetach is required before reattaching";
		}
	}
	BOOL attached = NO;
	JNIEnv *env = problem == nil ? SWTEnvironment(vm, &attached) : NULL;
	if (problem == nil && env == NULL) problem = @"Cannot attach UIKit's thread to the supplied JavaVM";
	if (problem == nil && !SWTRegister(env)) {
		problem = @"Cannot register SWT OS methods; add the iOS SWT classes to the embedding VM class path";
		if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionDescribe(env); (*env)->ExceptionClear(env); }
	}
	if (attached) (*vm)->DetachCurrentThread(vm);
	if (problem != nil) {
		if (error != NULL) *error = [NSError errorWithDomain:@"org.eclipse.swt.ios" code:1 userInfo:@{NSLocalizedDescriptionKey:problem}];
		return NO;
	}
	@synchronized([SWTMobileHost class]) {
		SWTVM = vm; SWTContainer = container;
		if (SWTMobileHosts == nil) SWTMobileHosts = [NSMutableDictionary new];
	}
	return YES;
}

void SWTIOSDetach(void) {
	NSCAssert([NSThread isMainThread], @"SWTIOSDetach must run on UIKit's main thread");
	NSArray<SWTMobileHost *> *hosts;
	JavaVM *vm;
	@synchronized([SWTMobileHost class]) {
		vm = SWTVM; hosts = SWTMobileHosts.allValues;
		[SWTMobileHosts removeAllObjects];
		SWTVM = NULL; SWTContainer = nil;
	}
	BOOL attached;
	JNIEnv *env = SWTEnvironment(vm, &attached);
	if (env == NULL) return;
	for (SWTMobileHost *host in hosts) { [host close]; SWTDisposeHost(env, host); }
	if (attached) (*vm)->DetachCurrentThread(vm);
}
