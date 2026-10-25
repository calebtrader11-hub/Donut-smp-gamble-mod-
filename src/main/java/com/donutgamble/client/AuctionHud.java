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
 * The stream-facing auction card: prize icon, title, description, big countdown, time bar,
 * leader and total. Shown only while an auction is live.
 */
final class AuctionHud extends HudElement {
	private static final int W = 176;

	private final GambleState state;

	AuctionHud(GambleState state) {
		super("auction", "Auction");
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
		preview.description = "Shown while an auction is live";
		preview.durationMs = 300_000;
		preview.remainingMs = 272_000;
		preview.status = Auction.Status.PAUSED;
		return preview;
	}

	private boolean hasDescription(Auction a) {
		return a != null && a.description != null && !a.description.isBlank();
	}

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		if (result() != null) {
			return new int[]{W, 46};
		}
		Auction a = auction(editing);
		if (compact) {
			return new int[]{W, 46};
		}
		return new int[]{W, 66 + (hasDescription(a) ? 10 : 0)};
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
		long left = preview ? a.remainingMs : a.remaining(System.currentTimeMillis());
		boolean paused = a.status == Auction.Status.PAUSED && !preview;

		ItemStack icon = preview ? new ItemStack(Items.SPAWNER) : ItemIcons.of(a);
		int textX = 6;
		if (!icon.isEmpty()) {
			Paint.round(ctx, 5, 5, 22, 22, 3, Colors.alpha(Colors.accent(), 0x30));
			ctx.drawItem(icon, 8, 8);
			if (a.quantity > 1) {
				ctx.drawStackOverlay(tr, icon.copyWithCount(Math.min(99, a.quantity)), 8, 8);
			}
			textX = 32;
		}
		Paint.hudText(ctx, tr, Paint.fit(tr, a.item, W - textX - 40), textX, 7, Colors.text());
		String status = preview ? "PREVIEW" : paused ? "PAUSED" : "LIVE";
		Paint.hudText(ctx, tr, status, W - 6 - tr.getWidth(status), 7, paused || preview ? Colors.GOLD : Colors.GREEN);
		int y = 18;
		if (compact) {
			int color = paused ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.INK;
			Paint.big(ctx, tr, Fmt.clock(left), 6, 22, 2, color, true);
			Standing leader = preview ? null : a.leader();
			if (leader != null) {
				String name = Paint.fit(tr, leader.player(), W - 70);
				Paint.hudText(ctx, tr, name, W - 6 - tr.getWidth(name), 26, Colors.GOLD);
			}
			float frac = (float) left / Math.max(1, Math.max(a.durationMs, left));
			Paint.bar(ctx, 6, 40, W - 12, 2, frac, Colors.alpha(0xFFFFFFFF, 0x20), paused ? Colors.GOLD : Colors.accent());
			return;
		}
		if (hasDescription(a)) {
			Paint.hudText(ctx, tr, Paint.fit(tr, a.description, W - textX - 6), textX, y, Colors.muted());
			y += 10;
		}

		int clockY = Math.max(y + 2, 30);
		int color = paused ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.INK;
		Paint.big(ctx, tr, Fmt.clock(left), 6, clockY, 2, color, true);
		boolean amounts = a.showAmounts && !state.hides(PrivacySettings.Surface.AUCTION_HUD);
		if (amounts && !preview) {
			String total = Fmt.money(a.total());
			Paint.hudText(ctx, tr, total, W - 6 - tr.getWidth(total), clockY + 4, Colors.GOLD);
		}

		float frac = (float) left / Math.max(1, Math.max(a.durationMs, left));
		Paint.bar(ctx, 6, clockY + 19, W - 12, 3, frac, Colors.alpha(0xFFFFFFFF, 0x20), paused ? Colors.GOLD : Colors.accent());
		Standing leader = preview ? null : a.leader();
		String lead = leader == null ? "No bids yet" : "Leader " + leader.player() + (amounts ? " " + Fmt.money(leader.total()) : "");
		Paint.hudText(ctx, tr, Paint.fit(tr, lead, W - 12), 6, clockY + 26, leader == null ? Colors.muted() : Colors.text());
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
		Paint.hudText(ctx, tr, cancelled ? "AUCTION CANCELLED" : "AUCTION ENDED", textX, 5, color);
		Paint.hudText(ctx, tr, Paint.fit(tr, a.item, W - textX - 6), textX, 15, Colors.text());
		if (cancelled || a.winner == null) {
			Paint.hudText(ctx, tr, cancelled ? "Everyone gets a refund" : "No winner", 6, 31, Colors.muted());
			return;
		}
		boolean amounts = a.showAmounts && !state.hides(PrivacySettings.Surface.AUCTION_HUD);
		Standing w = a.leader();
		String line = "Winner " + a.winner;
		Paint.hudText(ctx, tr, Paint.fit(tr, line, W - 70), 6, 31, Colors.GOLD);
		if (amounts && w != null) {
			String total = Fmt.money(w.total());
			Paint.hudText(ctx, tr, total, W - 6 - tr.getWidth(total), 31, Colors.text());
		}
	}
}
