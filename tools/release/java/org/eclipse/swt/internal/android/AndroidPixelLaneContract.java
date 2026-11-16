/* SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.swt.internal.android;

import android.graphics.Bitmap;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;

/** Exact SWT production AndroidPixels.java compiled with a test-only Bitmap stand-in. */
public final class AndroidPixelLaneContract {
    private static final Random RANDOM = new Random(0x20261116L);
    private static long checked;
    private AndroidPixelLaneContract() {}
    private static void test(int bitmapWidth, int bitmapHeight, int x, int y,
                             int width, int height, int dx, int dy) throws Exception {
        Bitmap bitmap = new Bitmap(bitmapWidth, bitmapHeight);
        int[] actual = bitmap.pixels();
        for (int i = 0; i < actual.length; i++) actual[i] = RANDOM.nextInt();
        int[] before = bitmap.snapshot(), expected = before.clone();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                expected[(y + row + dy) * bitmapWidth + x + col + dx] =
                        before[(y + row) * bitmapWidth + x + col];
            }
        }
        if ((checked & 1L) == 0) AndroidPixels.move(bitmap, x, y, width, height, dx, dy);
        else AndroidPixels.moveByLane(bitmap, x, y, width, height, dx, dy);
        if (!Arrays.equals(expected, bitmap.pixels())) {
            for (int i = 0; i < expected.length; i++) {
                if (expected[i] != actual[i]) {
                    throw new AssertionError("pixel mismatch case=" + checked + " i=" + i
                            + " dx=" + dx + " dy=" + dy + " width=" + width + " height=" + height);
                }
            }
        }
        Field field = AndroidPixels.class.getDeclaredField("lane");
        field.setAccessible(true);
        int[] lane = (int[]) field.get(null);
        if (lane.length > 65536) throw new AssertionError("pixel lane exceeds 65536: " + lane.length);
        checked++;
    }
    public static void main(String[] args) throws Exception {
        // Distinct horizontal and vertical copy directions; include same-row overlap and diagonal moves.
        test(100008, 7, 3, 1, 90000, 4, 7, 1);
        test(100008, 7, 10, 2, 90000, 4, -8, -1);
        test(100008, 7, 2, 1, 90000, 4, 9, 0);
        test(100008, 7, 12, 1, 90000, 4, -11, 0);
        test(100008, 7, 7, 1, 90000, 4, 0, 1);
        test(150025, 5, 10, 0, 130000, 4, 2, 1);
        test(150025, 5, 10, 1, 130000, 3, -4, -1);
        test(65550, 4, 3, 0, 65537, 3, 5, 1);
        for (int i = 0; i < 3000; i++) {
            int bw = 20 + RANDOM.nextInt(70), bh = 10 + RANDOM.nextInt(70);
            int w = 1 + RANDOM.nextInt(bw - 1), h = 1 + RANDOM.nextInt(bh - 1);
            int x = RANDOM.nextInt(bw - w + 1), y = RANDOM.nextInt(bh - h + 1);
            int dx = RANDOM.nextInt(bw - w + 1) - x;
            int dy = RANDOM.nextInt(bh - h + 1) - y;
            test(bw, bh, x, y, w, h, dx, dy);
        }
        Field field = AndroidPixels.class.getDeclaredField("lane");
        field.setAccessible(true);
        System.out.println("SWT_ANDROID_PIXEL_LANE_CASES="+ checked +
                           " MAX_LANE_PIXELS="+ ((int[]) field.get(null)).length +" PASS");
    }
}
