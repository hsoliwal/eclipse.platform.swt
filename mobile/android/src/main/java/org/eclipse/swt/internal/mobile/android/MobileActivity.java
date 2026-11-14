/* SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.swt.internal.mobile.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;
import org.eclipse.swt.internal.mobile.MobileWidgetRuntime;
import org.eclipse.swt.internal.mobile.MobileWidgetRuntime.*;

/** Executable Android platform-host smoke application, not a SWT API parity claim. */
public final class MobileActivity extends Activity {
	private MobileWidgetRuntime display;
	@Override protected void onCreate(Bundle state) {
		super.onCreate(state);
		FrameLayout frame = new FrameLayout(this);
		setContentView(frame);
		display = new MobileWidgetRuntime(new AndroidMobileHost(this, frame));
		MobileWidgetRuntime.Node shell = display.createShell();
		MobileWidgetRuntime.Node label = shell.createChild(Kind.LABEL);
		label.setText("SWT mobile: Android native host");
		MobileWidgetRuntime.Node button = shell.createChild(Kind.BUTTON);
		button.setText("Tap to update");
		button.on(EventType.SELECT, event -> label.setText("Android event delivered"));
		int width = getResources().getDisplayMetrics().widthPixels;
		int height = getResources().getDisplayMetrics().heightPixels;
		shell.setBounds(new Bounds(0, 0, width, height));
		label.setBounds(new Bounds(24, 60, width - 48, 80));
		button.setBounds(new Bounds(24, 150, width - 48, 72));
	}
	@Override protected void onPause() {
		if (display != null) display.setForeground(false);
		super.onPause();
	}
	@Override protected void onResume() {
		super.onResume();
		if (display != null) display.setForeground(true);
	}
	@Override protected void onDestroy() {
		if (display != null) { display.close(); display = null; }
		super.onDestroy();
	}
}
