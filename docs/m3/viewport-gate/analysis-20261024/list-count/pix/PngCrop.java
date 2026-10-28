import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/* Crops x0 y0 w h of each input PNG, scales it 4x and stacks the crops vertically into out.png. Usage: PngCrop out x0 y0 w h png... */
public class PngCrop {
	public static void main (String [] args) throws Exception {
		int x0 = Integer.parseInt (args [1]), y0 = Integer.parseInt (args [2]), w = Integer.parseInt (args [3]), h = Integer.parseInt (args [4]), s = 4;
		int n = args.length - 5;
		BufferedImage out = new BufferedImage (w * s, (h * s + 4) * n, BufferedImage.TYPE_INT_RGB);
		for (int k = 0; k < n; k++) {
			BufferedImage a = ImageIO.read (new File (args [5 + k]));
			for (int y = 0; y < h * s; y++) for (int x = 0; x < w * s; x++) out.setRGB (x, k * (h * s + 4) + y, a.getRGB (x0 + x / s, y0 + y / s));
		}
		ImageIO.write (out, "png", new File (args [0]));
	}
}
