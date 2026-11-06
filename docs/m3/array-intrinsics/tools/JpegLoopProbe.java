/*
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
 */
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;

/**
 * Headless differential probe of ARRAY_LOOP_INTRINSICS v1 on SWT's JPEG decoders (JPEGDecoder by default, the 3.2
 * JPEGFileFormat decoder with -Dorg.eclipse.swt.internal.image.JPEGFileFormat_3.2=1): writes deterministic baseline,
 * grayscale and progressive JPEGs with javax.imageio, decodes each with SWT's ImageLoader and prints the SHA-256 of
 * every ImageData. prove_jpeg_loops.sh runs it on the classes before and after the recipe and diffs the output.
 */
public class JpegLoopProbe {
	static final int[][] SIZES = {{1, 1}, {7, 5}, {16, 16}, {33, 17}, {64, 48}, {257, 9}};
	static final String[] KINDS = {"rgb", "gray", "progressive"};
	static final int RED_STEP = 31, GREEN_STEP = 17, BLUE_STEP = 7, BYTE_MASK = 0xFF, RED_SHIFT = 16, GREEN_SHIFT = 8;
	static final float QUALITY = 0.9f;

	public static void main(String[] args) throws Exception {
		for (String kind : KINDS) {
			for (int[] size : SIZES) {
				byte[] jpeg = encode(kind, size[0], size[1]);
				ImageData data = new ImageLoader().load(new ByteArrayInputStream(jpeg))[0];
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				md.update(data.data);
				System.out.println(kind + " " + size[0] + "x" + size[1] + " depth=" + data.depth + " bpl=" + data.bytesPerLine
					+ " sha256=" + HexFormat.of().formatHex(md.digest()));
			}
		}
	}

	static byte[] encode(String kind, int width, int height) throws Exception {
		boolean gray = "gray".equals(kind);
		BufferedImage image = new BufferedImage(width, height, gray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int r = (x * RED_STEP + y) & BYTE_MASK, g = (y * GREEN_STEP + x) & BYTE_MASK, b = ((x ^ y) * BLUE_STEP) & BYTE_MASK;
				image.setRGB(x, y, (r << RED_SHIFT) | (g << GREEN_SHIFT) | b);
			}
		}
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(QUALITY);
		if ("progressive".equals(kind)) {
			param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(ios);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		writer.dispose();
		return out.toByteArray();
	}
}
