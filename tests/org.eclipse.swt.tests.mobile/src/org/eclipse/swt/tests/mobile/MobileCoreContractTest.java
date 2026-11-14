/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.nebula.widgets.roundedcheckbox.RoundedCheckbox;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.mobile.MobilePlatform;
import org.eclipse.swt.internal.mobile.MobileRuntime;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/** Original widget/core contracts only. Passing these is not Android/iOS qualification. */
public final class MobileCoreContractTest {
	private static int assertions;
	private static void require(boolean condition,String detail){assertions++;if(!condition)throw new AssertionError(detail);}
	private static void drain(Display display){for(int i=0;i<10000;i++){if(!display.readAndDispatch())return;}throw new AssertionError("Event queue did not quiesce");}
	private static void expectSwt(int code,Runnable operation){try{operation.run();throw new AssertionError("Expected SWT code "+code);}catch(SWTException e){require(e.code==code,"Expected SWT code "+code+", got "+e.code);}}
	private static void await(CountDownLatch latch)throws InterruptedException{require(latch.await(3,TimeUnit.SECONDS),"Worker completed within deadline");}
	private static Event click(int x,int y){Event e=new Event();e.x=x;e.y=y;e.button=1;e.count=1;return e;}
	public static void main(String[] args)throws Exception {
		testOriginalWidgetAndPainting();
		testThreadAndQueueContracts();
		testGraphicsAndDisposalDuringPaint();
		testDestructionAndBlockedSyncExec();
		testOwnedShellCoordinateMapping();
		testMobileButtonLabelContracts();
		testMobileProgressAndScaleContracts();
		System.out.println("PASS: "+assertions+" SWT/Nebula owner-contract assertions. Native device qualification is separate.");
	}
	static void testOriginalWidgetAndPainting()throws Exception {
		ContractPlatform platform=new ContractPlatform();MobileRuntime.install(platform);Display display=new Display();
		try {
			Shell shell=new Shell(display);shell.setLayout(new GridLayout(1,false));
			RoundedCheckbox checkbox=new RoundedCheckbox(shell,SWT.NONE);long checkboxPeer=platform.lastCreated;
			GridData bounds=new GridData(SWT.CENTER,SWT.CENTER,true,false);bounds.widthHint=112;bounds.heightHint=112;checkbox.setLayoutData(bounds);
			shell.open();drain(display);
			require(checkbox.getSize().equals(new Point(112,112)),"Original GridLayout uses original checkbox computeSize");
			require(!checkbox.getSelection(),"Original checkbox starts unselected");
			int[] before=platform.controls.get(checkboxPeer).frame;
			require(before!=null&&Arrays.stream(before).distinct().count()>2,"Original Paint listener draws real offscreen pixels");
			AtomicInteger selected=new AtomicInteger();List<Boolean> valuesAtSelection=new ArrayList<>();
			SelectionListener listener=SelectionListener.widgetSelectedAdapter(e->{selected.incrementAndGet();valuesAtSelection.add(checkbox.getSelection());});
			checkbox.addSelectionListener(listener);
			checkbox.notifyListeners(SWT.MouseUp,click(56,56));drain(display);
			require(checkbox.getSelection()&&selected.get()==1,"Original mouse handler toggles and sends one Selection");
			require(valuesAtSelection.equals(List.of(false)),"Original Selection precedes original state toggle");
			require(!Arrays.equals(before,platform.controls.get(checkboxPeer).frame),"Selection redraw changes rendered pixels");
			SelectionListener veto=SelectionListener.widgetSelectedAdapter(e->e.doit=false);checkbox.addSelectionListener(veto);
			checkbox.notifyListeners(SWT.MouseUp,click(56,56));
			require(checkbox.getSelection(),"Cancellable original Selection preserves selected state");
			checkbox.removeSelectionListener(veto);checkbox.setSelection(false);
			require(selected.get()==2,"Programmatic selection does not send a Selection");
			Event key=new Event();key.keyCode=' ';key.character=' ';checkbox.notifyListeners(SWT.KeyUp,key);
			require(checkbox.getSelection()&&selected.get()==2,"Original KeyUp toggles without fabricating Selection");
			checkbox.removeSelectionListener(listener);checkbox.notifyListeners(SWT.MouseUp,click(56,56));
			require(!checkbox.getSelection()&&selected.get()==2,"Typed listener removal leaves original input behavior");
			platform.sink.accessibility(checkboxPeer,MobilePlatform.ACTION_ACTIVATE);drain(display);
			require(checkbox.getSelection(),"Native activate delegates to original cancellable mouse handler");
			checkbox.setEnabled(false);platform.sink.input(checkboxPeer,SWT.MouseUp,56,56,1,0,'\0',0,1,100);drain(display);
			require(checkbox.getSelection(),"Disabled widget suppresses native activation");
			AtomicInteger disposed=new AtomicInteger();checkbox.addDisposeListener(e->{disposed.incrementAndGet();checkbox.dispose();});
			shell.dispose();require(checkbox.isDisposed()&&disposed.get()==1,"Parent disposal disposes original child once despite listener reentry");
			expectSwt(SWT.ERROR_WIDGET_DISPOSED,checkbox::getSize);
		} finally {display.dispose();MobileRuntime.uninstall(platform);}
		require(platform.controls.isEmpty()&&platform.images.isEmpty(),"All original control peers and native images released");
		require(platform.openedGraphics==platform.closedGraphics,"All native drawing contexts released");
	}
	static void testThreadAndQueueContracts()throws Exception {
		ContractPlatform platform=new ContractPlatform();MobileRuntime.install(platform);Display display=new Display();
		try {
			Shell shell=new Shell(display);Canvas canvas=new Canvas(shell,SWT.NONE);long peer=platform.lastCreated;canvas.setBounds(5,6,80,80);shell.open();drain(display);
			List<Integer> received=new ArrayList<>();canvas.addListener(SWT.MouseMove,e->{require(Thread.currentThread()==display.getThread(),"Native event delivered on SWT thread");received.add(e.x);});
			AtomicReference<Throwable> failed=new AtomicReference<>();CountDownLatch completed=new CountDownLatch(1);
			Thread producer=new Thread(()->{try{
				expectSwt(SWT.ERROR_THREAD_INVALID_ACCESS,canvas::getBounds);
				platform.sink.input(peer,SWT.MouseMove,1,0,0,0,'\0',0,0,1);
				platform.sink.input(peer,SWT.MouseMove,2,0,0,0,'\0',0,0,2);
				display.asyncExec(()->received.add(3));display.syncExec(()->received.add(4));
			}catch(Throwable e){failed.set(e);}finally{completed.countDown();}},"test OS callback producer");producer.start();
			long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
			while(completed.getCount()!=0&&System.nanoTime()<deadline){if(!display.readAndDispatch())display.sleep();}
			await(completed);if(failed.get()!=null)throw new AssertionError("Producer failed",failed.get());drain(display);
			require(received.equals(List.of(1,2,3,4)),"Native callbacks, asyncExec and syncExec retain enqueue order");
			AtomicInteger times=new AtomicInteger();Runnable timer=times::incrementAndGet;display.timerExec(0,timer);display.timerExec(-1,timer);drain(display);
			require(times.get()==0,"Cancelled timer is not dispatched");display.timerExec(20,timer);display.timerExec(0,timer);drain(display);require(times.get()==1,"Rescheduling the same timer replaces it");
			display.asyncExec(()->received.add(5));display.timerExec(0,timer);display.readAndDispatch();require(times.get()==2,"Due timers cannot starve behind a nonempty native event queue");drain(display);
			AtomicInteger focused=new AtomicInteger();canvas.addFocusListener(org.eclipse.swt.events.FocusListener.focusGainedAdapter(e->focused.incrementAndGet()));canvas.setFocus();platform.sink.input(peer,SWT.FocusIn,0,0,0,0,'\0',0,0,5);drain(display);
			require(focused.get()==1,"Native focus acknowledgement does not duplicate synchronous FocusIn");
			platform.sink.lifecycle(false);drain(display);int graphics=platform.openedGraphics;canvas.redraw();drain(display);require(platform.openedGraphics==graphics,"Inactive application retains damage without rendering");platform.sink.lifecycle(true);drain(display);require(platform.openedGraphics>graphics,"Resume repaints retained control damage");
		} finally {display.dispose();MobileRuntime.uninstall(platform);}
	}
	static void testGraphicsAndDisposalDuringPaint() {
		ContractPlatform platform=new ContractPlatform();MobileRuntime.install(platform);Display display=new Display();
		try {
			Image image=new Image(display,20,20);Color red=new Color(display,255,0,0);GC gc=new GC(image);
			gc.setBackground(red);gc.setClipping(2,2,4,4);gc.fillRectangle(0,0,20,20);gc.dispose();
			ImageData data=image.getImageData();require(data.palette.getRGB(data.getPixel(3,3)).equals(new RGB(255,0,0)),"Native GC fill writes pixels");require(data.palette.getRGB(data.getPixel(0,0)).equals(new RGB(255,255,255)),"Native GC respects clipping");
			Image copied=new Image(display,4,4);gc=new GC(image);gc.copyArea(copied,2,2);gc.dispose();require(copied.getImageData().palette.getRGB(copied.getImageData().getPixel(0,0)).equals(new RGB(255,0,0)),"Image GC copies selected offscreen pixels");
			gc=new GC(display);try{gc.copyArea(copied,0,0);throw new AssertionError("Screen capture must not substitute offscreen pixels");}catch(UnsupportedOperationException expected){require(expected.getMessage().contains("screen-capture"),"Screen GC invokes actual platform capture and fails visibly without it");}finally{gc.dispose();}
			Transform t=new Transform(display);t.translate(5,9);t.scale(2,3);float[] point={4,2};t.transform(point);require(Arrays.equals(point,new float[]{13,15}),"Affine transform composes translation and scale");t.invert();t.transform(point);require(Math.abs(point[0]-4)<0.0001&&Math.abs(point[1]-2)<0.0001,"Affine inversion restores source coordinates");t.dispose();
			copied.dispose();image.dispose();
			Shell printable=new Shell(display);Composite nested=new Composite(printable,SWT.NONE);nested.setBounds(10,12,60,60);Canvas leaf=new Canvas(nested,SWT.NONE);leaf.setBounds(7,9,10,10);leaf.setBackground(red);printable.open();drain(display);
			Image printTarget=new Image(display,100,100);GC printer=new GC(printTarget);require(nested.print(printer),"Visible nested control prints its owned images");printer.dispose();ImageData printed=printTarget.getImageData();require(printed.palette.getRGB(printed.getPixel(8,10)).equals(new RGB(255,0,0)),"Control.print includes nested child coordinates");printTarget.dispose();printable.dispose();red.dispose();
			Shell shell=new Shell(display);Canvas canvas=new Canvas(shell,SWT.NONE);canvas.setBounds(0,0,40,40);canvas.addPaintListener(e->{canvas.dispose();e.gc.drawLine(0,0,2,2);});shell.open();drain(display);require(canvas.isDisposed(),"Control can dispose during original Paint callback");
			require(platform.openedGraphics==platform.closedGraphics,"GC retains image through reentrant control disposal then releases it");
		} finally {display.dispose();MobileRuntime.uninstall(platform);}
	}
	static void testDestructionAndBlockedSyncExec()throws Exception {
		ContractPlatform platform=new ContractPlatform();MobileRuntime.install(platform);Display display=new Display();
		try {
			Shell shell=new Shell(display);shell.addListener(SWT.Close,e->e.doit=false);shell.open();drain(display);
			shell.close();require(!shell.isDisposed(),"Application Shell.Close remains vetoable");
			AtomicInteger disposals=new AtomicInteger();display.addListener(SWT.Dispose,e->{disposals.incrementAndGet();display.dispose();});
			AtomicReference<Throwable> result=new AtomicReference<>();CountDownLatch completed=new CountDownLatch(1);
			Thread waiter=new Thread(()->{try{display.syncExec(()->{throw new AssertionError("Queued work ran after disposal");});}catch(Throwable e){result.set(e);}finally{completed.countDown();}},"blocked syncExec caller");waiter.start();
			long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(waiter.getState()!=Thread.State.WAITING&&System.nanoTime()<deadline)Thread.onSpinWait();require(waiter.getState()==Thread.State.WAITING,"syncExec waiter queued before disposal");
			display.dispose();await(completed);require(disposals.get()==1&&shell.isDisposed(),"Display disposal is reentrant-safe and non-vetoable");require(result.get() instanceof SWTException e&&e.code==SWT.ERROR_DEVICE_DISPOSED,"Pending syncExec caller is released on Display disposal");
			expectSwt(SWT.ERROR_DEVICE_DISPOSED,display::readAndDispatch);
		} finally {if(!display.isDisposed())display.dispose();MobileRuntime.uninstall(platform);}
		ContractPlatform finalPlatform=new ContractPlatform();MobileRuntime.install(finalPlatform);Display finalDisplay=new Display();
		try {Shell shell=new Shell(finalDisplay);shell.addListener(SWT.Close,e->e.doit=false);finalPlatform.sink.terminate();finalDisplay.readAndDispatch();require(finalDisplay.isDisposed()&&shell.isDisposed(),"OS host termination bypasses vetoable Shell.Close");}finally{if(!finalDisplay.isDisposed())finalDisplay.dispose();MobileRuntime.uninstall(finalPlatform);}
	}
	static void testOwnedShellCoordinateMapping() {
		ContractPlatform platform=new ContractPlatform();MobileRuntime.install(platform);Display display=new Display();
		try {
			Shell parent=new Shell(display,SWT.NONE);parent.setBounds(30,40,250,300);
			Shell owned=new Shell(parent,SWT.NONE);owned.setBounds(100,110,150,180);
			Canvas canvas=new Canvas(owned,SWT.NONE);canvas.setBounds(7,9,40,40);
			require(canvas.toDisplay(4,5).equals(new Point(111,124)),"Owned Shell display coordinates stop at the native-rooted Shell");
			require(canvas.toControl(111,124).equals(new Point(4,5)),"Owned Shell inverse coordinates round-trip correctly");
			require(display.map(canvas,parent,4,5).equals(new Point(81,84)),"Display.map crosses owned Shell boundaries without double-counting ancestors");
		} finally {display.dispose();MobileRuntime.uninstall(platform);}
	}

