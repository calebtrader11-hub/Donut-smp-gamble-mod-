package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.NotificationSettings.Kind;
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

	/** Icon for each notification type. */
	static Icons icon(Kind kind) {
		return switch (kind) {
			case PAYMENT_RECEIVED -> Icons.COIN;
			case PAYMENT_SENT -> Icons.PAYMENTS;
			case GAME_STARTED -> Icons.PLAY;
			case GAME_COMPLETED -> Icons.CHECK;
			case PAYOUT_DUE -> Icons.WARN;
			case AUCTION_BID -> Icons.AUCTION;
			case AUCTION_LEADER -> Icons.STAR;
			case AUCTION_ENDING -> Icons.BELL;
			case AUCTION_COMPLETED -> Icons.TROPHY;
			case SYSTEM -> Icons.SETTINGS;
		};
	}

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		boolean hide = state.hides(PrivacySettings.Surface.TOASTS);
		List<GambleState.Toast> toasts = state.activeToasts().stream().filter(t -> t.visibleText(hide) != null).toList();
		if (editing && toasts.isEmpty()) {
			toasts = List.of(new GambleState.Toast(Kind.PAYMENT_RECEIVED, "+$50m jimbo01 → Paper", "+$50m jimbo01 → Paper",
				Colors.GREEN, System.currentTimeMillis(), Long.MAX_VALUE, net.minecraft.item.ItemStack.EMPTY));
		}
		long now = System.currentTimeMillis();
		int y = GambleState.MAX_TOASTS * ROW - ROW;
		for (int i = toasts.size() - 1; i >= 0; i--) {
			GambleState.Toast t = toasts.get(i);
			float in = Anim.ease((now - t.start()) / (float) SLIDE_MS);
			float out = t.until() == Long.MAX_VALUE ? 1f : Anim.ease((t.until() - now) / (float) FADE_MS);
			float f = Math.min(in, out);
			if (f < 0.04f) {
				// Still collapses its slot as it leaves so the stack closes up smoothly.
				y -= Math.round(ROW * f);
				continue;
			}
			int h = ROW - 3;
			String text = Paint.fit(tr, t.visibleText(hide), W - 30);
			int w = tr.getWidth(text) + 30;
			int x = W - w + Math.round((1f - in) * 40);
			Paint.card(ctx, x, y, w, h, Paint.RADIUS, Colors.fade(0xF00B0D12, f), Colors.fade(Colors.LINE_HI, f));
			ctx.fill(x + 1, y + 3, x + 2, y + h - 3, Colors.fade(t.color(), f));
			if (!t.icon().isEmpty() && f > 0.6f) {
				ctx.getMatrices().pushMatrix();
				ctx.getMatrices().translate(x + 5, y + 1.5f);
				ctx.getMatrices().scale(0.75f, 0.75f);
				ctx.drawItem(t.icon(), 0, 0);
				ctx.getMatrices().popMatrix();
			} else {
				Paint.round(ctx, x + 5, y + 1, 13, 13, 3, Colors.fade(Colors.alpha(t.color(), 0x30), f));
				icon(t.kind()).draw(ctx, x + 7, y + 3, Colors.fade(t.color(), f));
			}
			Paint.hudText(ctx, tr, text, x + 23, y + 4, Colors.fade(Colors.INK, Math.max(f, 0.15f)));
			y -= Math.round(ROW * Math.min(1f, in * 1.5f));
		}
	}
}
