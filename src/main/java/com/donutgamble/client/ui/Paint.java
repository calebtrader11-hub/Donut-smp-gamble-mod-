package com.donutgamble.client.ui;

import com.donutgamble.client.Colors;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Shape and text primitives shared by every screen and HUD component. */
public final class Paint {
	/** Spacing scale (GUI pixels). */
	public static final int S1 = 2, S2 = 4, S3 = 6, S4 = 8, S5 = 10, S6 = 12, S8 = 16;
	public static final int RADIUS = 3;
	public static final int LINE_H = 11;

	private Paint() {
	}

	/** Filled rectangle with rounded corners, drawn as non-overlapping rows so translucent colors stay even. */
	public static void round(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
			ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			ctx.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
		}
		ctx.fill(x, y + r, x + w, y + h - r, color);
	}

	/** Rounded rectangle with a 1px border. */
	public static void card(DrawContext ctx, int x, int y, int w, int h, int r, int fill, int border) {
		round(ctx, x, y, w, h, r, border);
		round(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
	}

	/** Soft drop shadow under a panel. */
	public static void shadow(DrawContext ctx, int x, int y, int w, int h, int r) {
		for (int k = 4; k >= 1; k--) {
			round(ctx, x - k, y - k + 2, w + 2 * k, h + 2 * k, r + k, Colors.alpha(0xFF000000, 18));
		}
	}

	/** Window: shadow, surface and hairline border. */
	public static void window(DrawContext ctx, int x, int y, int w, int h) {
		shadow(ctx, x, y, w, h, 5);
		card(ctx, x, y, w, h, 5, Colors.WINDOW, Colors.LINE_HI);
	}

	/** HUD panel: translucent surface with an accent hairline. */
	public static void hud(DrawContext ctx, int x, int y, int w, int h) {
		card(ctx, x, y, w, h, RADIUS, Colors.HUD, Colors.alpha(Colors.accent(), 0xC0));
	}

	public static void pill(DrawContext ctx, int x, int y, int w, int h, int color) {
		round(ctx, x, y, w, h, h / 2, color);
	}

	/** Track plus filled part (0..1). */
	public static void bar(DrawContext ctx, int x, int y, int w, int h, float fraction, int track, int fill) {
		round(ctx, x, y, w, h, h / 2, track);
		int fw = Math.round(w * Math.max(0f, Math.min(1f, fraction)));
		if (fw > 0) {
			round(ctx, x, y, Math.max(fw, h), h, h / 2, fill);
		}
	}

	public static void hline(DrawContext ctx, int x1, int x2, int y, int color) {
		ctx.fill(x1, y, x2, y + 1, color);
	}

	// ---- text ----

	public static int text(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
		ctx.drawText(tr, s, x, y, color, false);
		return x + tr.getWidth(s);
	}

	/** Text with a shadow; for the HUD, where it sits over the world. */
	public static int hudText(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
		ctx.drawTextWithShadow(tr, s, x, y, color);
		return x + tr.getWidth(s);
	}

	public static void right(DrawContext ctx, TextRenderer tr, String s, int rightX, int y, int color) {
		ctx.drawText(tr, s, rightX - tr.getWidth(s), y, color, false);
	}

	public static void center(DrawContext ctx, TextRenderer tr, String s, int centerX, int y, int color) {
		ctx.drawText(tr, s, centerX - tr.getWidth(s) / 2, y, color, false);
	}

	/** Small uppercase label above a value or section. */
	public static void label(DrawContext ctx, TextRenderer tr, String s, int x, int y) {
		ctx.drawText(tr, s.toUpperCase(java.util.Locale.ROOT), x, y, Colors.INK_FAINT, false);
	}

	/** Text drawn at an integer multiple of the font size (crisp pixels). */
	public static void big(DrawContext ctx, TextRenderer tr, String s, int x, int y, int scale, int color, boolean shadow) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x, y);
		ctx.getMatrices().scale(scale, scale);
		ctx.drawText(tr, s, 0, 0, color, shadow);
		ctx.getMatrices().popMatrix();
	}

	public static String fit(TextRenderer tr, String s, int width) {
		if (tr.getWidth(s) <= width) {
			return s;
		}
		return tr.trimToWidth(s, Math.max(0, width - tr.getWidth("…"))) + "…";
	}
}
