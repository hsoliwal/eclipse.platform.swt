import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;

/**
 * Pixel difference of two PNG captures: size, differing pixels, their bounding box, the differing pixels per band of
 * <code>band</code> rows (the list's item height) and the first differing pixels with both colours.
 * Usage: java PngDiff <expected.png> <actual.png> <band>
 */
public class PngDiff {
	public static void main (String [] args) {
		ImageData a = new ImageData (args [0]), b = new ImageData (args [1]);
		int band = Integer.parseInt (args [2]);
		System.out.println ("expected " + a.width + "x" + a.height + " actual " + b.width + "x" + b.height);
		int width = Math.min (a.width, b.width), height = Math.min (a.height, b.height);
		int count = 0, minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1, shown = 0;
		int [] perBand = new int [height / Math.max (1, band) + 1];
		StringBuilder first = new StringBuilder ();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				RGB p = a.palette.getRGB (a.getPixel (x, y)), q = b.palette.getRGB (b.getPixel (x, y));
				if (p.equals (q)) continue;
				count++;
				minX = Math.min (minX, x);
				minY = Math.min (minY, y);
				maxX = Math.max (maxX, x);
				maxY = Math.max (maxY, y);
				perBand [y / Math.max (1, band)]++;
				if (shown++ < 12) first.append ("  (" + x + "," + y + ") expected " + p + " actual " + q + "\n");
			}
		}
		System.out.println ("differing pixels " + count + (count == 0 ? "" : " in box " + minX + "," + minY + " .. " + maxX + "," + maxY));
		StringBuilder bands = new StringBuilder ("per band of " + band + " rows (y offset of band = index * band):");
		for (int i = 0; i < perBand.length; i++) {
			if (perBand [i] != 0) bands.append (' ').append (i).append (':').append (perBand [i]);
		}
		System.out.println (bands);
		System.out.print (first);
	}
}
