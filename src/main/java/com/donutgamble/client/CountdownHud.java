package com.donutgamble.client;

import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.Auction;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * The last seconds of an auction as a big number in the middle of the screen (10, 9, 8 …), each
 * one popping in, then "SOLD" with the winner for a moment after it ends.
 */
final class CountdownHud extends HudElement {
	private static final int W = 120;
	private static final int H = 54;
	private static final long FROM_MS = 10_000;
	private static final long SOLD_MS = 4_000;

	private final GambleState state;

	CountdownHud(GambleState state) {
		super("countdown", "Final countdown");
		this.state = state;
	}

	private Auction counting() {
		Auction a = state.auctions.current;
		if (a == null || !a.hudCard || a.status != Auction.Status.RUNNING) {
			return null;
		}
		long left = a.remaining(System.currentTimeMillis());
		return left > 0 && left <= FROM_MS ? a : null;
	}

	private Auction sold() {
		Auction a = state.auctions.current == null ? state.recentResult() : null;
		if (a == null || a.winner == null || a.status == Auction.Status.CANCELLED) {
			return null;
		}
		return System.currentTimeMillis() - a.endedAt < SOLD_MS ? a : null;
	}

	@Override
	boolean hasContent() {
		return counting() != null || sold() != null;
	}

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		return new int[]{W, H};
	}

	@Override
	int[] defaultPosition(int screenW, int screenH, int w, int h) {
		// Just above the crosshair, where the eye already is.
		return new int[]{(screenW - w) / 2, Math.max(6, screenH / 2 - h - 8)};
	}

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		long now = System.currentTimeMillis();
		Auction a = counting();
		if (a != null || editing && sold() == null) {
			long left = a == null ? 5_000 : a.remaining(now);
			int sec = (int) ((left + 999) / 1000);
			float t = 1f - ((left - 1) % 1000) / 1000f; // 0 at the start of each second
			float pop = Math.max(0f, 1f - t * 3f);
			int color = sec <= 3 ? Colors.RED : 0xFFFFFFFF;
			center(ctx, tr, "ENDING", 2, 1f, Colors.fade(Colors.GOLD, 0.9f));
			center(ctx, tr, Integer.toString(sec), 14, 5f + 1.5f * pop, Colors.fade(color, 1f - 0.25f * t));
			return;
		}
		Auction s = sold();
		if (s == null) {
			return;
		}
		float in = Math.min(1f, (now - s.endedAt) / 200f);
		float out = Math.min(1f, (s.endedAt + SOLD_MS - now) / 400f);
		float f = Math.min(in, out);
		center(ctx, tr, "SOLD!", 10, 2.5f + 1f * (1f - in), Colors.fade(Colors.GOLD, f));
		center(ctx, tr, Paint.fit(tr, "to " + s.winner, W), 42, 1f, Colors.fade(0xFFFFFFFF, f));
	}

	/** Text centered in the component at {@code y}, scaled around its center. */
	private static void center(DrawContext ctx, TextRenderer tr, String s, int y, float scale, int color) {
		if (((color >>> 24) & 0xFF) < 8) {
			return;
		}
		Paint.record(s);
		float w = tr.getWidth(s) * scale;
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate((W - w) / 2f, y - (scale > 1f ? (scale - Math.round(scale)) * 4f : 0f));
		ctx.getMatrices().scale(scale, scale);
		ctx.drawText(tr, s, 0, 0, color, true);
		ctx.getMatrices().popMatrix();
	}
}
