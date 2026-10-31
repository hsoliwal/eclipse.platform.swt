/*
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Text leaf probe (scratch evidence, not product code): a StyledText with a dark background, a LineBackgroundListener,
 * a selection and Arabic, Hebrew and CJK lines, and a CLabel with emoji, CJK, Arabic, '&&' and a tab, in the startup
 * mode (-Dswt.widget.mode=m3|gpu, or stock). After the shell settles each control is captured from the screen
 * (GC(Display).copyArea) into <out>/<mode>/<name>.png; one TSV line per control with the M3 text counters.
 * Usage: java -Dtextprobe.out=<dir> [-Dswt.widget.mode=m3|gpu] M3TextProbe
 */
import java.io.*;
import java.nio.file.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

public class M3TextProbe {
	static final String MODE = System.getProperty ("swt.widget.mode", "stock");
	static final Path OUT = Path.of (System.getProperty ("textprobe.out", "H:/m3-scratch/text/probe"));

	public static void main (String [] args) throws Exception {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setLayout (new GridLayout (1, false));
		StyledText text = new StyledText (shell, SWT.MULTI | SWT.BORDER);
		text.setLayoutData (new GridData (420, 150));
		text.setFont (new Font (display, "Consolas", 10, SWT.NORMAL));
		text.setBackground (new Color (0x1E, 0x1F, 0x22));
		text.setForeground (new Color (0xDD, 0xDD, 0xDD));
		text.setText ("public class Hello { int[] a = {1, 2}; }\n// dark line background, selection below\n"
			+ "\u05e9\u05dc\u05d5\u05dd \u05e2\u05d5\u05dc\u05dd Hebrew\n\u0645\u0631\u062d\u0628\u0627 \u0628\u0627\u0644\u0639\u0627\u0644\u0645 Arabic\n\u6f22\u5b57\u304b\u306a\u30ab\u30ca CJK\n");
		text.addLineBackgroundListener (e -> { if (text.getLineAtOffset (e.lineOffset) % 2 == 1) e.lineBackground = new Color (0x2B, 0x2D, 0x30); });
		text.setStyleRange (new StyleRange (0, 6, new Color (0xCC, 0x78, 0x32), null));
		text.setSelection (7, 18);
		CLabel label = new CLabel (shell, SWT.NONE);
		label.setLayoutData (new GridData (420, 30));
		label.setText ("emoji \ud83d\ude00 CJK \u6f22\u5b57 Arabic \u0645\u0631\u062d\u0628\u0627 a&&b\ttab");
		shell.pack ();
		shell.open ();
		long end = System.currentTimeMillis () + 4000;
		while (System.currentTimeMillis () < end) { if (!display.readAndDispatch ()) Thread.sleep (20); }
		Files.createDirectories (OUT.resolve (MODE));
		try (PrintWriter tsv = new PrintWriter (Files.newBufferedWriter (OUT.resolve ("probe_" + MODE + ".tsv")))) {
			tsv.println (capture (display, text, "styledtext") + "\t" + counters ());
			tsv.println (capture (display, label, "clabel") + "\t" + counters ());
		}
		display.dispose ();
	}

	static String capture (Display display, Control control, String name) {
		Rectangle r = display.map (control, null, control.getClientArea ());
		Image image = new Image (display, r.width, r.height);
		GC gc = new GC (display);
		gc.copyArea (image, r.x, r.y);
		gc.dispose ();
		ImageLoader loader = new ImageLoader ();
		loader.data = new ImageData [] {image.getImageData ()};
		String png = OUT.resolve (MODE).resolve (name + ".png").toString ().replace ('\\', '/');
		loader.save (png, SWT.IMAGE_PNG);
		image.dispose ();
		return MODE + "\t" + name + "\t" + r.width + "x" + r.height + "\t" + png;
	}

	/* runs / blobs / fallbacks of the M3 text sink (reflection: absent in stock) */
	static String counters () {
		try {
			Class<?> k = Class.forName ("org.eclipse.swt.internal.render.m3.gc.M3GlyphRuns");
			return "runs=" + k.getMethod ("runs").invoke (null) + " blobs=" + k.getMethod ("blobs").invoke (null) + " fallbacks=" + k.getMethod ("fallbacks").invoke (null);
		} catch (Throwable t) {
			return "counters=-";
		}
	}
}
