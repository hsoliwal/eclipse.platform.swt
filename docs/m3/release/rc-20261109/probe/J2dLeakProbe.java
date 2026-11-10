import java.awt.geom.AffineTransform;
import java.util.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.Java2DCanvas;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/** Diagnostic only (not committed): which code creates the Java2DCanvas paint-path objects that stay alive. */
public class J2dLeakProbe {
	public static void main (String [] args) {
		DeviceData data = new DeviceData ();
		data.tracking = true;
		Display display = new Display (data);
		Shell shell = new Shell (display, SWT.NO_TRIM);
		shell.setBounds (40, 40, 320, 240);
		Java2DCanvas canvas = new Java2DCanvas (shell, SWT.NONE);
		canvas.setSize (80, 60);
		canvas.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		canvas.setRenderer (g -> { g.setColor (java.awt.Color.RED); g.fillRect (0, 0, 80, 60); });
		Image image = new Image (display, 80, 60);
		GC gc = new GC (image);
		paint (canvas, gc);
		Set<Object> baseline = Collections.newSetFromMap (new IdentityHashMap<> ());
		baseline.addAll (Arrays.asList (display.getDeviceData ().objects));
		for (int frame = 0; frame < 5; frame++) {
			canvas.setViewTransform (AffineTransform.getTranslateInstance (-frame, -frame * 3));
			paint (canvas, gc);
		}
		DeviceData after = display.getDeviceData ();
		for (int i = 0; i < after.objects.length; i++) {
			Object o = after.objects [i];
			if (baseline.contains (o) || !(o instanceof Image || o instanceof GC || o instanceof Region || o instanceof Transform)) continue;
			System.out.println ("LEAK " + o.getClass ().getSimpleName ());
			StackTraceElement [] st = after.errors [i].getStackTrace ();
			for (int k = 0; k < Math.min (14, st.length); k++) System.out.println ("    at " + st [k]);
		}
		gc.dispose (); image.dispose (); display.dispose ();
	}
	static void paint (Java2DCanvas canvas, GC gc) {
		Event event = new Event ();
		event.gc = gc; event.x = 0; event.y = 0; event.width = 80; event.height = 60;
		canvas.notifyListeners (SWT.Paint, event);
	}
}
