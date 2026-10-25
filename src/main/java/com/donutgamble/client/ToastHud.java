package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** Pop-ups for payments, results and auctions: slide in from the right, fade out, stack upward. */
final class ToastHud extends HudElement {
	private static final int W = 220;
	private static final int ROW = 18;
	private static final int SLIDE_MS = 180;
	private static final int FADE_MS = 400;

	private final GambleState state;

	ToastHud(GambleState state) {
		super("toasts", "Notifications");
		this.state = state;
	}

	@Override
	boolean hasContent() {
		return !state.activeToasts().isEmpty();
	}

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		return new int[]{W, GambleState.MAX_TOASTS * ROW};
	}

	@Override
	int[] defaultPosition(int screenW, int screenH, int w, int h) {
		return new int[]{screenW - w - 6, screenH - h - 34};
	}

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		List<GambleState.Toast> toasts = state.activeToasts();
		if (editing && toasts.isEmpty()) {
			toasts = List.of(new GambleState.Toast("+$50m jimbo01 → Paper", Colors.GREEN, System.currentTimeMillis(), Long.MAX_VALUE));
		}
		long now = System.currentTimeMillis();
		int y = GambleState.MAX_TOASTS * ROW - ROW;
		for (int i = toasts.size() - 1; i >= 0; i--) {
			GambleState.Toast t = toasts.get(i);
			float in = Anim.ease((now - t.start()) / (float) SLIDE_MS);
			float out = t.until() == Long.MAX_VALUE ? 1f : Anim.ease((t.until() - now) / (float) FADE_MS);
			int alpha = Math.round(255 * Math.min(in, out));
			if (alpha < 8) {
				y -= ROW;
				continue;
			}
			String text = Paint.fit(tr, t.text(), W - 16);
			int w = tr.getWidth(text) + 16;
			int x = W - w + Math.round((1f - in) * 40);
			Paint.card(ctx, x, y, w, ROW - 3, Paint.RADIUS, Colors.alpha(Colors.HUD, Math.round(0xE6 * alpha / 255f)),
				Colors.alpha(t.color(), Math.round(0x90 * alpha / 255f)));
			Paint.round(ctx, x + 4, y + 5, 3, 5, 1, Colors.alpha(t.color(), alpha));
			ctx.drawText(tr, text, x + 11, y + 4, Colors.alpha(t.color(), Math.max(alpha, 30)), true);
			y -= ROW;
		}
	}
}
