package org.eclipse.swt.tests.junit.raster;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.internal.canvasext.raster.IRasterCanvas;
import org.eclipse.swt.internal.canvasext.raster.IRasterImage;
import org.eclipse.swt.internal.canvasext.raster.IRasterSurface;
import io.github.humbleui.types.IRect;

public class RasterSurfaceDouble implements IRasterSurface {

	List<MethodCall> calls ;
	
	String name;
	private int childIndex;
	private int snapshotIndex;

	int width;
	int height;
	boolean closed;
	RasterCanvasDouble canvas;

	List<IRasterImage> imageSnapshots = new ArrayList<>();

	List<IRasterSurface> subSurfaces = new ArrayList<>();

	public RasterSurfaceDouble(int width, int height, String string, List<MethodCall> calls) {
		this.width = width;
		this.height = height;
		this.name = string;
		
		if(calls == null) {
			this.calls = new ArrayList<>();
		} else {
			this.calls = calls;
		}
		
		canvas = new RasterCanvasDouble(100, 100, name+"-canvas", this.calls);
		
	}

	public RasterSurfaceDouble(List<MethodCall> calls) {
		this(100, 100, "surface", calls);
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public IRasterCanvas getCanvas() {
		return canvas;
	}

	@Override
	public void close() {
		closed = true;
	}

	@Override
	public IRasterImage makeImageSnapshot() {

		var doub = new IRasterImageDouble();

		doub.closed = false;
		doub.width = width;
		doub.height = height;

		doub.name = name + "-snapshot-" + snapshotIndex++;

		imageSnapshots.add(doub);

		return doub;
	}

	@Override
	public IRasterImage makeImageSnapshot(IRect rect) {

		var doub = new IRasterImageDouble();

		doub.closed = false;
		doub.width = rect.getWidth();
		doub.height = rect.getHeight();
		doub.name = name + "-snapshot-" + snapshotIndex++;

		imageSnapshots.add(doub);

		return doub;
	}

	@Override
	public IRasterSurface makeSurface(int width, int height) {
		RasterSurfaceDouble surface = new RasterSurfaceDouble(width, height, name + "-child-" + childIndex++, calls);
		subSurfaces.add(surface);
		return surface;
	}

}