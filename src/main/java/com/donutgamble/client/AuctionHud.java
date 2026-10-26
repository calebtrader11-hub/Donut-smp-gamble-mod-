package com.donutgamble.client;

import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.Auction;
import com.donutgamble.core.PrivacySettings;
import com.donutgamble.core.Standing;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * The stream-facing auction timer: prize and title, a big centered countdown, a time bar, and the
 * leader with the total. Shown only while an auction is live, then a results card for a few seconds.
 */
final class AuctionHud extends HudElement {
	private static final int W = 180;

	private final GambleState state;

	AuctionHud(GambleState state) {
		super("auction", "Auction timer");
		this.state = state;
	}

	@Override
	boolean hasContent() {
		return (state.auctions.isLive() && state.auctions.current.hudCard) || result() != null;
	}

	/** The auction that just ended, for a few seconds after (the results card). */
	private Auction result() {
		return state.auctions.current == null ? state.recentResult() : null;
	}

	private Auction auction(boolean editing) {
		if (state.auctions.current != null || !editing) {
			return state.auctions.current;
		}
		Auction preview = new Auction();
		preview.item = "Your prize";
		preview.itemId = "minecraft:spawner";
		preview.durationMs = 300_000;
		preview.remainingMs = 272_000;
		preview.status = Auction.Status.PAUSED;
		return preview;
	}

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		if (result() != null) {
			return new int[]{W, 46};
		}
		return new int[]{W, compact ? 40 : 72};
	}

	@Override
	int[] defaultPosition(int screenW, int screenH, int w, int h) {
		return new int[]{screenW - w - 6, 6};
	}

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		Auction done = result();
		if (done != null) {
			drawResult(ctx, tr, done);
			return;
		}
		Auction a = auction(editing);
		if (a == null) {
			return;
		}
		int h = size(tr, editing)[1];
		Paint.hud(ctx, 0, 0, W, h);
		boolean preview = state.auctions.current == null;
		long now = System.currentTimeMillis();
		long left = preview ? a.remainingMs : a.remaining(now);
		boolean paused = a.status == Auction.Status.PAUSED && !preview;
		boolean ending = !paused && !preview && left < 30_000;
		int timeColor = paused || preview ? Colors.GOLD : ending ? Colors.RED : 0xFFFFFFFF;

		// title row: prize icon, title, status dot
		ItemStack icon = preview ? new ItemStack(Items.SPAWNER) : ItemIcons.of(a);
		int textX = 6;
		if (!icon.isEmpty()) {
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(5, 4);
			ctx.getMatrices().scale(0.75f, 0.75f);
			ctx.drawItem(icon, 0, 0);
			ctx.getMatrices().popMatrix();
			textX = 20;
		}
		Paint.hudText(ctx, tr, Paint.fit(tr, a.item, W - textX - 18), textX, 6, Colors.text());
		float pulse = paused || preview ? 1f : 0.55f + 0.45f * (float) Math.sin(now / 220.0);
		Paint.round(ctx, W - 11, 7, 5, 5, 2, Colors.fade(paused || preview ? Colors.GOLD : Colors.GREEN, pulse));

		String clock = Fmt.clock(left);
		if (compact) {
			Paint.big(ctx, tr, clock, (W - tr.getWidth(clock) * 2) / 2, 18, 2, timeColor, true);
			bar(ctx, a, left, paused, ending, h - 5);
			return;
		}
		// big centered countdown, nudged on each new second while ending
		float tick = ending ? 1f - (left % 1000) / 1000f : 0f;
		float scale = 3f + (ending ? 0.25f * Math.max(0f, 1f - tick * 4f) : 0f);
		int cw = Math.round(tr.getWidth(clock) * scale);
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate((W - cw) / 2f, 19f - (scale - 3f) * 4f);
		ctx.getMatrices().scale(scale, scale);
		Paint.record(clock);
		ctx.drawText(tr, clock, 0, 0, timeColor, true);
		ctx.getMatrices().popMatrix();
		bar(ctx, a, left, paused, ending, 46);

		boolean amounts = a.showAmounts && !state.hides(PrivacySettings.Surface.AUCTION_HUD);
		Standing leader = preview ? null : a.leader();
		if (leader == null) {
			Paint.hudText(ctx, tr, preview ? "Shown while an auction is live" : "No bids yet", 6, 55, Colors.muted());
			return;
		}
		String total = amounts ? Fmt.money(leader.total()) : "";
		int tw = tr.getWidth(total);
		Paint.hudText(ctx, tr, Paint.fit(tr, "Leader " + leader.player(), W - 18 - tw), 6, 55, Colors.GOLD);
		if (amounts) {
			Paint.hudText(ctx, tr, total, W - 6 - tw, 55, Colors.text());
		}
	}

	private void bar(DrawContext ctx, Auction a, long left, boolean paused, boolean ending, int y) {
		float frac = (float) left / Math.max(1, Math.max(a.durationMs, left));
		Paint.bar(ctx, 6, y, W - 12, 3, frac, Colors.alpha(0xFFFFFFFF, 0x20), paused ? Colors.GOLD : ending ? Colors.RED : Colors.accent());
	}

	/** Results card: winner and total, shown briefly after the auction ends. */
	private void drawResult(DrawContext ctx, TextRenderer tr, Auction a) {
		long since = System.currentTimeMillis() - a.endedAt;
		float pop = Math.min(1f, since / 250f);
		Paint.hud(ctx, 0, 0, W, 46);
		boolean cancelled = a.status == Auction.Status.CANCELLED;
		int color = cancelled ? Colors.ORANGE : Colors.GOLD;
		ctx.fillGradient(1, 1, W - 1, 20, Colors.fade(Colors.alpha(color, 0x50), pop), Colors.alpha(color, 0x00));
		ItemStack icon = ItemIcons.of(a);
		int textX = 6;
		if (!icon.isEmpty()) {
			ctx.drawItem(icon, 6, 5);
			textX = 26;
		}
		Paint.hudText(ctx, tr, cancelled ? "AUCTION CANCELLED" : "SOLD", textX, 5, color);
		Paint.hudText(ctx, tr, Paint.fit(tr, a.item, W - textX - 6), textX, 15, Colors.text());
		if (cancelled || a.winner == null) {
			Paint.hudText(ctx, tr, cancelled ? "Everyone gets a refund" : "No winner", 6, 31, Colors.muted());
			return;
		}
		boolean amounts = a.showAmounts && !state.hides(PrivacySettings.Surface.AUCTION_HUD);
		Standing w = a.leader();
		Paint.hudText(ctx, tr, Paint.fit(tr, "Winner " + a.winner, W - 70), 6, 31, Colors.GOLD);
		if (amounts && w != null) {
			String total = Fmt.money(w.total());
			Paint.hudText(ctx, tr, total, W - 6 - tr.getWidth(total), 31, Colors.text());
		}
	}
}
