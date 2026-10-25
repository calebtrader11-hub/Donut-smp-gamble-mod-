package com.donutgamble.client;

import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.Auction;
import com.donutgamble.core.PrivacySettings;
import com.donutgamble.core.Standing;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** Top bidders in the live auction, for viewers. Follows the auction's "viewers see" settings. */
final class LeaderboardHud extends HudElement {
	private static final int W = 176;
	private static final int ROW = 12;
	private static final String[][] SAMPLE = {{"jimbo01", "40"}, {"BeanznBread", "25"}, {"Tobias", "20"}};

	private final GambleState state;

	LeaderboardHud(GambleState state) {
		super("leaderboard", "Leaderboard");
		this.state = state;
	}

	private final RankingView ranking = new RankingView(true);

	@Override
	boolean hasContent() {
		return state.auctions.isLive() && state.auctions.current.hudLeaderboard && !state.auctions.current.rankings().isEmpty();
	}

	private int top(Auction a) {
		return compact ? Math.min(3, a.showTop) : a.showTop;
	}

	private int rows(boolean editing) {
		Auction a = state.auctions.current;
		if (a == null) {
			return editing ? SAMPLE.length : 0;
		}
		return Math.max(1, Math.min(top(a), a.rankings().size()));
	}

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		return new int[]{W, 18 + rows(editing) * ROW + 4};
	}

	@Override
	int[] defaultPosition(int screenW, int screenH, int w, int h) {
		return new int[]{screenW - w - 6, 78};
	}

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		int[] size = size(tr, editing);
		Paint.hud(ctx, 0, 0, size[0], size[1]);
		Paint.hudText(ctx, tr, "TOP BIDDERS", 6, 6, Colors.muted());
		Auction a = state.auctions.current;
		int y = 19;
		if (a == null) {
			for (int i = 0; i < SAMPLE.length; i++) {
				row(ctx, tr, i, SAMPLE[i][0], null, Integer.parseInt(SAMPLE[i][1]) / 100f, y, true);
				y += ROW;
			}
			return;
		}
		boolean amounts = a.showAmounts && !state.hides(PrivacySettings.Surface.AUCTION_HUD);
		List<Standing> ranks = a.rankings();
		// Rows glide to their new place when someone overtakes.
		ranking.reset(a);
		ranking.draw(ctx, tr, ranks, 3, y - 2, W - 6, ROW, 0, top(a), amounts, a.showShare);
	}

	private void row(DrawContext ctx, TextRenderer tr, int i, String name, String amount, float share, int y, boolean sample) {
		if (share >= 0) {
			Paint.round(ctx, 4, y - 2, Math.max(2, Math.round((W - 8) * share)), ROW - 1, 2, Colors.alpha(Colors.accent(), 0x22));
		}
		int rankColor = i == 0 ? Colors.GOLD : Colors.muted();
		Paint.hudText(ctx, tr, Integer.toString(i + 1), 6, y, rankColor);
		Paint.hudText(ctx, tr, Paint.fit(tr, name, 96), 18, y, i == 0 ? Colors.GOLD : Colors.text());
		int right = W - 6;
		if (share >= 0) {
			String pct = Math.round(share * 100) + "%";
			Paint.hudText(ctx, tr, pct, right - tr.getWidth(pct), y, Colors.muted());
			right -= 30;
		}
		if (amount != null) {
			Paint.hudText(ctx, tr, amount, right - tr.getWidth(amount), y, Colors.text());
		}
	}
}
