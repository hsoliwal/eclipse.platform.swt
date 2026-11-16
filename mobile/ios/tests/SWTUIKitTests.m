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
#import "SWTUIKit.m"
#include <stdio.h>

enum { TEST_SIZE = 64, TEST_BYTES = TEST_SIZE * TEST_SIZE * 4 };
static int checks;

static void check(BOOL result, const char *description) {
	checks++;
	if (!result) { fprintf(stderr, "FAIL: %s\n", description); exit(1); }
}

static SWTUIKitGraphics *graphics(void) {
	SWTUIKitImage *image = [SWTUIKitImage new];
	image.width = TEST_SIZE; image.height = TEST_SIZE;
	CGColorSpaceRef colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
	check(colorSpace != NULL, "real sRGB color space");
	image.context = CGBitmapContextCreate(NULL, TEST_SIZE, TEST_SIZE, 8, TEST_SIZE * 4,
		colorSpace, kCGBitmapByteOrder32Little | kCGImageAlphaPremultipliedFirst);
	CGColorSpaceRelease(colorSpace);
	check(image.context != NULL, "real CoreGraphics bitmap allocation");
	memset(CGBitmapContextGetData(image.context), 0, TEST_BYTES);
	CGContextTranslateCTM(image.context, 0, TEST_SIZE);
	CGContextScaleCTM(image.context, 1, -1);
	SWTUIKitGraphics *gc = [SWTUIKitGraphics new];
	gc.image = image; gc.foreground = 0xff0000ff; gc.background = 0xffff0000;
	gc.alpha = 1; gc.lineWidth = 1; gc.transform = CGAffineTransformIdentity;
	gc.antialias = NO; gc.textAntialias = YES;
	gc.font = CTFontCreateWithName(CFSTR("Helvetica"), 14, NULL);
	check(gc.font != NULL, "real CoreText font");
	return gc;
}

static const uint32_t *pixels(SWTUIKitGraphics *gc) {
	CGContextFlush(gc.image.context);
	return CGBitmapContextGetData(gc.image.context);
}

static unsigned area(SWTUIKitGraphics *gc) {
	unsigned result = 0;
	const uint32_t *data = pixels(gc);
	for (int i = 0; i < TEST_SIZE * TEST_SIZE; i++) if ((data[i] >> 24) != 0) result++;
	return result;
}

static void expectedRect(SWTUIKitGraphics *gc, CGRect rectangle) {
	CGContextRef context = swtBegin(gc);
	CGContextFillRect(context, rectangle);
	CGContextRestoreGState(context);
}

static void testNativeClientGeometry(void) {
	SWTUIKitView *parent = [[SWTUIKitView alloc] initWithFrame:CGRectMake(10, 20, 60, 40)];
	parent.style = SWT(BORDER);
	[parent layoutSubviews];
	check(CGRectEqualToRect(parent.clientView.frame, CGRectMake(1, 1, 58, 38)), "native client container is inset by the SWT border");
	check(parent.clientView.clipsToBounds, "native child pixels are clipped to their parent's client area");
	SWTUIKitView *child = [[SWTUIKitView alloc] initWithFrame:CGRectMake(3, 4, 20, 10)];
	child.style = SWT(BORDER);
	[parent.clientView addSubview:child];
	[child layoutSubviews];
	check(CGPointEqualToPoint([child convertPoint:CGPointMake(1, 1) toView:parent], CGPointMake(5, 6)),
		"nested native client origin includes parent and child borders");
	check(CGPointEqualToPoint(swtClientPoint(CGPointMake(7, 9), child.style), CGPointMake(6, 8)),
		"pointer coordinates use the actual native client origin");
	SWTUIKitGraphics *gc = graphics();
	CGImageRef snapshot = CGBitmapContextCreateImage(gc.image.context);
	check(snapshot != NULL, "native presentation snapshot");
	parent.snapshot = snapshot;
	parent.frame = CGRectMake(10, 20, 2, 2); [parent layoutSubviews];
	check(parent.snapshot == NULL && CGRectIsEmpty(parent.clientView.bounds), "empty client releases its retained native snapshot");
	parent.snapshot = snapshot;
	check(parent.snapshot == NULL, "late presentation cannot reinstall a snapshot after native client becomes empty");
	parent.frame = CGRectMake(10, 20, 3, 3); [parent layoutSubviews];
	parent.snapshot = snapshot;
	check(parent.snapshot != NULL, "positive client accepts a new native presentation after an empty extent");
	CGImageRelease(snapshot);
	swtDrawBorder(gc.image.context, CGRectMake(8, 8, 10, 10), SWT(BORDER));
	check(area(gc) == 36, "native border is four non-overlapping one-point bands");
	const uint32_t *data = pixels(gc);
	for (int i = 0; i < TEST_SIZE * TEST_SIZE; i++)
		check((data[i] >> 24) == 0 || data[i] == 0xff808080, "native border uses SWT widget normal shadow");
}

