import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/* Pixel diff of two PNGs: size, differing pixel count, bounding box of the differences, rows (y) touched. */
public class PngDiff {
	public static void main (String [] args) throws Exception {
		for (int k = 0; k + 1 < args.length; k += 2) {
			BufferedImage a = ImageIO.read (new File (args [k])), b = ImageIO.read (new File (args [k + 1]));
			int w = Math.min (a.getWidth (), b.getWidth ()), h = Math.min (a.getHeight (), b.getHeight ());
			int n = 0, x0 = w, y0 = h, x1 = -1, y1 = -1;
			StringBuilder ys = new StringBuilder ();
			int lastY = -2;
			for (int y = 0; y < h; y++) {
				boolean rowDiff = false;
				for (int x = 0; x < w; x++) {
					if (a.getRGB (x, y) != b.getRGB (x, y)) {
						n++; rowDiff = true;
						x0 = Math.min (x0, x); y0 = Math.min (y0, y); x1 = Math.max (x1, x); y1 = Math.max (y1, y);
					}
				}
				if (rowDiff && y != lastY + 1) ys.append (' ').append (y);
				if (!rowDiff && lastY == y - 1 && y > 0 && ys.length () > 0 && !ys.toString ().endsWith ("-")) { }
				if (rowDiff) lastY = y;
			}
			System.out.println (new File (args [k]).getName () + " vs " + new File (args [k + 1]).getName () + ": " + a.getWidth () + "x" + a.getHeight () + " / " + b.getWidth () + "x" + b.getHeight ()
				+ " differing " + n + (n > 0 ? " box " + x0 + "," + y0 + ".." + x1 + "," + y1 + " row starts" + ys : ""));
		}
	}
}
