/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *
 * Runs against real CoreGraphics/CoreText on an iOS simulator. This tests native
 * frame presentation; it does not pretend to execute a JavaVM or SWT widgets.
 *******************************************************************************/
#import "swt_mobile.m"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum { TEST_SIZE = 64, TEST_BYTES = TEST_SIZE * TEST_SIZE * 4 };
static int checks;

static void check(BOOL result, const char *description) {
	checks++;
	if (!result) { fprintf(stderr, "FAIL: %s\n", description); exit(1); }
}

static CGContextRef context(unsigned char *bytes) {
	CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
	CGContextRef result = CGBitmapContextCreate(bytes, TEST_SIZE, TEST_SIZE, 8, TEST_SIZE * 4,
		colorSpace, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
	CGColorSpaceRelease(colorSpace);
	check(result != NULL, "real bitmap context");
	CGContextTranslateCTM(result, 0, TEST_SIZE);
	CGContextScaleCTM(result, 1, -1);
	CGContextSetShouldAntialias(result, NO);
	return result;
}

static SWTMobileFrame *frame(const jint *ops, const jfloat *points, int count) {
	SWTMobileFrame *f = [SWTMobileFrame new];
	f.width = TEST_SIZE; f.height = TEST_SIZE;
	f.operations = [NSData dataWithBytes:ops length:(NSUInteger)count * sizeof(jint)];
	f.coordinates = [NSData dataWithBytes:points length:(NSUInteger)count * SWT_COORDINATE_STRIDE * sizeof(jfloat)];
	NSMutableData *colors = [NSMutableData dataWithLength:(NSUInteger)count * sizeof(jint)];
	NSMutableData *widths = [NSMutableData dataWithLength:(NSUInteger)count * sizeof(jfloat)];
	NSMutableData *sizes = [NSMutableData dataWithLength:(NSUInteger)count * sizeof(jint)];
	NSMutableData *styles = [NSMutableData dataWithLength:(NSUInteger)count * sizeof(jint)];
	NSMutableData *antialiases = [NSMutableData dataWithLength:(NSUInteger)count * sizeof(jint)];
	NSMutableArray *text = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
	NSMutableArray *fonts = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
	for (int i = 0; i < count; i++) {
		((jint *)colors.mutableBytes)[i] = (jint)0xffff0000;
		((jfloat *)widths.mutableBytes)[i] = 1;
		((jint *)sizes.mutableBytes)[i] = 14;
		[text addObject:@"SWT\niOS"];
		[fonts addObject:@"Helvetica"];
	}
	f.colors = colors; f.lineWidths = widths; f.fontSizes = sizes; f.fontStyles = styles;
	f.antialiases = antialiases;
	f.texts = text; f.fonts = fonts;
	return f;
}

static unsigned countOpaque(const unsigned char *bytes) {
	unsigned count = 0;
	for (int i = 0; i < TEST_SIZE * TEST_SIZE; i++) if (bytes[i * 4 + 3] != 0) count++;
	return count;
}

static void drawExpectedRect(CGContextRef ctx, CGRect rect) {
	CGContextSetRGBFillColor(ctx, 1, 0, 0, 1);
	CGContextFillRect(ctx, rect);
}

static void testClipAndRestore(void) {
	unsigned char actual[TEST_BYTES] = {0}, expected[TEST_BYTES] = {0};
	CGContextRef a = context(actual), b = context(expected);
	jint ops[] = {SWT_SAVE, SWT_TRANSLATE, SWT_CLIP, SWT_FILL_RECT, SWT_RESTORE, SWT_FILL_RECT};
	jfloat p[6 * SWT_COORDINATE_STRIDE] = {
		0,0,0,0,0,0,0,0,
		10,10,0,0,0,0,0,0,
		0,0,8,8,0,0,0,0,
		4,2,10,10,0,0,0,0,
		0,0,0,0,0,0,0,0,
		40,40,3,3,0,0,0,0
	};
	[frame(ops, p, 6) drawInContext:a];
	drawExpectedRect(b, CGRectMake(14,12,4,6));
	drawExpectedRect(b, CGRectMake(40,40,3,3));
	check(memcmp(actual, expected, TEST_BYTES) == 0, "translation, clip intersection and graphics restore");
	check(countOpaque(actual) == 33, "exact clipped area");
	CGContextRelease(a); CGContextRelease(b);
}

static void testArc(CGFloat sweep, CGRect quadrant) {
	unsigned char actual[TEST_BYTES] = {0}, mask[TEST_BYTES] = {0};
	CGContextRef a = context(actual), b = context(mask);
	jint ops[] = {SWT_FILL_ARC};
	jfloat p[] = {8,8,32,32,0,(jfloat)sweep,0,0};
	[frame(ops,p,1) drawInContext:a];
	drawExpectedRect(b,quadrant);
	unsigned area = countOpaque(actual);
	check(area > 180 && area < 220, "quarter ellipse has nonzero expected area");
	for (int i = 0; i < TEST_SIZE * TEST_SIZE; i++) {
		check(actual[i*4+3] == 0 || mask[i*4+3] != 0, "SWT arc direction stays in expected quadrant");
	}
	CGContextRelease(a); CGContextRelease(b);
}

static void testFullEllipse(void) {
	unsigned char actual[TEST_BYTES] = {0}, expected[TEST_BYTES] = {0};
	CGContextRef a = context(actual), b = context(expected);
	jint ops[] = {SWT_FILL_ARC};
	jfloat p[] = {8,8,32,24,19,720,0,0};
	[frame(ops,p,1) drawInContext:a];
	CGContextSetRGBFillColor(b,1,0,0,1);
	CGContextFillEllipseInRect(b,CGRectMake(8,8,32,24));
	check(memcmp(actual,expected,TEST_BYTES) == 0, "full arc extent draws a complete ellipse");
	CGContextRelease(a); CGContextRelease(b);
}

static void testText(void) {
	unsigned char actual[TEST_BYTES] = {0};
	CGContextRef ctx = context(actual);
	jint ops[] = {SWT_DRAW_TEXT};
	jfloat p[] = {2,2,0,0,0,0,0,0};
	[frame(ops,p,1) drawInContext:ctx];
	check(countOpaque(actual) > 40, "real CoreText multi-line rasterization is nonblank");
	CTFontRef font = SWTFont(@"Helvetica",14,3);
	check(font != NULL && CTFontGetAscent(font) > 0 && CTFontGetDescent(font) >= 0, "real CoreText styled font metrics");
	CFRelease(font); CGContextRelease(ctx);
}

static void testModifierKeys(void) {
	UIKeyboardHIDUsage left[] = {UIKeyboardHIDUsageKeyboardLeftShift, UIKeyboardHIDUsageKeyboardLeftControl,
		UIKeyboardHIDUsageKeyboardLeftAlt, UIKeyboardHIDUsageKeyboardLeftGUI};
	UIKeyboardHIDUsage right[] = {UIKeyboardHIDUsageKeyboardRightShift, UIKeyboardHIDUsageKeyboardRightControl,
		UIKeyboardHIDUsageKeyboardRightAlt, UIKeyboardHIDUsageKeyboardRightGUI};
	for (int i = 0; i < 4; i++) {
		int code = -11 - i, bit = 1 << i;
		check(SWTSpecialKey(left[i]) == code && SWTSpecialKey(right[i]) == code, "left/right UIKit modifier key mapping");
		check(SWTKeyModifiers(0, code, bit) == 0, "SWT KeyDown excludes just-pressed modifier");
		check(SWTKeyModifiers(1, code, 0) == bit, "SWT KeyUp includes just-released modifier");
		check(SWTKeyModifiers(0, code, 15) == (15 & ~bit), "KeyDown preserves other held modifiers");
		check(SWTKeyModifiers(1, code, 15 & ~bit) == 15, "KeyUp preserves other held modifiers");
	}
	check(SWTKeyModifiers(0, 65, 5) == 5 && SWTKeyModifiers(1, 65, 5) == 5, "ordinary keys preserve modifier state");
}

int main(void) {
	@autoreleasepool {
		testModifierKeys();
		testClipAndRestore();
		testArc(90,CGRectMake(24,8,16,16));
		testArc(-90,CGRectMake(24,24,16,16));
		testFullEllipse();
		testText();
		printf("SWT_IOS_NATIVE_RENDER_PASS checks=%d\n",checks);
	}
	return 0;
}