static void testClipAndRoundRectangle(void) {
	SWTUIKitGraphics *actual = graphics(), *expected = graphics();
	actual.transform = CGAffineTransformMakeTranslation(10, 10);
	actual.clipped = YES; actual.clip = CGRectMake(0, 0, 8, 8);
	check(CGRectEqualToRect(swtClipBounds(actual), CGRectMake(0, 0, 8, 8)), "effective native clipping in user coordinates");
	check(swtDrawRoundRectangle(actual, YES, CGRectMake(4, 2, 10, 10), 0, 0), "zero-radius rounded rectangle");
	expectedRect(expected, CGRectMake(14, 12, 4, 6));
	actual.transform = CGAffineTransformIdentity; actual.clipped = NO;
	check(CGRectEqualToRect(swtClipBounds(actual), CGRectMake(0, 0, TEST_SIZE, TEST_SIZE)), "clip and transform reset restores image bounds");
	check(swtDrawRoundRectangle(actual, YES, CGRectMake(40, 40, 3, 3), 0, 0), "restored drawing context");
	expectedRect(expected, CGRectMake(40, 40, 3, 3));
	check(memcmp(pixels(actual), pixels(expected), TEST_BYTES) == 0 && area(actual) == 33,
		"actual rounded-rectangle renderer preserves translation, clip intersection, and context restore");
	actual = graphics();
	check(swtDrawRoundRectangle(actual, YES, CGRectMake(28, 28, -20, -16), -12, -10), "signed rounded-rectangle dimensions");
	unsigned filled = area(actual);
	check(filled > 270 && filled < 320, "real rounded corners remove bounding-box corner pixels");
	expected = graphics(); expectedRect(expected, CGRectMake(8, 12, 1, 1));
	const uint32_t *a = pixels(actual), *b = pixels(expected);
	for (int i = 0; i < TEST_SIZE * TEST_SIZE; i++)
		check((a[i] >> 24) == 0 || (b[i] >> 24) == 0, "rounded corner excludes exact bounding-box corner");
	actual = graphics();
	check(swtDrawRoundRectangle(actual, NO, CGRectMake(8, 8, 32, 24), 10, 8), "rounded outline renderer");
	check(area(actual) > 40 && area(actual) < 200, "rounded outline does not fill its interior");
	a = pixels(actual);
	for (int i = 0; i < TEST_SIZE * TEST_SIZE; i++)
		check((a[i] >> 24) == 0 || a[i] == 0xff0000ff, "rounded outline uses foreground color");
}

static void testArc(jint sweep, CGRect quadrant) {
	SWTUIKitGraphics *actual = graphics(), *mask = graphics();
	check(swtDrawArc(actual, YES, CGRectMake(8, 8, 32, 32), 0, sweep), "actual arc renderer");
	expectedRect(mask, quadrant);
	unsigned filled = area(actual);
	check(filled > 180 && filled < 220, "quarter ellipse has the expected nonzero area");
	const uint32_t *a = pixels(actual), *b = pixels(mask);
	for (int i = 0; i < TEST_SIZE * TEST_SIZE; i++)
		check((a[i] >> 24) == 0 || (b[i] >> 24) != 0, "SWT arc direction remains in its expected quadrant");
}

