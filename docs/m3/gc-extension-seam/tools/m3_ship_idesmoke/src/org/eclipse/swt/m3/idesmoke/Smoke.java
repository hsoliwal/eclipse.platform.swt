/*
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Leaf 18 IDE smoke driver (scratch evidence, not product code). Runs inside the Eclipse workbench started with the
 * fork's SWT host, win32 fragment and M3 fragment: closes the intro, creates a project with Hello.java and notes.txt,
 * shows Package Explorer and Outline, opens both files (Java editor + text editor: two editor tabs), switches tabs,
 * and after each step captures the workbench shell with Control.print into a PNG and writes one TSV row with the M3
 * census (M3 canvas handlers, drawn planes and their backend, CTabFolders, StyledTexts), the distinct colours of the
 * capture and the error-log entries seen so far. Then closes the workbench.
 */
package org.eclipse.swt.m3.idesmoke;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.nio.file.Path;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.ide.*;

public class Smoke implements IStartup {
	static final String MODE = System.getProperty ("swt.widget.mode", "stock");
	static final Path OUT = Path.of (System.getProperty ("idesmoke.out", "H:/m3-scratch/l18/ide/out"));
	static final String DATA_KEY = "org.eclipse.swt.internal.render.m3.M3CanvasHandler";
	static final int SETTLE = Integer.getInteger ("idesmoke.settle", 6000);
	static final List<String> errors = Collections.synchronizedList (new ArrayList<> ());
	static PrintWriter tsv;
	static IFile hello, notes;

	@Override
	public void earlyStartup () {
		Platform.addLogListener ((status, plugin) -> {
			if (status.getSeverity () == IStatus.ERROR) errors.add (plugin + ": " + status.getMessage () + (status.getException () == null ? "" : " " + status.getException ()));
		});
		Display display = PlatformUI.getWorkbench ().getDisplay ();
		display.asyncExec (() -> run (display, 0));
	}

	static void run (Display display, int step) {
		try {
			if (step == 0) {
				Files.createDirectories (OUT.resolve (MODE));
				tsv = new PrintWriter (Files.newBufferedWriter (OUT.resolve ("ide_" + MODE + ".tsv"), StandardCharsets.UTF_8), true);
				tsv.println ("mode\tstep\tresult\tshell\tcolours\tcontrols\tm3_handlers\tdrawn_planes\tctabfolders\tstyledtexts\terrors_so_far\tactive_part\teditors\tpng\teditor_rect");
				setup ();
			} else if (step == 1) {
				row (display, "workbench_java_editor");
				StyledText st = editorText (PlatformUI.getWorkbench ().getActiveWorkbenchWindow ().getShell ());
				if (st != null) { int at = st.getText ().indexOf ("System.out.println"); st.setSelection (at, at + 30); }
			} else if (step == 2) {
				row (display, "java_editor_selection");
				page ().showView ("org.eclipse.jdt.ui.PackageExplorer");
			} else if (step == 3) {
				row (display, "package_explorer_active");
				IDE.openEditor (page (), notes, "org.eclipse.ui.DefaultTextEditor", true);
			} else if (step == 4) {
				row (display, "second_editor_tab");
				page ().activate (page ().findEditor (new org.eclipse.ui.part.FileEditorInput (hello)));
			} else if (step == 5) {
				row (display, "editor_tab_switched_back");
				tsv.println ("# errors: " + errors.size () + (errors.isEmpty () ? "" : " first: " + errors.get (0).replace ('\n', ' ')));
				tsv.close ();
				PlatformUI.getWorkbench ().close ();
				return;
			}
		} catch (Throwable t) {
			errors.add ("idesmoke step " + step + ": " + t);
			if (tsv != null) tsv.println ("# step " + step + " failed: " + t);
		}
		display.timerExec (SETTLE, () -> run (display, step + 1));
	}

	static IWorkbenchPage page () {
		return PlatformUI.getWorkbench ().getActiveWorkbenchWindow ().getActivePage ();
	}

	static void setup () throws CoreException {
		IWorkbench workbench = PlatformUI.getWorkbench ();
		if (workbench.getIntroManager ().getIntro () != null) workbench.getIntroManager ().closeIntro (workbench.getIntroManager ().getIntro ());
		IProject project = ResourcesPlugin.getWorkspace ().getRoot ().getProject ("m3smoke");
		if (!project.exists ()) project.create (null);
		project.open (null);
		hello = project.getFile ("Hello.java");
		notes = project.getFile ("notes.txt");
		String java = "// line comment: braces { } brackets [ ] parentheses ( ) semicolons ;\npublic class Hello {\n\t/** M3 IDE smoke. */\n\tprivate static final int[] VALUES = { 1, 2, 3 };\n\n\tpublic static void main(String[] args) {\n\t\tSystem.out.println(\"hello m3\"); /* block */\n\t\tfor (int i = 0; i < VALUES.length; i++) {\n\t\t\tif (args.length > 0 && VALUES[i] != 0) {\n\t\t\t\tSystem.out.printf(\"%d;%s%n\", VALUES[i], args[0]);\n\t\t\t}\n\t\t}\n\t}\n}\n";
		if (!hello.exists ()) hello.create (new ByteArrayInputStream (java.getBytes (StandardCharsets.UTF_8)), true, null);
		if (!notes.exists ()) notes.create (new ByteArrayInputStream ("notes for the second editor tab\n".getBytes (StandardCharsets.UTF_8)), true, null);
		page ().showView ("org.eclipse.ui.views.ContentOutline");
		page ().showView ("org.eclipse.jdt.ui.PackageExplorer");
		IDE.openEditor (page (), hello, "org.eclipse.jdt.ui.CompilationUnitEditor", true);
	}

