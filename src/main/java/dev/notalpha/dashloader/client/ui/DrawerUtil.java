package dev.notalpha.dashloader.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.Calendar;
import java.util.Date;

public class DrawerUtil {
	public static final float GLOW_SIZE = 30f;
	public static final float GLOW_STRENGTH = 0.1f;
	public static final Color FAILED_COLOR = new Color(250, 68, 51);
	public static final Color BACKGROUND_COLOR = new Color(34, 31, 34);
	public static final Color FOREGROUND_COLOR = new Color(252, 252, 250);
	public static final Color STATUS_COLOR = new Color(180, 180, 180);
	public static final Color NEUTRAL_LINE = new Color(45, 42, 46);
	public static final Color PROGRESS_TRACK = new Color(25, 25, 25);
	private static final Color[] PROGRESS_COLORS = new Color[]{
			new Color(0xff, 0x61, 0x88),
			new Color(0xfc, 0x98, 0x67),
			new Color(0xff, 0xd8, 0x66),
			new Color(0xa9, 0xdc, 0x76)
	};



	public static void drawRect(MatrixStack matrixStack, int x, int y, int width, int height, Color color) {
		final int x2 = width + x;
		final int y2 = height + y;
		DrawableHelper.fill(matrixStack, x, y, x2, y2, convertColor(color));
	}

	public static void drawText(MatrixStack matrixStack, TextRenderer textRenderer, int x, int y, String text, Color color) {
		DrawableHelper.drawTextWithShadow(matrixStack, textRenderer, Text.of(text), x, y - (textRenderer.fontHeight), color.getRGB() | 0xff000000);
	}

	private static void drawVertex(Matrix4f m4f, BufferBuilder bb, float x, float y, Color color) {
		bb.vertex(m4f, x, y, 0f).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).next();
	}
	public static void drawGlow(Matrix4f b4, BufferBuilder bb, float x, float y, float width, float height, float strength, Color color, boolean topLeft, boolean topRight, boolean bottomLeft, boolean bottomRight) {
		Color end = withOpacity(color, 0);
		Color glow = withOpacity(color, GLOW_STRENGTH * strength);

		Color tl = topLeft ? glow : end;
		Color tr = topRight ? glow : end;
		Color bl = bottomLeft ? glow : end;
		Color br = bottomRight ? glow : end;

		Color tlEnd = new Color(tl.getRed(), tl.getGreen(), tl.getBlue(), 0);
		Color trEnd = new Color(tr.getRed(), tr.getGreen(), tr.getBlue(), 0);
		Color blEnd = new Color(bl.getRed(), bl.getGreen(), bl.getBlue(), 0);
		Color brEnd = new Color(br.getRed(), br.getGreen(), br.getBlue(), 0);

		float x2 = x + width;
		float y2 = y + height;

		// Inside
		drawVertex(b4, bb, x, y2, bl); // left bottom
		drawVertex(b4, bb, x2, y2, br); // right bottom
		drawVertex(b4, bb, x2, y, tr); // right top
		drawVertex(b4, bb, x, y, tl); // left top

		// Top
		drawVertex(b4, bb, x, y, tl); // left bottom
		drawVertex(b4, bb, x2, y, tr); // right bottom
		drawVertex(b4, bb, x2, y - GLOW_SIZE, trEnd); // right top
		drawVertex(b4, bb, x, y - GLOW_SIZE, tlEnd); // left top

		// Top Right
		drawVertex(b4, bb, x2, y - GLOW_SIZE, trEnd); // left top
		drawVertex(b4, bb, x2, y, tr); // left bottom
		drawVertex(b4, bb, x2 + GLOW_SIZE, y, trEnd); // right bottom
		drawVertex(b4, bb, x2 + GLOW_SIZE, y - GLOW_SIZE, trEnd); // right top

		// Top Left
		drawVertex(b4, bb, x, y - GLOW_SIZE, tlEnd); // right top
		drawVertex(b4, bb, x - GLOW_SIZE, y - GLOW_SIZE, tlEnd); // left top
		drawVertex(b4, bb, x - GLOW_SIZE, y, tlEnd); // left bottom
		drawVertex(b4, bb, x, y, tl); // right bottom

		// Bottom
		drawVertex(b4, bb, x2, y2 + GLOW_SIZE, brEnd); // right bottom
		drawVertex(b4, bb, x2, y2, br); // right top
		drawVertex(b4, bb, x, y2, bl); // left top
		drawVertex(b4, bb, x, y2 + GLOW_SIZE, blEnd); // left bottom

		// Bottom Right
		drawVertex(b4, bb, x2 + GLOW_SIZE, y2, brEnd); // right top
		drawVertex(b4, bb, x2, y2, br); // left top
		drawVertex(b4, bb, x2, y2 + GLOW_SIZE, brEnd); // left bottom
		drawVertex(b4, bb, x2 + GLOW_SIZE, y2 + GLOW_SIZE, brEnd); // right bottom

		// Bottom Left
		drawVertex(b4, bb, x - GLOW_SIZE, y2, blEnd); // left top
		drawVertex(b4, bb, x - GLOW_SIZE, y2 + GLOW_SIZE, blEnd); // left bottom
		drawVertex(b4, bb, x, y2 + GLOW_SIZE, blEnd); // right bottom
		drawVertex(b4, bb, x, y2, bl); // right top

		// Right
		drawVertex(b4, bb, x2, y, tr); // left top
		drawVertex(b4, bb, x2, y2, br); // left bottom
		drawVertex(b4, bb, x2 + GLOW_SIZE, y2, brEnd); // right bottom
		drawVertex(b4, bb, x2 + GLOW_SIZE, y, trEnd); // right top

		// Left
		drawVertex(b4, bb,  x - GLOW_SIZE, y2, blEnd); // left bottom
		drawVertex(b4, bb,  x, y2, bl); // right bottom
		drawVertex(b4, bb,  x, y, tl); // right top
		drawVertex(b4, bb,  x - GLOW_SIZE, y, tlEnd); // left top
	}
	public static int convertColor(Color color) {
		return color.getRGB() | color.getAlpha() << 24;
	}

	public static Color withOpacity(Color color, float opacity) {
		float currentOpacity = color.getAlpha() / 255f;
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) ((opacity * currentOpacity) * 255));
	}


	public static Color getProgressColor(double progress) {
		return mix(progress, PROGRESS_COLORS);
	}

	private static Color mix(double pos, Color... colors) {
		if (colors.length == 1) {
			return colors[0];
		}
		pos = Math.min(1, Math.max(0, pos));
		int breaks = colors.length - 1;
		if (pos == 1) {
			return colors[breaks];
		}
		int colorPos = (int) Math.floor(pos * (breaks));
		final double step = 1d / (breaks);
		double localRatio = (pos % step) * breaks;
		return blend(colors[colorPos], colors[colorPos + 1], localRatio);
	}

	private static Color blend(Color i1, Color i2, double ratio) {
		if (ratio > 1f) {
			ratio = 1f;
		} else if (ratio < 0f) {
			ratio = 0f;
		}
		double iRatio = 1.0f - ratio;

		int a = (int) ((i1.getAlpha() * iRatio) + (i2.getAlpha() * ratio));
		int r = (int) ((i1.getRed() * iRatio) + (i2.getRed() * ratio));
		int g = (int) ((i1.getGreen() * iRatio) + (i2.getGreen() * ratio));
		int b = (int) ((i1.getBlue() * iRatio) + (i2.getBlue() * ratio));

		return new Color(r, g, b, a);
	}
}
