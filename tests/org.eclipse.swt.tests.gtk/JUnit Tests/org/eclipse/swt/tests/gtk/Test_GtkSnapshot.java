/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.gtk;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.internal.C;
import org.eclipse.swt.internal.cairo.Cairo;
import org.eclipse.swt.internal.gtk.GDK;
import org.eclipse.swt.internal.gtk.GTK;
import org.eclipse.swt.internal.gtk.GdkRGBA;
import org.eclipse.swt.internal.gtk.Graphene;
import org.eclipse.swt.internal.gtk.OS;
import org.eclipse.swt.internal.gtk4.GTK4;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercises real GTK4 snapshot ownership and Cairo rendering through JNI. */
@SuppressWarnings("restriction")
public class Test_GtkSnapshot {

	private static final int SIZE = 8;

	@BeforeAll
	public static void requireGtk4() {
		assumeTrue(GTK.GTK4, "Snapshot bindings require the GTK4 platform library");
		Display.getDefault();
	}

	@Test
	public void test_clipAndOpacityReachNativePixels() {
		long bounds = rect(0, 0, SIZE, SIZE);
		long clip = rect(2, 1, 4, 5);
		long snapshot = GTK4.gtk_snapshot_new();
		long node = 0;
		try {
			assertNotEquals(0, snapshot);
			GTK4.gtk_snapshot_push_opacity(snapshot, 0.5);
			GTK4.gtk_snapshot_push_clip(snapshot, clip);
			GTK4.gtk_snapshot_append_color(snapshot, color(1, 0, 0), bounds);
			GTK4.gtk_snapshot_pop(snapshot);
			GTK4.gtk_snapshot_pop(snapshot);
			node = GTK4.gtk_snapshot_free_to_node(snapshot);
			snapshot = 0;
			int[] pixels = render(node);
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					int pixel = pixels[y * SIZE + x];
					if (x >= 2 && x < 6 && y >= 1 && y < 6) {
						assertTrue(Math.abs((pixel >>> 24) - 128) <= 1);
						assertTrue(Math.abs(((pixel >>> 16) & 255) - 128) <= 1);
						assertEquals(0, pixel & 0xffff);
					} else {
						assertEquals(0, pixel, "Clip leaked at " + x + "," + y);
					}
				}
			}
		} finally {
			if (node != 0) GTK4.gsk_render_node_unref(node);
			if (snapshot != 0) OS.g_object_unref(snapshot);
			Graphene.graphene_rect_free(clip);
			Graphene.graphene_rect_free(bounds);
		}
	}

	@Test
	public void test_appendNodeRetainsReleasedInput() {
		long bounds = rect(0, 0, SIZE, SIZE);
		long sourceSnapshot = GTK4.gtk_snapshot_new();
		long snapshot = GTK4.gtk_snapshot_new();
		long source = 0;
		long node = 0;
		try {
			assertNotEquals(0, sourceSnapshot);
			assertNotEquals(0, snapshot);
			GTK4.gtk_snapshot_append_color(sourceSnapshot, color(0, 1, 0), bounds);
			source = GTK4.gtk_snapshot_free_to_node(sourceSnapshot);
			sourceSnapshot = 0;
			assertNotEquals(0, source);
			GTK4.gtk_snapshot_append_node(snapshot, source);
			GTK4.gsk_render_node_unref(source);
			source = 0;
			node = GTK4.gtk_snapshot_free_to_node(snapshot);
			snapshot = 0;
			for (int pixel : render(node)) assertEquals(0xff00ff00, pixel);
		} finally {
			if (node != 0) GTK4.gsk_render_node_unref(node);
			if (source != 0) GTK4.gsk_render_node_unref(source);
			if (snapshot != 0) OS.g_object_unref(snapshot);
			if (sourceSnapshot != 0) OS.g_object_unref(sourceSnapshot);
			Graphene.graphene_rect_free(bounds);
		}
	}

	@Test
	public void test_appendTextureRetainsPixelsAfterSourceRelease() {
		long bounds = rect(0, 0, SIZE, SIZE);
		long pixbuf = GDK.gdk_pixbuf_new(GDK.GDK_COLORSPACE_RGB, true, 8, 2, 2);
		long texture = 0;
		long snapshot = GTK4.gtk_snapshot_new();
		long node = 0;
		try {
			assertNotEquals(0, pixbuf);
			assertNotEquals(0, snapshot);
			byte[] blueRow = { 0, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255, (byte) 255 };
			long pixels = GDK.gdk_pixbuf_get_pixels(pixbuf);
			int stride = GDK.gdk_pixbuf_get_rowstride(pixbuf);
			for (int y = 0; y < 2; y++) C.memmove(pixels + y * stride, blueRow, blueRow.length);
			texture = GDK.gdk_texture_new_for_pixbuf(pixbuf);
			assertNotEquals(0, texture);
			OS.g_object_unref(pixbuf);
			pixbuf = 0;
			GTK4.gtk_snapshot_append_texture(snapshot, texture, bounds);
			OS.g_object_unref(texture);
			texture = 0;
			node = GTK4.gtk_snapshot_free_to_node(snapshot);
			snapshot = 0;
			for (int pixel : render(node)) assertEquals(0xff0000ff, pixel);
		} finally {
			if (node != 0) GTK4.gsk_render_node_unref(node);
			if (snapshot != 0) OS.g_object_unref(snapshot);
			if (texture != 0) OS.g_object_unref(texture);
			if (pixbuf != 0) OS.g_object_unref(pixbuf);
			Graphene.graphene_rect_free(bounds);
		}
	}

	@Test
	public void test_saveRestoreIsolatesTransform() {
		long redBounds = rect(1, 1, 2, 2);
		long blueBounds = rect(0, 0, 1, 1);
		long snapshot = GTK4.gtk_snapshot_new();
		long node = 0;
		try {
			assertNotEquals(0, snapshot);
			GTK4.gtk_snapshot_save(snapshot);
			GTK4.gtk_snapshot_scale(snapshot, 2.0f, 2.0f);
			GTK4.gtk_snapshot_append_color(snapshot, color(1, 0, 0), redBounds);
			GTK4.gtk_snapshot_restore(snapshot);
			GTK4.gtk_snapshot_append_color(snapshot, color(0, 0, 1), blueBounds);
			node = GTK4.gtk_snapshot_free_to_node(snapshot);
			snapshot = 0;
			int[] pixels = render(node);
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					int expected = x == 0 && y == 0 ? 0xff0000ff
							: x >= 2 && x < 6 && y >= 2 && y < 6 ? 0xffff0000 : 0;
					assertEquals(expected, pixels[y * SIZE + x], "Transform leaked at " + x + "," + y);
				}
			}
		} finally {
			if (node != 0) GTK4.gsk_render_node_unref(node);
			if (snapshot != 0) OS.g_object_unref(snapshot);
			Graphene.graphene_rect_free(blueBounds);
			Graphene.graphene_rect_free(redBounds);
		}
	}

	private static long rect(float x, float y, float width, float height) {
		long rect = Graphene.graphene_rect_alloc();
		assertNotEquals(0, rect);
		Graphene.graphene_rect_init(rect, x, y, width, height);
		return rect;
	}

	private static GdkRGBA color(double red, double green, double blue) {
		GdkRGBA color = new GdkRGBA();
		color.red = red;
		color.green = green;
		color.blue = blue;
		color.alpha = 1;
		return color;
	}

	private static int[] render(long node) {
		assertNotEquals(0, node);
		long surface = Cairo.cairo_image_surface_create(Cairo.CAIRO_FORMAT_ARGB32, SIZE, SIZE);
		long cr = 0;
		try {
			assertNotEquals(0, surface);
			cr = Cairo.cairo_create(surface);
			assertNotEquals(0, cr);
			Cairo.cairo_set_operator(cr, Cairo.CAIRO_OPERATOR_SOURCE);
			Cairo.cairo_set_source_rgba(cr, 0, 0, 0, 0);
			Cairo.cairo_paint(cr);
			Cairo.cairo_set_operator(cr, Cairo.CAIRO_OPERATOR_OVER);
			GTK4.gsk_render_node_draw(node, cr);
			Cairo.cairo_surface_flush(surface);
			long data = Cairo.cairo_image_surface_get_data(surface);
			int stride = Cairo.cairo_image_surface_get_stride(surface);
			int[] pixels = new int[SIZE * SIZE];
			int[] row = new int[SIZE];
			for (int y = 0; y < SIZE; y++) {
				C.memmove(row, data + y * stride, SIZE * 4L);
				System.arraycopy(row, 0, pixels, y * SIZE, SIZE);
			}
			return pixels;
		} finally {
			if (cr != 0) Cairo.cairo_destroy(cr);
			if (surface != 0) Cairo.cairo_surface_destroy(surface);
		}
	}
}
