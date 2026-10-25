package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.PrivacySettings;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Pop-ups for payments, results and auctions: slide in from the right, fade out, stack upward.
 * While Streamer Mode hides private toasts, each toast shows its public wording or nothing.
 */
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
		return !visible().isEmpty();
	}

	/** Toasts that may be drawn right now, with the text to draw. */
	private List<String[]> visible() {
		boolean hide = state.hides(PrivacySettings.Surface.TOASTS);
		List<String[]> out = new java.util.ArrayList<>();
		for (GambleState.Toast t : state.activeToasts()) {
			String text = t.visibleText(hide);
			if (text != null) {
				out.add(new String[]{text});
			}
		}
		return out;
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
		boolean hide = state.hides(PrivacySettings.Surface.TOASTS);
		List<GambleState.Toast> toasts = state.activeToasts().stream().filter(t -> t.visibleText(hide) != null).toList();
		if (editing && toasts.isEmpty()) {
			toasts = List.of(new GambleState.Toast("+$50m jimbo01 → Paper", "+$50m jimbo01 → Paper", Colors.GREEN,
				System.currentTimeMillis(), Long.MAX_VALUE, net.minecraft.item.ItemStack.EMPTY));
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
			boolean icon = !t.icon().isEmpty();
			int pad = icon ? 22 : 11;
			String text = Paint.fit(tr, t.visibleText(hide), W - pad - 6);
			int w = tr.getWidth(text) + pad + 5;
			int x = W - w + Math.round((1f - in) * 40);
			Paint.card(ctx, x, y, w, ROW - 3, Paint.RADIUS, Colors.alpha(Colors.HUD, Math.round(0xE6 * alpha / 255f)),
				Colors.alpha(t.color(), Math.round(0x90 * alpha / 255f)));
			if (icon && alpha > 160) {
				ctx.getMatrices().pushMatrix();
				ctx.getMatrices().translate(x + 4, y + 1.5f);
				ctx.getMatrices().scale(0.75f, 0.75f);
				ctx.drawItem(t.icon(), 0, 0);
				ctx.getMatrices().popMatrix();
			} else if (!icon) {
				Paint.round(ctx, x + 4, y + 5, 3, 5, 1, Colors.alpha(t.color(), alpha));
			}
			Paint.hudText(ctx, tr, text, x + pad, y + 4, Colors.alpha(t.color(), Math.max(alpha, 30)));
			y -= ROW;
		}
	}
}