	static void testMobileButtonLabelContracts() {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			Label label = new Label(shell, SWT.LEFT);
			long labelPeer = platform.lastCreated;
			label.setBounds(0, 0, 180, 30);
			label.setText("Mobile && Label");
			require("Mobile && Label".equals(label.getText()), "Label retains mnemonic source text");
			require(label.computeSize(SWT.DEFAULT, SWT.DEFAULT).x > 20, "Label text measures its own mobile GC");
			label.setAlignment(SWT.RIGHT);
			require(label.getAlignment() == SWT.RIGHT, "Label alignment setter/getter");
			require(!label.setFocus(), "Mobile Label remains nonfocusable");
			require(platform.controls.get(labelPeer).role == org.eclipse.swt.accessibility.ACC.ROLE_LABEL,
				"Label installs native accessibility static-text role");
			Button check = new Button(shell, SWT.CHECK);
			check.setText("Enable");
			check.setBounds(0, 40, 160, 32);
			long checkPeer = platform.lastCreated;
			require(platform.controls.get(labelPeer).kind == MobilePlatform.COMPOSITE,
				"Noninteractive Label uses nonclickable native peer");
			require(platform.controls.get(checkPeer).kind == MobilePlatform.CANVAS,
				"Button uses Android/iOS paint and accessibility-action native peer");
			Button radioA = new Button(shell, SWT.RADIO);
			radioA.setBounds(0, 80, 160, 32);
			Button radioB = new Button(shell, SWT.RADIO);
			radioB.setBounds(0, 120, 160, 32);
			Button push = new Button(shell, SWT.PUSH);
			push.setBounds(0, 160, 160, 32);
			push.setText("Go");
			shell.open();
			drain(display);
			require(platform.controls.get(checkPeer).frame != null, "Original Button paints through native peer image");
			AtomicInteger selections = new AtomicInteger();
			List<Boolean> during = new ArrayList<>();
			check.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
				selections.incrementAndGet();
				during.add(check.getSelection());
			}));
			platform.sink.input(checkPeer, SWT.MouseDown, 6, 8, 1, 0, '\0', 0, 1, 1);
			platform.sink.input(checkPeer, SWT.MouseUp, 6, 8, 1, 0, '\0', 0, 1, 2);
			drain(display);
			require(check.getSelection() && selections.get() == 1 && during.equals(List.of(true)),
				"Native down/up produces one Button Selection with updated state");
			require((platform.controls.get(checkPeer).state & org.eclipse.swt.accessibility.ACC.STATE_CHECKED) != 0,
				"Native checkbox accessibility state follows selection");
			check.setSelection(false);
			require(selections.get() == 1 && !check.getSelection(), "Programmatic selection does not notify");
			radioA.setSelection(true);
			radioB.notifyListeners(SWT.MouseDown, click(5, 5));
			radioB.notifyListeners(SWT.MouseUp, click(5, 5));
			require(radioB.getSelection() && !radioA.getSelection(),
				"Native radio input deselects contiguous sibling");
			push.setEnabled(false);
			AtomicInteger pushes = new AtomicInteger();
			push.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> pushes.incrementAndGet()));
			push.notifyListeners(SWT.MouseDown, click(5, 5));
			push.notifyListeners(SWT.MouseUp, click(5, 5));
			require(pushes.get() == 0, "Disabled Button ignores input");
			label.setImage(new Image(display, 12, 12));
			Image ownedExternally = label.getImage();
			label.setText("Text restored");
			require(label.getImage() == ownedExternally && !ownedExternally.isDisposed(),
				"Label image remains caller owned after restoring text");
			label.setImage(null);
			ownedExternally.dispose();
			shell.dispose();
			require(label.isDisposed() && check.isDisposed(), "Original Label and Button disposed with Shell");
		} finally {
			display.dispose();
			MobileRuntime.uninstall(platform);
		}
		require(platform.controls.isEmpty() && platform.images.isEmpty(),
				"Mobile Label/Button paint resources and peers fully released");
	}


	/** SWT owner-level contracts for Android/iOS-backed ProgressBar and Scale. */
	static void testMobileProgressAndScaleContracts() {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			ProgressBar bar = new ProgressBar(shell, SWT.HORIZONTAL);
			long barPeer = platform.lastCreated;
			bar.setBounds(0, 0, 160, 20);
			require(bar.getMinimum() == 0 && bar.getMaximum() == 100 && bar.getSelection() == 0,
					"ProgressBar starts at the original SWT range defaults");
			bar.setMinimum(20);
			require(bar.getSelection() == 20, "Minimum clamps ProgressBar selection");
			bar.setMaximum(10);
			require(bar.getMaximum() == 100, "Invalid ProgressBar maximum ignored");
			bar.setState(SWT.ERROR);
			require(bar.getState() == SWT.ERROR, "ProgressBar state is preserved");
			bar.setState(-1);
			require(bar.getState() == SWT.ERROR, "Invalid ProgressBar state ignored");
			require(platform.controls.get(barPeer).role == org.eclipse.swt.accessibility.ACC.ROLE_PROGRESSBAR,
					"ProgressBar publishes the native accessibility role");
			ProgressBar indeterminate = new ProgressBar(shell, SWT.INDETERMINATE);
			indeterminate.setBounds(0, 30, 160, 20);
			long indeterminatePeer = platform.lastCreated;
			require((platform.controls.get(indeterminatePeer).state
					& org.eclipse.swt.accessibility.ACC.STATE_BUSY) != 0,
					"Indeterminate progress advertises native busy state");
			Scale scale = new Scale(shell, SWT.HORIZONTAL);
			long scalePeer = platform.lastCreated;
			scale.setBounds(0, 60, 160, 28);
			require(platform.controls.get(scalePeer).kind == MobilePlatform.CANVAS,
					"Mobile Scale uses interactive original SWT Canvas peer");
			require(platform.controls.get(scalePeer).role == org.eclipse.swt.accessibility.ACC.ROLE_SLIDER,
					"Scale publishes slider role to Android/UIKit");
			shell.open();
			drain(display);
			int[] before = platform.controls.get(barPeer).frame.clone();
			bar.setSelection(80);
			drain(display);
			require(!Arrays.equals(before, platform.controls.get(barPeer).frame),
					"ProgressBar value paints into a real native peer image");
			AtomicInteger changes = new AtomicInteger();
			scale.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
				require(scale.getSelection() > 0, "Selection listener sees the already-updated Scale value");
				changes.incrementAndGet();
			}));
			platform.sink.input(scalePeer, SWT.MouseDown, 130, 12, 1, 0, '\0', 0, 1, 30);
			platform.sink.input(scalePeer, SWT.MouseUp, 130, 12, 1, 0, '\0', 0, 1, 31);
			drain(display);
			require(scale.getSelection() > 0 && changes.get() == 1,
					"Native touch down/up produces exactly one Scale Selection");
			scale.setSelection(50);
			require(changes.get() == 1, "Programmatic Scale selection is silent");
			scale.setMinimum(40);
			scale.setSelection(-10);
			require(scale.getSelection() == 40, "Scale clamps to changed minimum");
			scale.setMaximum(20);
			require(scale.getMaximum() == 100, "Invalid Scale maximum ignored");
			scale.setIncrement(0);
			require(scale.getIncrement() == 1, "Zero Scale increment ignored");
			Event keyboard = new Event();
			keyboard.keyCode = SWT.PAGE_UP;
			scale.notifyListeners(SWT.KeyDown, keyboard);
			require(scale.getSelection() == 50 && changes.get() == 2,
					"Scale keyboard PageUp applies page increment and sends Selection");
			platform.sink.accessibility(scalePeer, MobilePlatform.ACTION_INCREMENT);
			drain(display);
			require(scale.getSelection() == 51 && changes.get() == 3,
					"Native accessibility increment uses original Scale step and Selection owner");
			platform.sink.accessibility(scalePeer, MobilePlatform.ACTION_DECREMENT);
			drain(display);
			require(scale.getSelection() == 50 && changes.get() == 4,
					"Native accessibility decrement retains original Scale step and event");
			scale.setEnabled(false);
			platform.sink.input(scalePeer, SWT.MouseDown, 110, 12, 1, 0, '\0', 0, 1, 32);
			platform.sink.input(scalePeer, SWT.MouseUp, 110, 12, 1, 0, '\0', 0, 1, 33);
			drain(display);
			require(scale.getSelection() == 50 && changes.get() == 4,
					"Disabled Scale blocks native input");
			shell.dispose();
			require(indeterminate.isDisposed() && bar.isDisposed() && scale.isDisposed(),
					"Shell disposes progress timer and Scale native owners");
		} finally {
			display.dispose();
			MobileRuntime.uninstall(platform);
		}
		require(platform.controls.isEmpty() && platform.images.isEmpty(),
				"Range widgets release all native peers and images");
		require(platform.openedGraphics == platform.closedGraphics,
				"Range widgets close every native graphics context");
	}
}
