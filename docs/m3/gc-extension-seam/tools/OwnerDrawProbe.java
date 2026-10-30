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
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCExtension;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/** Display probe (GC seam, leaf 3): a drawn-mode Table (-Dswt.widget.mode=m3|custom) with owner-draw listeners; PaintItem fills its cell red through event.gc. */
public class OwnerDrawProbe {
	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setSize (420, 240);
		shell.setLayout (new FillLayout ());
		Table table = new Table (shell, SWT.BORDER | SWT.FULL_SELECTION);
		new TableColumn (table, SWT.NONE).setWidth (180);
		new TableColumn (table, SWT.NONE).setWidth (180);
		for (int i = 0; i < 5; i++) new TableItem (table, SWT.NONE).setText (new String [] {"row " + i, "cell " + i});
		int [] counts = new int [3];
		boolean [] extension = {true}, order = {true};
		int [] last = {SWT.PaintItem};
		table.addListener (SWT.MeasureItem, e -> { counts [0]++; order [0] &= last [0] == SWT.PaintItem; last [0] = SWT.MeasureItem; });
		table.addListener (SWT.EraseItem, e -> { counts [1]++; order [0] &= last [0] == SWT.MeasureItem; last [0] = SWT.EraseItem; });
		table.addListener (SWT.PaintItem, e -> {
			counts [2]++;
			order [0] &= last [0] == SWT.EraseItem;
			last [0] = SWT.PaintItem;
			extension [0] &= e.gc instanceof GCExtension;
			e.gc.setBackground (display.getSystemColor (SWT.COLOR_RED));
			e.gc.fillRectangle (e.x, e.y, e.width, e.height);
		});
		shell.open ();
		long end = System.currentTimeMillis () + 1500;
		while (System.currentTimeMillis () < end) if (!display.readAndDispatch ()) display.sleep ();
		int painted = counts [2];
		Rectangle area = table.getClientArea ();
		Image image = new Image (display, area.width, area.height);
		GC gc = new GC (image);
		table.print (gc);
		gc.dispose ();
		ImageData data = image.getImageData ();
		Rectangle cell = table.getItem (1).getTextBounds (1);
		int pixel = data.getPixel (cell.x + 4, cell.y + cell.height / 2);
		int rgb = data.palette.getRGB (pixel).red << 16 | data.palette.getRGB (pixel).green << 8 | data.palette.getRGB (pixel).blue;
		System.out.println ("PROBE mode=" + System.getProperty ("swt.widget.mode") + " backend=" + table.getClass ().getSimpleName ()
			+ " measure=" + counts [0] + " erase=" + counts [1] + " paint=" + counts [2] + " printPaint=" + (counts [2] - painted)
			+ " gcExtension=" + extension [0] + " order=" + order [0] + " pixel=" + Integer.toHexString (rgb));
		boolean pass = counts [0] > 0 && counts [1] > 0 && counts [2] > 0 && order [0] && rgb == 0xFF0000
			&& extension [0] == !"custom".equals (System.getProperty ("swt.widget.mode"));
		System.out.println (pass ? "PROBE PASS" : "PROBE FAIL");
		image.dispose ();
		shell.dispose ();
		display.dispose ();
		System.exit (pass ? 0 : 1);
	}
}
