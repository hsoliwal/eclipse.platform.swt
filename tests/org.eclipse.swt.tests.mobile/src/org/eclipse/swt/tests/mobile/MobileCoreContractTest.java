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
		testDisposingParentRejectsNewChildren();
		testDisposingDisplayRejectsNewOwners();
		testShellOpenSurvivesListenerDisposal();
		testLayoutCallbacksMayDisposeControls();
		testAncestorStateReleasesFocus();
		testDisableFocusOutMayDisposeOwners();
		testFocusOutReentrancyPreservesOwner();
		testFailingFocusOutStillReleasesControl();
		testDisplayExecuteNullContract();
		testDisplayDisposeExecNullContract();
		testUnsupportedNativeEventPosting();
		testNativeSafeAreaSnapshotContracts();
		testMobilePublicResourceAliases();
		testFontDataDefaultLocaleNormalization();
		testBorderClientGeometryAndPainting();
		testEmptyBorderClientReleasesImage();
		testOwnedShellCoordinateMapping();
		testMobileButtonLabelContracts();
		testMobileProgressAndScaleContracts();
		testMobileSashContracts();
		testMobileListContracts();
		testMobileOriginalTextContracts();
		testMobileComboContracts();
		testMobileOriginalTableContracts();
		System.out.println("PASS: "+assertions+" SWT/Nebula owner-contract assertions. Native device qualification is separate.");
	}
	static void testNativeSafeAreaSnapshotContracts() throws Exception {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			require(Arrays.equals(display.getSafeAreaInsets(), new int[4]),
					"Unconfigured native host reports zero safe-area insets");
			platform.safeInsets = new int[] {3, 24, 5, 17};
			int[] first = display.getSafeAreaInsets();
			require(Arrays.equals(first, platform.safeInsets),
					"Mobile Display reports left/top/right/bottom in SWT logical coordinates");
			first[0] = 200;
			require(display.getSafeAreaInsets()[0] == 3,
					"Caller mutation cannot change native safe-area state");
			platform.safeInsets = new int[] {0, 40, 0, 0};
			require(Arrays.equals(display.getSafeAreaInsets(), platform.safeInsets),
					"Safe-area query observes subsequent native orientation/system UI updates");
			AtomicReference<Throwable> wrongThread = new AtomicReference<>();
			Thread caller = new Thread(() -> {
				try { display.getSafeAreaInsets(); }
				catch (Throwable problem) { wrongThread.set(problem); }
			}, "safe-area-non-swt-thread");
			caller.start();
			caller.join(3000);
			require(!caller.isAlive(), "Safe-area query on wrong thread returns promptly");
			require(wrongThread.get() instanceof SWTException error
					&& error.code == SWT.ERROR_THREAD_INVALID_ACCESS,
					"Safe-area query preserves SWT Display thread ownership");
			platform.safeInsets = null;
			try {
				display.getSafeAreaInsets();
				throw new AssertionError("Null native safe-area must fail explicitly");
			} catch (IllegalStateException expected) { /* Invalid platform transport */ }
			platform.safeInsets = new int[] {1, 2, 3};
			try {
				display.getSafeAreaInsets();
				throw new AssertionError("Wrong native safe-area length must fail explicitly");
			} catch (IllegalStateException expected) { /* Invalid platform transport */ }
			platform.safeInsets = new int[] {1, -2, 3, 4};
			try {
				display.getSafeAreaInsets();
				throw new AssertionError("Negative native safe-area must fail explicitly");
			} catch (IllegalStateException expected) { /* Invalid platform transport */ }
		} finally {
			display.dispose();
			MobileRuntime.uninstall(platform);
		}
		expectSwt(SWT.ERROR_DEVICE_DISPOSED, display::getSafeAreaInsets);
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
	static void testFontDataDefaultLocaleNormalization() {
		FontData initial=new FontData("sans-serif",12,SWT.NORMAL);
		FontData empty=new FontData("sans-serif",12,SWT.NORMAL);empty.setLocale("");
		FontData reset=new FontData("sans-serif",12,SWT.NORMAL);reset.setLocale("en_US");reset.setLocale(null);
		require(initial.getLocale().equals("")&&empty.getLocale().equals("")&&reset.getLocale().equals(""),"Default, empty and reset locales expose the same default selection");
		require(initial.equals(empty)&&empty.equals(reset)&&reset.equals(initial),"Equivalent default locale requests compare equally");
		require(initial.hashCode()==empty.hashCode()&&empty.hashCode()==reset.hashCode(),"Equivalent default locale requests have equal hashes");
		java.util.HashSet<FontData> requests=new java.util.HashSet<>(List.of(initial,empty,reset));
		require(requests.size()==1,"Default locale spelling cannot duplicate a font request in hashed collections");
		FontData copied=new FontData(empty);
		require(copied.equals(initial)&&copied.hashCode()==initial.hashCode(),"FontData copy preserves normalized default locale identity");
		FontData serialized=new FontData(initial.toString());
		require(serialized.equals(initial),"Default locale survives the current FontData string constructor");
		FontData specific=new FontData(initial);specific.setLocale("en_US");
		FontData specificCopy=new FontData(specific);
		require(specificCopy.equals(specific)&&specificCopy.getLocale().equals("en_US"),"Existing nonempty locale values and copy behavior survive");
		specificCopy.setLocale(null);
		require(specificCopy.equals(initial)&&specific.getLocale().equals("en_US"),"Resetting a copied locale is independent of the original descriptor");
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
	static void testDisposingParentRejectsNewChildren() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Shell shell = new Shell(display);
			Composite parent = new Composite(shell, SWT.NONE);
			Canvas child = new Canvas(parent, SWT.NONE);
			AtomicReference<Canvas> escaped = new AtomicReference<>();
			AtomicInteger remainingListeners = new AtomicInteger();
			child.addDisposeListener(e -> escaped.set(new Canvas(parent, SWT.NONE)));
			child.addDisposeListener(e -> remainingListeners.incrementAndGet());
			expectSwt(SWT.ERROR_WIDGET_DISPOSED, parent::dispose);
			require(escaped.get() == null, "A child Dispose listener cannot create a live child under its disposing parent");
			require(parent.isDisposed() && child.isDisposed() && remainingListeners.get() == 1,
					"Rejected child creation does not interrupt remaining listeners or subtree disposal");
			require(shell.getChildren().length == 0 && platform.controls.size() == 1,
					"Rejected child creation leaves only the owning Shell peer");
		} finally { try { display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testDisposingDisplayRejectsNewOwners() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Shell shell = new Shell(display);
			AtomicReference<Canvas> escapedChild = new AtomicReference<>();
			AtomicReference<Shell> escapedShell = new AtomicReference<>();
			AtomicInteger remainingCallbacks = new AtomicInteger();
			display.addListener(SWT.Dispose, e -> escapedChild.set(new Canvas(shell, SWT.NONE)));
			display.disposeExec(() -> escapedShell.set(new Shell(display)));
			display.disposeExec(remainingCallbacks::incrementAndGet);
			expectSwt(SWT.ERROR_DEVICE_DISPOSED, display::dispose);
			require(escapedChild.get() == null && escapedShell.get() == null,
					"Display disposal rejects both parent-based and Display-based widget construction before creating peers");
			require(display.isDisposed() && shell.isDisposed() && Display.getCurrent() == null,
					"Rejected construction still releases the Display and its existing Shell");
			require(remainingCallbacks.get() == 1 && platform.disconnects == 1 && platform.controls.isEmpty() && platform.images.isEmpty(),
					"Later dispose callbacks and native disconnect run without retained owners");
		} finally { try { if (!display.isDisposed()) display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testShellOpenSurvivesListenerDisposal() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Shell shown = new Shell(display);
			shown.addListener(SWT.Show, e -> shown.dispose());
			shown.open();
			require(shown.isDisposed(), "Shell.open returns normally when its Show listener disposes the Shell");
			Shell focused = new Shell(display);
			focused.addListener(SWT.FocusIn, e -> focused.dispose());
			focused.open();
			require(focused.isDisposed() && display.getFocusControl() == null,
					"Shell.open returns normally when its FocusIn listener disposes the Shell");
			require(platform.controls.isEmpty() && platform.images.isEmpty(), "Reentrant Shell opening leaves no native peers or images");
		} finally { try { display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testLayoutCallbacksMayDisposeControls() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Layout disposingLayout = new Layout() {
				@Override protected Point computeSize(Composite composite, int wHint, int hHint, boolean flushCache) { return new Point(40, 40); }
				@Override protected void layout(Composite composite, boolean flushCache) { composite.dispose(); }
			};
			Shell disposedOnOpen = new Shell(display);
			disposedOnOpen.setLayout(disposingLayout);
			disposedOnOpen.open();
			require(disposedOnOpen.isDisposed(), "Shell.open tolerates its custom Layout disposing the Shell");
			Shell shell = new Shell(display);
			Composite disposedOnResize = new Composite(shell, SWT.NONE);
			disposedOnResize.setLayout(disposingLayout);
			disposedOnResize.setSize(40, 40);
			require(disposedOnResize.isDisposed(), "A resize-triggered Layout can dispose its control without a later redraw");
			Composite parent = new Composite(shell, SWT.NONE);
			Composite first = new Composite(parent, SWT.NONE), removed = new Composite(parent, SWT.NONE);
			AtomicInteger layouts = new AtomicInteger();
			first.setLayout(new Layout() {
				@Override protected Point computeSize(Composite composite, int wHint, int hHint, boolean flushCache) { return new Point(40, 40); }
				@Override protected void layout(Composite composite, boolean flushCache) {
					require(flushCache, "Recursive layout retains the requested changed flag");
					layouts.incrementAndGet(); removed.dispose();
				}
			});
			parent.setLayoutDeferred(true); parent.layout(true, true);
			require(layouts.get() == 0, "Deferred layout does not enter child layouts");
			parent.setLayoutDeferred(false);
			require(layouts.get() == 1 && removed.isDisposed() && !first.isDisposed(),
					"Recursive layout skips a disposed snapshot sibling and preserves live children");
		} finally { try { display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testAncestorStateReleasesFocus() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Shell shell = new Shell(display); Composite parent = new Composite(shell, SWT.NONE);
			Canvas canvas = new Canvas(parent, SWT.NONE); long peer = platform.lastCreated;
			parent.setBounds(0, 0, 80, 80); canvas.setBounds(0, 0, 40, 40); shell.open(); drain(display);
			AtomicInteger focusOut = new AtomicInteger(); canvas.addListener(SWT.FocusOut, e -> focusOut.incrementAndGet());
			require(canvas.setFocus(), "Visible enabled child accepts focus");
			platform.sink.input(peer, SWT.FocusIn, 0, 0, 0, 0, '\0', 0, 0, 101);
			parent.setEnabled(false);
			require(display.getFocusControl() == null && focusOut.get() == 1, "Disabling an ancestor clears descendant focus exactly once");
			platform.sink.input(peer, SWT.FocusOut, 0, 0, 0, 0, '\0', 0, 0, 102); drain(display);
			require(display.getFocusControl() == null && focusOut.get() == 1, "Queued native focus acknowledgements cannot restore disabled focus");
			parent.setEnabled(true); require(canvas.setFocus(), "Re-enabled child can regain focus");
			platform.sink.input(peer, SWT.FocusIn, 0, 0, 0, 0, '\0', 0, 0, 103);
			parent.setVisible(false);
			require(display.getFocusControl() == null && focusOut.get() == 2, "Hiding an ancestor clears descendant focus exactly once");
			drain(display);
			require(display.getFocusControl() == null && focusOut.get() == 2, "Queued FocusIn cannot restore focus to a hidden descendant");
			parent.setVisible(true); require(canvas.setFocus(), "Shown child can regain focus");
			canvas.addListener(SWT.FocusOut, e -> canvas.dispose()); parent.setEnabled(false);
			require(canvas.isDisposed() && display.getFocusControl() == null && !platform.controls.containsKey(peer),
					"FocusOut may dispose the child while an ancestor is being disabled");
		} finally { try { display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testFocusOutReentrancyPreservesOwner() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Shell shell = new Shell(display); Canvas first = new Canvas(shell, SWT.NONE);
			Canvas requested = new Canvas(shell, SWT.NONE), redirected = new Canvas(shell, SWT.NONE);
			shell.open(); require(first.setFocus(), "Initial child receives focus");
			AtomicInteger requestedFocusIn = new AtomicInteger(); requested.addListener(SWT.FocusIn, e -> requestedFocusIn.incrementAndGet());
			first.addListener(SWT.FocusOut, e -> redirected.setFocus());
			require(!requested.setFocus(), "An outer focus request reports failure when FocusOut redirects focus");
			require(display.getFocusControl() == redirected && requestedFocusIn.get() == 0,
					"A nested focus request retains its owner without a stale outer FocusIn");
			Canvas disposedOnFocus = new Canvas(shell, SWT.NONE); long peer = platform.lastCreated;
			disposedOnFocus.addListener(SWT.FocusIn, e -> disposedOnFocus.dispose());
			require(!disposedOnFocus.setFocus() && disposedOnFocus.isDisposed() && display.getFocusControl() == null,
					"FocusIn disposal clears focus and makes the request report failure");
			require(!platform.controls.containsKey(peer), "FocusIn disposal releases the native peer");
		} finally { try { display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testDisableFocusOutMayDisposeOwners() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Shell shell = new Shell(display); shell.open(); drain(display);
			for (boolean ancestor : new boolean[] {false, true}) {
				Composite parent = new Composite(shell, SWT.BORDER); parent.setBounds(0, 0, 80, 80);
				Canvas canvas = new Canvas(parent, SWT.BORDER); canvas.setBounds(0, 0, 40, 40); long peer = platform.lastCreated;
				require(canvas.setFocus(), "Child receives focus before disabling its owner");
				canvas.addListener(SWT.FocusOut, e -> { if (ancestor) parent.dispose(); else canvas.dispose(); });
				if (ancestor) parent.setEnabled(false); else canvas.setEnabled(false);
				require(canvas.isDisposed() && display.getFocusControl() == null && !platform.controls.containsKey(peer),
						"Disabling may dispose the focused owner or its ancestor without a later redraw on a disposed widget");
				if (!parent.isDisposed()) parent.dispose();
			}
		} finally { try { display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testFailingFocusOutStillReleasesControl() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Shell shell = new Shell(display); Canvas canvas = new Canvas(shell, SWT.NONE); long peer = platform.lastCreated;
			canvas.setSize(40, 40); shell.open(); drain(display); require(canvas.setFocus(), "Child receives focus before disposal");
			IllegalArgumentException expected = new IllegalArgumentException("FocusOut listener failure");
			canvas.addListener(SWT.FocusOut, e -> { throw expected; });
			try { canvas.dispose(); throw new AssertionError("Focused disposal must deliver FocusOut"); }
			catch (IllegalArgumentException actual) { require(actual == expected, "FocusOut failure is propagated unchanged"); }
			require(canvas.isDisposed() && display.getFocusControl() == null && shell.getChildren().length == 0,
					"A failing FocusOut listener does not retain the disposed child or focus");
			require(!platform.controls.containsKey(peer) && platform.openedGraphics == platform.closedGraphics,
					"A failing FocusOut listener does not retain native control or graphics owners");
		} finally { try { display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testDisplayExecuteNullContract() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			try { display.execute(null); throw new AssertionError("Executor must reject a null command"); }
			catch (NullPointerException expected) { require(true, "Display.execute uses the Executor null-command contract"); }
		} finally { try { display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testDisplayDisposeExecNullContract() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			AtomicInteger calls = new AtomicInteger();
			display.disposeExec(null); display.disposeExec(calls::incrementAndGet); display.dispose();
			require(calls.get() == 1 && display.isDisposed(), "A null disposeExec is ignored without losing later disposal callbacks");
		} finally { try { if (!display.isDisposed()) display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testUnsupportedNativeEventPosting() throws Exception {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Shell shell = new Shell(display); Canvas canvas = new Canvas(shell, SWT.NONE);
			canvas.setBounds(0, 0, 40, 40); shell.open(); canvas.setFocus(); drain(display);
			AtomicInteger delivered = new AtomicInteger();
			for (int type : new int[] {SWT.KeyDown, SWT.KeyUp, SWT.MouseDown, SWT.MouseUp, SWT.MouseMove, SWT.MouseWheel}) {
				canvas.addListener(type, e -> delivered.incrementAndGet());
				Event event = new Event(); event.type = type; event.x = 4; event.y = 5; event.keyCode = 'a'; event.character = 'a'; event.button = 1;
				require(!display.post(event), "Mobile native input injection reports unsupported for event type " + type);
				require(event.type == type && event.x == 4 && event.y == 5, "Refused native posting leaves the caller's event unchanged");
			}
			try { display.post(null); throw new AssertionError("post requires an Event"); }
			catch (IllegalArgumentException expected) { }
			AtomicReference<Throwable> failed = new AtomicReference<>(); CountDownLatch completed = new CountDownLatch(1);
			Thread caller = new Thread(() -> {
				try { Event event = new Event(); event.type = SWT.KeyDown; require(!display.post(event), "Native post refusal is callable from a worker thread"); }
				catch (Throwable failure) { failed.set(failure); }
				finally { completed.countDown(); }
			}, "mobile post caller");
			caller.start(); await(completed); caller.join(3000);
			if (failed.get() != null) throw new AssertionError("Worker post failed", failed.get());
			drain(display); require(delivered.get() == 0, "Unsupported native posting does not synthesize Java listener events");
			display.dispose();
			expectSwt(SWT.ERROR_DEVICE_DISPOSED, () -> display.post(new Event()));
			expectSwt(SWT.ERROR_DEVICE_DISPOSED, () -> display.post(null));
		} finally { try { if (!display.isDisposed()) display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testMobilePublicResourceAliases() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Color color = new Color(display, 12, 34, 56, 78);
			require(color.internalArgb() == 0x4e0c2238 && color.internalArgb() == color.internal_argb(),
					"Existing public color spelling retains the exact RGBA value of the current owner");
			color.dispose(); expectSwt(SWT.ERROR_GRAPHIC_DISPOSED, color::internalArgb);
			Font font = new Font(display, "sans-serif", 12, SWT.NORMAL);
			require(font.internalPixelSize() == 16, "Existing public font pixel accessor converts 12 points at 96 DPI to 16 pixels");
			font.dispose(); expectSwt(SWT.ERROR_GRAPHIC_DISPOSED, font::internalPixelSize);
			Font minimum = new Font(display, "sans-serif", 0, SWT.NORMAL);
			try { require(minimum.internalPixelSize() == 1, "Existing public font pixel accessor retains its one-pixel minimum"); }
			finally { minimum.dispose(); }
		} finally { try { display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testNativeBorderReliefFailClosed() {
		ContractPlatform platform = new ContractPlatform();
		// master's mobile Control delegates relief to the transport; this contract
		// covers a transport without it (MobilePlatform.setBorderRelief's default).
		platform.refuseBorderRelief = true;
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			Composite control = new Composite(shell, SWT.BORDER);
			int original = control.getBorderWidth();
			require(control.getBorderRelief() == SWT.DEFAULT, "Mobile relief defaults to platform border");
			control.setBorderRelief(SWT.DEFAULT);
			for (int relief : new int[] { SWT.SHADOW_NONE, SWT.SHADOW_IN,
					SWT.SHADOW_OUT, SWT.SHADOW_ETCHED_IN, SWT.SHADOW_ETCHED_OUT }) {
				try {
					control.setBorderRelief(relief);
					throw new AssertionError("Mobile relief must not claim an unimplemented native bezel");
				} catch (org.eclipse.swt.SWTError unsupported) {
					require(unsupported.code == SWT.ERROR_NOT_IMPLEMENTED,
							"Mobile native relief fails with ERROR_NOT_IMPLEMENTED");
				}
			}
			try {
				control.setBorderRelief(SWT.PUSH | SWT.BORDER);
				throw new AssertionError("Invalid relief must be rejected");
			} catch (IllegalArgumentException expected) {
				// The original peer is unchanged.
			}
			require(control.getBorderRelief() == SWT.DEFAULT
					&& control.getBorderWidth() == original,
					"Mobile fail-closed relief preserves native trim");
		} finally {
			if (!display.isDisposed()) display.dispose();
			MobileRuntime.uninstall(platform);
		}
	}

	static void testBorderClientGeometryAndPainting() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Shell shell = new Shell(display, SWT.NONE);
			Composite parent = new Composite(shell, SWT.BORDER); parent.setBounds(10, 20, 100, 80); long parentPeer = platform.lastCreated;
			Canvas child = new Canvas(parent, SWT.BORDER); child.setBounds(4, 5, 40, 30); long childPeer = platform.lastCreated;
			require(parent.getClientArea().equals(new Rectangle(0, 0, 98, 78)), "Bordered parent retains its inset client extent");
			require(child.toDisplay(0, 0).equals(new Point(16, 27)) && child.toControl(16, 27).equals(new Point(0, 0)),
					"Nested bordered coordinates map from client origin and round-trip");
			require(child.computeTrim(0, 0, 38, 28).equals(new Rectangle(-1, -1, 40, 30)), "Client extent and outer trim remain inverse");
			parent.setBackground(display.getSystemColor(SWT.COLOR_BLUE)); child.setBackground(display.getSystemColor(SWT.COLOR_RED));
			shell.open(); drain(display);
			require(platform.controls.get(parentPeer).frame.length == 98 * 78 && platform.controls.get(childPeer).frame.length == 38 * 28,
					"Native presentation receives client-sized images rather than outer bounds");
			GC childGC = new GC(child);
			try { require(childGC.getClipping().equals(new Rectangle(0, 0, 38, 28)), "Control GC starts at client origin with client clipping"); }
			finally { childGC.dispose(); }
			Image target = new Image(display, 100, 80); GC printer = new GC(target);
			try {
				require(parent.print(printer), "Bordered parent prints its own and child client images");
				ImageData pixels = target.getImageData();
				require(pixels.palette.getRGB(pixels.getPixel(0, 0)).equals(new RGB(128, 128, 128))
						&& pixels.palette.getRGB(pixels.getPixel(99, 79)).equals(new RGB(128, 128, 128)),
						"Printed border occupies the outer one-pixel bands");
				require(pixels.palette.getRGB(pixels.getPixel(1, 1)).equals(new RGB(0, 0, 255)), "Parent client begins one pixel inside its border");
				require(pixels.palette.getRGB(pixels.getPixel(5, 6)).equals(new RGB(128, 128, 128))
						&& pixels.palette.getRGB(pixels.getPixel(6, 7)).equals(new RGB(255, 0, 0)),
						"Printed child outer border and client pixels match toDisplay offsets");
				child.setLocation(-20, -20); drain(display); parent.print(printer); pixels = target.getImageData();
				require(pixels.palette.getRGB(pixels.getPixel(0, 0)).equals(new RGB(128, 128, 128)),
						"A child outside the client area cannot paint over its parent's border");
			} finally { printer.dispose(); target.dispose(); }
			Shell owned = new Shell(shell, SWT.BORDER); owned.setBounds(100, 110, 150, 180);
			Canvas ownedChild = new Canvas(owned, SWT.BORDER); ownedChild.setBounds(7, 9, 40, 40);
			require(ownedChild.toDisplay(0, 0).equals(new Point(109, 121)), "Owned Shell client mapping stops at its native root while retaining its own border");
		} finally { try { display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
	}

	static void testEmptyBorderClientReleasesImage() {
		ContractPlatform platform = new ContractPlatform(); MobileRuntime.install(platform); Display display = new Display();
		try {
			Shell shell = new Shell(display, SWT.NONE); Canvas canvas = new Canvas(shell, SWT.BORDER); canvas.setSize(10, 10);
			AtomicInteger paints = new AtomicInteger(); canvas.addPaintListener(e -> paints.incrementAndGet()); shell.open(); drain(display);
			GC lease = new GC(canvas); int images = platform.images.size(), before = paints.get();
			try {
				canvas.setSize(2, 2); canvas.redraw(); drain(display);
				require(paints.get() == before && platform.images.size() == images, "Empty client stops painting while the outstanding GC keeps its old image lease");
			} finally { lease.dispose(); }
			require(platform.images.size() == images - 1, "Empty client releases its retained image after the final GC lease");
			try { GC invalid = new GC(canvas); invalid.dispose(); throw new AssertionError("Empty client must not allocate a drawing image"); }
			catch (IllegalArgumentException expected) { }
			Image target = new Image(display, 2, 2); GC printer = new GC(target);
			try {
				require(canvas.print(printer), "Positive outer bounds can print a border without an empty client GC");
				ImageData pixels = target.getImageData();
				for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++)
					require(pixels.palette.getRGB(pixels.getPixel(x, y)).equals(new RGB(128, 128, 128)), "Tiny border fills its outer bounds without overlap leaks");
			} finally { printer.dispose(); target.dispose(); }
			canvas.setSize(3, 3); drain(display);
			require(paints.get() == before + 1, "Growing to a one-pixel client resumes painting exactly once");
		} finally { try { display.dispose(); } finally { MobileRuntime.uninstall(platform); } }
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


	/** SWT owner-level contracts for the Android/iOS-backed Sash: the desktop event contract on one native peer. */
	static void testMobileSashContracts() {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			shell.setSize(300, 200);
			Sash sash = new Sash(shell, SWT.VERTICAL);
			long peer = platform.lastCreated;
			sash.setBounds(50, 0, 3, 100);
			require(platform.controls.get(peer).kind == MobilePlatform.CANVAS,
					"Mobile Sash uses an interactive original SWT Canvas peer");
			require(platform.controls.get(peer).role == org.eclipse.swt.accessibility.ACC.ROLE_SLIDER,
					"Sash publishes an adjustable role to Android/UIKit");
			ArrayList<String> events = new ArrayList<>();
			int[] veto = {Integer.MAX_VALUE};
			sash.addListener(SWT.Selection, e -> {
				events.add(e.detail + "@" + e.x);
				if (e.x > veto[0]) e.doit = false;
				if (e.detail == 0 && e.doit) sash.setBounds(e.x, e.y, e.width, e.height);
			});
			shell.open();
			drain(display);
			platform.sink.input(peer, SWT.MouseDown, 1, 10, 1, 0, '\0', 0, 1, 10);
			platform.sink.input(peer, SWT.MouseMove, 21, 10, 0, 0, '\0', SWT.BUTTON1, 1, 11);
			platform.sink.input(peer, SWT.MouseUp, 21, 10, 1, 0, '\0', 0, 1, 12);
			drain(display);
			require(events.equals(java.util.List.of(SWT.DRAG + "@50", SWT.DRAG + "@70", "0@70")),
					"Press and move send SWT.DRAG at the position the sash would take, release the final Selection: " + events);
			require(sash.getBounds().x == 70, "The listener moved the sash; the sash does not move itself");
			events.clear();
			veto[0] = 80;
			platform.sink.input(peer, SWT.MouseDown, 1, 10, 1, 0, '\0', 0, 1, 20);
			platform.sink.input(peer, SWT.MouseMove, 6, 10, 0, 0, '\0', SWT.BUTTON1, 1, 21);
			platform.sink.input(peer, SWT.MouseMove, 21, 10, 0, 0, '\0', SWT.BUTTON1, 1, 22);
			platform.sink.input(peer, SWT.MouseUp, 21, 10, 1, 0, '\0', 0, 1, 23);
			drain(display);
			require(events.equals(java.util.List.of(SWT.DRAG + "@70", SWT.DRAG + "@75", SWT.DRAG + "@90", "0@75")),
					"A refused position is not kept: the release goes to the last accepted one: " + events);
			require(sash.getBounds().x == 75, "The release placed the sash at the last accepted position");
			Sash smooth = new Sash(shell, SWT.HORIZONTAL | SWT.SMOOTH);
			long smoothPeer = platform.lastCreated;
			smooth.setBounds(0, 120, 200, 3);
			ArrayList<String> smoothEvents = new ArrayList<>();
			smooth.addListener(SWT.Selection, e -> {
				smoothEvents.add(e.detail + "@" + e.y);
				smooth.setBounds(e.x, e.y, e.width, e.height);
			});
			drain(display);
			platform.sink.input(smoothPeer, SWT.MouseDown, 10, 1, 1, 0, '\0', 0, 1, 30);
			platform.sink.input(smoothPeer, SWT.MouseMove, 10, 11, 0, 0, '\0', SWT.BUTTON1, 1, 31);
			platform.sink.input(smoothPeer, SWT.MouseUp, 10, 11, 1, 0, '\0', 0, 1, 32);
			drain(display);
			require(smoothEvents.equals(java.util.List.of("0@120", "0@130", "0@130")),
					"SWT.SMOOTH sends plain Selection events while dragging: " + smoothEvents);
			events.clear();
			veto[0] = Integer.MAX_VALUE;
			Event across = new Event();
			across.keyCode = SWT.ARROW_RIGHT;
			sash.notifyListeners(SWT.KeyDown, across);
			Event fine = new Event();
			fine.keyCode = SWT.ARROW_LEFT;
			fine.stateMask = SWT.CTRL;
			sash.notifyListeners(SWT.KeyDown, fine);
			Event along = new Event();
			along.keyCode = SWT.ARROW_UP;
			sash.notifyListeners(SWT.KeyDown, along);
			require(events.equals(java.util.List.of("0@84", "0@83")),
					"Arrows across the sash step 9, 1 with Ctrl; arrows along it do nothing: " + events);
			platform.sink.accessibility(peer, MobilePlatform.ACTION_INCREMENT);
			drain(display);
			require(sash.getBounds().x == 92, "Native accessibility increment moves the sash one step through its Selection owner");
			sash.setEnabled(false);
			events.clear();
			platform.sink.input(peer, SWT.MouseDown, 1, 10, 1, 0, '\0', 0, 1, 40);
			drain(display);
			require(events.isEmpty(), "A disabled sash sends nothing");
			sash.setEnabled(true);
			sash.addListener(SWT.Selection, e -> {
				if (e.detail == SWT.DRAG && e.x > 100) sash.dispose();
			});
			platform.sink.input(peer, SWT.MouseDown, 1, 10, 1, 0, '\0', 0, 1, 50);
			platform.sink.input(peer, SWT.MouseMove, 30, 10, 0, 0, '\0', SWT.BUTTON1, 1, 51);
			platform.sink.input(peer, SWT.MouseUp, 30, 10, 1, 0, '\0', 0, 1, 52);
			drain(display);
			require(sash.isDisposed(), "A listener may dispose the sash during a drag; the stale native callbacks are ignored");
		} finally {
			display.dispose();
			MobileRuntime.uninstall(platform);
		}
		require(platform.controls.isEmpty() && platform.images.isEmpty(), "Sashes release all native peers and images");
		require(platform.openedGraphics == platform.closedGraphics, "Sashes close every native graphics context");
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

	/** Original List's native-peer input, segmented state and virtual row contracts. */
	static void testMobileListContracts() {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List(shell, SWT.MULTI | SWT.V_SCROLL);
			long peer = platform.lastCreated;
			list.setBounds(0, 0, 280, 120);
			require((list.getStyle() & SWT.V_SCROLL) != 0, "Mobile List retains SWT scroll style");
			require(platform.controls.get(peer).kind == MobilePlatform.CANVAS, "List owns actual Android/UIKit paintable input peer");
			require(platform.controls.get(peer).role == org.eclipse.swt.accessibility.ACC.ROLE_LIST,
					"List publishes native role for Android and UIKit accessibility");
			list.setItems("alpha", "beta", "gamma", "delta");
			list.add("extra", 2);
			require(Arrays.equals(list.getItems(), new String[] {"alpha", "beta", "extra", "gamma", "delta"}),
					"List insert preserves indexed array contents");
			list.setSelection(new int[] {1, 3});
			require(Arrays.equals(list.getSelectionIndices(), new int[] {1, 3}),
					"List multi-selection returns stable sorted bit indices");
			list.remove(0);
			require(Arrays.equals(list.getSelectionIndices(), new int[] {0, 2}),
					"List removal shifts selected logical indices without notifying");
			list.clear(1);
			require(list.getItem(1).isEmpty(), "List clear retains logical slot");
			AtomicInteger selections = new AtomicInteger();
			list.addListener(SWT.Selection, e -> selections.incrementAndGet());
			shell.open();
			drain(display);
			require(platform.controls.get(peer).frame != null, "List rows paint through real SWT mobile Image/GC");
			list.setSelection(1);
			require(selections.get() == 0, "Programmatic List selection is silent");
			int y = list.getItemHeight() / 2;
			platform.sink.input(peer, SWT.MouseDown, 10, y, 1, 0, '\0', 0, 1, 1);
			platform.sink.input(peer, SWT.MouseUp, 10, y, 1, 0, '\0', 0, 1, 2);
			drain(display);
			require(list.getSelectionIndex() == 0 && selections.get() == 1,
					"Native List tap updates index and emits Selection once");
			org.eclipse.swt.widgets.List virtual = new org.eclipse.swt.widgets.List(
					shell, SWT.SINGLE | SWT.VIRTUAL | SWT.V_SCROLL);
			virtual.setBounds(0, 0, 280, 100);
			AtomicInteger loaded = new AtomicInteger();
			virtual.addListener(SWT.SetData, e -> {
				loaded.incrementAndGet();
				virtual.setItem(e.index, "virtual-" + e.index);
			});
			virtual.setItemCount(1_000_000_000);
			virtual.setTopIndex(999_999_998);
			drain(display);
			require(loaded.get() <= 7 && loaded.get() >= 2,
					"Billion-row virtual List materializes only visible SetData cells");
			require(virtual.getItem(999_999_999).equals("virtual-999999999"),
					"VIRTUAL GetItem resolves the final logical index");
			int selectionBeforeScroll = virtual.getSelectionIndex();
			platform.sink.accessibility(platform.lastCreated, MobilePlatform.ACTION_INCREMENT);
			drain(display);
			require(virtual.getTopIndex() == 999_999_999
					&& virtual.getSelectionIndex() == selectionBeforeScroll,
					"Native accessibility scroll pages the List without synthesizing Selection");
			int previous = loaded.get();
			virtual.clear(999_999_999);
			require(virtual.getItem(999_999_999).equals("virtual-999999999")
					&& loaded.get() == previous + 1, "Virtual clear triggers exactly one new SetData");
			virtual.setItemCount(1);
			require(virtual.getItemCount() == 1 && virtual.getTopIndex() == 0,
					"Sparse virtual List shrinks from a billion without per-row enumeration");
			shell.dispose();
			require(list.isDisposed() && virtual.isDisposed(),
					"Original list native owners disposed with their SWT parent");
		} finally {
			display.dispose();
			MobileRuntime.uninstall(platform);
		}
		require(platform.controls.isEmpty() && platform.images.isEmpty(),
				"List releases native control and image ownership");
		require(platform.openedGraphics == platform.closedGraphics,
				"List closes all native SWT graphics contexts");
	}

	/** Original SWT Text Verify/Modify with queued Android/UIKit editor snapshots. */
	static void testMobileOriginalTextContracts() {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			Text editor = new Text(shell, SWT.SINGLE | SWT.BORDER);
			long peer = platform.lastCreated;
			require(platform.controls.get(peer).kind == MobilePlatform.TEXT,
					"Original Text receives a native Android/UIKit text peer");
			editor.setBounds(0, 0, 210, 40);
			require(platform.controls.get(peer).text.isEmpty(), "Native editor starts empty");
			java.util.ArrayList<String> verify = new java.util.ArrayList<>();
			java.util.ArrayList<String> modified = new java.util.ArrayList<>();
			editor.addVerifyListener(e -> {
				verify.add(e.start + ":" + e.end + "=" + e.text);
				if ("X".equals(e.text)) e.text = "Q";
				if ("!".equals(e.text)) e.doit = false;
			});
			editor.addModifyListener(e -> modified.add(editor.getText()));
			editor.setText("abc");
			require("abc".equals(editor.getText()) && modified.equals(java.util.List.of("abc")),
					"Programmatic setText verifies then modifies canonical Java string");
			editor.append("def");
			editor.setSelection(3, 5);
			require(editor.getSelection().equals(new Point(3, 5))
					&& platform.controls.get(peer).selectionStart == 3
					&& platform.controls.get(peer).selectionEnd == 5,
					"UTF-16 selection is published to native text owner");
			editor.insert("X");
			require(editor.getText().equals("abcQf")
					&& modified.get(modified.size() - 1).equals("abcQf"),
					"Original Verify listener rewrites replacement before Modify");
			int countBefore = modified.size();
			editor.append("!");
			require(editor.getText().equals("abcQf") && modified.size() == countBefore,
					"Vetoed Verify never mutates text or emits Modify");
			shell.open();
			drain(display);
			platform.sink.textChanged(peer, "abcQfZ", 6, 6);
			drain(display);
			require(editor.getText().equals("abcQfZ")
					&& platform.controls.get(peer).text.equals(editor.getText())
					&& editor.getCaretPosition() == 6
					&& modified.size() == countBefore + 1,
					"Native Text input is applied on SWT thread with exactly one Modify");
			platform.sink.textChanged(peer, "abcQfZ!", 7, 7);
			drain(display);
			require(editor.getText().equals("abcQfZ")
					&& platform.controls.get(peer).text.equals(editor.getText()),
					"Native Verify veto rolls back the OS editor snapshot");
			editor.setMessage("Search");
			require(platform.controls.get(peer).hint.equals("Search"),
					"Text message is forwarded to native editor placeholder");
			editor.setTextLimit(7);
			editor.append("abcdef");
			require(editor.getText().length() == 7,
					"TextLimit caps accepted UTF-16 insertion without exceeding limit");
			editor.setEditable(false);
			String old = editor.getText();
			platform.sink.textChanged(peer, "native bypass", 13, 13);
			drain(display);
			require(editor.getText().equals(old) && !platform.controls.get(peer).editable,
					"Read-only original Text rejects native input and republishes canonical text");
			editor.setEditable(true);
			editor.setTextLimit(Text.LIMIT);
			editor.setText("line1\nline2");
			require(editor.getLineCount() == 2 && editor.getLineAtOffset(6) == 1
					&& editor.getOffsetAtLine(1) == 6,
					"Text line navigation uses original SWT UTF-16 code unit offsets");
			editor.setTextLimit(Text.LIMIT);
			editor.setText("clipboard");
			int verifiesBeforeSelection = verify.size(), modifiesBeforeSelection = modified.size();
			platform.sink.textChanged(peer, "clipboard", 2, 2);
			drain(display);
			require(editor.getCaretPosition() == 2 && editor.getSelectionCount() == 0,
					"Native caret-only snapshot updates the original Text owner");
			platform.sink.textChanged(peer, "clipboard", 0, 4);
			drain(display);
			require(editor.getSelection().equals(new Point(0, 4))
					&& editor.getSelectionText().equals("clip")
					&& verify.size() == verifiesBeforeSelection
					&& modified.size() == modifiesBeforeSelection,
					"Native selection-only snapshots never emit Verify or Modify");
			editor.copy();
			require(platform.clipboard.equals("clip") && editor.getSelectionText().equals("clip")
					&& verify.size() == verifiesBeforeSelection
					&& modified.size() == modifiesBeforeSelection,
					"Text.copy uses the native selection without Verify or Modify");
			editor.cut();
			require(editor.getText().equals("board")
					&& editor.getCaretPosition() == 0
					&& verify.size() == verifiesBeforeSelection + 1
					&& modified.size() == modifiesBeforeSelection + 1,
					"Text.cut uses the native selection with exactly one Verify and Modify");
			editor.paste();
			require(editor.getText().equals("clipboard"),
					"Text.paste reads system clipboard and applies exact canonical replacement");
			editor.setText("A\uD83D\uDE03B");
			require(editor.getCharCount() == 4, "Supplementary Unicode retains UTF-16 width");
			require(editor.getText(1, 2).equals("\uD83D\uDE03"),
					"Inclusive getText range preserves complete surrogate pair");
			try {
				editor.setTextLimit(0);
				throw new AssertionError("Zero TextLimit must fail");
			} catch (IllegalArgumentException expected) {
				require(editor.getTextLimit() == Text.LIMIT,
						"Invalid TextLimit does not mutate the editor");
			}
			Control child = editor;
			shell.dispose();
			require(child.isDisposed(), "Parent disposal closes original Text native peer");
			platform.sink.textChanged(peer, "stale", 5, 5);
			drain(display);
			require(!platform.controls.containsKey(peer), "Stale UI callback cannot resurrect Text");
		} finally {
			display.dispose();
			MobileRuntime.uninstall(platform);
		}
		require(platform.controls.isEmpty() && platform.images.isEmpty(),
				"Text releases native views and backing SWT resources");
	}

	/** Original Combo API and native Text/List/Shell ownership on both mobile hosts. */
	static void testMobileComboContracts() {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			Combo simple = new Combo(shell, SWT.SIMPLE);
			long listPeer = platform.lastCreated;
			simple.setBounds(0, 0, 240, 180);
			org.eclipse.swt.events.SegmentListener segmentListener = event -> {};
			try {
				simple.addSegmentListener(segmentListener);
				throw new AssertionError("Unqualified Combo bidi segments must be rejected explicitly");
			} catch (UnsupportedOperationException expected) { }
			try {
				simple.removeSegmentListener(segmentListener);
				throw new AssertionError("Unqualified Combo bidi segment removal must be rejected explicitly");
			} catch (UnsupportedOperationException expected) { }
			try {
				simple.addSegmentListener(null);
				throw new AssertionError("Null Combo SegmentListener must report SWT.ERROR_NULL_ARGUMENT");
			} catch (IllegalArgumentException expected) { }
			try {
				simple.removeSegmentListener(null);
				throw new AssertionError("Null Combo SegmentListener removal must report SWT.ERROR_NULL_ARGUMENT");
			} catch (IllegalArgumentException expected) { }
			require(simple.getChildren().length == 0,
					"Internal native Text/List peers are hidden by original Combo getChildren API");
			require(platform.controls.get(listPeer).kind == MobilePlatform.CANVAS,
					"SIMPLE Combo reuses original SWT List native canvas peer");
			simple.setItems("alpha", "beta", "gamma");
			AtomicInteger selections = new AtomicInteger();
			AtomicInteger modifications = new AtomicInteger();
			simple.addSelectionListener(SelectionListener.widgetSelectedAdapter(event -> selections.incrementAndGet()));
			simple.addModifyListener(event -> modifications.incrementAndGet());
			simple.addVerifyListener(event -> {
				if ("blocked".equals(event.text)) event.doit = false;
			});
			simple.select(1);
			require(simple.getText().equals("beta") && simple.getSelectionIndex() == 1
					&& selections.get() == 0, "Combo select updates Text/List without Selection event");
			simple.add("zero", 0);
			require(simple.getSelectionIndex() == 2 && simple.getItem(2).equals("beta"),
					"Array insert preserves selected Combo logical item identity");
			simple.remove(0);
			require(simple.getSelectionIndex() == 1 && simple.getText().equals("beta"),
					"Array remove shifts selection without changing editor text");
			simple.setText("custom");
			require(simple.getSelectionIndex() == -1, "Editable Combo can display unmatched free text");
			int beforeVeto = modifications.get();
			simple.setText("blocked");
			require(simple.getText().equals("custom") && modifications.get() == beforeVeto,
					"Original Combo Verify veto prevents native text mutation and Modify");
			simple.setSelection(new Point(0, 3));
			simple.copy();
			require(platform.clipboard.equals("cus"), "Combo copies original text selection via OS clipboard");
			simple.cut();
			require(simple.getText().equals("tom"), "Combo cut delegates to original Text Verify/Modify");
			simple.paste();
			require(simple.getText().equals("custom"), "Combo paste restores original selected clipboard text");
			shell.open();
			drain(display);
			platform.sink.input(listPeer, SWT.MouseDown, 12, 6, 1, 0, '\0', 0, 1, 41);
			platform.sink.input(listPeer, SWT.MouseUp, 12, 6, 1, 0, '\0', 0, 1, 42);
			drain(display);
			require(simple.getSelectionIndex() == 0 && simple.getText().equals("alpha")
					&& selections.get() == 1,
					"Native List tap sends exactly one original Combo Selection with chosen text");
			Event arrowDown = new Event();
			arrowDown.keyCode = SWT.ARROW_DOWN;
			simple.notifyListeners(SWT.KeyDown, arrowDown);
			require(simple.getSelectionIndex() == 1 && "beta".equals(simple.getText())
					&& selections.get() == 2,
					"Original Combo keyboard navigation updates selection and notifies once");
			Combo dropdown = new Combo(shell, SWT.DROP_DOWN | SWT.READ_ONLY);
			dropdown.setBounds(0, 190, 240, 34);
			dropdown.setItems("first", "second");
			dropdown.setText("not present");
			require(dropdown.getText().isEmpty(),
					"READ_ONLY Combo refuses strings absent from its original item list");
			dropdown.select(1);
			require(dropdown.getText().equals("second") && dropdown.getSelectionIndex() == 1,
					"READ_ONLY Combo select keeps Java-owned text and index coherent");
			dropdown.setListVisible(true);
			require(dropdown.getListVisible(), "Mobile Combo opens owned native popup Shell/List");
			dropdown.setListVisible(false);
			require(!dropdown.getListVisible(), "Mobile Combo popup can be hidden without losing items");
			long comboPeer = -1;
			for (var entry : platform.controls.entrySet())
				if (entry.getValue().role == org.eclipse.swt.accessibility.ACC.ROLE_COMBOBOX
						&& entry.getKey() > comboPeer) comboPeer = entry.getKey();
			require(comboPeer > 0, "Native Android/UIKit Combo publishes its original accessibility role");
			boolean focusReported = platform.reportFocusChanges;
			platform.reportFocusChanges = true;
			platform.sink.accessibility(comboPeer, MobilePlatform.ACTION_ACTIVATE);
			drain(display);
			require(dropdown.getListVisible(),
					"Native accessibility activate opens original owned Combo popup");
			// A device sends a hardware key to the focused native view, and the open popup's List holds focus.
			long popupListPeer = platform.focusedPeer;
			require(popupListPeer != 0 && popupListPeer != comboPeer,
					"Opening the Combo popup gives native focus to its List");
			int selectedBeforeEscape = dropdown.getSelectionIndex();
			platform.sink.input(popupListPeer, SWT.KeyDown, 0, 0, 0, SWT.ESC, '\u001b', 0, 1, 3);
			drain(display);
			require(!dropdown.getListVisible() && dropdown.getSelectionIndex() == selectedBeforeEscape,
					"Escape typed in the focused native popup List closes it without selecting");
			require(platform.focusedPeer != popupListPeer && platform.focusedPeer != 0,
					"Closing the popup with Escape returns native focus to the Combo");
			platform.reportFocusChanges = focusReported;
			platform.sink.accessibility(comboPeer, MobilePlatform.ACTION_ACTIVATE);
			drain(display);
			require(dropdown.getListVisible(), "Native accessibility activate reopens the Combo popup");
			Event escape = new Event();
			escape.keyCode = SWT.ESC;
			dropdown.notifyListeners(SWT.KeyDown, escape);
			require(!dropdown.getListVisible(),
					"Native Combo Escape key closes an open popup");
			AtomicInteger keyboardSelections = new AtomicInteger();
			dropdown.addSelectionListener(SelectionListener.widgetSelectedAdapter(
					e -> keyboardSelections.incrementAndGet()));
			Event arrowUp = new Event();
			arrowUp.keyCode = SWT.ARROW_UP;
			dropdown.notifyListeners(SWT.KeyDown, arrowUp);
			require(dropdown.getSelectionIndex() == 0 && "first".equals(dropdown.getText())
					&& keyboardSelections.get() == 1,
					"READ_ONLY Combo keyboard navigation sends one original SWT.Selection");
			dropdown.setEnabled(false);
			dropdown.notifyListeners(SWT.KeyDown, arrowDown);
			require(dropdown.getSelectionIndex() == 0 && keyboardSelections.get() == 1,
					"Disabled native Combo rejects keyboard-driven selection");
			require(!dropdown.getListVisible(), "Disabling Combo closes native popup");
			Combo changing = new Combo(shell, SWT.DROP_DOWN);
			changing.setItems("one", "two");
			AtomicInteger reentrant = new AtomicInteger();
			changing.addModifyListener(e -> {
				if (reentrant.incrementAndGet() == 1) changing.removeAll();
			});
			changing.select(1);
			require(changing.getItemCount() == 0 && changing.getSelectionIndex() == -1
					&& changing.getText().isEmpty(),
					"Modify may replace Combo row model without stale array access");
			Combo hidden = new Combo(shell, SWT.DROP_DOWN);
			hidden.setItems("one");
			hidden.addListener(SWT.Hide, e -> hidden.dispose());
			hidden.setVisible(false);
			require(hidden.isDisposed(),
					"Hide listener may dispose Combo before its popup close path");
			shell.dispose();
			require(simple.isDisposed() && dropdown.isDisposed(),
					"Parent disposal closes all original Combo child/native popup owners");
		} finally {
			display.dispose();
			MobileRuntime.uninstall(platform);
		}
		require(platform.controls.isEmpty() && platform.images.isEmpty(),
				"Original Combo releases native Text/List/Button/Shell peers and graphics");
		require(platform.openedGraphics == platform.closedGraphics,
				"Original Combo closes native graphics contexts without leaking handles");
	}

	static void testMobileOriginalTableContracts() {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			Table table = new Table(shell, SWT.VIRTUAL | SWT.MULTI | SWT.CHECK | SWT.FULL_SELECTION);
			long tablePeer = platform.lastCreated;
			table.setBounds(0, 0, 300, 150);
			table.setHeaderVisible(true);
			table.setLinesVisible(true);
			require((table.getStyle() & SWT.VIRTUAL) != 0
					&& platform.controls.get(tablePeer).kind == MobilePlatform.CANVAS
					&& platform.controls.get(tablePeer).role == org.eclipse.swt.accessibility.ACC.ROLE_TABLE,
					"Original Table owns native Android/UIKit Canvas peer and semantic Table role");
			TableColumn first = new TableColumn(table, SWT.LEFT);
			first.setText("Name"); first.setWidth(140);
			TableColumn second = new TableColumn(table, SWT.RIGHT);
			second.setText("Value"); second.setWidth(100);
			require(table.getColumnCount() == 2 && table.getColumn(0) == first
					&& Arrays.equals(table.getColumnOrder(), new int[] {0, 1}),
					"TableColumn identity and initial order match original API");
			table.setColumnOrder(new int[] {1, 0});
			require(Arrays.equals(table.getColumnOrder(), new int[] {1, 0}),
					"Original column-order permutation is retained without changing model columns");
			table.setColumnOrder(new int[] {0, 1});
			AtomicInteger loaded = new AtomicInteger();
			table.addListener(SWT.SetData, event -> {
				loaded.incrementAndGet();
				TableItem item = (TableItem)event.item;
				item.setText(0, "row-" + event.index);
				item.setText(1, "value-" + event.index);
				item.setChecked((event.index & 1) != 0);
			});
			AtomicInteger selectionEvents = new AtomicInteger();
			table.addSelectionListener(SelectionListener.widgetSelectedAdapter(event ->
					selectionEvents.incrementAndGet()));
			table.setItemCount(1_000_000_000);
			table.setTopIndex(999_999_998);
			shell.open();
			drain(display);
			require(loaded.get() >= 2 && loaded.get() <= 9,
					"One-billion-row virtual Table requests only its visible SWT.SetData rows");
			TableItem last = table.getItem(999_999_999);
			require("value-999999999".equals(last.getText(1))
					&& last.getChecked() && table.indexOf(last) == 999_999_999,
					"Sparse original TableItem supports columns and CHECK with high logical index");
			int previousLoads = loaded.get();
			last.getText();
			require(loaded.get() == previousLoads, "Cached virtual row does not reenter SWT.SetData");
			table.clear(999_999_999);
			require(last == table.getItem(999_999_999)
					&& "row-999999999".equals(last.getText())
					&& loaded.get() == previousLoads + 1,
					"Table.clear reuses stable row identity and reloads exactly one SWT.SetData");
			TableColumn inserted = new TableColumn(table, SWT.CENTER, 0);
			inserted.setText("Inserted"); inserted.setWidth(60);
			require(last.getText(0).isEmpty()
					&& "row-999999999".equals(last.getText(1))
					&& "value-999999999".equals(last.getText(2)),
					"Physical column insertion shifts original TableItem cell payload indices");
			inserted.dispose();
			require("row-999999999".equals(last.getText(0))
					&& "value-999999999".equals(last.getText(1)),
					"Column disposal restores original TableItem cell ordering");
			table.select(999_999_999);
			require(table.getSelectionIndex() == 999_999_999
					&& table.getSelectionCount() == 1
					&& selectionEvents.get() == 0,
					"High-index programmatic Table selection uses sparse masks without user event");
			table.selectAll();
			require(table.getSelectionCount() == 1_000_000_000,
					"Billion-row SWT.MULTI selectAll has constant-size interval storage");
			table.deselect(10, 999_999_998);
			require(table.getSelectionCount() == 11
					&& table.getSelectionIndices().length == 11
					&& table.isSelected(999_999_999),
					"Multi-interval selection and huge deselect are bounded by range count");
			table.setSelection(999_999_999);
			table.setTopIndex(999_999_998);
			int topBefore = table.getTopIndex();
			platform.sink.accessibility(tablePeer, MobilePlatform.ACTION_DECREMENT);
			drain(display);
			require(table.getTopIndex() < topBefore
					&& table.getSelectionIndex() == 999_999_999
					&& selectionEvents.get() == 0,
					"Native Table accessibility paging preserves selection without events");
			table.setTopIndex(999_999_998);
			int y = table.getHeaderHeight() + table.getItemHeight() / 2;
			platform.sink.input(tablePeer, SWT.MouseDown, 18, y, 1, 0, '\0', 0, 1, 10);
			platform.sink.input(tablePeer, SWT.MouseUp, 18, y, 1, 0, '\0', 0, 1, 11);
			drain(display);
			require(table.getSelectionIndex() == 999_999_998 && selectionEvents.get() == 1,
					"Real native Table peer pointer selects correct virtual row once");
			table.setItemCount(999_999_999);
			require(last.isDisposed() && table.getItemCount() == 999_999_999,
					"Shrinking a huge table disposes only out-of-range materialized TableItems");
			table.removeAll();
			require(table.getItemCount() == 0 && table.getSelectionCount() == 0,
					"Table.removeAll compacts sparse row and selection state");
			shell.dispose();
			require(table.isDisposed() && first.isDisposed() && second.isDisposed(),
					"Original Table and TableColumn owners dispose with their Shell");
		} finally {
			display.dispose();
			MobileRuntime.uninstall(platform);
		}
		require(platform.controls.isEmpty() && platform.images.isEmpty(),
				"Original Table releases native peers and GC-owned images");
		require(platform.openedGraphics == platform.closedGraphics,
				"Original Table closes all native drawing contexts");
	}
}
