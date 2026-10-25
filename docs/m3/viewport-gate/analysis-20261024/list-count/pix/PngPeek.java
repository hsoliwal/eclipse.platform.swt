import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/* Prints distinct ARGB values (with counts) inside a box of each PNG. Usage: PngPeek x0 y0 x1 y1 png... */
public class PngPeek {
	public static void main (String [] args) throws Exception {
		int x0 = Integer.parseInt (args [0]), y0 = Integer.parseInt (args [1]), x1 = Integer.parseInt (args [2]), y1 = Integer.parseInt (args [3]);
		for (int k = 4; k < args.length; k++) {
			BufferedImage a = ImageIO.read (new File (args [k]));
			java.util.TreeMap<String, Integer> counts = new java.util.TreeMap<> ();
			for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) counts.merge (String.format ("%08x", a.getRGB (x, y)), 1, Integer::sum);
			System.out.println (new File (args [k]).getName () + ": " + counts);
			StringBuilder row = new StringBuilder ();
			for (int x = x0; x <= Math.min (x1, x0 + 12); x++) row.append (String.format (" %06x", a.getRGB (x, y0) & 0xffffff));
			System.out.println ("   first pixels of row " + y0 + ":" + row);
		}
	}
}