	static void row (Display display, String step) throws IOException {
		Shell shell = PlatformUI.getWorkbench ().getActiveWorkbenchWindow ().getShell ();
		shell.update ();
		Rectangle size = shell.getBounds ();
		Image image = new Image (display, size.width, size.height);
		GC gc = new GC (image);
		boolean printed = shell.print (gc);
		gc.dispose ();
		ImageData data = image.getImageData ();
		image.dispose ();
		Path png = OUT.resolve (MODE).resolve (step + ".png");
		ImageLoader loader = new ImageLoader ();
		loader.data = new ImageData [] {data};
		loader.save (png.toString (), SWT.IMAGE_PNG);
		List<Control> all = new ArrayList<> ();
		collect (shell, all);
		int handlers = 0, tabs = 0, styled = 0;
		List<String> drawn = new ArrayList<> ();
		for (Control c : all) {
			if (c.getData (DATA_KEY) != null) handlers++;
			if (c instanceof CTabFolder) tabs++;
			if (c instanceof StyledText) styled++;
			String plane = drawnPlane (c);
			if (plane != null) drawn.add (c.getClass ().getSimpleName () + ":" + plane);
		}
		int colours = colours (data);
		IWorkbenchPart part = page ().getActivePart ();
		String result = !printed ? "NO_PRINT" : colours <= 1 ? "BLANK" : "PAINTED";
		tsv.println (String.join ("\t", MODE, step, result, size.width + "x" + size.height, String.valueOf (colours), String.valueOf (all.size ()),
			String.valueOf (handlers), String.join (",", new TreeSet<> (drawn)) + " (" + drawn.size () + ")", String.valueOf (tabs), String.valueOf (styled),
			String.valueOf (errors.size ()), part == null ? "-" : part.getTitle (), String.valueOf (page ().getEditorReferences ().length),
			png.toString ().replace ('\\', '/'), editorRect (shell)));
	}

	/* the visible StyledText showing Hello.java (the Java editor's text widget) */
	static StyledText editorText (Shell shell) {
		List<Control> all = new ArrayList<> ();
		collect (shell, all);
		for (Control c : all) if (c instanceof StyledText t && t.isVisible () && t.getText ().contains ("class Hello")) return t;
		return null;
	}

	/* x,y,w,h of the Java editor's client area in the printed shell image (the editor area the text leaf diffs) */
	static String editorRect (Shell shell) {
		StyledText t = editorText (shell);
		if (t == null) return "-";
		Rectangle r = t.getDisplay ().map (t, shell, t.getClientArea ());
		Rectangle trim = shell.computeTrim (0, 0, 0, 0);
		return (r.x - trim.x) + "," + (r.y - trim.y) + "," + r.width + "," + r.height;
	}

	static void collect (Control c, List<Control> out) {
		out.add (c);
		if (c instanceof Composite k) for (Control child : k.getChildren ()) collect (child, out);
	}

	static int colours (ImageData d) {
		Set<Integer> seen = new HashSet<> ();
		for (int y = 0; y < d.height && seen.size () < 4096; y += 2) {
			for (int x = 0; x < d.width && seen.size () < 4096; x += 2) seen.add (d.getPixel (x, y));
		}
		return seen.size ();
	}

	static String drawnPlane (Control c) {
		Object plane = field (c, "drawnPlane");
		if (plane == null) return null;
		Object canvas = field (plane, "canvas");
		if (canvas == null) canvas = field (plane, "mirroring");
		if (canvas != null && "MirroredCanvas".equals (canvas.getClass ().getSimpleName ())) {
			Object inner = field (canvas, "canvas");
			if (inner != null) canvas = inner;
		}
		if (canvas == null) return plane.getClass ().getSimpleName () + "/null";
		String kind = canvas.getClass ().getSimpleName ();
		if ("M3RowCanvas".equals (kind)) kind += field (canvas, "gpu") != null ? "(gpu)" : "(raster)";
		return kind;
	}

	static Object field (Object o, String name) {
		for (Class<?> k = o.getClass (); k != null; k = k.getSuperclass ()) {
			try {
				Field f = k.getDeclaredField (name);
				f.setAccessible (true);
				return f.get (o);
			} catch (NoSuchFieldException e) {
				continue;
			} catch (Throwable t) {
				return null;
			}
		}
		return null;
	}
}