static void testFullAndEmptyArcs(void) {
	SWTUIKitGraphics *actual = graphics(), *expected = graphics();
	check(swtDrawArc(actual, YES, CGRectMake(40, 32, -32, -24), INT_MAX, INT_MIN), "extreme full negative arc extent");
	CGContextRef context = swtBegin(expected);
	CGContextFillEllipseInRect(context, CGRectMake(8, 8, 32, 24));
	CGContextRestoreGState(context);
	check(memcmp(pixels(actual), pixels(expected), TEST_BYTES) == 0, "full signed arc is the original complete ellipse");
	uint32_t before[TEST_SIZE * TEST_SIZE]; memcpy(before, pixels(actual), TEST_BYTES);
	check(swtDrawArc(actual, NO, CGRectMake(0, 0, 64, 64), 0, 0), "zero arc sweep");
	check(swtDrawArc(actual, YES, CGRectMake(0, 0, 0, 64), 0, 90), "empty arc width");
	check(memcmp(before, pixels(actual), TEST_BYTES) == 0, "empty arcs do not alter native pixels");
}

static SWTUIKitGraphics *textGraphics(BOOL shapeAntialias, BOOL textAntialias) {
	SWTUIKitGraphics *gc = graphics();
	gc.antialias = shapeAntialias; gc.textAntialias = textAntialias;
	check(swtDrawText(gc, @"Ag/", 3, 3, SWT(DRAW_TRANSPARENT)), "actual CoreText rendering helper");
	check(area(gc) > 30, "real CoreText glyph pixels are nonblank");
	return gc;
}

static void testText(void) {
	SWTUIKitGraphics *plain = textGraphics(NO, NO), *plainShape = textGraphics(YES, NO);
	SWTUIKitGraphics *smooth = textGraphics(NO, YES), *smoothShape = textGraphics(YES, YES);
	check(memcmp(pixels(plain), pixels(plainShape), TEST_BYTES) == 0, "shape antialias does not alter plain text pixels");
	check(memcmp(pixels(smooth), pixels(smoothShape), TEST_BYTES) == 0, "shape antialias does not alter smooth text pixels");
	check(memcmp(pixels(plain), pixels(smooth), TEST_BYTES) != 0, "text antialias changes real CoreText glyph edges");
	CGSize single, multiline, tabbed, mnemonic;
	check(swtLayoutText(smooth, @"SWT", 0, &single, nil), "CoreText single-line metrics");
	check(swtLayoutText(smooth, @"SWT\r\niOS", SWT(DRAW_DELIMITER), &multiline, nil), "CoreText CRLF metrics");
	check(single.width > 0 && single.height > 0 && multiline.height == 2 * single.height, "real CoreText multiline metrics");
	check(swtLayoutText(smooth, @"SWT\tiOS", SWT(DRAW_TAB), &tabbed, nil) && tabbed.width > single.width,
		"current actual tab stops are retained");
	check(swtLayoutText(smooth, @"&SWT", SWT(DRAW_MNEMONIC), &mnemonic, nil) && CGSizeEqualToSize(single, mnemonic),
		"mnemonic metadata does not increase text extent");
	check(CTFontGetAscent(smooth.font) > 0 && CTFontGetDescent(smooth.font) >= 0, "real native font metrics");
}

