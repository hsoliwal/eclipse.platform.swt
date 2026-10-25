import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/* Verifier tool: pixel comparison of PNG pairs; differing pixels, bounding box, colour pairs (a->b), raw and with 0x0078d7 == 0x0078d4. */
public class PngCompare {
	public static void main (String [] args) throws Exception {
		for (int k = 0; k + 1 < args.length; k += 2) {
			BufferedImage a = ImageIO.read (new File (args [k])), b = ImageIO.read (new File (args [k + 1]));
			StringBuilder out = new StringBuilder (new File (args [k]).getName () + " vs " + new File (args [k + 1]).getName () + " " + a.getWidth () + "x" + a.getHeight () + "/" + b.getWidth () + "x" + b.getHeight ());
			for (boolean norm : new boolean [] {false, true}) {
				int w = Math.min (a.getWidth (), b.getWidth ()), h = Math.min (a.getHeight (), b.getHeight ());
				int n = 0, x0 = w, y0 = h, x1 = -1, y1 = -1;
				Map<String, Integer> pairs = new TreeMap<> ();
				for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
					int p = a.getRGB (x, y) & 0xffffff, q = b.getRGB (x, y) & 0xffffff;
					if (norm) { if (p == 0x0078d7) p = 0x0078d4; if (q == 0x0078d7) q = 0x0078d4; }
					if (p != q) {
						n++; x0 = Math.min (x0, x); y0 = Math.min (y0, y); x1 = Math.max (x1, x); y1 = Math.max (y1, y);
						pairs.merge (String.format ("%06x->%06x", p, q), 1, Integer::sum);
					}
				}
				out.append (norm ? " | norm " : " | raw ").append (n);
				if (n > 0) {
					out.append (" box ").append (x0).append (',').append (y0).append ("..").append (x1).append (',').append (y1).append (' ');
					pairs.entrySet ().stream ().sorted ((e, f) -> f.getValue () - e.getValue ()).limit (4).forEach (e -> out.append (e.getKey ()).append ('x').append (e.getValue ()).append (' '));
				}
			}
			System.out.println (out);
		}
	}
}
