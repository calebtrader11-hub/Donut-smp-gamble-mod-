package com.donutgamble.client;

import net.minecraft.client.gui.DrawContext;

final class Draw {
	private Draw() {
	}

	/** See-through box with a 1px pink outline, like a scoreboard. */
	static void box(DrawContext ctx, int x1, int y1, int x2, int y2, int bg) {
		ctx.fill(x1, y1, x2, y2, bg);
		ctx.drawStrokedRectangle(x1, y1, x2 - x1, y2 - y1, Colors.outline());
	}
}
