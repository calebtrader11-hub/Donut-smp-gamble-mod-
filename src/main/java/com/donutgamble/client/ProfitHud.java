package com.donutgamble.client;

import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.PrivacySettings;
import com.donutgamble.core.Stats;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Total profit at a glance: one number for games, auctions and everything else, plus this
 * session. While Streamer Mode hides finance, money isn't drawn; the public part (session time,
 * games, W/L/T) still is.
 */
final class ProfitHud extends HudElement {
	private final GambleState state;

	ProfitHud(GambleState state) {
		super("profit", "Profit");
		this.state = state;
	}

	private boolean hidden() {
		return state.hides(PrivacySettings.Surface.FINANCE);
	}

	private static String counts(Stats s) {
		int games = s.playerWins() + s.playerLosses() + s.ties();
		return games + (games == 1 ? " game" : " games") + " · W " + s.playerWins() + " · L " + s.playerLosses() + " · T " + s.ties();
	}

	private String clock() {
		return Fmt.duration(System.currentTimeMillis() - state.ledger.sessionStart);
	}

	private String sessionLine() {
		return "This session " + Fmt.signed(state.sessionProfit()) + " · " + clock();
	}

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		Stats s = state.ledger.sessionStats();
		if (hidden()) {
			return new int[]{Math.max(tr.getWidth("SESSION  " + clock()), tr.getWidth(counts(s))) + 12, 30};
		}
		int w = Math.max(tr.getWidth("TOTAL PROFIT") + 10, tr.getWidth(Fmt.signed(state.totalProfit())) * 2);
		if (!compact) {
			w = Math.max(w, tr.getWidth(sessionLine()));
		}
		return new int[]{Math.max(w + 12, 96), compact ? 32 : 42};
	}

	@Override
	int[] defaultPosition(int screenW, int screenH, int w, int h) {
		return new int[]{screenW - w - 6, Math.max(6, screenH / 2 - h / 2)};
	}

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		int[] size = size(tr, editing);
		Paint.hud(ctx, 0, 0, size[0], size[1]);
		if (hidden()) {
			Stats s = state.ledger.sessionStats();
			Paint.hudText(ctx, tr, "SESSION", 6, 5, Colors.muted());
			String clock = clock();
			Paint.hudText(ctx, tr, clock, size[0] - 6 - tr.getWidth(clock), 5, Colors.muted());
			Paint.hudText(ctx, tr, counts(s), 6, 17, Colors.text());
			return;
		}
		long total = state.totalProfit();
		Paint.hudText(ctx, tr, "TOTAL PROFIT", 6, 5, Colors.muted());
		Paint.big(ctx, tr, Fmt.signed(total), 6, 15, 2, Colors.profit(total), true);
		if (!compact) {
			Paint.hudText(ctx, tr, sessionLine(), 6, 32, Colors.text());
		}
	}
}