static void testModifierKeys(void) {
	UIKeyboardHIDUsage left[] = {UIKeyboardHIDUsageKeyboardLeftShift, UIKeyboardHIDUsageKeyboardLeftControl,
		UIKeyboardHIDUsageKeyboardLeftAlt, UIKeyboardHIDUsageKeyboardLeftGUI};
	UIKeyboardHIDUsage right[] = {UIKeyboardHIDUsageKeyboardRightShift, UIKeyboardHIDUsageKeyboardRightControl,
		UIKeyboardHIDUsageKeyboardRightAlt, UIKeyboardHIDUsageKeyboardRightGUI};
	jint codes[] = {SWT(SHIFT), SWT(CTRL), SWT(ALT), SWT(COMMAND)};
	UIKeyModifierFlags bits[] = {UIKeyModifierShift, UIKeyModifierControl, UIKeyModifierAlternate, UIKeyModifierCommand};
	UIKeyModifierFlags all = UIKeyModifierShift | UIKeyModifierControl | UIKeyModifierAlternate | UIKeyModifierCommand;
	jint swtAll = SWT(SHIFT) | SWT(CTRL) | SWT(ALT) | SWT(COMMAND);
	for (int i = 0; i < 4; i++) {
		check(swtSpecialKey(left[i]) == codes[i] && swtSpecialKey(right[i]) == codes[i], "left/right UIKit modifier key mapping");
		check(swtKeyState(SWT(KEY_DOWN), codes[i], bits[i]) == 0, "KeyDown excludes its just-pressed modifier");
		check(swtKeyState(SWT(KEY_UP), codes[i], 0) == codes[i], "KeyUp includes its just-released modifier");
		check(swtKeyState(SWT(KEY_DOWN), codes[i], all) == (swtAll & ~codes[i]), "KeyDown preserves other held modifiers");
		check(swtKeyState(SWT(KEY_UP), codes[i], all & ~bits[i]) == swtAll, "KeyUp preserves other held modifiers");
	}
	check(swtKeyState(SWT(KEY_DOWN), 'a', all) == swtAll && swtKeyState(SWT(KEY_UP), 'a', all) == swtAll,
		"ordinary keys retain native modifier state");
}


static void testRegistryFreeHeap(void) {
	SWTHandleRegistry registry = {.kind = 4};
	SWTRegistryError failure = SWTRegistryOK;
	jlong handles[128];
	for (int i = 0; i < 128; i++) {
		handles[i] = swtRegistryAdd(&registry, [NSObject new], &failure);
		check(failure == SWTRegistryOK && handles[i] > 0, "registry retains native objects");
	}
	check(registry.capacity >= 128 && registry.freeCount == 0, "native registry grows parallel arrays");
	swtRegistryRemove(&registry, handles[73]);
	swtRegistryRemove(&registry, handles[7]);
	swtRegistryRemove(&registry, handles[35]);
	check(registry.freeCount == 3, "removed native handles return free slots");
	check(swtRegistryGet(&registry, handles[7]) == nil, "disposed handle is invalid");
	jlong first = swtRegistryAdd(&registry, [NSObject new], &failure);
	jlong second = swtRegistryAdd(&registry, [NSObject new], &failure);
	jlong third = swtRegistryAdd(&registry, [NSObject new], &failure);
	check((first & SWT_SLOT_MASK) == 8 && (second & SWT_SLOT_MASK) == 36 &&
		(third & SWT_SLOT_MASK) == 74, "native min heap keeps original lowest-slot reuse");
	check(first != handles[7] && second != handles[35] && third != handles[73],
		"recycled handle generation advances");
	check(swtRegistryGet(&registry, handles[7]) == nil && swtRegistryGet(&registry, handles[35]) == nil,
		"old generations cannot resolve reallocated native handles");
	swtRegistryClear(&registry);
	check(registry.freeCount == registry.length, "disconnect releases native registry occupants");
	check(swtRegistryGet(&registry, first) == nil, "disconnect invalidates live handles");
	jlong replacement = swtRegistryAdd(&registry, [NSObject new], &failure);
	check((replacement & SWT_SLOT_MASK) == 1 && replacement != handles[0],
		"reconnect uses lowest slot but a new generation");
	swtRegistryClear(&registry);
	free(registry.values); free(registry.generation); free(registry.freeHeap);
}

int main(void) {
	@autoreleasepool {
		testRegistryFreeHeap();
		testModifierKeys();
		testNativeClientGeometry();
		testClipAndRoundRectangle();
		testArc(90, CGRectMake(24, 8, 16, 16));
		testArc(-90, CGRectMake(24, 24, 16, 16));
		testFullAndEmptyArcs();
		testText();
		printf("SWT_IOS_NATIVE_RENDER_PASS checks=%d\n", checks);
	}
	return 0;
}
