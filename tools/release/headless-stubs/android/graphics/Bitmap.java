/* SPDX-License-Identifier: EPL-2.0 */
package android.graphics;

/** Test-only Bitmap stub; never packages with the SWT Android port. */
public final class Bitmap {
    private final int width, height;
    private final int[] pixels;
    public Bitmap(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("geometry");
        this.width = width;
        this.height = height;
        this.pixels = new int[Math.multiplyExact(width, height)];
    }
    public int[] pixels() { return pixels; }
    public int[] snapshot() { return pixels.clone(); }
    private void validate(int[] buffer, int offset, int stride, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || w > width - x || h > height - y ||
            stride < w || offset < 0 || (long) offset + (long)(h - 1) * stride + w > buffer.length) {
            throw new IllegalArgumentException("pixels bounds");
        }
    }
    public void getPixels(int[] result, int offset, int stride, int x, int y, int w, int h) {
        validate(result, offset, stride, x, y, w, h);
        for (int row = 0; row < h; row++) System.arraycopy(pixels, (y + row) * width + x, result, offset + row * stride, w);
    }
    public void setPixels(int[] source, int offset, int stride, int x, int y, int w, int h) {
        validate(source, offset, stride, x, y, w, h);
        for (int row = 0; row < h; row++) System.arraycopy(source, offset + row * stride, pixels, (y + row) * width + x, w);
    }
}
