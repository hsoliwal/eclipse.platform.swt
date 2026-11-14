/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.mobile;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.mobile.MobilePlatform;

/**
 * TEST ONLY. A strict, headless Java2D transport for SWT owner-contract tests.
 * It does not implement Android/iOS, cannot capture a screen, and is excluded
 * from mobile application artifacts. Native qualification uses device tests.
 */
final class ContractPlatform implements MobilePlatform {
	EventSink sink;
	Thread owner;
	long next = 1, lastCreated;
	int connects, disconnects, openedGraphics, closedGraphics;
	final Map<Long, Peer> controls = new HashMap<>();
	final Map<Long, BufferedImage> images = new HashMap<>();
	static final class Peer {
		long parent;
		int kind,x,y,width,height,selectionStart,selectionEnd,textLimit=Integer.MAX_VALUE;
		boolean enabled=true,visible,editable=true;
		String title,label,text="",hint="";
		int role,state; int[] frame;
	}
	private void check() { if (owner != Thread.currentThread()) throw new AssertionError("OS operations must run from the SWT owner thread in this transport"); }
	private Peer peer(long id) { check(); Peer p=controls.get(id); if(p==null)throw new IllegalStateException("Stale test peer "+id); return p; }
	private BufferedImage bitmap(long id) { check(); BufferedImage image=images.get(id); if(image==null)throw new IllegalStateException("Stale test image "+id);return image; }
	@Override public String name(){return "mobile-contract-test";}
	@Override public void connect(EventSink sink){if(this.sink!=null)throw new AssertionError("Already connected");owner=Thread.currentThread();this.sink=sink;connects++;}
	@Override public void disconnect(){check();if(!controls.isEmpty()||!images.isEmpty())throw new AssertionError("Leaked native owners: "+controls.size()+" controls, "+images.size()+" images");sink=null;disconnects++;}
	@Override public int[] displayBounds(){check();return new int[]{0,0,400,600};}
	@Override public int[] dpi(){check();return new int[]{96,96};}
	@Override public long createControl(int kind,long parent,int style){check();if(parent!=0)peer(parent);Peer p=new Peer();p.parent=parent;p.kind=kind;long id=next++;controls.put(id,p);lastCreated=id;return id;}
	@Override public void disposeControl(long id){peer(id);if(controls.values().stream().anyMatch(p->p.parent==id))throw new AssertionError("Parent destroyed before children");controls.remove(id);}
	@Override public void setBounds(long id,int x,int y,int w,int h){Peer p=peer(id);p.x=x;p.y=y;p.width=w;p.height=h;}
	@Override public void setVisible(long id,boolean visible){peer(id).visible=visible;}
	@Override public void setEnabled(long id,boolean enabled){peer(id).enabled=enabled;}
	@Override public void setTitle(long id,String title){peer(id).title=title;}
	@Override public void textContent(long id,String text,int start,int end,boolean editable,int limit,String hint){
		Peer p=peer(id);
		if(p.kind!=TEXT)throw new AssertionError("Only a real TEXT peer can receive native text");
		p.text=text; p.selectionStart=start; p.selectionEnd=end;
		p.editable=editable; p.textLimit=limit; p.hint=hint;
	}
	@Override public boolean requestFocus(long id){Peer p=peer(id);return p.enabled&&p.visible;}
	@Override public void setAccessibility(long id,String label,int role,int state){Peer p=peer(id);p.label=label;p.role=role;p.state=state;}
	@Override public void present(long id,long image){peer(id).frame=imagePixels(image);}
	@Override public long createImage(int w,int h,int[] pixels){check();BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);if(pixels!=null)image.setRGB(0,0,w,h,pixels,0,w);long id=next++;images.put(id,image);return id;}
	@Override public void disposeImage(long id){bitmap(id);images.remove(id);}
	@Override public int[] imagePixels(long id){BufferedImage image=bitmap(id);return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());}
	@Override public int[] captureScreen(int x,int y,int w,int h){check();throw new UnsupportedOperationException("Contract fixture cannot provide OS screen-capture evidence");}
	@Override public Graphics graphics(long id){BufferedImage image=bitmap(id);openedGraphics++;return new TestGraphics(image);}
	final class TestGraphics implements Graphics {
		final Graphics2D g; int foreground=0xff000000,background=0xffffffff,alpha=255; boolean closed;
		TestGraphics(BufferedImage image){g=image.createGraphics();}
		void drawing(){check();if(closed)throw new AssertionError("Drawing after GC disposal");}
		void color(boolean back){drawing();g.setColor(new java.awt.Color(back?background:foreground,true));g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER,alpha/255f));}
		@Override public void setForeground(int color){drawing();foreground=color;}
		@Override public void setBackground(int color){drawing();background=color;}
		@Override public void setAlpha(int value){drawing();alpha=value;}
		@Override public void setLineWidth(float width){drawing();g.setStroke(new BasicStroke(width));}
		@Override public void setAntialias(boolean enabled){drawing();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,enabled?RenderingHints.VALUE_ANTIALIAS_ON:RenderingHints.VALUE_ANTIALIAS_OFF);}
		@Override public void setFont(String family,float size,int style){drawing();g.setFont(new java.awt.Font(family,((style&SWT.BOLD)!=0?java.awt.Font.BOLD:0)|((style&SWT.ITALIC)!=0?java.awt.Font.ITALIC:0),Math.max(1,Math.round(size))));}
		@Override public void setTransform(float[] m){drawing();g.setTransform(new AffineTransform(m));}
		@Override public void setClipping(int x,int y,int w,int h){drawing();g.setClip(x,y,w,h);}
		@Override public void resetClipping(){drawing();g.setClip(null);}
		@Override public void drawLine(int x1,int y1,int x2,int y2){color(false);g.drawLine(x1,y1,x2,y2);}
		@Override public void drawRectangle(int x,int y,int w,int h){color(false);g.drawRect(x,y,w,h);}
		@Override public void fillRectangle(int x,int y,int w,int h){color(true);g.fillRect(x,y,w,h);}
		@Override public void drawOval(int x,int y,int w,int h){color(false);g.drawOval(x,y,w,h);}
		@Override public void fillOval(int x,int y,int w,int h){color(true);g.fillOval(x,y,w,h);}
		@Override public void drawPolygon(int[] points,boolean fill){color(fill);Path2D path=new Path2D.Float();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);path.closePath();if(fill)g.fill(path);else g.draw(path);}
		private String[] lines(String text,int flags){return (flags&SWT.DRAW_DELIMITER)!=0?text.split("\n",-1):new String[]{text};}
		@Override public void drawText(String text,int x,int y,int flags){int[] extent=textExtent(text,flags);if((flags&SWT.DRAW_TRANSPARENT)==0)fillRectangle(x,y,extent[0],extent[1]);color(false);java.awt.FontMetrics fm=g.getFontMetrics();int row=0;for(String line:lines(text,flags))g.drawString(line,x,y+fm.getAscent()+row++*fm.getHeight());}
		@Override public void drawImage(long image,int sx,int sy,int sw,int sh,int dx,int dy,int dw,int dh){color(false);g.drawImage(bitmap(image),dx,dy,dx+dw,dy+dh,sx,sy,sx+sw,sy+sh,null);}
		@Override public int[] textExtent(String text,int flags){drawing();java.awt.FontMetrics fm=g.getFontMetrics();int width=0;String[] lines=lines(text,flags);for(String line:lines)width=Math.max(width,fm.stringWidth(line));return new int[]{width,fm.getHeight()*lines.length};}
		@Override public int[] fontMetrics(){drawing();java.awt.FontMetrics f=g.getFontMetrics();return new int[]{f.getAscent(),f.getDescent(),f.charWidth('n'),f.getLeading(),f.getHeight()};}
		@Override public void close(){if(closed)return;drawing();g.dispose();closed=true;closedGraphics++;}
	}
}
