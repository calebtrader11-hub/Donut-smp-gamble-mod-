package com.donutgamble.client;

import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.Auction;
import com.donutgamble.core.Standing;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** A compact auction timer for the stream: item, big countdown, time bar, leader and total. Shown only while an auction is live. */
final class AuctionHud extends HudElement {
	private static final int W = 150;
	private static final int H = 58;

	private final GambleState state;

	AuctionHud(GambleState state) {
		super("auction", "Auction timer");
		this.state = state;
	}

	@Override
	boolean hasContent() {
		return state.auctions.isLive();
	}

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		return new int[]{W, H};
	}

	@Override
	int[] defaultPosition(int screenW, int screenH, int w, int h) {
		return new int[]{screenW - w - 6, 6};
	}

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		Paint.hud(ctx, 0, 0, W, H);
		Auction a = state.auctions.current;
		long now = System.currentTimeMillis();
		String item = a != null ? a.item : "Auction (preview)";
		long left = a != null ? a.remaining(now) : 272_000;
		boolean paused = a != null && a.status == Auction.Status.PAUSED;
		Standing leader = a != null ? a.leader() : null;
		long total = a != null ? a.total() : 0;

		Paint.hudText(ctx, tr, Paint.fit(tr, item, W - 12), 6, 6, Colors.text());
		int color = paused ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.INK;
		String clock = Fmt.clock(left);
		Paint.big(ctx, tr, clock, 6, 17, 2, color, true);
		String status = paused ? "PAUSED" : "LIVE";
		Paint.hudText(ctx, tr, status, W - 6 - tr.getWidth(status), 6, paused ? Colors.GOLD : Colors.GREEN);
		Paint.hudText(ctx, tr, Fmt.money(total), W - 6 - tr.getWidth(Fmt.money(total)), 22, Colors.GOLD);

		float frac = a == null ? 0.6f : (float) left / Math.max(1, Math.max(a.durationMs, left));
		Paint.bar(ctx, 6, 36, W - 12, 3, frac, Colors.alpha(0xFFFFFFFF, 0x20), paused ? Colors.GOLD : Colors.accent());
		String lead = leader == null ? "No bids yet" : "Leader " + leader.player() + " " + Fmt.money(leader.total());
		Paint.hudText(ctx, tr, Paint.fit(tr, lead, W - 12), 6, 44, leader == null ? Colors.muted() : Colors.text());
	}
}
