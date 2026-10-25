package com.donutgamble.client;

import net.minecraft.client.gui.DrawContext;

/** Small drawing helpers for the flat, rounded look. */
final class Draw {
	private Draw() {
	}

	/** A rectangle with 1px cut corners, which reads as rounded at normal GUI scales. */
	static void rounded(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1 + 1, y1, x2 - 1, y2, color);
		ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	static void roundedBordered(DrawContext ctx, int x1, int y1, int x2, int y2, int fill, int border) {
		rounded(ctx, x1, y1, x2, y2, border);
		rounded(ctx, x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
	}
}
